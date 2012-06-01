package tdm.romkeeper;

import android.os.Handler;
import android.os.Process;

import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.URL;
import java.net.URLConnection;

class HttpFileDownloader extends FileDownloader
{
    private static final int MAX_BUFLEN = 64*1024;

    HttpFileDownloader(Handler handler, String url, String pathname, long size) {
        super(handler, url, pathname, size);
    }

    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        try {
            onStart();

            URL url = new URL(getUrl());
            URLConnection conn = url.openConnection();
            InputStream is = conn.getInputStream();
            int len;
            len = conn.getContentLength();
            if (len > 0 && len != getSize()) {
                throw new Exception("Bad content length");
            }
            Log.i("FileDownloader", "length "+len+" bytes");
            OutputStream os = new FileOutputStream(getPathname());

            byte[] buf = new byte[MAX_BUFLEN];
            while ((len = is.read(buf)) > 0) {
                os.write(buf, 0, len);
                updateBytesWritten(len);
            }

            os.close();
            is.close();

            onFinish(SUCCESS);
        }
        catch (Exception e) {
            Log.e("FileDownloader", "exception: " + e.getMessage());
            e.printStackTrace();
            onFinish(FAILURE);
        }
    }
}
