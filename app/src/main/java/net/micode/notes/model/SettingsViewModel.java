package net.micode.notes.model;

import android.app.Application;
import android.content.Context;
import android.text.format.DateFormat;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import net.micode.notes.R;
import net.micode.notes.gtask.remote.GTaskSyncService;

public class SettingsViewModel extends AndroidViewModel {
    private static final String PREF_NAME = "notes_preferences";
    private static final String KEY_SYNC_ACCOUNT = "pref_key_account_name";
    private static final String KEY_LAST_SYNC_TIME = "pref_last_sync_time";

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
        android.content.SharedPreferences sp = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean isSyncing = GTaskSyncService.isSyncing();
        String account = sp.getString(KEY_SYNC_ACCOUNT, "");

        syncButtonText.setValue(isSyncing ?
                app.getString(R.string.preferences_button_sync_cancel) :
                app.getString(R.string.preferences_button_sync_immediately));

        syncButtonEnabled.setValue(!account.isEmpty());

        if (isSyncing) {
            syncStatusText.setValue(GTaskSyncService.getProgressString());
            syncStatusVisible.setValue(true);
        } else {
            long lastSyncTime = sp.getLong(KEY_LAST_SYNC_TIME, 0);
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
