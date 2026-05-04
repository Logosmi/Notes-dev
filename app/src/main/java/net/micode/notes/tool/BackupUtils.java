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

package net.micode.notes.tool;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import android.provider.MediaStore;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import android.content.ContentValues;
import android.net.Uri;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;


public class BackupUtils {
    private static final String TAG = "BackupUtils";
    // Singleton stuff
    private static BackupUtils sInstance;

    public static synchronized BackupUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BackupUtils(context);
        }
        return sInstance;
    }

    /**
     * Following states are signs to represents backup or restore
     * status
     */
    // Currently, the sdcard is not mounted
    public static final int STATE_SD_CARD_UNMOUONTED           = 0;
    // The backup file not exist
    public static final int STATE_BACKUP_FILE_NOT_EXIST        = 1;
    // The data is not well formated, may be changed by other programs
    public static final int STATE_DATA_DESTROIED               = 2;
    // Some run-time exception which causes restore or backup fails
    public static final int STATE_SYSTEM_ERROR                 = 3;
    // Backup or restore success
    public static final int STATE_SUCCESS                      = 4;

    private TextExport mTextExport;

    private BackupUtils(Context context) {
        mTextExport = new TextExport(context);
    }

    private static boolean externalStorageAvailable() {
        // return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
        return true;
    }

    public int exportToText() {
        return mTextExport.exportToText();
    }

    public String getExportedTextFileName() {
        return mTextExport.mFileName;
    }

    public String getExportedTextFileDir() {
        return mTextExport.mFileDirectory;
    }

    private static class TextExport {
        private static final String[] NOTE_PROJECTION = {
                NoteColumns.ID,
                NoteColumns.MODIFIED_DATE,
                NoteColumns.SNIPPET,
                NoteColumns.TYPE
        };

        private static final int NOTE_COLUMN_ID = 0;

        private static final int NOTE_COLUMN_MODIFIED_DATE = 1;

        private static final int NOTE_COLUMN_SNIPPET = 2;

        private static final String[] DATA_PROJECTION = {
                DataColumns.CONTENT,
                DataColumns.MIME_TYPE,
                DataColumns.DATA1,
                DataColumns.DATA2,
                DataColumns.DATA3,
                DataColumns.DATA4,
        };

        private static final int DATA_COLUMN_CONTENT = 0;

        private static final int DATA_COLUMN_MIME_TYPE = 1;

        private static final int DATA_COLUMN_CALL_DATE = 2;

        private static final int DATA_COLUMN_PHONE_NUMBER = 4;

        private final String [] TEXT_FORMAT;
        private static final int FORMAT_FOLDER_NAME          = 0;
        private static final int FORMAT_NOTE_DATE            = 1;
        private static final int FORMAT_NOTE_CONTENT         = 2;

        private Context mContext;
        private String mFileName;
        private String mFileDirectory;

        public TextExport(Context context) {
            TEXT_FORMAT = context.getResources().getStringArray(R.array.format_for_exported_note);
            mContext = context;
            mFileName = "";
            mFileDirectory = "";
        }

        private String getFormat(int id) {
            return TEXT_FORMAT[id];
        }

        /**
         * Export the folder identified by folder id to text
         */
        private void exportFolderToText(String folderId, PrintStream ps) {
            // Query notes belong to this folder
            Cursor notesCursor = mContext.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION, NoteColumns.PARENT_ID + "=?", new String[] {
                        folderId
                    }, null);

            if (notesCursor != null) {
                if (notesCursor.moveToFirst()) {
                    do {
                        // Print note's last modified date
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                notesCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // Query data belong to this note
                        String noteId = notesCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (notesCursor.moveToNext());
                }
                notesCursor.close();
            }
        }

        /**
         * Export note identified by id to a print stream
         */
        private void exportNoteToText(String noteId, PrintStream ps) {
            Cursor dataCursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI,
                    DATA_PROJECTION, DataColumns.NOTE_ID + "=?", new String[] {
                        noteId
                    }, null);

            if (dataCursor != null) {
                if (dataCursor.moveToFirst()) {
                    do {
                        String mimeType = dataCursor.getString(DATA_COLUMN_MIME_TYPE);
                        if (DataConstants.CALL_NOTE.equals(mimeType)) {
                            // Print phone number
                            String phoneNumber = dataCursor.getString(DATA_COLUMN_PHONE_NUMBER);
                            long callDate = dataCursor.getLong(DATA_COLUMN_CALL_DATE);
                            String location = dataCursor.getString(DATA_COLUMN_CONTENT);

                            if (!TextUtils.isEmpty(phoneNumber)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        phoneNumber));
                            }
                            // Print call date
                            ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT), DateFormat
                                    .format(mContext.getString(R.string.format_datetime_mdhm),
                                            callDate)));
                            // Print call attachment location
                            if (!TextUtils.isEmpty(location)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        location));
                            }
                        } else if (DataConstants.NOTE.equals(mimeType)) {
                            String content = dataCursor.getString(DATA_COLUMN_CONTENT);
                            if (!TextUtils.isEmpty(content)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        content));
                            }
                        }
                    } while (dataCursor.moveToNext());
                }
                dataCursor.close();
            }
            // print a line separator between note
            try {
                ps.write(new byte[] {
                        Character.LINE_SEPARATOR, Character.LETTER_NUMBER
                });
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        /**
         * Note will be exported as text which is user readable
         */
        public int exportToText() {
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            PrintStream ps = getExportToTextPrintStream();
            if (ps == null) {
                Log.e(TAG, "get print stream error");
                return STATE_SYSTEM_ERROR;
            }
            // First export folder and its notes
            Cursor folderCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    "(" + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER + " AND "
                            + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER + ") OR "
                            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER, null, null);

            if (folderCursor != null) {
                if (folderCursor.moveToFirst()) {
                    do {
                        // Print folder's name
                        String folderName = "";
                        if(folderCursor.getLong(NOTE_COLUMN_ID) == Notes.ID_CALL_RECORD_FOLDER) {
                            folderName = mContext.getString(R.string.call_record_folder_name);
                        } else {
                            folderName = folderCursor.getString(NOTE_COLUMN_SNIPPET);
                        }
                        if (!TextUtils.isEmpty(folderName)) {
                            ps.println(String.format(getFormat(FORMAT_FOLDER_NAME), folderName));
                        }
                        String folderId = folderCursor.getString(NOTE_COLUMN_ID);
                        exportFolderToText(folderId, ps);
                    } while (folderCursor.moveToNext());
                }
                folderCursor.close();
            }

            // Export notes in root's folder
            Cursor noteCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    NoteColumns.TYPE + "=" + +Notes.TYPE_NOTE + " AND " + NoteColumns.PARENT_ID
                            + "=0", null, null);

            if (noteCursor != null) {
                if (noteCursor.moveToFirst()) {
                    do {
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE), DateFormat.format(
                                mContext.getString(R.string.format_datetime_mdhm),
                                noteCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));
                        // Query data belong to this note
                        String noteId = noteCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (noteCursor.moveToNext());
                }
                noteCursor.close();
            }
            ps.close();

            return STATE_SUCCESS;
        }

       private PrintStream getExportToTextPrintStream() {
            // 生成文件名
            mFileName = mContext.getString(
                    R.string.file_name_txt_format,
                    DateFormat.format(mContext.getString(R.string.format_date_ymd),
                            System.currentTimeMillis()));

            final String RELATIVE_PATH = "Download/Notes";

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, mFileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH);

                try {
                    Uri uri = mContext.getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) {
                        Log.e(TAG, "MediaStore insert returned null");
                        return null;
                    }
                    mFileDirectory = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()
                            + File.separator + "Notes";
                    OutputStream os = mContext.getContentResolver().openOutputStream(uri);
                    if (os == null) {
                        Log.e(TAG, "Failed to open OutputStream for URI: " + uri);
                        return null;
                    }
                    return new PrintStream(os);
                } catch (SecurityException e) {
                    Log.e(TAG, "Permission denied writing to MediaStore", e);
                    return null;
                } catch (IOException e) {
                    Log.e(TAG, "IO error creating file in MediaStore", e);
                    return null;
                }
            } else {
                if (!externalStorageAvailable()) {
                    Log.d(TAG, "External storage not available");
                    return null;
                }
                try {
                    File dir = new File(Environment.getExternalStorageDirectory(),
                            RELATIVE_PATH);
                    if (!dir.exists()) {
                        if (!dir.mkdirs()) {
                            Log.e(TAG, "Failed to create directory: " + dir.getAbsolutePath());
                            return null;
                        }
                    }
                    File file = new File(dir, mFileName);
                    mFileDirectory = dir.getAbsolutePath();
                    FileOutputStream fos = new FileOutputStream(file);
                    return new PrintStream(fos);
                } catch (SecurityException e) {
                    Log.e(TAG, "Permission denied writing to external storage", e);
                    return null;
                } catch (IOException e) {
                    Log.e(TAG, "IO error creating file on external storage", e);
                    return null;
                }
            }
        }
    }
}


