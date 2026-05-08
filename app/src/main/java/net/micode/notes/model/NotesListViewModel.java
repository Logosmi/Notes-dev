package net.micode.notes.ui;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import net.micode.notes.data.NoteRepository;
import net.micode.notes.data.Notes;

import java.util.HashSet;
import java.util.List;

public class NotesListViewModel extends AndroidViewModel {

    private final NoteRepository repository;
    private final MutableLiveData<Long> currentFolderId = new MutableLiveData<>((long) Notes.ID_ROOT_FOLDER);
    private final MutableLiveData<Boolean> isSearchMode = new MutableLiveData<>(false);
    private final MutableLiveData<List<NoteItemData>> noteList = new MutableLiveData<>();

    private LiveData<List<NoteItemData>> activeSource;
    private final Observer<List<NoteItemData>> dataObserver = data -> noteList.postValue(data);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public NotesListViewModel(Application application) {
        super(application);
        repository = new NoteRepository(application.getContentResolver(), application);
        loadNotes(Notes.ID_ROOT_FOLDER);
    }

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
        isSearchMode.setValue(true);
        isSearchMode.postValue(true);

        mainHandler.post(() -> {
            if (activeSource != null) {
                activeSource.removeObserver(dataObserver);
            }
            activeSource = repository.searchNotes(query);
            activeSource.observeForever(dataObserver);
        });
    }

    public void exitSearch() {
        isSearchMode.postValue(false);
        loadNotes(currentFolderId.getValue());
    }

    public LiveData<List<NoteItemData>> getNoteList() {
        return noteList;
    }

    public LiveData<Long> getCurrentFolderId() {
        return currentFolderId;
    }

    public LiveData<Boolean> isSearchMode() {
        return isSearchMode;
    }

    public LiveData<List<NoteRepository.FolderItem>> getFoldersForMove() {
        return repository.getFoldersForMove(currentFolderId.getValue());
    }

    public void deleteNotes(HashSet<Long> ids) {
        repository.deleteNotes(ids, () -> loadNotes(currentFolderId.getValue()));
    }

    public void moveNotes(HashSet<Long> ids, long targetFolderId) {
        repository.moveNotes(ids, targetFolderId, () -> loadNotes(currentFolderId.getValue()));
    }

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

    public boolean handleBackPressed() {
        if (Boolean.TRUE.equals(isSearchMode.getValue())) {
            exitSearch();
            return true;
        }
        if (currentFolderId.getValue() != null && currentFolderId.getValue() != Notes.ID_ROOT_FOLDER) {
            loadNotes(Notes.ID_ROOT_FOLDER);
            currentFolderId.postValue((long) Notes.ID_ROOT_FOLDER);
            return true;
        }
        return false;
    }

    public boolean checkFolderNameExists(String name) {
        return repository.checkFolderNameExists(name);
    }

    public int getUserFolderCount() {
        return repository.getUserFolderCount();
    }
}