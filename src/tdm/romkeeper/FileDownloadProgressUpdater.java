package tdm.romkeeper;

import android.content.Context;

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

class FileDownloadProgressUpdater implements StreamListener
{
    static final int MSG_START          = 100;
    static final int MSG_PROGRESS       = 101;
    static final int MSG_FINISH         = 102;

    static final int SUCCESS            = 0;
    static final int FAILURE            = 1;

    private Handler             mHandler;
    private long                mSize;
    private long                mTotalWritten;

    FileDownloadProgressUpdater(Handler handler, long size) {
        mHandler = handler;
        mSize = size;
    }

    private void sendStatus(int what, int arg1, int arg2) {
        mHandler.sendMessage(Message.obtain(mHandler, what, arg1, arg2));
    }
    private void sendStatus(int what, int arg1) { sendStatus(what, arg1, 0); }
    private void sendStatus(int what) { sendStatus(what, 0, 0); }

    private int calcPercent(long n, long d) {
        int pct = (100*((int)(n>>8)))/((int)(d>>8));
        return pct;
    }

    long getSize() { return mSize; }

    public void updateBytesRead(int len) {}

    public void updateBytesWritten(int len) {
        if (mSize > 0) {
            int oldPct = calcPercent(mTotalWritten, mSize);
            int newPct = calcPercent(mTotalWritten+len, mSize);
            if (newPct > oldPct) {
                sendStatus(MSG_PROGRESS, newPct);
            }
        }
        mTotalWritten += len;
    }

    void onStart() {
        sendStatus(MSG_START);
    }

    void onFinish(int status) {
        sendStatus(MSG_FINISH, status);
    }
}
