/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.data;


import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;


public class NotesProvider extends ContentProvider {
    private static final UriMatcher mMatcher;

    private NotesDatabaseHelper mHelper;

    private static final String TAG = "NotesProvider";

    private static final int URI_NOTE            = 1;
    private static final int URI_NOTE_ITEM       = 2;
    private static final int URI_DATA            = 3;
    private static final int URI_DATA_ITEM       = 4;

    private static final int URI_SEARCH          = 5;
    private static final int URI_SEARCH_SUGGEST  = 6;
    private static final int URI_USER            = 7;
    private static final int URI_USER_ITEM       = 8;
    private static final int URI_TAG             = 9;
    private static final int URI_TAG_ITEM        = 10;
    private static final int URI_NOTE_TAG        = 11;
    private static final int URI_NOTE_TAG_ITEM   = 12;
    private static final int URI_IMAGE           = 13;
    private static final int URI_IMAGE_ITEM      = 14;

    static {
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mMatcher.addURI(Notes.AUTHORITY, "note", URI_NOTE);
        mMatcher.addURI(Notes.AUTHORITY, "note/#", URI_NOTE_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "data", URI_DATA);
        mMatcher.addURI(Notes.AUTHORITY, "data/#", URI_DATA_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "search", URI_SEARCH);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_SEARCH_SUGGEST);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", URI_SEARCH_SUGGEST);
        // 新增：账户、标签、图片表
    mMatcher.addURI(Notes.AUTHORITY, "user", URI_USER);
    mMatcher.addURI(Notes.AUTHORITY, "user/#", URI_USER_ITEM);
    mMatcher.addURI(Notes.AUTHORITY, "tag", URI_TAG);
    mMatcher.addURI(Notes.AUTHORITY, "tag/#", URI_TAG_ITEM);
    mMatcher.addURI(Notes.AUTHORITY, "note_tag", URI_NOTE_TAG);
    // note_tag 可以不需要单独 # 模式，但为通用，也可支持
    // 暂时不加 note_tag 的 item 匹配，因为通常通过 noteId 或 tagId 查询关联
    mMatcher.addURI(Notes.AUTHORITY, "image", URI_IMAGE);
    mMatcher.addURI(Notes.AUTHORITY, "image/#", URI_IMAGE_ITEM);
    }

    /**
     * x'0A' represents the '\n' character in sqlite. For title and content in the search result,
     * we will trim '\n' and white space in order to show more information.
     */
    private static final String NOTES_SEARCH_PROJECTION = NoteColumns.ID + ","
        + NoteColumns.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","
        + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","
        + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","
        + R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","
        + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","
        + "'" + Notes.TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA;

    private static String NOTES_SNIPPET_SEARCH_QUERY = "SELECT " + NOTES_SEARCH_PROJECTION
        + " FROM " + TABLE.NOTE
        + " WHERE " + NoteColumns.SNIPPET + " LIKE ?"
        + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER
        + " AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;

    @Override
    public boolean onCreate() {
        mHelper = NotesDatabaseHelper.getInstance(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Cursor c = null;
        SQLiteDatabase db = mHelper.getReadableDatabase();
        int match = mMatcher.match(uri);
        switch (match) {
            case URI_NOTE:
            case URI_NOTE_ITEM:
                c = queryTable(db, TABLE.NOTE, NoteColumns.ID, uri, match, projection, selection, selectionArgs, sortOrder);
                break;
            case URI_DATA:
            case URI_DATA_ITEM:
                c = queryTable(db, TABLE.DATA, DataColumns.ID, uri, match, projection, selection, selectionArgs, sortOrder);
                break;
            case URI_SEARCH:
            case URI_SEARCH_SUGGEST:
                if (sortOrder != null || projection != null) {
                    throw new IllegalArgumentException(
                            "do not specify sortOrder, selection, selectionArgs, or projection" + "with this query");
                }

                String searchString = null;
                if (match == URI_SEARCH_SUGGEST) {
                    if (uri.getPathSegments().size() > 1) {
                        searchString = uri.getPathSegments().get(1);
                    }
                } else {
                    searchString = uri.getQueryParameter("pattern");
                }

                if (TextUtils.isEmpty(searchString)) {
                    return null;
                }

                try {
                    searchString = String.format("%%%s%%", searchString);
                    c = db.rawQuery(NOTES_SNIPPET_SEARCH_QUERY,
                            new String[] { searchString });
                } catch (IllegalStateException ex) {
                    Log.e(TAG, "got exception: " + ex.toString());
                }
                break;
            case URI_USER:
            case URI_USER_ITEM:
                c = queryTable(db, TABLE.USER, "_id", uri, match, projection, selection, selectionArgs, sortOrder);
                break;
            case URI_TAG:
            case URI_TAG_ITEM:
                c = queryTable(db, TABLE.TAG, "_id", uri, match, projection, selection, selectionArgs, sortOrder);
                break;
            case URI_NOTE_TAG:
                c = db.query(TABLE.NOTE_TAG, projection, selection, selectionArgs, null, null, sortOrder);
                break;
            case URI_IMAGE:
            case URI_IMAGE_ITEM:
                c = queryTable(db, TABLE.IMAGE, "_id", uri, match, projection, selection, selectionArgs, sortOrder);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    private Cursor queryTable(SQLiteDatabase db, String table, String idColumn, Uri uri,
            int match, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        boolean isItem = (match % 2 == 0);
        if (isItem) {
            String id = uri.getPathSegments().get(1);
            return db.query(table, projection, idColumn + "=" + id
                    + parseSelection(selection), selectionArgs, null, null, sortOrder);
        }
        return db.query(table, projection, selection, selectionArgs, null, null, sortOrder);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        long dataId = 0, noteId = 0, insertedId = 0;
        switch (mMatcher.match(uri)) {
            case URI_USER:
            case URI_TAG:
            case URI_NOTE_TAG:
            case URI_IMAGE:
                return insertAndNotify(db, uri, values);
            case URI_NOTE:
                insertedId = noteId = db.insert(TABLE.NOTE, null, values);
                break;
            case URI_DATA:
                if (values.containsKey(DataColumns.NOTE_ID)) {
                    noteId = values.getAsLong(DataColumns.NOTE_ID);
                } else {
                    Log.d(TAG, "Wrong data format without note id:" + values.toString());
                }
                insertedId = dataId = db.insert(TABLE.DATA, null, values);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // Notify the note uri
        if (noteId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), null);
        }

        // Notify the data uri
        if (dataId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), null);
        }

        return ContentUris.withAppendedId(uri, insertedId);
    }

    private Uri insertAndNotify(SQLiteDatabase db, Uri uri, ContentValues values) {
        long id = db.insert(getTableName(mMatcher.match(uri)), null, values);
        if (id > 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return ContentUris.withAppendedId(uri, id);
    }

    private String getTableName(int match) {
        switch (match) {
            case URI_NOTE: case URI_NOTE_ITEM: return TABLE.NOTE;
            case URI_DATA: case URI_DATA_ITEM: return TABLE.DATA;
            case URI_USER: case URI_USER_ITEM: return TABLE.USER;
            case URI_TAG: case URI_TAG_ITEM: return TABLE.TAG;
            case URI_NOTE_TAG: return TABLE.NOTE_TAG;
            case URI_IMAGE: case URI_IMAGE_ITEM: return TABLE.IMAGE;
            default: throw new IllegalArgumentException("Unknown URI match: " + match);
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean deleteData = false;
        int match = mMatcher.match(uri);
        switch (match) {
            case URI_USER: case URI_USER_ITEM:
                count = deleteTable(db, TABLE.USER, "_id", uri, match, selection, selectionArgs);
                break;
            case URI_TAG: case URI_TAG_ITEM:
                count = deleteTable(db, TABLE.TAG, "_id", uri, match, selection, selectionArgs);
                break;
            case URI_NOTE_TAG:
                count = db.delete(TABLE.NOTE_TAG, selection, selectionArgs);
                break;
            case URI_IMAGE: case URI_IMAGE_ITEM:
                count = deleteTable(db, TABLE.IMAGE, "_id", uri, match, selection, selectionArgs);
                break;
            case URI_NOTE:
                selection = "(" + selection + ") AND " + NoteColumns.ID + ">0 ";
                count = db.delete(TABLE.NOTE, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM: {
                String id = uri.getPathSegments().get(1);
                long noteId = Long.valueOf(id);
                if (noteId <= 0) break;
                count = db.delete(TABLE.NOTE,
                        NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;
            }
            case URI_DATA:
                count = db.delete(TABLE.DATA, selection, selectionArgs);
                deleteData = true;
                break;
            case URI_DATA_ITEM:
                count = deleteTable(db, TABLE.DATA, DataColumns.ID, uri, match, selection, selectionArgs);
                deleteData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        if (count > 0) {
            if (deleteData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    private int deleteTable(SQLiteDatabase db, String table, String idColumn, Uri uri,
            int match, String selection, String[] selectionArgs) {
        if (isItemUri(match)) {
            String id = uri.getPathSegments().get(1);
            return db.delete(table, idColumn + "=" + id + parseSelection(selection), selectionArgs);
        }
        return db.delete(table, selection, selectionArgs);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean updateData = false;
        int match = mMatcher.match(uri);
        switch (match) {
            case URI_USER: case URI_USER_ITEM:
                count = updateTable(db, TABLE.USER, "_id", uri, match, values, selection, selectionArgs);
                break;
            case URI_TAG: case URI_TAG_ITEM:
                count = updateTable(db, TABLE.TAG, "_id", uri, match, values, selection, selectionArgs);
                break;
            case URI_NOTE_TAG:
                count = db.update(TABLE.NOTE_TAG, values, selection, selectionArgs);
                break;
            case URI_IMAGE: case URI_IMAGE_ITEM:
                count = updateTable(db, TABLE.IMAGE, "_id", uri, match, values, selection, selectionArgs);
                break;
            case URI_NOTE:
                increaseNoteVersion(-1, selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                increaseNoteVersion(Long.valueOf(uri.getPathSegments().get(1)), selection, selectionArgs);
                count = updateTable(db, TABLE.NOTE, NoteColumns.ID, uri, match, values, selection, selectionArgs);
                break;
            case URI_DATA:
                count = db.update(TABLE.DATA, values, selection, selectionArgs);
                updateData = true;
                break;
            case URI_DATA_ITEM:
                count = updateTable(db, TABLE.DATA, DataColumns.ID, uri, match, values, selection, selectionArgs);
                updateData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if (count > 0) {
            if (updateData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    private int updateTable(SQLiteDatabase db, String table, String idColumn, Uri uri,
            int match, ContentValues values, String selection, String[] selectionArgs) {
        if (isItemUri(match)) {
            String id = uri.getPathSegments().get(1);
            return db.update(table, values, idColumn + "=" + id + parseSelection(selection), selectionArgs);
        }
        return db.update(table, values, selection, selectionArgs);
    }

    private boolean isItemUri(int match) {
        return match % 2 == 0;
    }

    private String parseSelection(String selection) {
        return (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
    }

    private void increaseNoteVersion(long id, String selection, String[] selectionArgs) {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        StringBuilder whereClause = new StringBuilder();
        String[] whereArgs = null;

        if (id > 0) {
            whereClause.append(NoteColumns.ID).append("=").append(id);
        }
        if (!TextUtils.isEmpty(selection)) {
            if (whereClause.length() > 0) {
                whereClause.append(" AND (").append(selection).append(")");
            } else {
                whereClause.append(selection);
            }
            whereArgs = selectionArgs;
        }

        if (whereClause.length() > 0) {
            if (whereArgs != null && whereArgs.length > 0) {
                db.execSQL("UPDATE " + TABLE.NOTE + " SET " + NoteColumns.VERSION
                        + "=" + NoteColumns.VERSION + "+1 WHERE " + whereClause.toString(), whereArgs);
            } else {
                db.execSQL("UPDATE " + TABLE.NOTE + " SET "
                        + NoteColumns.VERSION + "=" + NoteColumns.VERSION + "+1 WHERE " + whereClause.toString());
            }
        } else {
            db.execSQL("UPDATE " + TABLE.NOTE + " SET "
                    + NoteColumns.VERSION + "=" + NoteColumns.VERSION + "+1");
        }
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

}
