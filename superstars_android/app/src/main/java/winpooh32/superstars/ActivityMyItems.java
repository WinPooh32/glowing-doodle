package winpooh32.superstars;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.Toast;

import java.util.Date;

import static winpooh32.superstars.AppSuperstars.ACTION_ADD_ITEM;
import static winpooh32.superstars.AppSuperstars.ACTION_ADD_MIRROR;
import static winpooh32.superstars.ParserTags.parseTags;

public class ActivityMyItems extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    public enum Page {
        MY_ITEMS,
        MY_MIRRORS,
        GLOBAL;

        public String title(Activity activity) {
            String title = "";
            switch (this) {
                case MY_ITEMS:
                    title = activity.getString(R.string.page_my_items);
                    break;
                case MY_MIRRORS:
                    title = activity.getString(R.string.page_my_mirrors);
                    break;
                case GLOBAL:
                    title = activity.getString(R.string.page_global);
                    break;
            }
            return title;
        }
    }

    private Page currentPage = Page.MY_ITEMS;

    private boolean prevOnlineStatus = true;

    private long RENEW_THRESHOLD = 5 * 1000;
    private int TIME_LOCAL = 0;
    private int TIME_ONLINE = 1;
    private long[] prevTimes = new long[]{0, 0};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_items);
        setTitle("My items");

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        setupToolbar(toolbar);
        setupNavigationPanel(toolbar);

        setupTabs();
        setupConnectionEvents();
        setupItemLists();
    }

    private void setupNavigationPanel(Toolbar toolbar) {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        //выбираем 1ю строку в навигации
        navigationView.getMenu().getItem(0).setChecked(true);
    }

    private void setupToolbar(Toolbar toolbar) {
        setSupportActionBar(toolbar);

        EditText searchBar = toolbar.findViewById(R.id.search_edit);
        searchBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                SearchAndShowItems();
            }
            return true;
        });
    }

    private void setupConnectionEvents() {
        Storage storage = Storage.getInstance();
        storage.asyncOnConnect(
                //connected
                args -> {
                    updateAndShowMyItems(false, false, null);

                    Storage syncStorage = Storage.getInstance();
                    syncStorage.pushUpdates(null);
                },
                //error
                args -> {
                    updateAndShowMyItems(false, false, null);
                },
                args -> {
                    Log.wtf("LIST", "UPDATE ITEMS ON EVENT!");
                    updateCurrentList();
                }
        );

        storage.asyncOnRemoteStatusChanged((args) -> {
            boolean connected = (boolean) args[0];

            if (connected) {
                showToast("Connected");
                prevOnlineStatus = true;
            }
            else if (prevOnlineStatus) {
                prevOnlineStatus = false;
                showToastNoRemoteAccess();
            }
        });
    }

    private void setupItemLists() {
        ExpandableListView listMyItems = (ExpandableListView) findViewById(R.id.myItemsList);
        ExpandableListView listMyMirrors = (ExpandableListView) findViewById(R.id.myMirrorsList);
        ExpandableListView listOnlineItems = (ExpandableListView) findViewById(R.id.onlineItemsList);

        setItemClickListener(listMyItems);
        setItemClickListener(listMyMirrors);
        setItemClickListener(listOnlineItems);

        registerForContextMenu(listMyItems);
        registerForContextMenu(listMyMirrors);
        registerForContextMenu(listOnlineItems);
    }

    private void setItemClickListener(ExpandableListView listView) {
        listView.setOnChildClickListener((ExpandableListView parent, View v, int groupPosition, int childPosition, long id) -> {
            RowItem item = (RowItem) parent.getExpandableListAdapter().getChild(groupPosition, childPosition);

            showDialogItemInfo(item);

            return true;
        });
    }

    private void showDialogItemInfo(RowItem item) {
        DialogFragmentItemPreview dialog = new DialogFragmentItemPreview();
        dialog._item = item;

        dialog.show(getFragmentManager(), "dialogItemPreview");
    }

    private void setupTabs() {
        TabWidget tabWidget = (TabWidget) findViewById(android.R.id.tabs);
        tabWidget.setVisibility(View.GONE);

        TabHost tabHost = (TabHost) findViewById(R.id.tabHost);

        tabHost.setup();

        TabHost.TabSpec tabSpec = tabHost.newTabSpec("1");

        tabSpec.setContent(R.id.tab1);
        tabSpec.setIndicator(Page.MY_ITEMS.title(this));
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec("2");
        tabSpec.setContent(R.id.tab2);
        tabSpec.setIndicator(Page.MY_MIRRORS.title(this));
        tabHost.addTab(tabSpec);

        tabSpec = tabHost.newTabSpec("3");
        tabSpec.setContent(R.id.tab3);
        tabSpec.setIndicator(Page.GLOBAL.title(this));
        tabHost.addTab(tabSpec);

        tabHost.setCurrentTab(currentPage.ordinal());
    }

    private void updateAndShowMyItems(boolean mirrors, boolean onlyLocal, String[] filterTags) {
        Storage storage = Storage.getInstance(null, null);

        storage.getDeviceItems(storage.getDeviceId(), onlyLocal, mirrors, (Object[] args) -> {
            if (args[0] == null) {
                return;
            }

            Pair<RowItem[], RowRelations[]> myItems = (Pair<RowItem[], RowRelations[]>) args[0];
            AdapterMyItems adapter = new AdapterMyItems(getApplicationContext(), myItems, filterTags);

            ActivityMyItems.this.runOnUiThread(() -> {
                int listId = (mirrors ? R.id.myMirrorsList : R.id.myItemsList);

                ExpandableListView listView = (ExpandableListView) findViewById(listId);
                listView.setAdapter(adapter);
                listView.setVisibility(View.VISIBLE);
            });
        });
    }

    private void pullAndShowOnlineItems(String[] filterTags) {
        Storage storage = Storage.getInstance(null, null);

        storage.getAllItems(0, (Object[] args) -> {
            if (args[0] == null) {
                return;
            }

            Pair<RowItem[], RowRelations[]> myItems = (Pair<RowItem[], RowRelations[]>) args[0];
            AdapterMyItems adapter = new AdapterMyItems(getApplicationContext(), myItems, filterTags);

            ActivityMyItems.this.runOnUiThread(() -> {
                int listId = R.id.onlineItemsList;

                ExpandableListView listView = (ExpandableListView) findViewById(listId);
                listView.setAdapter(adapter);
                listView.setVisibility(View.VISIBLE);
            });
        });
    }

    public void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }

            view.clearFocus();
        }
    }

    private void clearAndHideSearchBox() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        EditText searchBar = toolbar.findViewById(R.id.search_edit);

        searchBar.setText("");
        searchBar.setVisibility(View.GONE);
        hideKeyboard();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.controls, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_add) {
            createItem(null);
        } else if (id == R.id.action_renew) {
            if ((isRenewTimeout() && currentPage != Page.GLOBAL) ||
                    (isOnlineRenewTimeout() && currentPage == Page.GLOBAL)) {
                updateCurrentList();
            }
        } else if (id == R.id.action_search) {
            SearchAndShowItems();
        }

        return super.onOptionsItemSelected(item);
    }

    private void createItem(RowItem editItem) {
        AppSuperstars app = (AppSuperstars) getApplication();

        Intent intent = new Intent(ActivityMyItems.this, ActivityAddItem.class);

        if(editItem != null){
            intent.putExtra("edit", true);

            intent.putExtra("android_id", editItem._android_id);
            intent.putExtra("hash_name", editItem._hash_name);
            intent.putExtra("review", editItem._review);
            intent.putExtra("rating", editItem._rating);
            intent.putExtra("tags", editItem.toStringTags());
        }else{
            intent.putExtra("android_id", app.getDeviceId());
            intent.putExtra("hash_name", app.storage.generateItemHash(app.getDeviceId()));
        }

        if (currentPage == Page.MY_ITEMS) {
            intent.putExtra("request_code", ACTION_ADD_ITEM);
            startActivityForResult(intent, ACTION_ADD_ITEM);
        } else if (currentPage == Page.MY_MIRRORS) {
            StorageRemote remote = StorageRemote.getInstance();

            if (remote.isConnected()) {
                intent.putExtra("request_code", ACTION_ADD_MIRROR);
                startActivityForResult(intent, ACTION_ADD_MIRROR);
            } else {
                showToastNoRemoteAccess();
            }
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (v.getId() == R.id.myItemsList || v.getId() == R.id.myMirrorsList) {
            ExpandableListView.ExpandableListContextMenuInfo info = (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;

            if (R.id.rowGroup == info.targetView.getId()) {
                MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.context_item, menu);
                super.onCreateContextMenu(menu, v, menuInfo);
            }
        }else if(v.getId() == R.id.onlineItemsList){
            ExpandableListView.ExpandableListContextMenuInfo info = (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;

            if (R.id.rowGroup == info.targetView.getId()) {
                MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.context_online_item, menu);
                super.onCreateContextMenu(menu, v, menuInfo);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ExpandableListView.ExpandableListContextMenuInfo info = (ExpandableListView.ExpandableListContextMenuInfo) item.getMenuInfo();

        ExpandableListView expView = (ExpandableListView) info.targetView.getParent();
        ExpandableListAdapter adapter = expView.getExpandableListAdapter();

        int groupId = ExpandableListView.getPackedPositionGroup(info.packedPosition);
        AdapterMyItems.Group selectedGroup = (AdapterMyItems.Group) adapter.getGroup(groupId);

        RowItem selectedItem = selectedGroup.item;
        ItemIndexPair itemIdx = new ItemIndexPair(selectedItem._android_id, selectedItem._hash_name);

        switch (item.getItemId()) {
            case R.id.action_context_info:
                    showDialogItemInfo(selectedItem);
                break;

            case R.id.action_context_edit:
                    createItem(selectedItem);
                break;

            case R.id.action_context_delete:
                Storage storage = Storage.getInstance();
                storage.deleteItem(itemIdx);

                storage.pushUpdates((args) -> ActivityMyItems.this.runOnUiThread(this::updateCurrentList));
                break;

            default:
                return super.onContextItemSelected(item);
        }

        return true;
    }

    private boolean isRenewTimeout(long[] prevStates, int state) {
        long currentTime = new Date().getTime();

        //Обновление только раз в RENEW_THRESHOLD сек
        if (currentTime - prevStates[state] > RENEW_THRESHOLD) {
            prevStates[state] = currentTime;
            return true;
        }

        return false;
    }

    private boolean isRenewTimeout() {
        return isRenewTimeout(prevTimes, TIME_LOCAL);
    }

    private boolean isOnlineRenewTimeout() {
        return isRenewTimeout(prevTimes, TIME_ONLINE);
    }

    private void tryOnlineWithToast(String[] filterTags) {
        StorageRemote remote = StorageRemote.getInstance();
        if (!remote.isConnected()) {
            showToastNoRemoteAccess();
        } else {
            pullAndShowOnlineItems(filterTags);
        }
    }

    private void showToast(String msg){
        ActivityMyItems.this.runOnUiThread(
                ()->Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show()
        );
    }

    private void showToastNoRemoteAccess() {
        showToast("Remote server is not accessible!");
    }

    private void SearchAndShowItems() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        EditText searchBar = toolbar.findViewById(R.id.search_edit);

        if (searchBar.getVisibility() == View.VISIBLE) {
            //apply filter
            String[] tags = parseTags(searchBar.getText().toString());
            if (tags.length == 0) tags = null;

            if (currentPage == Page.MY_ITEMS) {
                updateAndShowMyItems(false, true, tags);
            } else if (currentPage == Page.MY_MIRRORS) {
                updateAndShowMyItems(true, true, tags);
            } else if (currentPage == Page.GLOBAL) {
                tryOnlineWithToast(tags);
            }

            hideKeyboard();
        } else {
            searchBar.setVisibility(View.VISIBLE);
        }
    }

    private void updateCurrentList() {
        if (currentPage == Page.MY_ITEMS) {
            updateAndShowMyItems(false, false, null);
        } else if (currentPage == Page.MY_MIRRORS) {
            updateAndShowMyItems(true, false, null);
        } else if (currentPage == Page.GLOBAL) {
            tryOnlineWithToast(null);
        }
    }

    private void showAddButton() {
        findViewById(R.id.action_add).setVisibility(View.VISIBLE);
    }

    private void hideAddButton() {
        findViewById(R.id.action_add).setVisibility(View.GONE);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        TabHost tabHost = (TabHost) findViewById(R.id.tabHost);

        if (id == R.id.nav_my_items) {
            setupPageMyItems();
        } else if (id == R.id.nav_my_mirrors) {
            setupPageMyMirrors();
        } else if (id == R.id.nav_global) {
            setupPageGlobalItems();
        }
        tabHost.setCurrentTab(currentPage.ordinal());

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);

        return true;
    }

    private void setupPageGlobalItems() {
        hideAddButton();
        clearAndHideSearchBox();

        currentPage = Page.GLOBAL;
        setTitle(currentPage.title(this));

        tryOnlineWithToast(null);
    }

    private void setupPageMyMirrors() {
        showAddButton();
        clearAndHideSearchBox();

        currentPage = Page.MY_MIRRORS;
        setTitle(currentPage.title(this));

        updateAndShowMyItems(true, true, null);
    }

    private void setupPageMyItems() {
        showAddButton();
        clearAndHideSearchBox();

        currentPage = Page.MY_ITEMS;
        setTitle(currentPage.title(this));

        updateAndShowMyItems(false, true, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == Activity.RESULT_OK &&
                (requestCode == ACTION_ADD_ITEM || requestCode == ACTION_ADD_MIRROR)) {

            String parentHash = data.getStringExtra("parent_hash");
            boolean hasParent = (parentHash != null);
            boolean isMirror = (requestCode == ACTION_ADD_MIRROR);

            RowItem item = new RowItem();
            item._android_id = data.getStringExtra("android_id");
            item._hash_name = (!hasParent ? data.getStringExtra("hash_name") : parentHash);
            item._rating = data.getIntExtra("rating", 0);
            item._review = data.getStringExtra("review");
            item._tags = data.getStringArrayExtra("tags");

            Storage storage = Storage.getInstance();

            if (!hasParent) {
                storage.createItem(item);
                updateAndShowMyItems(isMirror, true, null);

                storage.pushUpdates((args) -> updateAndShowMyItems(isMirror, true, null));
            } else {
                storage.createMirror(item, parentHash, (Object[] ret) -> {
                    if ((Integer) ret[0] == StorageRemote.NO_ERROR) {
                        storage.pushUpdates(args ->
                                updateAndShowMyItems(isMirror, true, null)
                        );
                    }
                });
            }
        }
    }

    @Override
    public void onBackPressed() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        EditText searchBar = toolbar.findViewById(R.id.search_edit);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (searchBar.getVisibility() == View.VISIBLE) {
            clearAndHideSearchBox();
            updateCurrentList();
        } else {
            super.onBackPressed();
        }
    }
}
