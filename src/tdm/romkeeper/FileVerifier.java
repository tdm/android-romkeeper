package tdm.romkeeper;

import android.content.Context;

import android.util.Log;

import android.os.Handler;
import android.os.Message;
import android.os.Process;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

import java.security.MessageDigest;

class FileVerifier extends Thread
{
    static final int MSG_START          = 100;
    static final int MSG_PROGRESS       = 101;
    static final int MSG_FINISH         = 102;

    static final int SUCCESS            = 0;
    static final int FAILURE            = 1;

    private Handler             mHandler;
    private String              mPathname;
    private long                mSize;
    private String              mDigest;

    private void sendStatus(int what, int arg1, int arg2) {
            mHandler.sendMessage(Message.obtain(mHandler, what, arg1, arg2));
    }
    private void sendStatus(int what, int arg1) { sendStatus(what, arg1, 0); }
    private void sendStatus(int what) { sendStatus(what, 0, 0); }

    FileVerifier(Handler handler, String pathname, long size, String digest) {
        mHandler = handler;
        mPathname = pathname;
        mSize = size;
        mDigest = digest;
    }

    public void run() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            sendStatus(MSG_START);
            File f = new File(mPathname);
            if (f.length() != mSize) {
                throw new Exception("Incorrect length");
            }

            Log.i("FileVerifier", "verify " + mPathname);
            FileInputStream is = new FileInputStream(f);
            MessageDigest digester = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[4096];
            int progress = 0;
            long total = 0;
            int len;
            while ((len = is.read(buf)) > 0) {
                digester.update(buf, 0, len);
                total += len;
                int p = (100*((int)(total>>8)))/((int)(mSize>>8));
                if (p > progress) {
                    progress = p;
                    sendStatus(MSG_PROGRESS, progress);
                }
            }
            is.close();

            byte[] calculatedDigest = digester.digest();
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < calculatedDigest.length; ++i) {
                sb.append(String.format("%02x", calculatedDigest[i]));
            }
            Log.i("FileVerifier", "Expected digest=" + mDigest);
            Log.i("FileVerifier", "Calculated digest=" + sb.toString());
            if (!sb.toString().equalsIgnoreCase(mDigest)) {
                throw new Exception("Incorrect digest");
            }
            sendStatus(MSG_FINISH, SUCCESS);
        }
        catch (Exception e) {
            Log.e("FileVerifier", "exception: " + e.getMessage());
            e.printStackTrace();
            sendStatus(MSG_FINISH, FAILURE);
        }
    }
}
