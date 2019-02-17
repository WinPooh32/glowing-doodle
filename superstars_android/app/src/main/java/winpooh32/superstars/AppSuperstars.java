package winpooh32.superstars;

import android.app.Application;
import android.provider.Settings;

public class AppSuperstars extends Application {

    public static final int ACTION_ADD_ITEM = 0;
    public static final int ACTION_ADD_MIRROR = 1;

    public Storage storage = null;
    private String android_id = null;

    @Override
    public void onCreate() {
        super.onCreate();
        storage = Storage.getInstance(getApplicationContext(), getDeviceId());
    }

    public String getDeviceId(){
        if(android_id == null){
            //16 символов не вместятся на экран, обрезаем до 8
            android_id = Settings.Secure.getString(getApplicationContext().getContentResolver(),
                            Settings.Secure.ANDROID_ID).substring(0, 8);
        }

        return android_id;
    }
}
