package tdm.romkeeper;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;

import android.content.Context;
import android.content.Intent;

import android.database.sqlite.SQLiteDatabase;

import android.util.Log;

import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;

import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import java.net.URL;
import java.net.URLConnection;

import java.util.ArrayList;
import java.util.List;

/*
 * XXX:
 * Currently we update the database directly with the download/verify
 * status.  This is not really good practice, and it is probably the
 * only thing preventing this from being a general purpose class.
 */

public class FileDownloadService extends Service
{
    private static final int DS_NONE = 0;
    private static final int DS_VERIFY_EXISTING = 1;
    private static final int DS_DOWNLOAD = 2;
    private static final int DS_VERIFY_DOWNLOAD = 3;
    private static final int DS_COMPLETE = 4;

    static final String EXTRA_NAME          = "EXTRA_NAME";
    static final String EXTRA_FILENAME      = "EXTRA_FILENAME";
    static final String EXTRA_SIZE          = "EXTRA_SIZE";
    static final String EXTRA_DIGEST        = "EXTRA_DIGEST";
    static final String EXTRA_BASIS         = "EXTRA_BASIS";
    static final String EXTRA_DELTAURL      = "EXTRA_DELTAURL";
    static final String EXTRA_DELTAFILENAME = "EXTRA_DELTAFILENAME";
    static final String EXTRA_DELTASIZE     = "EXTRA_DELTASIZE";

    static final int NOTIFICATION_ID    = 1; /* XXX: need one id per file download */

    class DownloadEntry
    {
        int                     mState;
        int                     mProgress; /* Download: 0..79, Verify: 80..99 */
        String                  mUrl;
        String                  mName;
        String                  mPathname;
        long                    mSize;
        String                  mDigest;
        String                  mBasis;
        String                  mDeltaUrl;
        String                  mDeltaPathname;
        long                    mDeltaSize;
    }

    class DownloadStatusHandler extends Handler
    {
        private FileDownloadService     mOwner;
        private DownloadEntry           mEntry;

