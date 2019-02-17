package winpooh32.superstars;

import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import static java.lang.Long.signum;

public class RowItem implements Comparable<RowItem> {
    public String   _hash_name;
    public String   _android_id;
    public long     _create_date;
    public long     _change_date;
    public int      _rating;
    public String   _review;
    public String[] _tags;


    //Для StorageLocal
    public long _local_id;
    public long _local_parent;
    public boolean _delete = false;
    //Для AdapterMyItems
    public boolean _filtered = false;


    public static RowItem castJsonObject(JSONObject object){
        RowItem item = new RowItem();

        try{
            item._hash_name = object.getString("hash_name");
            item._android_id = object.getString("android_id");
            item._create_date = object.getLong("create_date");
            item._change_date = object.getLong("last_update");
            item._rating = object.getInt("rating");
            item._review = object.getString("review");

            JSONArray jsonTags = object.getJSONArray("tags");
            item._tags = new String[jsonTags.length()];

            for(int i = 0; i < jsonTags.length(); ++i){
                item._tags[i] = jsonTags.getString(i);
            }

        }catch (Exception err){
            Log.wtf("ERROR", err.getMessage().toString());
            err.printStackTrace();
        }

        return item;
    }

    public JSONObject toJsonObject(){
        JSONObject object = new JSONObject();
        try{
            object.put("hash_name", _hash_name);
            object.put("android_id", _android_id);
            object.put("create_date", _create_date);
            object.put("last_update", _change_date);
            object.put("rating", _rating);
            object.put("review", _review);
            object.put("delete", _delete);

            JSONArray jsonTags = new JSONArray();

            for(String tag: _tags){
                jsonTags.put(tag);
            }

            object.put("tags", jsonTags);

        }catch (Exception err){
            err.printStackTrace();
        }

        return object;
    }

    public String toStringTags(){
        String tags = "";

        if(_tags.length > 0) {
            tags += _tags[0];

            for (int i = 1; i < _tags.length; ++i) {
                tags += ", " + _tags[i];
            }
        }

        return tags;
    }

    public String toString(){
        return String.format("Item:\n   %s,\n   %s,\n   %s,\n   %s,\n   %s,\n   %s,\n   %s",
                _hash_name, _android_id, _rating, _review, _create_date, _change_date, toStringTags()
        );
    }

    @Override
    public int compareTo(@NonNull RowItem o) {
        //return signum(_change_date - o._change_date);

        //для сортировки по убыванию
        return signum(o._change_date - _change_date);
    }
}
