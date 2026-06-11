package net.micode.notes.model;

import android.app.Application;
import android.appwidget.AppWidgetManager;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import net.micode.notes.R;
import net.micode.notes.data.NoteItemData;
import net.micode.notes.data.NoteRepository;
import net.micode.notes.data.Notes;
import net.micode.notes.tag.Tag;
import net.micode.notes.tag.TagManager;
import net.micode.notes.tool.ExportTextWorker;
import net.micode.notes.tool.ResourceParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NotesListViewModel extends AndroidViewModel {

    public enum ListEditState {
        NOTE_LIST, SUB_FOLDER, CALL_RECORD_FOLDER
    }

    public enum ExportResult { SUCCESS, FAILURE }

    private static final String PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction";

    private final NoteRepository repository;

    private final MutableLiveData<Long> currentFolderId = new MutableLiveData<>((long) Notes.ID_ROOT_FOLDER);
    private final MutableLiveData<Boolean> isSearchMode = new MutableLiveData<>(false);
    private final MutableLiveData<List<NoteItemData>> noteList = new MutableLiveData<>();
    private final MutableLiveData<ListEditState> listState = new MutableLiveData<>(ListEditState.NOTE_LIST);
    private final MutableLiveData<Boolean> isInChoiceMode = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> selectedCount = new MutableLiveData<>(0);
    private final MutableLiveData<ExportResult> exportResult = new MutableLiveData<>();

    private LiveData<List<NoteItemData>> activeSource;
    private final Observer<List<NoteItemData>> dataObserver = data -> {
        currentNotes = data;
        noteList.postValue(data);
    };

    private List<NoteItemData> currentNotes;
    private final HashSet<Integer> selectedPositions = new HashSet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public NotesListViewModel(Application application) {
        super(application);
        repository = new NoteRepository(application.getContentResolver(), application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (activeSource != null) {
            activeSource.removeObserver(dataObserver);
            activeSource = null;
        }
        repository.shutdown();
    }

    // ── 数据加载 ──

    public void loadNotes(long folderId) {
        currentFolderId.postValue(folderId);
        mainHandler.post(() -> {
            if (activeSource != null) {
                activeSource.removeObserver(dataObserver);
            }
            activeSource = repository.getNotesByFolder(folderId);
            activeSource.observeForever(dataObserver);
        });
    }

    public void searchNotes(String query) {
        isSearchMode.postValue(true);
        mainHandler.post(() -> {
            if (activeSource != null) {
                activeSource.removeObserver(dataObserver);
            }
            activeSource = repository.searchNotes(query);
            activeSource.observeForever(dataObserver);
        });
    }

    public void filterByNoteIds(List<Long> noteIds) {
        mainHandler.post(() -> {
            if (activeSource != null) {
                activeSource.removeObserver(dataObserver);
            }
            activeSource = repository.getNotesByIds(noteIds);
            activeSource.observeForever(dataObserver);
        });
    }

    public void exitSearch() {
        isSearchMode.postValue(false);
        loadNotes(currentFolderId.getValue());
    }

    // ── LiveData 访问器 ──

    public LiveData<List<NoteItemData>> getNoteList() { return noteList; }
    public LiveData<Long> getCurrentFolderId() { return currentFolderId; }
    public LiveData<Boolean> isSearchMode() { return isSearchMode; }
    public LiveData<ListEditState> getListState() { return listState; }
    public LiveData<Boolean> getInChoiceMode() { return isInChoiceMode; }
    public LiveData<Integer> getSelectedCount() { return selectedCount; }
    public LiveData<ExportResult> getExportResult() { return exportResult; }

    public long getCurrentFolderIdValue() {
        Long id = currentFolderId.getValue();
        return id != null ? id : Notes.ID_ROOT_FOLDER;
    }

    // ── 文件夹导航 ──

    public void openFolder(long folderId, String folderName) {
        if (folderId == Notes.ID_CALL_RECORD_FOLDER) {
            listState.setValue(ListEditState.CALL_RECORD_FOLDER);
        } else {
            listState.setValue(ListEditState.SUB_FOLDER);
        }
        loadNotes(folderId);
    }

    public boolean handleBackPressed() {
        if (Boolean.TRUE.equals(isSearchMode.getValue())) {
            exitSearch();
            return true;
        }
        if (currentFolderId.getValue() != null && currentFolderId.getValue() != Notes.ID_ROOT_FOLDER) {
            loadNotes(Notes.ID_ROOT_FOLDER);
            currentFolderId.setValue((long) Notes.ID_ROOT_FOLDER);
            listState.setValue(ListEditState.NOTE_LIST);
            return true;
        }
        return false;
    }

    // ── 多选模式 ──

    public void enterChoiceMode() {
        selectedPositions.clear();
        isInChoiceMode.setValue(true);
        selectedCount.setValue(0);
    }

    public void exitChoiceMode() {
        selectedPositions.clear();
        isInChoiceMode.setValue(false);
        selectedCount.setValue(0);
    }

    public void toggleSelection(int position, NoteItemData item) {
        if (item.getType() != Notes.TYPE_NOTE) return;
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
        } else {
            selectedPositions.add(position);
        }
        selectedCount.setValue(selectedPositions.size());
    }

    public void selectAll(boolean select) {
        selectedPositions.clear();
        if (select && currentNotes != null) {
            for (int i = 0; i < currentNotes.size(); i++) {
                if (currentNotes.get(i).getType() == Notes.TYPE_NOTE) {
                    selectedPositions.add(i);
                }
            }
        }
        selectedCount.setValue(selectedPositions.size());
    }

    public boolean isPositionSelected(int position) {
        return selectedPositions.contains(position);
    }

    public boolean isAllSelected() {
        int noteCount = 0;
        if (currentNotes != null) {
            for (NoteItemData item : currentNotes) {
                if (item.getType() == Notes.TYPE_NOTE) noteCount++;
            }
        }
        return selectedPositions.size() > 0 && selectedPositions.size() == noteCount;
    }

    public HashSet<Long> getSelectedIds() {
        HashSet<Long> ids = new HashSet<>();
        for (int pos : selectedPositions) {
            if (currentNotes != null && pos < currentNotes.size()) {
                long id = currentNotes.get(pos).getId();
                if (id != Notes.ID_ROOT_FOLDER) ids.add(id);
            }
        }
        return ids;
    }

    public int getSelectableNoteCount() {
        int count = 0;
        if (currentNotes != null) {
            for (NoteItemData item : currentNotes) {
                if (item.getType() == Notes.TYPE_NOTE) count++;
            }
        }
        return count;
    }

    public Set<Integer> getSelectedPositions() {
        return new HashSet<>(selectedPositions);
    }

    // ── 批量操作 ──

    public void deleteNotes(HashSet<Long> ids) {
        repository.deleteNotes(ids, () -> loadNotes(currentFolderId.getValue()));
    }

    public void moveNotes(HashSet<Long> ids, long targetFolderId) {
        repository.moveNotes(ids, targetFolderId, () -> loadNotes(currentFolderId.getValue()));
    }

    // ── 文件夹 CRUD ──

    public void createFolder(String name, long parentId) {
        repository.createFolder(name, parentId);
        loadNotes(parentId);
    }

    public void renameFolder(long folderId, String newName, long currentFolder) {
        repository.renameFolder(folderId, newName);
        loadNotes(currentFolder);
    }

    public void deleteFolderAndRefresh(long folderId) {
        HashSet<Long> ids = new HashSet<>();
        ids.add(folderId);
        repository.deleteNotes(ids, () -> {
            loadNotes(Notes.ID_ROOT_FOLDER);
            currentFolderId.postValue((long) Notes.ID_ROOT_FOLDER);
        });
    }

    public boolean checkFolderNameExists(String name) {
        return repository.checkFolderNameExists(name);
    }

    public int getUserFolderCount() {
        return repository.getUserFolderCount();
    }

    // ── 移动目标文件夹 ──

    public LiveData<List<NoteRepository.FolderItem>> getFoldersForMove() {
        return repository.getFoldersForMove(currentFolderId.getValue());
    }

    // ── 首次运行引导笔记 ──

    public void createIntroductionNoteIfNeeded() {
        Application app = getApplication();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(app);
        if (sp.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) return;

        StringBuilder sb = new StringBuilder();
        InputStream in = null;
        try {
            in = app.getResources().openRawResource(R.raw.introduction);
            if (in != null) {
                InputStreamReader isr = new InputStreamReader(in);
                BufferedReader br = new BufferedReader(isr);
                char[] buf = new char[1024];
                int len;
                while ((len = br.read(buf)) > 0) {
                    sb.append(buf, 0, len);
                }
            }
        } catch (IOException e) {
            return;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
        }

        WorkingNote note = WorkingNote.createEmptyNote(app, Notes.ID_ROOT_FOLDER,
                AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                ResourceParser.RED);
        note.setWorkingText(sb.toString());
        if (note.saveNote()) {
            sp.edit().putBoolean(PREFERENCE_ADD_INTRODUCTION, true).commit();
        }
    }

    // ── 导出文本 ──

    public void exportNotes() {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ExportTextWorker.class).build();
        WorkManager wm = WorkManager.getInstance(getApplication());
        wm.enqueue(request);
        wm.getWorkInfoByIdLiveData(request.getId()).observeForever(new Observer<WorkInfo>() {
            @Override
            public void onChanged(WorkInfo workInfo) {
                if (workInfo != null && workInfo.getState().isFinished()) {
                    wm.getWorkInfoByIdLiveData(request.getId()).removeObserver(this);
                    exportResult.postValue(workInfo.getState() == WorkInfo.State.SUCCEEDED
                            ? ExportResult.SUCCESS : ExportResult.FAILURE);
                }
            }
        });
    }
}
