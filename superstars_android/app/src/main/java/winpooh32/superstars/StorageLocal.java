package winpooh32.superstars;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import android.util.Pair;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;


//Singleton storage class
public class StorageLocal {

    private final String TABLE_ITEMS = "Items";
    private final String TABLE_TAGS = "Tags";
    private final String TABLE_RELATIONS = "Relations";
    private final String TABLE_UPDATE_QUEUE = "UpdateQueue";
    

    private static final int DATABASE_VERSION = 25;
    private static final String DATABASE_NAME = "StorageLocal.sqlite";

    
    private static StorageLocal sInstance = null;

    private DBOpenHelper dbOpenHelper = null;
    private SQLiteDatabase dbRW = null;

    
    private StorageLocal(Context context) {
        dbOpenHelper = new DBOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION);
        dbRW = dbOpenHelper.getWritableDatabase();
    }

    public static synchronized StorageLocal getInstance(){
        return getInstance(null);
    }

    public static synchronized StorageLocal getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new StorageLocal(context);
        }

        return sInstance;
    }

    public RowItem[] getItemSubscribers(long itemId) {
        //------------------------------
        //Получаем список id подписчиков
        List<Long> subscribersList = new LinkedList<>();

        Cursor cursor = dbRW.query(true, TABLE_RELATIONS, new String[]{"subscriber"},
                "item=?", new String[]{Long.toString(itemId)},
                null, null, null, null
        );

        if (cursor.moveToFirst()) {
            do {
                subscribersList.add(cursor.getLong(0));
            } while (cursor.moveToNext());
        }
        cursor.close();
        //------------------------------

        RowItem[] items = new RowItem[subscribersList.size()];

        for (int i = 0; i < items.length; ++i) {
            items[i] = getItemById(subscribersList.get(i));
        }

        return items;
    }

    public void setRelations(RowRelations[] relations) {
        for (RowRelations relation : relations) {
            setItemSubscribers(
                    new ItemIndexPair(relation.creator, relation.item_hash),
                    relation.subscribers
            );
        }
    }

    public boolean addItemSubscriber(String rootHash, ItemIndexPair itemIndex){
        long rootId = findRootItemByHash(rootHash);
        long subId = findItemId(itemIndex);

        return addItemSubscriber(rootId, subId);
    }

    public boolean addItemSubscriber(long itemId, long subscriberId){

        dbRW.beginTransaction();

        ContentValues insertValues = new ContentValues();
        insertValues.put("item", itemId);
        insertValues.put("subscriber", subscriberId);

        try {
            dbRW.insertWithOnConflict(TABLE_RELATIONS, null, insertValues, SQLiteDatabase.CONFLICT_NONE);
            dbRW.setTransactionSuccessful();
        }catch (Exception ex){
            return false;
        }
        finally {
            dbRW.endTransaction();
        }

        return true;
    }

    //Перезаписывает старых подписчиков
    public void setItemSubscribers(ItemIndexPair parentIndex, String[] subscribersDevices) {
        long parentId = findItemId(parentIndex);

        if (parentId <= 0) {
            Log.wtf("ERROR", "addItemSubscribers(): parent not found.");
            return;
        }

        //Получаем индексы подписчиков
        List<Long> childrenIds = new LinkedList<>();

        //Подписываем на самого себя
        childrenIds.add(parentId);

        for (String childDevice : subscribersDevices) {

            long childId = findItemId(new ItemIndexPair(childDevice, parentIndex.table_hash));

            if (childId <= 0) {
                Log.e("setItemSubscribers()", "addItemSubscribers(): child not found.");
            }

            childrenIds.add(childId);
        }

        //Добавляем подписчиков в БД
        dbRW.beginTransaction();

        try {
            dbRW.delete(TABLE_RELATIONS, "item=" + parentId, null);

            ContentValues insertValues = new ContentValues();

            for (Long subId : childrenIds) {
                insertValues.put("item", parentId);
                insertValues.put("subscriber", subId);

                dbRW.insertWithOnConflict(TABLE_RELATIONS, null, insertValues, SQLiteDatabase.CONFLICT_NONE);

                insertValues.clear();
            }

            dbRW.setTransactionSuccessful();
        } finally {
            dbRW.endTransaction();
        }
    }

    public long findRootItemByHash(String itemHash){
        Cursor cursor = dbRW.query("Items JOIN Relations ON _id = item", new String[]{"_id"},
                String.format("hash_name = '%s' AND is_visible > 0 AND item = subscriber", itemHash),
                null, null, null, null
        );

        long itemId;

        if (cursor.moveToFirst()) {
            itemId = cursor.getLong(0);
        } else {
            itemId = -1;
        }

        cursor.close();

        return itemId;
    }

    public long findItemId(ItemIndexPair itemIndex) {
        Cursor cursor = dbRW.query(TABLE_ITEMS, new String[]{"_id"},
                String.format("android_id = '%s' AND hash_name = '%s' AND is_visible > 0",
                        itemIndex.android_id, itemIndex.table_hash
                ),
                null, null, null, null
        );

        long itemId;

        if (cursor.moveToFirst()) {
            itemId = cursor.getLong(0);
        } else {
            itemId = -1;
        }

        cursor.close();

        return itemId;
    }

    public void markItemForOnlineUpdate(ItemIndexPair itemIndex, boolean deleteIt) {
        dbRW.beginTransaction();

        try {
            long itemId = findItemId(itemIndex);
            boolean deleteCreate = false;

            if(itemId < 0){
                return;
            }

            ContentValues insertValues = new ContentValues();
            insertValues.put("item", itemId);

            if (deleteIt) {
                insertValues.put("toDelete", 1);

                //Удаляем из очереди на создание таблицы
                deleteCreate = deleteCreateFromUpdateQueue(itemId);

                if(deleteCreate){
                    //Удаляем локальную таблицу полностью, т.к. ее на сервере еще нет
                    dbRW.delete(TABLE_ITEMS, "_id="+itemId, null);
                }else {
                    ContentValues updateValues = new ContentValues();
                    updateValues.put("is_visible", 0);

                    //Скрываем табличку
                    dbRW.update(TABLE_ITEMS, updateValues, "_id=" + itemId, null);
                }
            }

            if(!deleteCreate){
                dbRW.insert(TABLE_UPDATE_QUEUE, null, insertValues);
            }

            dbRW.setTransactionSuccessful();
        } finally {
            dbRW.endTransaction();
        }
    }

    private boolean deleteCreateFromUpdateQueue(long itemId){
        return (dbRW.delete(TABLE_UPDATE_QUEUE, "item = ? AND toDelete = 0",
                    new String[]{Long.toString(itemId)} ) > 0);
    }

    private List<RowItem> getSubscribersItems(RowRelations relation){
        List<RowItem> items = new LinkedList<>();

        for(String sub: relation.subscribers){
            items.add(getItem(new ItemIndexPair(sub, relation.item_hash)));
        }

        return items;
    }

    public Pair<RowItem[], RowRelations[]> getItemsForUpdate(){
        List<RowItem> items = new LinkedList<>();
        List<RowRelations> relations = new LinkedList<>();

        Cursor cursor = dbRW.query(false, "Items JOIN UpdateQueue ON _id = item",
                new String[]{"hash_name", "android_id", "create_date", "change_date", "rating", "review",
                        "_id", "item", "toDelete"},
                null, null,
                null, null, null, null
        );

        if (cursor.moveToFirst()) {
            do {
                RowItem item = readItem(cursor.getLong(6), cursor);
                items.add(item);

                long parentId = cursor.getLong(7);
                long toDelete = cursor.getLong(8);

                if(toDelete > 0){
                    item._delete = true;
                }
                else if(parentId > 0){
                    RowItem parent = getItemById(parentId);
                    RowRelations relation = getRelationsByItemId(parentId, new ItemIndexPair(parent._android_id, item._hash_name));

                    if(relation != null){
                        relations.add(relation);
                        items.addAll(getSubscribersItems(relation));
                    }
                }
            } while (cursor.moveToNext());
        }

        return new Pair<>(
                items.toArray(new RowItem[items.size()]),
                relations.toArray(new RowRelations[relations.size()])
        );
    }

    public void flushUpdateQueue(){
        dbRW.beginTransaction();
        try {
            dbRW.execSQL("DELETE FROM UpdateQueue;");
            dbRW.setTransactionSuccessful();
        } finally {
            dbRW.endTransaction();
        }
    }

    public String[] getItemTags(Long id) {
        List<String> tags = new LinkedList<>();

        Cursor cursor = dbRW.query(false, TABLE_TAGS, new String[]{"tag"},
                "item=?", new String[]{id.toString()},
                null, null, null, null
        );

        if (cursor.moveToFirst()) {
            do {
                tags.add(cursor.getString(0));
            } while (cursor.moveToNext());
        }

        cursor.close();

        return tags.toArray(new String[tags.size()]);
    }

    private boolean isInList(List<RowItem> items, String android_id, String parent_hash) {
        for (RowItem item : items) {
            if (item._android_id.equals(android_id)
                    && item._hash_name.equals(parent_hash)) {
                return true;
            }
        }

        return false;
    }

    public Pair<RowItem[], RowRelations[]> getItemsAndRelationsByDevice(String android_id, boolean mirrors) {
        Pair<RowItem[], RowRelations[]> pair = null;

        RowItem[] items;

        if(mirrors){
            items = getMirrorItemsByDevice(android_id);
        }else{
            items = getItemsByDevice(android_id);
        }

        List<RowItem> listMyItems = new LinkedList<>();
        List<RowRelations> listRelations = new LinkedList<>();

        if (items != null) {

            for (RowItem item : items) {
                long relItemId;

                if(mirrors){
                    relItemId = item._local_parent;
                    Log.wtf("ERROR_ID", item._local_parent + " ");
                }else{
                    relItemId = item._local_id;
                }

                RowRelations relations = getRelationsByItemId(relItemId, new ItemIndexPair(android_id, item._hash_name));

                if (relations != null) {
                    listMyItems.add(item);
                    listRelations.add(relations);

                    for (String device : relations.subscribers) {
                        //пополняем список из подписчиков, если еще нет в списке
                        if (!isInList(listMyItems, device, item._hash_name)) {
                            RowItem sub = getItem(new ItemIndexPair(device, relations.item_hash));

                            if (sub == null)
                                throw new AssertionError("Object cannot be null.\nCheck Parent and Subscriber hashes, they must be same!");

                            listMyItems.add(sub);
                        }
                    }
                }
            }

        }

        return new Pair<>(
                listMyItems.toArray(new RowItem[listMyItems.size()]),
                listRelations.toArray(new RowRelations[listRelations.size()])
        );
    }

    public RowRelations getRelationsByItemId(long itemId, ItemIndexPair indexPair) {
        RowRelations relations = null;

        Cursor cursor = dbRW.query(false, TABLE_RELATIONS,
                new String[]{"subscriber"},
                "item=?", new String[]{Long.toString(itemId)},
                null, null, null, null
        );

        if (cursor.moveToFirst()) {
            relations = new RowRelations();
            relations.creator = indexPair.android_id;
            relations.item_hash = indexPair.table_hash;
            relations.subscribers = new String[cursor.getCount()];

            for (int i = 0; i < relations.subscribers.length; ++i) {
                relations.subscribers[i] = getItemById(cursor.getLong(0))._android_id;
                cursor.moveToNext();
            }
        }

        cursor.close();

        return relations;
    }

    public RowItem[] getItemsByDevice(String android_id) {
        RowItem[] items = null;

        //Выборка по android_id
        Cursor cursor = dbRW.query(false, TABLE_ITEMS,
                new String[]{"hash_name", "android_id", "create_date", "change_date", "rating", "review", "_id"},
                "android_id=? AND is_visible > 0", new String[]{android_id},
                null, null, null, null
        );

        if (cursor.moveToFirst()) {
            items = new RowItem[cursor.getCount()];

            for (int i = 0; i < items.length; ++i) {
                items[i] = readItem(cursor.getLong(6), cursor); // _id
                cursor.moveToNext();
            }
        }

        cursor.close();

        return items;
    }

    public RowItem[] getMirrorItemsByDevice(String android_id) {
        RowItem[] items = null;

        //Выборка по android_id
        Cursor cursor = dbRW.query(false, "Items JOIN Relations ON _id=subscriber",
                new String[]{"hash_name", "android_id", "create_date", "change_date", "rating", "review", "_id", "item"},
                "android_id=? AND item <> subscriber", new String[]{android_id},
                null, null, null, null
        );

        if (cursor.moveToFirst()) {
            items = new RowItem[cursor.getCount()];

            for (int i = 0; i < items.length; ++i) {
                items[i] = readItem(cursor.getLong(6), cursor); // _id
                items[i]._local_parent = cursor.getLong(7);
                cursor.moveToNext();
            }
        }

        cursor.close();

        return items;
    }

    private RowItem readItem(Long itemId, Cursor cursor) {
        RowItem item = new RowItem();

        item._hash_name = cursor.getString(0);
        item._android_id = cursor.getString(1);
        item._create_date = cursor.getLong(2);
        item._change_date = cursor.getLong(3);
        item._rating = cursor.getInt(4);
        item._review = cursor.getString(5);
        item._tags = getItemTags(itemId);

        item._local_id = itemId;

        return item;
    }

    public RowItem getItemById(Long id) {
        RowItem item = null;

        Cursor cursor = dbRW.query(false, TABLE_ITEMS,
                new String[]{"hash_name", "android_id", "create_date", "change_date", "rating", "review"},
                "_id=?", new String[]{id.toString()},
                null, null, null, null
        );

        if (cursor.moveToFirst()) {
            item = readItem(id, cursor);
        }

        cursor.close();

        return item;
    }
