package net.micode.notes.model;

import android.app.Application;
import android.text.format.DateFormat;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import net.micode.notes.R;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.ui.NotesPreferenceActivity;

public class SettingsViewModel extends AndroidViewModel {
    private final MutableLiveData<String> syncButtonText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> syncButtonEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<String> syncStatusText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> syncStatusVisible = new MutableLiveData<>(false);

    public SettingsViewModel(Application application) {
        super(application);
        refreshSyncUI();
    }

    public LiveData<String> getSyncButtonText() { return syncButtonText; }
    public LiveData<Boolean> getSyncButtonEnabled() { return syncButtonEnabled; }
    public LiveData<String> getSyncStatusText() { return syncStatusText; }
    public LiveData<Boolean> getSyncStatusVisible() { return syncStatusVisible; }

    public void refreshSyncUI() {
        Application app = getApplication();
        boolean isSyncing = GTaskSyncService.isSyncing();
        String account = NotesPreferenceActivity.getSyncAccountName(app);

        syncButtonText.setValue(isSyncing ?
                app.getString(R.string.preferences_button_sync_cancel) :
                app.getString(R.string.preferences_button_sync_immediately));

        syncButtonEnabled.setValue(!account.isEmpty());

        if (isSyncing) {
            syncStatusText.setValue(GTaskSyncService.getProgressString());
            syncStatusVisible.setValue(true);
        } else {
            long lastSyncTime = NotesPreferenceActivity.getLastSyncTime(app);
            if (lastSyncTime != 0) {
                String timeStr = DateFormat.format(
                        app.getString(R.string.preferences_last_sync_time_format),
                        lastSyncTime).toString();
                syncStatusText.setValue(app.getString(R.string.preferences_last_sync_time, timeStr));
                syncStatusVisible.setValue(true);
            } else {
                syncStatusText.setValue("");
                syncStatusVisible.setValue(false);
            }
        }
    }
}
