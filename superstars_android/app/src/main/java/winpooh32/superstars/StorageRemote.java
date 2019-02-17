package winpooh32.superstars;

import android.util.Log;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONObject;

import io.socket.emitter.Emitter;

public class StorageRemote {
    private static StorageRemote _instance = null;

    public static final int NO_ERROR                     = -1;
    public static final int ERROR_NO_CONNECTION          = 0;
    public static final int ERROR_RESPONSE_PARSE_ERROR   = 1;
    public static final int ERROR_REMOTE_PROBLEMS        = 2;


    private String _remoteURL = "http://winpooh64.tk:3384/";//winpooh64.tk
    private MasterServer _master = null;


    public static synchronized StorageRemote getInstance(){
        return getInstance(null, null, null, null);
    }

    public static synchronized StorageRemote getInstance(MasterServer master,
                                                         Emitter.Listener onConnect,
                                                         Emitter.Listener onError,
                                                         Emitter.Listener onServerUpdate){
        if(_instance == null && master != null){
            _instance = new StorageRemote(master, onConnect, onError, onServerUpdate);
        }

        return _instance;
    }

    private StorageRemote(MasterServer master, Emitter.Listener onConnect, Emitter.Listener onError,
                          Emitter.Listener onServerUpdate
    ){
        _master = master;

        _master.connect(_remoteURL,
                //Connected callback
                (Object[] args)->{
                    Log.e("REMOTE", "Connected!");
                    onConnect.call();
                },
                //Error callback
                (Object[] args) -> {
                    final String error = (String)args[0];
                    Log.e("REMOTE", error);
                    onError.call();
                },
                onServerUpdate
        );
    }

    private boolean hasConnectionErrors(Emitter.Listener callback){
        if(!_master.isConnected()){
            callback.call(ERROR_NO_CONNECTION, "No connection to remote server", null);
            return true;
        }
        return false;
    }

    public boolean isConnected(){
        if(_master != null){
            return _master.isConnected();
        }
        return false;
    }

    public void initDevice(String android_id, Emitter.Listener callback){
        if(hasConnectionErrors(callback)){return;}

        _master.emit(_master.REQUEST_PUSH_DEVICE, android_id);

        _master.once(_master.EVENT_CALLBACK_PUSH_DEVICE, (Object[] args) -> {
            String errorMsg = (String) args[0];
            int errorCode = errorMsg != null ? ERROR_REMOTE_PROBLEMS : NO_ERROR;

            callback.call(errorCode, errorMsg, null);
        });
    }

    private void parsePullResults(Object[] args, Emitter.Listener callback){
        String errorMsg = (String) args[0];
        int errorCode = NO_ERROR;

        if(errorMsg != null){
            errorCode = ERROR_REMOTE_PROBLEMS;
            callback.call(errorCode, errorMsg, null);
            return;
        }

        try {
            JSONObject result = (JSONObject)args[1];

            //Items
            JSONArray jsonItems = result.getJSONArray("items");
            RowItem[] items = new RowItem[jsonItems.length()];

            for (int i = 0; i < jsonItems.length(); i++) {
                items[i] = RowItem.castJsonObject(jsonItems.getJSONObject(i));
            }

            //Relations
            JSONArray jsonRelations = result.getJSONArray("relations");
            RowRelations[] relations = new RowRelations[jsonRelations.length()];

            for (int i = 0; i < jsonRelations.length(); i++) {
                relations[i] = RowRelations.castJsonObject(jsonRelations.getJSONObject(i));
            }

            callback.call(NO_ERROR, null, items, relations);
        }
        catch (Exception ex){
            Log.e("REMOTE", "StorageRemote.PullDeviceItems():\n" + ex);
            callback.call(ERROR_RESPONSE_PARSE_ERROR);
        }
    }

    public void pullAllItems(int page, Emitter.Listener callback){
        _master.emit(_master.REQUEST_PULL_ALL, page);

        _master.once(_master.EVENT_CALLBACK_PULL_ALL, (Object[] args) -> {
            parsePullResults(args, callback);
        });
    }

    public void pullItem(String itemHash, Emitter.Listener callback){
        _master.emit(_master.REQUEST_PULL_ITEM, itemHash);

        _master.once(_master.EVENT_CALLBACK_PULL_ITEM, (Object[] args) -> {
            try {
                JSONObject jsonItem = (JSONObject) args[0];
                RowItem item = RowItem.castJsonObject(jsonItem);
                callback.call(item);
            }catch (Exception ex){
                Log.e("REMOTE", "StorageRemote.pullItem():\n" + ex);
                callback.call(null);
            }
        });
    }

    public void pullDeviceItems(String android_id, Emitter.Listener callback){
        _master.emit(_master.REQUEST_PULL_DEVICE_ITEMS, android_id);

        _master.once(_master.EVENT_CALLBACK_PULL_DEVICE_ITEMS, (Object[] args) -> {
            parsePullResults(args, callback);
        });
    }

    public void pushUpdates(Pair<RowItem[], RowRelations[]> update, Emitter.Listener callback){
        JSONObject container = new JSONObject();

        JSONArray items = new JSONArray();
        JSONArray relations = new JSONArray();

        //fill items
        for(RowItem item: update.first){
            items.put(item.toJsonObject());
        }

        //fill relations
        for(RowRelations rel: update.second){
            relations.put(rel.toJsonObject());
        }

        try {
            container.put("items", items);
            container.put("relations", relations);
        }catch (Exception ex){

        }

        _master.emit(_master.REQUEST_PUSH_UPDATES, container);

        _master.once(_master.EVENT_CALLBACK_PUSH_UPDATES, (Object[] args) -> {
            callback.call();
        });
    }
}