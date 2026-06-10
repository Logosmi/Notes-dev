package net.micode.notes.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NoteRepository {

    private final ContentResolver contentResolver;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public NoteRepository(ContentResolver resolver, Context ctx) {
        this.contentResolver = resolver;
        this.context = ctx.getApplicationContext();
    }

    // 查询笔记列表（根据文件夹 ID）
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
                    list.add(new NoteItemData(context, cursor));
                }
                cursor.close();
            }
            liveData.postValue(list);
        });
        return liveData;
    }

    // 搜索笔记
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
            String selection = NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>? AND " + NoteColumns.ID + "<>?";
            if (currentFolderId != Notes.ID_ROOT_FOLDER) {
                selection = "(" + selection + ") OR (" + NoteColumns.ID + "=" + Notes.ID_ROOT_FOLDER + ")";
            }
            
            Cursor cursor = contentResolver.query(
                    Notes.CONTENT_NOTE_URI,
                    new String[]{NoteColumns.ID, NoteColumns.SNIPPET},
                    selection,
                    new String[]{
                        String.valueOf(Notes.TYPE_FOLDER),
                        String.valueOf(Notes.ID_TRASH_FOLER),
                        String.valueOf(currentFolderId)
                    },
                    NoteColumns.MODIFIED_DATE + " DESC"
            );
            
            List<FolderItem> folders = new ArrayList<>();
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    String name;
                    if (id == Notes.ID_ROOT_FOLDER) {
                        name = context.getString(net.micode.notes.R.string.menu_move_parent_folder);
                    } else {
                        name = cursor.getString(1);
                    }
                    folders.add(new FolderItem(id, name));
                }
                cursor.close();
            }
            liveData.postValue(folders);
        });
        return liveData;
    }

    public void deleteNotes(HashSet<Long> ids, Runnable onComplete) {
        executor.execute(() -> {
            DataUtils.batchDeleteNotes(contentResolver, ids);
            if (onComplete != null) onComplete.run();
        });
    }

    public void moveNotes(HashSet<Long> ids, long targetFolderId, Runnable onComplete) {
        executor.execute(() -> {
            DataUtils.batchMoveToFolder(contentResolver, ids, targetFolderId);
            if (onComplete != null) onComplete.run();
        });
    }

    public long createFolder(String name, long parentId) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.SNIPPET, name);
        values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
        values.put(NoteColumns.PARENT_ID, parentId);
        Uri uri = contentResolver.insert(Notes.CONTENT_NOTE_URI, values);
        return uri == null ? -1 : Long.parseLong(uri.getPathSegments().get(1));
    }

    public void renameFolder(long folderId, String newName) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.SNIPPET, newName);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        contentResolver.update(Notes.CONTENT_NOTE_URI, values,
                NoteColumns.ID + "=?", new String[]{String.valueOf(folderId)});
    }

    public boolean checkFolderNameExists(String name) {
        Cursor cursor = contentResolver.query(Notes.CONTENT_NOTE_URI, null,
                NoteColumns.TYPE + "=? AND " + NoteColumns.SNIPPET + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[]{String.valueOf(Notes.TYPE_FOLDER), name, String.valueOf(Notes.ID_TRASH_FOLER)},
                null);
        boolean exists = cursor != null && cursor.getCount() > 0;
        if (cursor != null) cursor.close();
        return exists;
    }

    public int getUserFolderCount() {
        return DataUtils.getUserFolderCount(contentResolver);
    }

    public void shutdown() {
        executor.shutdown();
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