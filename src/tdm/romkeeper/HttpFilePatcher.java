package tdm.romkeeper;

import android.content.Context;

import android.util.Log;

import android.os.Handler;
import android.os.Message;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.EOFException;
import java.io.IOException;

import java.net.URL;
import java.net.URLConnection;

class HttpFilePatcher extends FileDownloader
{
    private String              mDeltaPathname;
    private long                mDeltaSize;
    private String              mBasisPathname;
    private RandomAccessFile    mBasis;

    HttpFilePatcher(Handler handler,
            String deltaUrl, String deltaPathname, long deltaSize,
            String outPathname, long outSize, String basisPathname) {
        super(handler, deltaUrl, outPathname, outSize);
        mDeltaPathname = deltaPathname;
        mDeltaSize = deltaSize;
        mBasisPathname = basisPathname;
    }

    public void run() {
        try {
            onStart();

            URL url = new URL(getUrl());
            URLConnection conn = url.openConnection();
            InputStream is = conn.getInputStream();
            int len = conn.getContentLength();
            if (len > 0 && len != mDeltaSize) {
                throw new Exception("Bad content length");
            }
            Log.i("FileDownloader", "length "+len+" bytes");
            OutputStream os = new FileOutputStream(getPathname());

            FilePatcher patcher = new FilePatcher(this, mBasisPathname, is, os);
            patcher.patch();

            os.close();
            os.close();

            onFinish(SUCCESS);
        }
        catch (Exception e) {
            Log.e("FileDownloader", "exception: " + e.getMessage());
            e.printStackTrace();
            onFinish(FAILURE);
        }
    }
}
