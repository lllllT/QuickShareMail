package org.tamanegi.quicksharemail.content;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

public class SendToDB
{
    private static final String TAG = "QuickShareMail";

    private static final String DATABASE_NAME = "sendto.db";
    private static final int DATABASE_VERSION = 1;

    private DBHelper db_helper;

    public SendToDB(Context context)
    {
        db_helper = new DBHelper(context);
    }

    public void close()
    {
        db_helper.close();
    }

    public long addSendinfo(SendToContent info)
    {
        return updateSendinfo(info, false);
    }

    public long updateSendinfo(SendToContent info)
    {
        return updateSendinfo(info, true);
    }

    private long updateSendinfo(SendToContent info, boolean is_update)
    {
        ContentValues info_vals = new ContentValues();
        info_vals.put(SendinfoColumns.LABEL, info.getLabel());
        info_vals.put(SendinfoColumns.SUBJECT_FORMAT, info.getSubjectFormat());
        info_vals.put(SendinfoColumns.BODY_FORMAT, info.getBodyFormat());
        info_vals.put(SendinfoColumns.ALLOW_TYPE, info.getAllowType());
        info_vals.put(SendinfoColumns.PRIORITY, info.getPriority());
        info_vals.put(SendinfoColumns.ENABLE, (info.isEnable() ? 1 : 0));
        info_vals.put(SendinfoColumns.ALTERNATE, (info.isAlternate() ? 1 : 0));

        SQLiteDatabase db = db_helper.getWritableDatabase();

        db.beginTransaction();
        try {
            long info_id;
            if(is_update) {
                info_id = info.getId();

                String[] where_args = new String[] { String.valueOf(info_id) };
                db.update(SendinfoColumns.TABLE_NAME, info_vals,
                          SendinfoColumns._ID + " = ?", where_args);
                db.delete(AddressColumns.TABLE_NAME,
                          AddressColumns.SENDINFO_ID + " = ?", where_args);
            }
            else {
                // insert to sendinfo db
                info_id = db.insertOrThrow(
                    SendinfoColumns.TABLE_NAME, SendinfoColumns.LABEL,
                    info_vals);
                info.setId(info_id);
            }

            // insert to sendinfo-address db
            ContentValues addr_vals = new ContentValues();
            addr_vals.put(AddressColumns.SENDINFO_ID, info_id);

            int address_cnt = info.getAddressCount();
            for(int i = 0; i < address_cnt; i++) {
                addr_vals.put(AddressColumns.ADDRESS, info.getAddress(i));

                db.insertOrThrow(
                    AddressColumns.TABLE_NAME, AddressColumns.SENDINFO_ID,
                    addr_vals);
            }

            db.setTransactionSuccessful();

            return info_id;
        }
        finally {
            db.endTransaction();
            db.close();
        }
    }

    public void deleteSendinfo(SendToContent info)
    {
        SQLiteDatabase db = db_helper.getWritableDatabase();

        db.beginTransaction();
        try {
            String[] where_args = new String[] { String.valueOf(info.getId()) };

            db.delete(SendinfoColumns.TABLE_NAME,
                      SendinfoColumns._ID + " = ?", where_args);
            db.delete(AddressColumns.TABLE_NAME,
                      AddressColumns.SENDINFO_ID + " = ?", where_args);

            db.setTransactionSuccessful();
        }
        finally {
            db.endTransaction();
            db.close();
        }
    }

    public void deleteAll()
    {
        SQLiteDatabase db = db_helper.getWritableDatabase();

        db.beginTransaction();
        try {
            db.delete(SendinfoColumns.TABLE_NAME, null, null);
            db.delete(AddressColumns.TABLE_NAME, null, null);

            db.setTransactionSuccessful();
        }
        finally {
            db.endTransaction();
            db.close();
        }
    }

    public SendToContent[] getSendinfo(String type, boolean alternate)
    {
        return getSendinfo(
            SendinfoColumns.ENABLE + " = 1 AND " +
            SendinfoColumns.ALTERNATE + " = ? AND " +
            "? GLOB " + SendinfoColumns.ALLOW_TYPE, // WHERE
            new String[] { (alternate ? "1" : "0"), type });
    }

    public SendToContent[] getAllSendinfo()
    {
        return getSendinfo(null, null);
    }

    public SendToContent getSendinfoById(long id)
    {
        SendToContent[] info = getSendinfo(
            SendinfoColumns._ID + " = ?",       // WHERE
            new String[] { String.valueOf(id) });
        return (info != null ? info[0] : null);
    }

