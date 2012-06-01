package tdm.romkeeper;

import android.content.Context;

import android.os.PowerManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import java.util.HashMap;
import java.util.Map;

import android.util.Log;

class Rom
{
    private String              mName;
    private String              mUrl;
    private String              mFilename;
    private long                mSize;
    private String              mDigest;
    private String              mBasis;
    private String              mDeltaUrl;
    private String              mDeltaFilename;
    private long                mDeltaSize;
    private boolean             mVerified;
    private long                mModTime;

    private int toInt(String s) {
        int i = 0;
        try {
            i = Integer.parseInt(s);
        }
        catch (Exception e) {
            // Ignore it
        }
        return i;
    }

    Rom(String name) {
        mName = name;
        mSize = 0;
        mVerified = false;
        mModTime = 0;
    }

    void setUrl(String val) { mUrl = val; }
    void setFilename(String val) { mFilename = val; }
    void setSize(long val) { mSize = val; }
    void setDigest(String val) { mDigest = val; }
    void setBasis(String val) { mBasis = val; }
    void setDeltaUrl(String val) { mDeltaUrl = val; }
    void setDeltaFilename(String val) { mDeltaFilename = val; }
    void setDeltaSize(long val) { mDeltaSize = val; }
    void setVerified(boolean val, long mtime) { mVerified = val; mModTime = mtime; }

    void setSize(String val) { setSize(toInt(val)); }
    void setDeltaSize(String val) { setDeltaSize(toInt(val)); }

    String getName() { return mName; }
    String getUrl() { return mUrl; }
    String getFilename() { return mFilename; }
    long getSize() { return mSize; }
    String getDigest() { return mDigest; }
    String getBasis() { return mBasis; }
    String getDeltaUrl() { return mDeltaUrl; }
    String getDeltaFilename() { return mDeltaFilename; }
    long getDeltaSize() { return mDeltaSize; }
    boolean getVerified() { return mVerified; }
    long getModTime() { return mModTime; }

    boolean exists() {
        File f = new File("/sdcard/" + mFilename);
        return f.exists();
    }

    public String toString() {
        return mName;
    }

    void install(Context ctx) {
        if (!mVerified) {
            Log.e("RomKeeper", "not verified");
            return;
        }

        String cmd = "mkdir -p /cache/recovery " +
                     "&& echo '--update_package=" +
                     "/sdcard/" + mFilename + "' > /cache/recovery/command " +
                     "&& reboot recovery";
        String[] argv = { "su", "-c", cmd };
        String res;
        BufferedReader br = null;
        try {
            Process proc = Runtime.getRuntime().exec(argv);
            br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            res = br.readLine();

            PowerManager pm = (PowerManager)ctx.getSystemService(Context.POWER_SERVICE);
            pm.reboot("recovery");
        }
        catch (Exception e) {
            Log.e("Rom", "install failed: " + e.getMessage());
            e.printStackTrace();
        }
        finally {
            try { br.close(); } catch (Exception e) {}
            br = null;
        }
    }
}
