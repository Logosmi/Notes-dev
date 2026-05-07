package net.micode.notes.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.ui.NoteItemData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class NoteRepository {

    private final ContentResolver contentResolver;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Context context;

    public NoteRepository(ContentResolver cr, Context ctx) {
        this.contentResolver = cr;
        this.context = ctx.getApplicationContext();
    }

    public LiveData<List<NoteItemData>> getNotesByFolder(long folderId) {
        MutableLiveData<List<NoteItemData>> liveData = new MutableLiveData<>();
        executor.execute(() -> {
            String selection;
            if (folderId == Notes.ID_ROOT_FOLDER) {
                selection = "(" + NoteColumns.TYPE + "<>" + Notes.TYPE_SYSTEM +
                        " AND " + NoteColumns.PARENT_ID + "=?)" +
                        " OR (" + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER +
                        " AND " + NoteColumns.NOTES_COUNT + ">0)";
            } else {
                selection = NoteColumns.PARENT_ID + "=?";
            }
            Cursor cursor = contentResolver.query(
                    Notes.CONTENT_NOTE_URI,
                    NoteItemData.PROJECTION,
                    selection,
                    new String[]{String.valueOf(folderId)},
                    NoteColumns.TYPE + " DESC, " + NoteColumns.MODIFIED_DATE + " DESC"
            );
            List<NoteItemData> list = new ArrayList<>();
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    list.add(new NoteItemData(context, cursor));  // 若没有对应构造器，自行从 Cursor 中读取字段
                }
                cursor.close();
            }
            liveData.postValue(list);
        });
        return liveData;
    }

    public LiveData<List<NoteItemData>> searchNotes(String query) {
        MutableLiveData<List<NoteItemData>> liveData = new MutableLiveData<>();
        executor.execute(() -> {
            String selection = NoteColumns.SNIPPET + " LIKE ? AND (" +
                               NoteColumns.TYPE + "=? OR " + NoteColumns.TYPE + "=?)";
            String[] args = new String[]{
                    "%" + query + "%",
                    String.valueOf(Notes.TYPE_NOTE),
                    String.valueOf(Notes.TYPE_FOLDER)
            };
            Cursor cursor = contentResolver.query(
                    Notes.CONTENT_NOTE_URI,
                    NoteItemData.PROJECTION,
                    selection,
                    args,
                    NoteColumns.TYPE + " DESC, " + NoteColumns.MODIFIED_DATE + " DESC"
            );
            List<NoteItemData> list = new ArrayList<>();
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    list.add(new NoteItemData(context, cursor));
                }
                cursor.close();
            }
            liveData.postValue(list);
        });
        return liveData;
    }

    public LiveData<List<FolderItem>> getFoldersForMove(long currentFolderId) {
        MutableLiveData<List<FolderItem>> liveData = new MutableLiveData<>();
        executor.execute(() -> {
            String selection = NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?";
            Cursor cursor = contentResolver.query(
                    Notes.CONTENT_NOTE_URI,
                    new String[]{NoteColumns.ID, NoteColumns.SNIPPET},
                    selection,
                    new String[]{String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)},
                    NoteColumns.MODIFIED_DATE + " DESC"
            );
            List<FolderItem> folders = new ArrayList<>();
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    String name = cursor.getString(1);
                    folders.add(new FolderItem(id, name));
                }
                cursor.close();
            }
            liveData.postValue(folders);
        });
        return liveData;
    }

    public void deleteNotes(List<Long> ids, Runnable onComplete) {
        executor.execute(() -> {
            for (long id : ids) {
                contentResolver.delete(Notes.CONTENT_NOTE_URI,
                        NoteColumns.ID + "=?", new String[]{String.valueOf(id)});
            }
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public void moveNotes(List<Long> ids, long targetFolderId, Runnable onComplete) {
        executor.execute(() -> {
            ContentValues values = new ContentValues();
            values.put(NoteColumns.PARENT_ID, targetFolderId);
            values.put(NoteColumns.LOCAL_MODIFIED, 1);
            for (long id : ids) {
                contentResolver.update(Notes.CONTENT_NOTE_URI, values,
                        NoteColumns.ID + "=?", new String[]{String.valueOf(id)});
            }
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public long createNote(long folderId) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.PARENT_ID, folderId);
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
        Uri uri = contentResolver.insert(Notes.CONTENT_NOTE_URI, values);
        if (uri != null) {
            return Long.parseLong(uri.getPathSegments().get(1));
        }
        return -1;
    }

    public static class FolderItem {
        public long id;
        public String name;
        public FolderItem(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}