    private SendToContent[] getSendinfo(String where, String[] where_args)
    {
        SQLiteDatabase db = db_helper.getReadableDatabase();

        Cursor info_cur = db.query(
            SendinfoColumns.TABLE_NAME, SendinfoColumns.ALL_COLUMNS,
            where, where_args,
            null, null,
            SendinfoColumns.PRIORITY + " DESC," +
            SendinfoColumns.LABEL + " DESC");   // ORDER BY
        try {
            int info_cnt = info_cur.getCount();
            if(info_cnt < 1) {
                return null;
            }

            SendToContent[] info = new SendToContent[info_cnt];

            for(int i = 0; i < info_cnt; i++) {
                info_cur.moveToNext();
                long info_id = info_cur.getLong(SendinfoColumns.COL_IDX_ID);

                info[i] = new SendToContent();
                info[i].setId(info_id);
                info[i].setLabel(
                    info_cur.getString(SendinfoColumns.COL_IDX_LABEL));
                info[i].setSubjectFormat(
                    info_cur.getString(SendinfoColumns.COL_IDX_SUBJECT_FORMAT));
                info[i].setBodyFormat(
                    info_cur.getString(SendinfoColumns.COL_IDX_BODY_FORMAT));
                info[i].setAllowType(
                    info_cur.getString(SendinfoColumns.COL_IDX_ALLOW_TYPE));
                info[i].setPriority(
                    info_cur.getInt(SendinfoColumns.COL_IDX_PRIORITY));
                info[i].setEnable(
                    (info_cur.getInt(SendinfoColumns.COL_IDX_ENABLE) != 0));
                info[i].setAlternate(
                    (info_cur.getInt(SendinfoColumns.COL_IDX_ALTERNATE) != 0));

                Cursor addr_cur = db.query(
                    AddressColumns.TABLE_NAME, AddressColumns.ALL_COLUMNS,
                    AddressColumns.SENDINFO_ID + " = ?", // WHERE
                    new String[] { String.valueOf(info_id) },
                    null, null, null);
                try {
                    int addr_cnt = addr_cur.getCount();
                    String[] addr = new String[addr_cnt];

                    for(int j = 0; j < addr_cnt; j++) {
                        addr_cur.moveToNext();
                        addr[j] = addr_cur.getString(
                            AddressColumns.COL_IDX_ADDRESS);
                    }

                    info[i].setAddress(addr);
                }
                finally {
                    addr_cur.close();
                }
            }

            return info;
        }
        finally {
            info_cur.close();
            db.close();
        }
    }

    public static final class SendinfoColumns implements BaseColumns
    {
        public static final String TABLE_NAME = "sendinfo";

        public static final String LABEL = "label";
        public static final String SUBJECT_FORMAT = "subject_format";
        public static final String BODY_FORMAT = "body_format";
        public static final String ALLOW_TYPE = "allow_type";
        public static final String PRIORITY = "priority";
        public static final String ENABLE = "enable";
        public static final String ALTERNATE = "alternate";

        public static final String[] ALL_COLUMNS = new String[] {
            _ID, LABEL, SUBJECT_FORMAT, BODY_FORMAT,
            ALLOW_TYPE, PRIORITY, ENABLE, ALTERNATE
        };
        public static final int COL_IDX_ID = 0;
        public static final int COL_IDX_LABEL = 1;
        public static final int COL_IDX_SUBJECT_FORMAT = 2;
        public static final int COL_IDX_BODY_FORMAT = 3;
        public static final int COL_IDX_ALLOW_TYPE = 4;
        public static final int COL_IDX_PRIORITY = 5;
        public static final int COL_IDX_ENABLE = 6;
        public static final int COL_IDX_ALTERNATE = 7;
    }

    public static final class AddressColumns implements BaseColumns
    {
        public static final String TABLE_NAME = "address";

        public static final String SENDINFO_ID = "sendinfo_id";
        public static final String ADDRESS = "address";

        public static final String[] ALL_COLUMNS = new String[] {
            _ID, SENDINFO_ID, ADDRESS
        };
        public static final int COL_IDX_ID = 0;
        public static final int COL_IDX_SENDINFO_ID = 1;
        public static final int COL_IDX_ADDRESS = 2;
    }

    private static class DBHelper extends SQLiteOpenHelper
    {
        public DBHelper(Context context)
        {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db)
        {
            db.execSQL("CREATE TABLE " + SendinfoColumns.TABLE_NAME + " (" +
                       SendinfoColumns._ID + " INTEGER PRIMARY KEY," +
                       SendinfoColumns.LABEL + " TEXT," +
                       SendinfoColumns.SUBJECT_FORMAT + " TEXT," +
                       SendinfoColumns.BODY_FORMAT + " TEXT," +
                       SendinfoColumns.ALLOW_TYPE + " TEXT," +
                       SendinfoColumns.PRIORITY + " INTEGER," +
                       SendinfoColumns.ENABLE + " INTEGER," +
                       SendinfoColumns.ALTERNATE + " INTEGER" +
                       ");");
            db.execSQL("CREATE TABLE " + AddressColumns.TABLE_NAME + " (" +
                       AddressColumns._ID + " INTEGER PRIMARY KEY," +
                       AddressColumns.SENDINFO_ID + " INTEGER," +
                       AddressColumns.ADDRESS + " TEXT" +
                       ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
        {
            Log.w(TAG, "message db is not support upgrade: " +
                  oldVersion + ", " + newVersion);
            db.execSQL("DROP TABLE IF EXISTS " + SendinfoColumns.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + AddressColumns.TABLE_NAME);
            onCreate(db);
        }
    }
}
