package winpooh32.superstars;

import java.util.TreeSet;

public class ParserTags {

    static public String[] parseTags(String rawTags){
        String[] splitted = rawTags.split(",");

        if(splitted.length == 0){
            return splitted;
        }

        TreeSet<String> normalizedSet = new TreeSet<>();

        for(String tag: splitted){
            String norm = tag.replaceAll("\n", "").trim();
            if(tag.length() > 2){
                normalizedSet.add(norm);
            }
        }

        return normalizedSet.toArray(new String[normalizedSet.size()]);
    }
}
