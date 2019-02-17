package winpooh32.superstars;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

public class RowRelations {
    public String item_hash;
    public String creator;
    public String[] subscribers;

    public static RowRelations castJsonObject(JSONObject object){
        RowRelations relation = new RowRelations();

        try {
            relation.item_hash = object.getString("item_hash");
            relation.creator = object.getString("creator_device");

            JSONArray subs = object.getJSONArray("subscribers");

            relation.subscribers = new String[subs.length()];

            for(int i = 0; i < subs.length(); ++i){
                relation.subscribers[i] = subs.getString(i);
            }
        }
        catch (Exception ex) {
            Log.e("castJsonObject()", ex.getMessage());
            ex.printStackTrace();
        }

        return relation;
    }

    public JSONObject toJsonObject(){
        JSONObject object = new JSONObject();

        JSONArray subs = new JSONArray();
        for(String sub: subscribers){
            subs.put(sub);
        }

        try {
            object.put("item_hash", item_hash);
            object.put("creator_device", creator);
            object.put("subscribers", subs);
        }catch (Exception ex){
        }

        return object;
    }
}