package winpooh32.superstars;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;

import io.socket.emitter.Emitter;

public class Storage {
    private static Storage _instance = null;

    private Context _context = null;
    private StorageLocal _local = null;
    private StorageRemote _remote = null;
    private MasterServer _master = null;

    private String _deviceId = null;

    private Emitter.Listener onConnect = null;
    private Emitter.Listener onError = null;
    private Emitter.Listener onServerUpdate = null;
    private Emitter.Listener onStatusChange = null;

    public static synchronized Storage  getInstance(){
        return getInstance(null, null);
    }

    public static synchronized Storage  getInstance(Context context, String deviceId){
        if(_instance == null && context != null && deviceId != null){
            _instance = new Storage(context, deviceId);
        }

        return _instance;
    }

    private Storage(Context context, String deviceId){
        _context = context;
        _deviceId = deviceId;

        _master = new MasterServer();

        _local = StorageLocal.getInstance(context);
        _remote = StorageRemote.getInstance(_master,
            //once connect
            (conArgs)->{
                _remote.initDevice(_deviceId, (Object[] remoteArgs) -> {
                    int errorCode = (Integer) remoteArgs[0];
                    String message = (String)remoteArgs[1];

                    if(StorageRemote.NO_ERROR == errorCode){
                        Log.wtf("ERROR", "Device successfully pushed!");
                    }else{
                        Log.wtf("ERROR", "Device init error: " + message);
                    }

                    if(onConnect != null){
                        onConnect.call(errorCode);
                        onConnect = null;
                    }

                    onStatusChanged(true);
                });
            },
            // once error
            (errArgs)->{
                if(onError != null){
                    onError.call();
                    onError = null;
                }

                onStatusChanged(false);
            },
            // every update
            (updateArgs) ->{
                Log.wtf("STORAGE", "(updateArgs) ->{}");
                if(onServerUpdate != null) {
                    Log.wtf("STORAGE","CALL");
                    onServerUpdate.call();
                }
            }
        );
    }

    public String getDeviceId(){
        return _deviceId;
    }

    public void pushUpdates(Emitter.Listener callback){
        Pair<RowItem[], RowRelations[]> pair = _local.getItemsForUpdate();

        if(pair.first.length > 0){
            if(_remote.isConnected()) {
                _remote.pushUpdates(pair, (args) -> {
                    _local.flushUpdateQueue();
                    if (callback != null) callback.call();
                });
            }else{
                if(callback != null) callback.call();
            }
        }else{
            if(callback != null) callback.call();
        }
    }

    private void onStatusChanged(boolean isOnline){
        if(onStatusChange != null){
            onStatusChange.call(isOnline);
        }
    }

    public void asyncOnRemoteStatusChanged(Emitter.Listener callback){
        onStatusChange = callback;
    }

    public void asyncOnConnect(Emitter.Listener connectCallback, Emitter.Listener errorCallback,
                               Emitter.Listener serverUpdateCallback){
        if(_remote.isConnected()){
            connectCallback.call();
        }else{
            onConnect = connectCallback;
            onError = errorCallback;
        }

        onServerUpdate = serverUpdateCallback;
    }

    public String generateItemHash(String deviceId){
        String result = null;

        try {
            result = HashGeneratorUtils.generateSHA1(deviceId + new Date().toString());
        }catch (Exception ex){
            Log.wtf("ERROR", ex);
        }

        return result.substring(0, 20).toUpperCase();
    }

    private String[] append(String[] strings,  String str){
        String[] result = Arrays.copyOf(strings, strings.length + 1);
        result[result.length - 1] = str;
        return result;
    }

    private void subscribeItself(RowRelations[] relations){
        for(RowRelations rel: relations){
            rel.subscribers = append(rel.subscribers, rel.creator);
        }
    }

