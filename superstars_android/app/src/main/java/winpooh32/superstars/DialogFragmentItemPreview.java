package winpooh32.superstars;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.w3c.dom.Text;

public class DialogFragmentItemPreview extends DialogFragment {
    //Устанавливается извне перед запуском
    public RowItem _item;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = getActivity().getLayoutInflater();

        View view = inflater.inflate(R.layout.fragment_item_preview, null);

        TextView itemHash = (TextView) view.findViewById(R.id.itemHashValue);
        TextView device = (TextView) view.findViewById(R.id.deviceValue);
        TextView rating = (TextView) view.findViewById(R.id.ratingValue);
        TextView review = (TextView) view.findViewById(R.id.reviewValue);
        TextView tags = (TextView) view.findViewById(R.id.tagsValue);

        itemHash.setText(_item._hash_name);
        device.setText(_item._android_id);
        rating.setText(Integer.toString(_item._rating));
        review.setText(_item._review);
        tags.setText(_item.toStringTags());

        builder.setView(view)
                // Add action buttons
                .setPositiveButton("Ok", (dialog, id) -> {
                });

        return builder.create();
    }
}