//
//    public RowItem[] getItemsByTags(String[] tags) {
//        List<Long> idList = new LinkedList<>();
//
//        for (String tag : tags) {
//            Cursor cursor = dbRW.query(true, TABLE_TAGS, new String[]{"item"},
//                    "tag=?", new String[]{tag},
//                    null, null, null, null
//            );
//
//            if (cursor.moveToFirst()) {
//                do {
//                    idList.add(cursor.getLong(0));
//                } while (cursor.moveToNext());
//            }
//            cursor.close();
//        }
//
//        RowItem[] items = new RowItem[idList.size()];
//
//        for (int i = 0; i < items.length; ++i) {
//            items[i] = getItemById(idList.get(i));
//        }
//
//        return items;
//    }

    public void addItems(RowItem[] items) {
        dbRW.beginTransaction();

        try {
            ContentValues tagValues = new ContentValues();
            ContentValues insertValues = new ContentValues();

            for (RowItem item : items) {

                insertValues.put("review", item._review);
                insertValues.put("rating", item._rating);
                insertValues.put("android_id", item._android_id);
                insertValues.put("hash_name", item._hash_name);
                insertValues.put("change_date", item._change_date);
                insertValues.put("create_date", item._create_date);

                Cursor cur = dbRW.query(TABLE_ITEMS, new String[]{"_id"},
                        String.format("android_id = '%s' AND hash_name = '%s'",
                                item._android_id, item._hash_name
                        ),
                        null, null, null, null
                );

                long insertRow;

                //Если такая строка уже есть, то обновляем значения в ней
                if (cur.moveToFirst()) {
                    insertRow = cur.getLong(0);
                    dbRW.update(TABLE_ITEMS, insertValues, "_id=" + insertRow, null);
                    dbRW.delete(TABLE_TAGS, "item=" + insertRow, null);
                } else {
                    insertRow = dbRW.insert(TABLE_ITEMS, null, insertValues);
                }

                item._local_id = insertRow;

                //Добавляем теги в БД
                for (String tag : item._tags) {
                    tagValues.put("item", insertRow);
                    tagValues.put("tag", tag);

                    dbRW.insert(TABLE_TAGS, null, tagValues);

                    tagValues.clear();
                }

                insertValues.clear();
                cur.close();
            }

            dbRW.setTransactionSuccessful();
        } catch (Exception e) {
            Log.w("Exception:", e);
        } finally {
            dbRW.endTransaction();
        }
    }

    public RowItem getItem(ItemIndexPair indexPair) {
        return getItemById(findItemId(indexPair));
    }

    //Управление созданием БД
    private class DBOpenHelper extends SQLiteOpenHelper {

        public DBOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
            super(context, name, factory, version);
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            final String createItems =
                    "CREATE TABLE Items (\n"
                            + "  _id INTEGER  PRIMARY KEY AUTOINCREMENT,\n"
                            + "  review TEXT,\n"
                            + "  rating INTEGER ,\n"
                            + "  android_id TEXT,\n"
                            + "  hash_name TEXT,\n"
                            + "  change_date INTEGER,\n"
                            + "  create_date INTEGER,\n"
                            + "  is_visible INTEGER DEFAULT 1,\n"
                            + "  UNIQUE (android_id, hash_name)\n"
                            + ");";

            final String createTags =
                    "CREATE TABLE Tags (\n"
                            + "  item INTEGER,\n"
                            + "  tag TEXT,\n"
                            + "  PRIMARY KEY (item, tag),\n"
                            + "  FOREIGN KEY (item) REFERENCES Items (_id)\n"
                            + ");";

            final String createRelations =
                    "CREATE TABLE Relations (\n"
                            + "  item INTEGER ,\n"
                            + "  subscriber INTEGER,\n"
                            + "  PRIMARY KEY (item, subscriber),\n"
                            + "  FOREIGN KEY (item) REFERENCES Items (_id),\n"
                            + "  FOREIGN KEY (subscriber) REFERENCES Items (_id)\n"
                            + ");";

            final String createRemoteUpdate =
                    "CREATE TABLE UpdateQueue ("
                            + "  item INTEGER PRIMARY KEY,\n"
                            + "  toDelete INTEGER DEFAULT 0,"
                            + "  parent INTEGER DEFAULT 0"
                            + ");";


            sqLiteDatabase.execSQL(createItems);
            sqLiteDatabase.execSQL("CREATE INDEX ItemIndices ON Items (android_id, hash_name);");
            sqLiteDatabase.execSQL("CREATE INDEX ItemIndices_device ON Items (android_id);");

            sqLiteDatabase.execSQL(createTags);
            sqLiteDatabase.execSQL("CREATE INDEX TagIndices ON Tags (item);");

            sqLiteDatabase.execSQL(createRelations);
            sqLiteDatabase.execSQL("CREATE INDEX RelationsIndices_item ON Relations (item);");
            sqLiteDatabase.execSQL("CREATE INDEX RelationsIndices_subscriber ON Relations (subscriber);");

            sqLiteDatabase.execSQL(createRemoteUpdate);
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
            final String[] updateQueries = new String[]{
                    "DROP TABLE IF EXISTS Items;",
                    "DROP TABLE IF EXISTS Tags;",
                    "DROP TABLE IF EXISTS Relations;",
                    "DROP TABLE IF EXISTS UpdateQueue;",

                    "DROP INDEX IF EXISTS ItemIndex;",
                    "DROP INDEX IF EXISTS ItemIndices_device;",

                    "DROP INDEX IF EXISTS TagIndices;",

                    "DROP INDEX IF EXISTS RelationsIndices_item;",
                    "DROP INDEX IF EXISTS RelationsIndices_subscriber;"};

            for (String query : updateQueries) {
                sqLiteDatabase.execSQL(query);
            }

            onCreate(sqLiteDatabase);
        }
    }
}
