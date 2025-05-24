package fr.android.androidgeomap;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class LocalDatabaseHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "photos.db";
    public static final int DB_VERSION = 1;

    public static final String TABLE_PHOTOS = "photos";
    public static final String COL_ID = "_id";
    public static final String COL_LAT = "latitude";
    public static final String COL_LON = "longitude";
    public static final String COL_ADDRESS = "address";
    public static final String COL_URI = "uri";
    public static final String COL_DATE = "date";

    public LocalDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TABLE =
                "CREATE TABLE " + TABLE_PHOTOS + " (" +
                        COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COL_LAT + " REAL, " +
                        COL_LON + " REAL, " +
                        COL_ADDRESS + " TEXT, " +
                        COL_URI + " TEXT, " +
                        COL_DATE + " TEXT)";
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PHOTOS);
        onCreate(db);
    }
}
