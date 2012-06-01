package tdm.romkeeper;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;

import android.content.Context;
import android.content.Intent;

import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import android.util.Log;

import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.security.MessageDigest;

import java.util.Arrays;

interface ManifestCheckerCallback
{
    void onManifestCheckComplete(boolean isUpdated);
}

interface IManifestChecker
{
    void doCheck(ManifestCheckerCallback cb);
}

public class ManifestCheckerService extends Service
{
    private File                        mCacheFile;
    private ManifestCheckerCallback     mCallback;
    private ManifestFetcher             mFetcher;

    public class ManifestCheckerBinder extends Binder
                                       implements IManifestChecker
    {
        private ManifestCheckerService  mService;

        ManifestCheckerBinder(ManifestCheckerService svc) {
            mService = svc;
        }
        ManifestCheckerService getService() {
            return ManifestCheckerService.this;
        }
        public void doCheck(ManifestCheckerCallback cb) {
            mService.doCheck(cb);
        }
    }
    private final ManifestCheckerBinder mBinder = new ManifestCheckerBinder(this);

    class ManifestFetcherHandler extends Handler
    {
        private ManifestCheckerService mService;

        ManifestFetcherHandler(ManifestCheckerService service) {
            mService = service;
        }

        public void handleMessage(Message msg) {
            mService.onManifestCheckDone();
        }
    }


    @Override
    public void onCreate() {
        Log.i("ManifestCheckerService", "onCreate");
        String pathname = getCacheDir() + "/manifest.xml";
        mCacheFile = new File(pathname);

        Intent alarmIntent = new Intent(this, ManifestCheckerService.class);
        PendingIntent alarmPending = PendingIntent.getService(this, 0, alarmIntent, 0);

        AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
        am.setInexactRepeating(AlarmManager.RTC,
                        System.currentTimeMillis(),
                        AlarmManager.INTERVAL_HOUR,
                        alarmPending);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("ManifestCheckerService", "onStartCommand");

        startCheck();

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        Log.i("ManifestCheckerService", "onDestroy");
    }

    private void startCheck() {
        if (mFetcher == null) {
            Handler handler = new ManifestFetcherHandler(this);
            mFetcher = new ManifestFetcher(this, handler);
            mFetcher.start();
        }
    }

    void doCheck(ManifestCheckerCallback cb) {
        mCallback = cb;
        startCheck();
    }

    boolean isManifestUpdated() {
        if (!mCacheFile.exists()) {
            Log.i("ManifestCheckerService", "updated: cache file does not exist");
            return true;
        }

        try {
            MessageDigest dataDigester = MessageDigest.getInstance("MD5");
            dataDigester.update(mFetcher.getData(), 0, mFetcher.getDataLen());
            byte[] dataDigest = dataDigester.digest();

            MessageDigest fileDigester = MessageDigest.getInstance("MD5");
            FileInputStream is = new FileInputStream(mCacheFile);
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) > 0) {
                fileDigester.update(buf, 0, len);
            }
            is.close();
            byte[] fileDigest = fileDigester.digest();

            if (Arrays.equals(dataDigest, fileDigest)) {
                return false;
            }
            Log.i("ManifestCheckerService", "updated: digests differ");
        }
        catch (Exception e) {
            Log.e("ManifestFetcher", "caught exception: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    void writeManifest() {
        try {
            FileOutputStream os = new FileOutputStream(mCacheFile);
            os.write(mFetcher.getData(), 0, mFetcher.getDataLen());
            os.close();
        }
        catch (Exception e) {
            Log.e("ManifestFetcher", "caught exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    void onManifestCheckDone() {
        boolean isUpdated = isManifestUpdated();
        if (isUpdated) {
            writeManifest();

            NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            Notification n = new Notification(R.drawable.icon, "Rom List Updated", System.currentTimeMillis());
            n.flags |= Notification.FLAG_AUTO_CANCEL;
            PendingIntent i = PendingIntent.getActivity(this, 0,
                    new Intent(this, RomKeeperActivity.class), 0);
            n.setLatestEventInfo(this, "RomKeeper", "Rom List Updated", i);
            nm.notify(1, n);
        }
        if (mCallback != null) {
            mCallback.onManifestCheckComplete(isUpdated);
            mCallback = null;
        }
        mFetcher = null;
    }
}
