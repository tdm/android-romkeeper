package tdm.romkeeper;

import android.content.Context;
import android.content.Intent;

import android.net.Uri;

import android.util.Log;

import android.os.Handler;
import android.os.Message;
import android.os.Process;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.net.URL;
import java.net.URLConnection;

class BrowserFilePatcher extends FileDownloader
{
    private Context             mContext;
    private String              mDeltaPathname;
    private long                mDeltaSize;
    private String              mBasisPathname;

    BrowserFilePatcher(Context ctx, Handler handler,
            String deltaUrl, String deltaPathname, long deltaSize,
            String outPathname, long outSize, String basisPathname) {
        super(handler, deltaUrl, outPathname, outSize);
        mContext = ctx;
        mDeltaPathname = deltaPathname;
        mDeltaSize = deltaSize;
        mBasisPathname = basisPathname;
    }

    private String basename(String pathname) {
        int idx = pathname.lastIndexOf('/');
        if (idx > 0) {
            return pathname.substring(idx+1);
        }
        return pathname;
    }

    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        onStart();

        Log.i("BrowserFilePatcher", "downloading "+getUrl());

        Intent i = new Intent(Intent.ACTION_VIEW);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // XXX: is this what we want?
        i.setData(Uri.parse(getUrl()));
        mContext.startActivity(i);

        String dlpath = "/sdcard/download/" + basename(mDeltaPathname);

        long lastTime = System.currentTimeMillis();
        long lastSize = 0;
        File f = new File(dlpath);
        while (lastSize < mDeltaSize) {
            long currentTime = System.currentTimeMillis();
            long currentSize = 0;
            if (f.exists()) {
                currentSize = f.length();
            }
            if (currentSize <= lastSize) {
                if (currentTime - lastTime > 60*1000) {
                    Log.e("BrowserFilePatcher", "Download failed or stalled");
                    onFinish(FAILURE);
                    return;
                }
            }
            lastTime = currentTime;
            lastSize = currentSize;

            // XXX: update progress...???

            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                // Ignore
            }
        }

        try {
            InputStream is = new FileInputStream(f);
            OutputStream os = new FileOutputStream(getPathname());
            FilePatcher patcher = new FilePatcher(this, mBasisPathname, is, os);
            patcher.patch();

            f.delete();

            Log.i("BrowserFilePatcher", "Download complete");
            onFinish(SUCCESS);
        }
        catch (Exception e) {
            Log.e("BrowserFilePatcher", "Download failed or incomplete");
            onFinish(FAILURE);
        }
    }
}
