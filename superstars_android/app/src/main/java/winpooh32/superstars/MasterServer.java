package winpooh32.superstars;

import android.util.Log;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import static java.lang.System.out;

public class MasterServer {

    public final String REQUEST_PULL_ITEM      = "pull_item";
    public final String REQUEST_PULL_DEVICE_ITEMS      = "pull_device_items";
    public final String REQUEST_PULL_ALL       = "pull_all";
    public final String REQUEST_PUSH_DEVICE    = "push_device";
    public final String REQUEST_PUSH_UPDATES   = "push_updates";

    public final String EVENT_CALLBACK_PULL_ITEM            = "CALLBACK_pull_item";
    public final String EVENT_CALLBACK_PULL_DEVICE_ITEMS    = "CALLBACK_pull_device_items";
    public final String EVENT_CALLBACK_PULL_ALL             = "CALLBACK_pull_all";
    public final String EVENT_CALLBACK_PUSH_DEVICE          = "CALLBACK_push_device";
    public final String EVENT_CALLBACK_PUSH_UPDATES         = "CALLBACK_push_updates";

    public final String EVENT_SERVER_UPDATE                  = "SERVER_UPDATE";


    public final String ERROR_CONNECTION_FAILED = "Connection failed";


    private Socket _socket;
    private Emitter.Listener _onConnected;
    private Emitter.Listener _onAnyError;
    private Emitter.Listener _onServerUpdate;

    private String _address;

    public MasterServer(){
    }

    private void reset(){
        disconnect();
        _address = null;
    }

    private void registerEvents(){
        //==========================================================================================
        //Обработчики служебных событий
        //==========================================================================================
        _socket.on(Socket.EVENT_CONNECT, args -> {
            if(_onConnected != null){
                _onConnected.call(args);
            }
        });
        _socket.on(Socket.EVENT_CONNECT_ERROR, args -> onConnectionError(args));
        _socket.on(Socket.EVENT_ERROR, args -> onConnectionError(args));
        _socket.on(Socket.EVENT_CONNECT_TIMEOUT, args -> onConnectionError(args));

        _socket.on(EVENT_SERVER_UPDATE, args -> {
            Log.wtf("MASTER", "UPDATE ITEMS");
            if(_onServerUpdate != null){
                _onServerUpdate.call(args);
            }
        });
    }

    private void onConnectionError(Object... args){
        Log.e("onConnectionError()", String.format("%s: %s\n", ERROR_CONNECTION_FAILED, _address));

        if(_onAnyError != null){
            _onAnyError.call(ERROR_CONNECTION_FAILED);
        }
    }

    public boolean isConnected(){
        boolean connected = false;

        if(_socket != null){
            connected = _socket.connected();
        }

        return connected;
    }

    public void connect(String address,  Emitter.Listener onConnected, Emitter.Listener onError,
                        Emitter.Listener onServerUpdate){
        _address = address;
        out.println(_address);
        connect(onConnected, onError, onServerUpdate);
    }

    private void connect( Emitter.Listener onConnected,  Emitter.Listener onError, Emitter.Listener onServerUpdate){
        _onConnected = onConnected;
        _onAnyError = onError;
        _onServerUpdate = onServerUpdate;

        disconnect();

        IO.Options opts = new IO.Options();
        opts.forceNew = true;
        opts.reconnection = true;
        opts.transports = new String[]{"websocket"}; //принудительно используем вебсокеты, вместо опросов по http
        opts.timeout = 500; // 0.5 sec

        try {
            _socket = IO.socket(_address, opts);
            registerEvents();
            _socket.connect();
        } catch (Exception e){

            if(_onAnyError != null){
                _onAnyError.call(ERROR_CONNECTION_FAILED);
            }
            e.printStackTrace();
        }
    }

    public void disconnect(){
        if(_socket != null){
            if(_socket.connected()){
                _socket.disconnect();
                _socket.close();
                _socket = null;
            }
        }
    }

    public void once(String event, Emitter.Listener fn){
        if(_socket != null){
            _socket.once(event, fn);
        }
    }

    public void emit(String event, Object... args){
        if(_socket != null){
            _socket.emit(event, args);
        }
    }
}