        DownloadStatusHandler(FileDownloadService owner, DownloadEntry e) {
            mOwner = owner;
            mEntry = e;
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
            case FileDownloader.MSG_START:
                break;
            case FileDownloader.MSG_PROGRESS:
                mOwner.onDownloadProgress(mEntry, msg.arg1);
                break;
            case FileDownloader.MSG_FINISH:
                mOwner.onDownloadFinish(mEntry, msg.arg1);
                break;
            default:
                Log.e("FileDownloadService", "Unknown msg.what=" + msg.what);
            }
        }
    }
    class VerifyStatusHandler extends Handler
    {
        private FileDownloadService     mOwner;
        private DownloadEntry           mEntry;

        VerifyStatusHandler(FileDownloadService owner, DownloadEntry e) {
            mOwner = owner;
            mEntry = e;
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
            case FileVerifier.MSG_START:
                break;
            case FileVerifier.MSG_PROGRESS:
                mOwner.onVerifyProgress(mEntry, msg.arg1);
                break;
            case FileVerifier.MSG_FINISH:
                mOwner.onVerifyFinish(mEntry, msg.arg1);
                break;
            default:
                Log.e("FileDownloadService", "Unknown msg.what=" + msg.what);
            }
        }
    }

    private DbAdapter                   mDbAdapter;
    private NotificationManager         mNM;
    private Notification                mNotification;
    private PendingIntent               mPendingIntent;
    private List<DownloadEntry>         mDownloads;

    private void updateProgress() {
        String status;
        int count = mDownloads.size();
        if (count == 0) {
            status = "Complete";
        }
        else if (count == 1) {
            DownloadEntry entry = mDownloads.get(0);
            if (entry.mState == DS_VERIFY_EXISTING || entry.mState == DS_VERIFY_DOWNLOAD) {
                status = "Verifying: " + entry.mProgress + "% complete";
            }
            else {
                status = "Downloading: " + entry.mProgress + "% complete";
            }
        }
        else {
            int total = 0;
            for (DownloadEntry entry : mDownloads) {
                total += entry.mProgress;
            }
            int pct = total/count;
            status = "Downloading " + count + " files: " + pct + "% complete";
        }
        mNotification.setLatestEventInfo(this, "Download", status, mPendingIntent);
        mNM.notify(NOTIFICATION_ID, mNotification);
    }

    private boolean browserDownloadRequired(String location) {
        try {
            URL url = new URL(location);
            String host = url.getHost().toLowerCase();
            if (host.indexOf("mediafire.com") != -1) {
                return true;
            }
        }
        catch (Exception e) {
            // Ignore
        }
        return false;
    }

    private void startFetch(DownloadEntry e) {
        File f = new File(e.mPathname);
        e.mState = DS_NONE;
        e.mProgress = 0;
        updateProgress();
        if (f.exists()) {
            Log.i("FileDownloadService", "verify existing");
            e.mState = DS_VERIFY_EXISTING;
            VerifyStatusHandler h = new VerifyStatusHandler(this, e);
            FileVerifier fv = new FileVerifier(h, e.mPathname, e.mSize, e.mDigest);
            fv.start();
        }
        else {
            e.mState = DS_DOWNLOAD;
            DownloadStatusHandler h = new DownloadStatusHandler(this, e);

            /*
             * TODO: figure out if mediafire is in the url.
             *   If so, launch a BrowserDownloader or BrowserPatcher.
             *   If not, launch a FileFetcher or FilePatcher.
             *
             * This should all be hidden behind the scenes, especially the
             * patching.  Or at least the FilePatcher could extend a new
             * class StreamPatcher, or something.
             */
            FileDownloader downloader;
            if (e.mBasis != null && e.mDeltaUrl != null) {
                Log.i("FileDownloadService", "patch");
                if (browserDownloadRequired(e.mDeltaUrl)) {
                    downloader = new BrowserFilePatcher(this, h,
                            e.mDeltaUrl, e.mDeltaPathname, e.mDeltaSize,
                            e.mPathname, e.mSize, e.mBasis);
                }
                else {
                    downloader = new HttpFilePatcher(h,
                            e.mDeltaUrl, e.mDeltaPathname, e.mDeltaSize,
                            e.mPathname, e.mSize, e.mBasis);
                }
            }
            else {
                Log.i("FileDownloadService", "download");
                if (browserDownloadRequired(e.mUrl)) {
                    downloader = new BrowserFileDownloader(this, h,
                            e.mUrl, e.mPathname, e.mSize);
                }
                else {
                    downloader = new HttpFileDownloader(h,
                            e.mUrl, e.mPathname, e.mSize);
                }
            }
            downloader.start();
        }
    }

    private void onDownloadProgress(DownloadEntry e, int pct) {
        e.mProgress = (pct*80)/100;
        updateProgress();
    }

    private void onDownloadFinish(DownloadEntry e, int status) {
        Log.i("FileDownloadService", "onDownloadFinish: status="+status);
        if (status == FileDownloader.SUCCESS) {
            e.mState = DS_VERIFY_DOWNLOAD;
            e.mProgress = 80;
            updateProgress();
            VerifyStatusHandler h = new VerifyStatusHandler(this, e);
            FileVerifier fv = new FileVerifier(h, e.mPathname, e.mSize, e.mDigest);
            fv.start();
            return;
        }
        mDownloads.remove(e);
        updateProgress();

        mDbAdapter.setVerified(e.mName, false, 0);

        if (mDownloads.isEmpty()) {
            stopSelf();
        }
    }

    private void onVerifyProgress(DownloadEntry e, int pct) {
        e.mProgress = 80 + (pct*20)/100;
        updateProgress();
    }

    private void onVerifyFinish(DownloadEntry e, int status) {
        Log.i("FileDownloadService", "onVerifyFinish: status="+status);
        if (e.mState == DS_VERIFY_EXISTING && status != FileVerifier.SUCCESS) {
            Log.i("FileDownloadService", "Verify failed, downloading...");
            File f = new File(e.mPathname);
            f.delete();
            startFetch(e);
            return;
        }

        mDownloads.remove(e);

        long mtime = 0;
        File f = new File(e.mPathname);
        if (f.exists()) {
            mtime = f.lastModified();
        }

        mDbAdapter.setVerified(e.mName, (status == 0), mtime);

        if (mDownloads.isEmpty()) {
            stopSelf();
        }
    }

    @Override
    public void onCreate() {
        Log.i("FileDownloadService", "onCreate");
        mDbAdapter = DbAdapter.getInstance(this);
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        mNotification = new Notification(R.drawable.icon, "Download", System.currentTimeMillis());
        mNotification.flags |= Notification.FLAG_AUTO_CANCEL;
        mPendingIntent = PendingIntent.getActivity(this, 0,
                        new Intent(this, RomKeeperActivity.class), 0);
        mDownloads = new ArrayList<DownloadEntry>();
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Log.i("FileDownloadService", "onDestroy");
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        Bundle extras = i.getExtras();
        if (extras == null) {
            Log.e("FileDownloadService", "no extras in intent");
            return START_NOT_STICKY;
        }

        DownloadEntry e = new DownloadEntry();
        e.mState = DS_NONE;
        e.mProgress = 0;
        e.mUrl = i.getData().toString();
        e.mName = extras.getString("EXTRA_NAME");
        e.mPathname = "/sdcard/" + extras.getString(EXTRA_FILENAME);
        e.mSize = extras.getLong(EXTRA_SIZE);
        e.mDigest = extras.getString(EXTRA_DIGEST);

        Log.i("FileDownloadService", "onStartCommand: url="+e.mUrl+", pathname="+e.mPathname);

        String val;
        val = extras.getString(EXTRA_BASIS);
        if (val != null && val.length() > 0) {
            e.mBasis = "/sdcard/" + val;
            Log.i("FileDownloadService", "onStartCommand: basis=" + e.mBasis);
        }
        val = extras.getString(EXTRA_DELTAURL);
        if (val != null && val.length() > 0) {
            e.mDeltaUrl = val;
            Log.i("FileDownloadService", "onStartCommand: deltaurl=" + e.mDeltaUrl);
            e.mDeltaPathname = "/sdcard/" + extras.getString(EXTRA_DELTAFILENAME);
            e.mDeltaSize = extras.getLong(EXTRA_DELTASIZE);
        }

        mDownloads.add(e);

        startFetch(e);

        return START_STICKY;
    }
}
