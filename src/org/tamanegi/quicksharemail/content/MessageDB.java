package org.tamanegi.quicksharemail.content;

import java.util.Date;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

public class MessageDB
{
    private static final String TAG = "QuickShareMail";

    private static final String DATABASE_NAME = "message.db";
    private static final int DATABASE_VERSION = 1;

    private static Object lock = new Object();

    private DBHelper db_helper;

    public MessageDB(Context context)
    {
        db_helper = new DBHelper(context);
    }

    public void close()
    {
        db_helper.close();
    }

    public void pushBack(MessageContent msg)
    {
        ContentValues msg_vals = new ContentValues();
        msg_vals.put(MsgColumns.SUBJECT_FORMAT, msg.getSubjectFormat());
        msg_vals.put(MsgColumns.DATE, msg.getDate().getTime());
        msg_vals.put(MsgColumns.CONTENT_TYPE, msg.getType());
        msg_vals.put(MsgColumns.CONTENT_TEXT, msg.getText());
        if(msg.getStream() != null) {
            msg_vals.put(MsgColumns.CONTENT_STREAM, msg.getStream().toString());
        }
        msg_vals.put(MsgColumns.RETRY_LATER, 0);

        synchronized(lock) {
            SQLiteDatabase db = db_helper.getWritableDatabase();

            db.beginTransaction();
            try {
                // insert to message db
                long msg_id = db.insertOrThrow(
                    MsgColumns.TABLE_NAME, MsgColumns.CONTENT_TEXT, msg_vals);

                // insert to message-sendto db
                ContentValues msg_sendto_vals = new ContentValues();
                msg_sendto_vals.put(MsgSendtoColumns.MESSAGE_ID, msg_id);

                int address_cnt = msg.getAddressCount();
                for(int i = 0; i < address_cnt; i++) {
                    msg_sendto_vals.put(MsgSendtoColumns.ADDRESS,
                                        msg.getAddressInfo(i).getAddress());

                    db.insertOrThrow(
                        MsgSendtoColumns.TABLE_NAME,
                        MsgSendtoColumns.MESSAGE_ID,
                        msg_sendto_vals);
                }

                db.setTransactionSuccessful();
            }
            finally {
                db.endTransaction();
                db.close();
            }
        }
    }

    public MessageContent popFront()
    {
        MessageContent msg = new MessageContent();
        long msg_id;

        synchronized(lock) {
            SQLiteDatabase db = db_helper.getReadableDatabase();
            try {
                // get message
                Cursor msg_cur = db.query(
                    MsgColumns.TABLE_NAME, MsgColumns.ALL_COLUMNS,
                    MsgColumns.RETRY_LATER + " = 0",    // WHERE
                    null, null, null,
                    MsgColumns._ID + " ASC",            // ORDER BY
                    "1");                               // LIMIT
                try {
                    if(msg_cur.getCount() != 1) {
                        return null;
                    }
                    msg_cur.moveToFirst();

                    msg_id = msg_cur.getLong(MsgColumns.COL_IDX_ID);

                    msg.setId(msg_id);
                    msg.setSubjectFormat(
                        msg_cur.getString(MsgColumns.COL_IDX_SUBJECT_FORMAT));
                    msg.setDate(
                        new Date(msg_cur.getLong(MsgColumns.COL_IDX_DATE)));
                    msg.setType(
                        msg_cur.getString(MsgColumns.COL_IDX_CONTENT_TYPE));
                    msg.setText(
                        msg_cur.getString(MsgColumns.COL_IDX_CONTENT_TEXT));
                    msg.setStream(
                        msg_cur.getString(MsgColumns.COL_IDX_CONTENT_STREAM));
                }
                finally {
                    msg_cur.close();
                }

                // get message-sendto
                Cursor msg_sendto_cur = db.query(
                    MsgSendtoColumns.TABLE_NAME, MsgSendtoColumns.ALL_COLUMNS,
                    MsgSendtoColumns.MESSAGE_ID + " = ?", // WHERE
                    new String[] { String.valueOf(msg_id) },
                    null, null, null, null);
                try {
                    int address_cnt = msg_sendto_cur.getCount();
                    if(address_cnt < 1) {
                        return msg;
                    }

                    MessageContent.AddressInfo[] address =
                        new MessageContent.AddressInfo[address_cnt];

                    for(int i = 0; i < address_cnt; i++) {
                        msg_sendto_cur.moveToNext();
                        address[i] = new MessageContent.AddressInfo(
                            msg_sendto_cur.getLong(
                                MsgSendtoColumns.COL_IDX_ID),
                            msg_sendto_cur.getString(
                                MsgSendtoColumns.COL_IDX_ADDRESS));
                    }

                    msg.setAddress(address);

                    return msg;
                }
                finally {
                    msg_sendto_cur.close();
                }
            }
            finally {
                db.close();
            }
        }
    }

