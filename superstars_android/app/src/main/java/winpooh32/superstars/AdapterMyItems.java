package winpooh32.superstars;

import android.content.Context;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class AdapterMyItems extends BaseExpandableListAdapter {

    class Group {
        public int avgRating = 0;
        public RowItem item;
        public RowItem[] subscribers;
        public String label;
        public long id;

        Group(RowItem parent, RowItem[] children, long groupId){
            item = parent;
            subscribers = children;

            calcAverage();

            label = item._hash_name;
        }

        void calcAverage(){
            int sum = 0;

            for(RowItem item: subscribers){
                sum += item._rating;
            }

            avgRating = sum / subscribers.length;
        }
    }

    private Group[] _groups;
    private static LayoutInflater inflater = null;

    public AdapterMyItems(Context context, Pair<RowItem[], RowRelations[]> pair, String[] filterTags){
        List<Group> groupList = new LinkedList<>();

        long itemsCount = 0;
        boolean hasFilter = (filterTags != null);

        for(RowRelations relations: pair.second){
            boolean filterPassed = !hasFilter;

            RowItem parent = takeItem(pair.first, relations.creator, relations.item_hash);
            List<RowItem> childrenList = new LinkedList<>();

            for(String childDevice: relations.subscribers){
                RowItem child = takeItem(pair.first, childDevice, parent._hash_name);

                childrenList.add(child);

                if(hasFilter && hasTags(child._tags, filterTags)){
                    filterPassed = true;
                    child._filtered = true;
                }
            }

            if(filterPassed){
                itemsCount += childrenList.size();

                groupList.add(
                        new Group(parent,
                                childrenList.toArray(new RowItem[childrenList.size()]),
                                itemsCount)
                );
            }
        }

        _groups = groupList.toArray(new Group[groupList.size()]);

        sortSubscribers();

        inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    private boolean hasTags(String[] itemTags, String[] filterTags){
        for(String tag: itemTags){
            for(String filter: filterTags){
                if(tag.equals(filter)){
                    return true;
                }
            }
        }

        return false;
    }

    private void sortSubscribers(){
        for(Group group: _groups){
            Arrays.sort(group.subscribers);
        }
    }

    private RowItem takeItem(RowItem[] items, String android_id, String table_hash){
        for(RowItem item: items){
            if(item._android_id.equals(android_id)){
                if(item._hash_name.equals(table_hash)){
                    return  item;
                }
            }
        }

        return null;
    }

    @Override
    public int getGroupCount() {
        return _groups.length;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return _groups[groupPosition].subscribers.length;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return _groups[groupPosition];
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return _groups[groupPosition].subscribers[childPosition];
    }

    @Override
    public long getGroupId(int groupPosition) {
        return _groups[groupPosition].id;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return -(_groups[groupPosition].id + childPosition);
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        View vi = convertView;
        if (vi == null) {
            vi = inflater.inflate(R.layout.row_item_group, null);
        }

        //vi.setLongClickable(true);

        final Group group = (Group) getGroup(groupPosition);

        TextView text = (TextView) vi.findViewById(R.id.text);
        text.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        text.setText(group.label);

        TextView avgRating = (TextView) vi.findViewById(R.id.avgRating);
        avgRating.setText(Integer.toString(group.avgRating));

        return vi;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        View vi = convertView;
        if (vi == null) {
            vi = inflater.inflate(R.layout.row_item_child, null);
        }

        final RowItem item = (RowItem)getChild(groupPosition, childPosition);

        if(item._filtered){
            vi.setBackgroundColor(vi.getResources().getColor(R.color.filteredChildItem));
        }else{
            vi.setBackgroundColor(vi.getResources().getColor(R.color.colorChildItem));
        }

        TextView text = (TextView) vi.findViewById(R.id.text);
        text.setText(item._android_id);
        text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);

        TextView rating = (TextView) vi.findViewById(R.id.rating);
        rating.setText(Integer.toString(item._rating));


        TextView date = (TextView) vi.findViewById(R.id.date);
        Date stampDate = new Date(item._change_date * 1000L);
        date.setText(new SimpleDateFormat("dd.MM.yyyy").format(stampDate));

        return vi;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }
}
