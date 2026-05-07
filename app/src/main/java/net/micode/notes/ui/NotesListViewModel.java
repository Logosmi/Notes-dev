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

import java.util.List;

public class NotesListViewModel extends AndroidViewModel {

    private final NoteRepository repository;
    private final MutableLiveData<Long> currentFolderId = new MutableLiveData<>((long) Notes.ID_ROOT_FOLDER);
    private final MutableLiveData<Boolean> isSearchMode = new MutableLiveData<>(false);

    private final MutableLiveData<List<NoteItemData>> noteList = new MutableLiveData<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Observer<List<NoteItemData>> dataObserver = data -> noteList.postValue(data);

    private LiveData<List<NoteItemData>> activeSource;

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
            LiveData<List<NoteItemData>> newSource = repository.getNotesByFolder(folderId);
            newSource.observeForever(dataObserver);
            activeSource = newSource;
        });
    }

    public void searchNotes(String query) {
        isSearchMode.setValue(true);
        mainHandler.post(() -> {
            if (activeSource != null) {
                activeSource.removeObserver(dataObserver);
            }
            LiveData<List<NoteItemData>> newSource = repository.searchNotes(query);
            newSource.observeForever(dataObserver);
            activeSource = newSource;
        });
    }

    public void exitSearch() {
        isSearchMode.setValue(false);
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

    public void deleteNotes(List<Long> ids) {
        repository.deleteNotes(ids, () -> {
            loadNotes(currentFolderId.getValue());
        });
    }

    public void moveNotes(List<Long> ids, long targetFolderId) {
        repository.moveNotes(ids, targetFolderId, () -> {
            loadNotes(currentFolderId.getValue());
        });
    }

    public long createNewNote() {
        return repository.createNote(currentFolderId.getValue());
    }

    public boolean handleBackPressed() {
        if (Boolean.TRUE.equals(isSearchMode.getValue())) {
            exitSearch();
            return true;
        }
        if (currentFolderId.getValue() != null && currentFolderId.getValue() != Notes.ID_ROOT_FOLDER) {
            loadNotes(Notes.ID_ROOT_FOLDER);
            return true;
        }
        return false;
    }
}