    public void getAllItems(int page, Emitter.Listener callback){
        if(!_remote.isConnected()){
            return;
        }

        _remote.pullAllItems(page, (Object[] pullArgs) ->{

            Log.wtf("ERROR", "pullAllItems() callback");

            int errorCode = (Integer) pullArgs[0];
            String message = (String)pullArgs[1];

            RowItem[] items = (RowItem[]) pullArgs[2];
            RowRelations[] relations = (RowRelations[]) pullArgs[3];

            subscribeItself(relations);

            Pair<RowItem[], RowRelations[]> pair;

            if(_remote.NO_ERROR == errorCode){
                pair = new Pair<>(items, relations);
            }else{
                pair = new Pair<>(new RowItem[0], new RowRelations[0]);
            }

            callback.call(pair);
        });
    }

    public void getDeviceItems(String deviceId, boolean onlyLocal, boolean mirrors, Emitter.Listener callback){
        if(_remote.isConnected() && !onlyLocal){
            Log.wtf("ERROR", "GET REMOTE ITEMS " + deviceId);

            _remote.pullDeviceItems(deviceId, (Object[] pullArgs) ->{
                int errorCode = (Integer) pullArgs[0];

                if(_remote.NO_ERROR == errorCode){
                    String message = (String)pullArgs[1];

                    RowItem[] items = (RowItem[]) pullArgs[2];
                    RowRelations[] relations = (RowRelations[]) pullArgs[3];

                    _local.addItems(items);
                    _local.setRelations(relations);

                    callback.call(_local.getItemsAndRelationsByDevice(deviceId, mirrors));
                }else{
                    callback.call(null);
                }
            });
        }
        else{
            Log.wtf("ERROR", "GETTING LOCAL ITEMS " + deviceId);
            callback.call(_local.getItemsAndRelationsByDevice(deviceId, mirrors));
        }
    }

    public void createItem(RowItem item){
        long dbTimestamp = getDbTimestamp();

        item._change_date = dbTimestamp;
        item._create_date = dbTimestamp;

        ItemIndexPair index = new ItemIndexPair(item._android_id, item._hash_name);

        _local.addItems(new RowItem[]{item});
        _local.addItemSubscriber(item._local_id, item._local_id);

        _local.markItemForOnlineUpdate(index, false);
    }

    private static long getDbTimestamp() {
        Date date = new Date();
        return date.getTime() / 1000L;
    }

    public void createMirror(RowItem item, String parentHash, Emitter.Listener callback){
        _remote.pullItem(parentHash, (Object[] retItem) -> {
            if(retItem == null || retItem[0] == null){
                callback.call(StorageRemote.ERROR_REMOTE_PROBLEMS);
            }

            RowItem parentItem = (RowItem) retItem[0];
            ItemIndexPair parentIdx = new ItemIndexPair(parentItem._android_id, parentItem._hash_name);

            getDeviceItems(parentItem._android_id, false, false, (Object[] args)->{
                if(args == null || args[0] == null){
                    return;
                }

                Pair<RowItem[], RowRelations[]> myItems = (Pair<RowItem[], RowRelations[]>) args[0];
                if(myItems.first.length == 0){
                    Log.wtf("ERROR", "Item " + parentIdx.table_hash + " doesn't exist.");
                    callback.call(StorageRemote.ERROR_REMOTE_PROBLEMS);
                    return;
                }

                long dbTimestamp = getDbTimestamp();

                item._change_date = dbTimestamp;
                item._create_date = dbTimestamp;

                ItemIndexPair index = new ItemIndexPair(item._android_id, item._hash_name);

                _local.addItems(new RowItem[]{item});
                _local.addItemSubscriber(item._hash_name, index);

                _local.addItemSubscriber(parentIdx.table_hash, index);

                //Отправляем на обновление родительский отзыв, потомки обновятся автоматически
                _local.markItemForOnlineUpdate(parentIdx, false);

                callback.call(StorageRemote.NO_ERROR);
            });
        });
    }

    public void deleteItem(ItemIndexPair idx){
        _local.markItemForOnlineUpdate(idx, true);
    }

    public void onDataUpdate(){
        //
    }
}
