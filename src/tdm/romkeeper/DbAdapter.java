package tdm.romkeeper;

import android.content.ContentValues;
import android.content.Context;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;

import java.util.ArrayList;

interface DbObserver
{
    void onContentInsert(String name);
    void onContentUpdate(String name);
    void onContentDelete(String name);
}

class DbAdapter
{
    private static class DbHelper extends SQLiteOpenHelper
    {
        private static final String DB_NAME = "roms";
        private static final int DB_VERSION = 1;

        private static final String DB_CREATE =
                "CREATE TABLE romlist (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name VARCHAR(64) NOT NULL UNIQUE, " +
                    "url VARCHAR(256) NOT NULL UNIQUE, " +
                    "filename VARCHAR(64) NOT NULL UNIQUE, " +
                    "size INTEGER, " +
                    "digest CHAR(32), " +
                    "basis VARCHAR(64), " +
                    "deltaurl VARCHAR(256), " +
                    "deltafilename VARCHAR(64), " +
                    "deltasize INTEGER, " +
                    "mtime INTEGER, " +
                    "verified INTEGER" +
                    ")";

        DbHelper(Context ctx) {
            super(ctx, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(DB_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS romlist");
            onCreate(db);
        }
    }

    private final Context       mCtx;
    private DbHelper            mHelper;
    private SQLiteDatabase      mDb;

    ArrayList<DbObserver>       mObservers;

    void registerObserver(DbObserver o) {
        mObservers.add(o);
    }
    void removeObserver(DbObserver o) {
        mObservers.remove(o);
    }

    private static DbAdapter sInstance;
    static DbAdapter getInstance(Context ctx) {
        if (sInstance == null) {
            sInstance = new DbAdapter(ctx);
        }
        return sInstance;
    }

    private DbAdapter(Context ctx) {
        mCtx = ctx;
        mHelper = new DbHelper(ctx);
        mDb = mHelper.getWritableDatabase();
        mObservers = new ArrayList<DbObserver>();
    }

    Cursor getRomCursor() {
        Cursor c = mDb.query("romlist",
                new String[] { "_id", "name" },
                null, null, null, null, null);
        return c;
    }

    void insert(Rom r) {
        ContentValues values = new ContentValues();
        values.put("name", r.getName());
        values.put("url", r.getUrl());
        values.put("filename", r.getFilename());
        values.put("size", r.getSize());
        values.put("digest", r.getDigest());
        values.put("basis", r.getBasis());
        values.put("deltaurl", r.getDeltaUrl());
        values.put("deltafilename", r.getDeltaFilename());
        values.put("deltasize", r.getDeltaSize());
        values.put("mtime", r.getModTime());
        values.put("verified", (r.getVerified() ? 1 : 0));
        mDb.insert("romlist", null, values);

        for (DbObserver o : mObservers) {
            o.onContentInsert(r.getName());
        }
    }

    void update(Rom r) {
        ContentValues values = new ContentValues();
        values.put("url", r.getUrl());
        values.put("filename", r.getFilename());
        values.put("size", r.getSize());
        values.put("digest", r.getDigest());
        values.put("basis", r.getBasis());
        values.put("deltaurl", r.getDeltaUrl());
        values.put("deltafilename", r.getDeltaFilename());
        values.put("deltasize", r.getDeltaSize());

        mDb.update("romlist",
                values,
                "name=?",
                new String[] { r.getName() });

        for (DbObserver o : mObservers) {
            o.onContentUpdate(r.getName());
        }
    }

    void delete(Rom r) {
        mDb.delete("romlist",
                "name=?",
                new String[] { r.getName() });

        for (DbObserver o : mObservers) {
            o.onContentDelete(r.getName());
        }
    }

    void deleteAll() {
        mDb.delete("romlist", null, null);
        //XXX: no observer callback...?
    }

    Rom get(String name) {
        Rom r = null;
        Cursor c = mDb.query("romlist",
                new String[] { "url", "filename", "size", "digest", "basis",
                               "deltaurl", "deltafilename", "deltasize",
                               "mtime", "verified" },
                "name=?",
                new String[] { name },
                null, null, null);
        if (c != null) {
            c.moveToFirst();
            int idx = 0;
            r = new Rom(name);
            r.setUrl(c.getString(idx++));
            r.setFilename(c.getString(idx++));
            r.setSize(c.getInt(idx++));
            r.setDigest(c.getString(idx++));
            r.setBasis(c.getString(idx++));
            r.setDeltaUrl(c.getString(idx++));
            r.setDeltaFilename(c.getString(idx++));
            r.setDeltaSize(c.getInt(idx++));
            long mtime = c.getLong(idx++);
            boolean verified = (c.getInt(idx++) != 0);
            r.setVerified(verified, mtime);
        }
        return r;
    }

    void setVerified(String name, boolean val, long mtime) {
        ContentValues values = new ContentValues();
        values.put("mtime", mtime);
        values.put("verified", (val ? 1 : 0));
        mDb.update("romlist",
                values,
                "name=?",
                new String[] { name });

        for (DbObserver o : mObservers) {
            o.onContentUpdate(name);
        }
    }
}