    public int getRestCount()
    {
        synchronized(lock) {
            SQLiteDatabase db = db_helper.getReadableDatabase();
            try {
                Cursor cur = db.query(
                    MsgColumns.TABLE_NAME, MsgColumns.ALL_COLUMNS,
                    MsgColumns.RETRY_LATER + " = 0",    // WHERE
                    null, null, null, null);
                try {
                    return cur.getCount();
                }
                finally {
                    cur.close();
                }
            }
            finally {
                db.close();
            }
        }
    }

    public void delete(MessageContent msg)
    {
        synchronized(lock) {
            SQLiteDatabase db = db_helper.getWritableDatabase();
            try {
                boolean retry_later = false;
                int address_cnt = msg.getAddressCount();
                for(int i = 0; i < address_cnt; i++) {
                    MessageContent.AddressInfo address = msg.getAddressInfo(i);
                    if(! address.isProcessed()) {
                        // if not processed address exists, set retry-later flag
                        retry_later = true;
                        continue;
                    }

                    // delete send-to row
                    db.delete(MsgSendtoColumns.TABLE_NAME,
                              MsgSendtoColumns._ID + " = ?",
                              new String[] { String.valueOf(address.getId()) });
                }

                if(retry_later) {
                    // set retry-later flag
                    ContentValues vals = new ContentValues();
                    vals.put(MsgColumns.RETRY_LATER, 1);

                    db.update(MsgColumns.TABLE_NAME, vals,
                              MsgColumns._ID + " = ?",
                              new String[] { String.valueOf(msg.getId()) });
                }
                else {
                    // delete message row
                    db.delete(MsgColumns.TABLE_NAME,
                              MsgColumns._ID + " = ?",
                              new String[] { String.valueOf(msg.getId()) });
                }
            }
            finally {
                db.close();
            }
        }
    }

    public void clearRetryFlag()
    {
        ContentValues vals = new ContentValues();
        vals.put(MsgColumns.RETRY_LATER, 0);

        synchronized(lock) {
            SQLiteDatabase db = db_helper.getWritableDatabase();
            db.update(MsgColumns.TABLE_NAME, vals, null, null);
        }
    }

    public static final class MsgColumns implements BaseColumns
    {
        public static final String TABLE_NAME = "message";

        public static final String SUBJECT_FORMAT = "subject_format";
        public static final String DATE = "date";
        public static final String CONTENT_TYPE = "content_type";
        public static final String CONTENT_TEXT = "content_text";
        public static final String CONTENT_STREAM = "content_stream";
        public static final String RETRY_LATER = "retry_later";

        public static final String[] ALL_COLUMNS = new String[] {
            _ID, SUBJECT_FORMAT, DATE,
            CONTENT_TYPE, CONTENT_TEXT, CONTENT_STREAM,
            RETRY_LATER
        };
        public static final int COL_IDX_ID = 0;
        public static final int COL_IDX_SUBJECT_FORMAT = 1;
        public static final int COL_IDX_DATE = 2;
        public static final int COL_IDX_CONTENT_TYPE = 3;
        public static final int COL_IDX_CONTENT_TEXT = 4;
        public static final int COL_IDX_CONTENT_STREAM = 5;
        public static final int COL_IDX_RETRY_LATER = 6;
    }

    public static final class MsgSendtoColumns implements BaseColumns
    {
        public static final String TABLE_NAME = "message_sendto";

        public static final String MESSAGE_ID = "message_id";
        public static final String ADDRESS = "address";

        public static final String[] ALL_COLUMNS = new String[] {
            _ID, MESSAGE_ID, ADDRESS
        };
        public static final int COL_IDX_ID = 0;
        public static final int COL_IDX_MESSAGE_ID = 1;
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
            db.execSQL("CREATE TABLE " + MsgColumns.TABLE_NAME + " (" +
                       MsgColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                       MsgColumns.SUBJECT_FORMAT + " TEXT," +
                       MsgColumns.DATE + " INTEGER," +
                       MsgColumns.CONTENT_TYPE + " TEXT," +
                       MsgColumns.CONTENT_TEXT + " TEXT," +
                       MsgColumns.CONTENT_STREAM + " TEXT," +
                       MsgColumns.RETRY_LATER + " INTEGER" +
                       ");");
            db.execSQL("CREATE TABLE " + MsgSendtoColumns.TABLE_NAME + " (" +
                       MsgSendtoColumns._ID + " INTEGER PRIMARY KEY," +
                       MsgSendtoColumns.MESSAGE_ID + " INTEGER," +
                       MsgSendtoColumns.ADDRESS + " TEXT" +
                       ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
        {
            Log.w(TAG, "message db is not support upgrade: " +
                  oldVersion + ", " + newVersion);
            db.execSQL("DROP TABLE IF EXISTS " + MsgColumns.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + MsgSendtoColumns.TABLE_NAME);
            onCreate(db);
        }
    }
}
