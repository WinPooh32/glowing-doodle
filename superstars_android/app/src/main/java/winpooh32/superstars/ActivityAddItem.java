package winpooh32.superstars;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import static winpooh32.superstars.AppSuperstars.ACTION_ADD_ITEM;
import static winpooh32.superstars.AppSuperstars.ACTION_ADD_MIRROR;
import static winpooh32.superstars.ParserTags.parseTags;

public class ActivityAddItem extends AppCompatActivity {
    private String parentHash;

    private RowItem item = new RowItem();
    private int requestCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_item);

        setupSeekBar();

        Intent data = getIntent();

        item._android_id = data.getStringExtra("android_id");
        item._hash_name = data.getStringExtra("hash_name");

        //Режим редактирования
        boolean edit = data.getBooleanExtra("edit", false);
        if(edit){
            item._review = data.getStringExtra("review");
            item._rating = data.getIntExtra("rating", 0);

            String txtTags = data.getStringExtra("tags");

            SeekBar barRating = (SeekBar)findViewById(R.id.seekBar);
            barRating.setProgress(item._rating);

            ((TextView)findViewById(R.id.reviewValue)).setText(item._review);
            ((TextView)findViewById(R.id.textRating)).setText(Integer.toString(item._rating));
            ((TextView)findViewById(R.id.tagsValue)).setText(txtTags);

            parentHash = item._hash_name;
        }else{
            parentHash = data.getStringExtra("parent_hash");
        }

        requestCode = data.getIntExtra("request_code", -1);

        ((TextView)findViewById(R.id.deviceValue)).setText(item._android_id);
        ((TextView)findViewById(R.id.itemHashValue)).setText(item._hash_name);


        if(requestCode == ACTION_ADD_MIRROR){
            ((TableRow)findViewById(R.id.parentTableRow)).setVisibility(View.VISIBLE);
            ((TableRow)findViewById(R.id.hashRow)).setVisibility(View.GONE);

            if(parentHash != null){
                EditText parentHashEdit = (EditText)findViewById(R.id.parentHashValue);

                parentHashEdit.setText(parentHash);
                parentHashEdit.setEnabled(false); // только для чтения
            }
        }
    }

    private void setupSeekBar() {
        TextView textRating = (TextView) findViewById(R.id.textRating);
        SeekBar barRating = (SeekBar)findViewById(R.id.seekBar);

        textRating.setText(Integer.toString(barRating.getProgress()));

        barRating.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textRating.setText(Integer.toString(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    public void onCancel(View v){
        Close(null);
    }

    public void onCreateItem(View v){
        Intent data = new Intent();

        EditText editReview = (EditText)findViewById(R.id.reviewValue);
        EditText editTags = (EditText)findViewById(R.id.tagsValue);
        SeekBar barRating = (SeekBar)findViewById(R.id.seekBar);

        data.putExtra("hash_name", item._hash_name);
        data.putExtra("android_id", item._android_id);
        data.putExtra("review", editReview.getText().toString());
        data.putExtra("rating", barRating.getProgress());
        data.putExtra("tags", parseTags(editTags.getText().toString()));

        if(requestCode == ACTION_ADD_MIRROR){
            EditText editParent = (EditText)findViewById(R.id.parentHashValue);
            data.putExtra("parent_hash", editParent.getText().toString());
        }

        Close(data);
    }

    private void Close(Intent data){
        if(data != null){
            setResult(Activity.RESULT_OK, data);
        }else{
            setResult(Activity.RESULT_CANCELED, new Intent());
        }

        finish();
    }
}
