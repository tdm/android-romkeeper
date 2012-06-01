package tdm.romkeeper;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.ListActivity;
import android.app.PendingIntent;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;

import android.net.Uri;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.Parcelable;

import android.util.Log;

import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;

import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import android.widget.RelativeLayout;

import java.util.ArrayList;

import android.content.BroadcastReceiver;

public class RomKeeperActivity extends Activity
                               implements AdapterView.OnItemClickListener,
                                          ManifestCheckerCallback,
                                          DbObserver
{
    static final String         TAG = "RomKeeper";

    Rom                                 mCurrentRom;
    private boolean                     mShowingDetail;

    private ManifestCheckerService      mManifestCheckerService;
    private ServiceConnection mManifestCheckerConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mManifestCheckerService = ((ManifestCheckerService.ManifestCheckerBinder)service).getService();
        }
        public void onServiceDisconnected(ComponentName className) {
            mManifestCheckerService = null;
        }
    };

    private ListView                    mRomListView;
    private ArrayAdapter<Rom>           mRomListAdapter;

    private ListView                    mRomDetailView;
    private ArrayAdapter<String>        mRomDetailAdapter;

    private DbAdapter                   mDb;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        bindService(new Intent(RomKeeperActivity.this, ManifestCheckerService.class),
                mManifestCheckerConnection, Context.BIND_AUTO_CREATE);

        /*
         * This does not work, returns NULL.  WTF?!?
         * mRomListView = (ListView)findViewById(R.id.romlist_view);
         */
        mRomListView = new ListView(this);
        mRomListAdapter = new ArrayAdapter<Rom>(this,
                R.layout.list_item, new ArrayList<Rom>());
        mRomListView.setAdapter(mRomListAdapter);
        mRomListView.setOnItemClickListener(this);

        mRomDetailView = new ListView(this);
        mRomDetailAdapter = new ArrayAdapter<String>(this,
                R.layout.list_item, new ArrayList<String>());
        mRomDetailView.setAdapter(mRomDetailAdapter);

        /* XXX: should not do this in the UI thread */
        mDb = DbAdapter.getInstance(this);

        Cursor c = mDb.getRomCursor();
        if (c.moveToFirst()) {
            while (!c.isAfterLast()) {
                String name = c.getString(1);
                Log.i("RomKeeperActivity", "onCreate: found rom " + name);
                Rom r = mDb.get(name);
                mRomListAdapter.add(r);
                c.moveToNext();
            }
        }
        c.close();

        mDb.registerObserver(this);

        showRomList();
    }

    protected void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
    }

    protected void onSaveInstanceState(Bundle state) {
        Log.i(TAG, "onSaveInstanceState");
    }

    protected void onPause() {
        Log.e(TAG, "onPause");
        super.onPause();
        // ...?
    }

    protected void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    protected void onDestroy() {
        unbindService(mManifestCheckerConnection);

        super.onDestroy();
    }

    void showRomList() {
        mShowingDetail = false;
        mCurrentRom = null;

        setContentView(mRomListView);
    }

    void showRomDetail(Rom r) {
        mShowingDetail = true;
        mCurrentRom = r;

        refreshRomDetail();
        setContentView(mRomDetailView);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "onCreateOptionsMenu");
        return super.onCreateOptionsMenu(menu);
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.i(TAG, "onPrepareOptionsMenu");
        menu.clear();
        MenuInflater inflater = getMenuInflater();
        if (mShowingDetail) {
            inflater.inflate(R.menu.romdetail_options_menu, menu);
        }
        else {
            inflater.inflate(R.menu.romlist_options_menu, menu);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private void doCheckManifest() {
        mManifestCheckerService.doCheck(this);
    }

    public void onManifestCheckComplete(boolean isUpdated) {
        Log.i("RomKeeperActivity", "onManifestCheckComplete");
        if (isUpdated) {
            ManifestReader reader = new ManifestReader();
            ArrayList<Rom> newList = reader.readManifest(getCacheDir());

            /* XXX: should not do this in the UI thread */
            mDb.deleteAll();
            mRomListAdapter.clear();
            mRomListAdapter.notifyDataSetChanged();
            for (Rom r : newList) {
                mDb.insert(r);
            }
        }
    }

    private void refreshRomDetail() {
        mRomDetailAdapter.clear();
        Rom r = mCurrentRom;

        String status = "not present";
        if (r.exists()) {
            status = "present";
        }
        if (r.getVerified()) {
            status = "verified";
        }

        mRomDetailAdapter.add("name: " + r.getName());
        mRomDetailAdapter.add("filename: " + r.getFilename());
        mRomDetailAdapter.add("size: " + r.getSize());
        mRomDetailAdapter.add("digest: " + r.getDigest());
        mRomDetailAdapter.add("status: " + status);
        mRomDetailAdapter.notifyDataSetChanged();
    }

    private void fetchRom() {
        if (mCurrentRom == null) {
            Log.e(TAG, "fetchRom: no current rom");
            return;
        }

        Intent i = new Intent(this, FileDownloadService.class);
        i.setData(Uri.parse(mCurrentRom.getUrl()));
        i.putExtra(FileDownloadService.EXTRA_NAME, mCurrentRom.getName());
        i.putExtra(FileDownloadService.EXTRA_FILENAME, mCurrentRom.getFilename());
        i.putExtra(FileDownloadService.EXTRA_SIZE, mCurrentRom.getSize());
        i.putExtra(FileDownloadService.EXTRA_DIGEST, mCurrentRom.getDigest());
        i.putExtra(FileDownloadService.EXTRA_BASIS, mCurrentRom.getBasis());
        i.putExtra(FileDownloadService.EXTRA_DELTAURL, mCurrentRom.getDeltaUrl());
        i.putExtra(FileDownloadService.EXTRA_DELTAFILENAME, mCurrentRom.getDeltaFilename());
        i.putExtra(FileDownloadService.EXTRA_DELTASIZE, mCurrentRom.getDeltaSize());
        startService(i);
    }

    void onFetchRomUpdate() {
        refreshRomDetail(); //XXX: inefficient
    }

    void onFetchRomDone() {
        Toast.makeText(this, "Fetch complete", Toast.LENGTH_SHORT).show();
    }

    private void installRom() {
        if (mCurrentRom == null) {
            Log.e(TAG, "installRom: no current rom");
            return;
        }
        mCurrentRom.install(this);
    }

    private void showSettings() {
        startActivity(new Intent(this, RomKeeperPreferenceActivity.class));
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_refresh:
            doCheckManifest();
            break;
        case R.id.menu_fetch:
            fetchRom();
            break;
        case R.id.menu_install:
            installRom();
            break;
        case R.id.menu_settings:
            showSettings();
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Log.i(TAG, "onItemClick: pos=" + position + ", id=" + id);
        if (mShowingDetail) {
            // XXX: do anything useful here?
            return;
        }
        Rom r = mRomListAdapter.getItem(position);
        showRomDetail(r);
    }

    public void onBackPressed() {
        Log.i("RomKeeperActivity", "onBackPressed");
        if (mShowingDetail) {
            Log.i("RomKeeperActivity", ".. showing rom list");
            showRomList();
            return;
        }
        Log.i("RomKeeperActivity", ".. calling super");
        super.onBackPressed();
    }

    public void onContentInsert(String name) {
        Rom r = mDb.get(name);
        mRomListAdapter.add(r);
        mRomListAdapter.notifyDataSetChanged();
    }
    public void onContentUpdate(String name) {
        if (mShowingDetail && mCurrentRom != null && mCurrentRom.getName().equals(name)) {
            mCurrentRom = mDb.get(name);
            refreshRomDetail();
        }
    }
    public void onContentDelete(String name) {
        Rom r = mDb.get(name);
        mRomListAdapter.remove(r);
        if (mShowingDetail && mCurrentRom != null && mCurrentRom.getName().equals(name)) {
            showRomList();
        }
    }
}
