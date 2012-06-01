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
import java.io.IOException;

import java.net.URL;
import java.net.URLConnection;

/* A base/helper class for implementing async HTTP file fetches. */
class BrowserFileDownloader extends FileDownloader
{
    private Context             mContext;

    BrowserFileDownloader(Context ctx, String url, String pathname, long size) {
        super(url, pathname, size);
        mContext = ctx;
    }
    BrowserFileDownloader(Context ctx, Handler handler, String url, String pathname, long size) {
        super(handler, url, pathname, size);
        mContext = ctx;
    }

    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        onStart();

        Log.i("BrowserFileDownloader", "downloading "+getUrl());

        Intent i = new Intent(Intent.ACTION_VIEW);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // XXX: is this what we want?
        i.setData(Uri.parse(getUrl()));
        mContext.startActivity(i);

        String filename = getPathname();
        int idx = filename.lastIndexOf('/');
        if (idx > 0) {
            filename = filename.substring(idx+1);
        }
        String dlpath = "/sdcard/download/" + filename;

        long startTime = System.currentTimeMillis();
        File f = new File(dlpath);
        long lastTime = startTime;
        long lastSize = 0;
        while (lastSize < getSize()) {
            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                // Ignore
            }
            long currentTime = System.currentTimeMillis();
            long currentSize = 0;
            if (f.exists()) {
                currentSize = f.length();
            }
            if (currentSize > lastSize) {
                updateBytesWritten((int)(currentSize - lastSize));
            }
            else {
                if (currentTime - lastTime > 60*1000) {
                    Log.e("FileDownloader", "Download failed or complete");
                    onFinish(FAILURE);
                    return;
                }
            }
            lastTime = currentTime;
            lastSize = currentSize;
        }

        File dest = new File("/sdcard/" + filename);
        f.renameTo(dest);

        Log.i("FileDownloader", "Download complete");
        onFinish(SUCCESS);
    }
}
