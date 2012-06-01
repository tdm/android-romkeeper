package tdm.romkeeper;

import android.content.Context;
import android.content.SharedPreferences;

import android.os.Handler;
import android.os.Message;
import android.os.Process;

import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

class ManifestFetcher extends Thread
{
    static final String mDefaultManifestBaseUrl = "http://vmroms.com/~vmstorage/romkeeper";

    String              mManifestBaseUrl;

    Context             mContext;
    Handler 		mHandler;
    byte[]              mData;
    int                 mDataLen;

    ManifestFetcher(Context ctx, Handler handler) {
        mContext = ctx;
        mHandler = handler;

        SharedPreferences sp = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        mManifestBaseUrl = sp.getString("manifesturl", mDefaultManifestBaseUrl);
    }

    byte[] getData() { return mData; }
    int getDataLen() { return mDataLen; }

    public void run() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            String manifestUrl = mManifestBaseUrl;
            manifestUrl += "/" + android.os.Build.MODEL;
            manifestUrl += "/manifest.xml";
            Log.i("ManifestFetcher", "manifestUrl=" + manifestUrl);
            HttpClient client = new DefaultHttpClient();
            HttpGet request = new HttpGet(manifestUrl);
            HttpResponse response = client.execute(request);
            InputStream is = response.getEntity().getContent();
            mData = new byte[64*1024];
            mDataLen = 0;
            int len = 0;
            do {
                len = is.read(mData, mDataLen, mData.length - mDataLen);
                if (len > 0) {
                    mDataLen += len;
                    if (mDataLen == mData.length) {
                        throw new Exception("Manifest too large");
                    }
                }
            }
            while (len > 0);
            is.close();
            Log.i("ManifestFetcher", "success");
        }
        catch (Exception e) {
            Log.e("ManifestFetcher", "caught exception: " + e.getMessage());
            e.printStackTrace();
        }
        Message msg = Message.obtain(mHandler, 0, this);
        mHandler.sendMessage(msg);
    }
}
