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

class FilePatcher
{
    private static final int MAX_BUFLEN = 64*1024;

    private static final int  DELTA_MAGIC   = 0x72730236;

    private static final byte OP_END        = 0x00;

    private static final byte OP_LITERAL_N1 = 0x41;
    private static final byte OP_LITERAL_N2 = 0x42;
    private static final byte OP_LITERAL_N4 = 0x43;

    private static final byte OP_COPY_N2_N4 = 0x4b;
    private static final byte OP_COPY_N4_N2 = 0x4e;
    private static final byte OP_COPY_N4_N4 = 0x4f;

    private StreamListener      mListener;
    private String              mBasisPathname;
    private InputStream         mDeltaStream;
    private OutputStream        mOutputStream;
    private RandomAccessFile    mBasis;

    FilePatcher(StreamListener listener, String basisPathname, InputStream deltas, OutputStream out) {
        mListener = listener;
        mBasisPathname = basisPathname;
        mDeltaStream = deltas;
        mOutputStream = out;
    }

    private int readInt(int len) throws IOException {
        int val = 0;
        int b;
        while (len-- != 0) {
            b = mDeltaStream.read();
            if (b == -1) {
                throw new EOFException();
            }
            val = (val << 8) | b;
        }
        return val;
    }

    private void readHeader() throws Exception {
        if (readInt(4) != DELTA_MAGIC) {
            throw new IOException("Bad delta header");
        }
    }

    private void applyLiteral(int n) throws Exception {
        byte[] buf = new byte[MAX_BUFLEN];
        int bufsz;
        int total = readInt(n);
        int len;
        while (total > 0) {
            len = mDeltaStream.read(buf, 0, Math.min(MAX_BUFLEN, total));
            if (len == -1) {
                throw new IOException("Failed to read from deltas");
            }
            mOutputStream.write(buf, 0, len);
            mListener.updateBytesWritten(len);
            total -= len;
        }
    }
    private void applyCopy(int n1, int n2) throws Exception {
        byte[] buf = new byte[MAX_BUFLEN];
        int bufsz;
        int oldoff = readInt(n1);
        int total = readInt(n2);
        int len;
        mBasis.seek(oldoff);
        while (total > 0) {
            len = mBasis.read(buf, 0, Math.min(MAX_BUFLEN, total));
            if (len == -1) {
                throw new IOException("Failed to read from basis");
            }
            mOutputStream.write(buf, 0, len);
            mListener.updateBytesWritten(len);
            total -= len;
        }
    }

    void patch() throws Exception {
        mBasis = new RandomAccessFile(mBasisPathname, "rw");
        readHeader();
        boolean end = false;
        while (!end) {
            int cmd = mDeltaStream.read();
            switch (cmd) {
            case OP_END:        end = true;      break;
            case OP_LITERAL_N1: applyLiteral(1); break;
            case OP_LITERAL_N2: applyLiteral(2); break;
            case OP_LITERAL_N4: applyLiteral(4); break;
            case OP_COPY_N2_N4: applyCopy(2, 4); break;
            case OP_COPY_N4_N2: applyCopy(4, 2); break;
            case OP_COPY_N4_N4: applyCopy(4, 4); break;
            default:
                throw new IOException("Bad delta command");
            }
        }
        mBasis.close();
    }
}
