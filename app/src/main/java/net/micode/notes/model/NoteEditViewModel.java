package net.micode.notes.model;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.tag.TagManager;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.tool.ResourceParser.TextAppearanceResources;
import net.micode.notes.ui.AlarmReceiver;
import net.micode.notes.ui.NotesPreferenceActivity;

import java.util.HashSet;

public class NoteEditViewModel extends AndroidViewModel implements WorkingNote.NoteSettingChangedListener {

    private static final String TAG = "NoteEditViewModel";
    private static final String PREFERENCE_FONT_SIZE = "pref_font_size";

    private WorkingNote mWorkingNote;
    private int mFontSizeId;
    private String mUserQuery;

    private final MutableLiveData<Integer> bgColorId = new MutableLiveData<>();
    private final MutableLiveData<Integer> fontSizeId = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isCheckListMode = new MutableLiveData<>(false);
    private final MutableLiveData<Long> modifiedDate = new MutableLiveData<>();
    private final MutableLiveData<AlertInfo> alertInfo = new MutableLiveData<>();
    private final MutableLiveData<Boolean> noteDeleted = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> noteSaved = new MutableLiveData<>();
    private final MutableLiveData<Boolean> widgetUpdated = new MutableLiveData<>();
    private final MutableLiveData<Boolean> checkListModeChanged = new MutableLiveData<>();

    private int mWidgetId;
    private int mWidgetType;

    public static class AlertInfo {
        public final long alertDate;
        public final boolean enabled;
        public AlertInfo(long d, boolean e) { alertDate = d; enabled = e; }
    }

    public NoteEditViewModel(Application application) {
        super(application);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(application);
        mFontSizeId = sp.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE);
        if (mFontSizeId >= TextAppearanceResources.getResourcesSize()) {
            mFontSizeId = ResourceParser.BG_DEFAULT_FONT_SIZE;
        }
    }

    // ── LiveData accessors ──

    public LiveData<Integer> getBgColorId() { return bgColorId; }
    public LiveData<Integer> getFontSizeId() { return fontSizeId; }
    public LiveData<Boolean> getIsCheckListMode() { return isCheckListMode; }
    public LiveData<Long> getModifiedDate() { return modifiedDate; }
    public LiveData<AlertInfo> getAlertInfo() { return alertInfo; }
    public LiveData<Boolean> getNoteDeleted() { return noteDeleted; }
    public LiveData<Boolean> getNoteSaved() { return noteSaved; }
    public LiveData<Boolean> getWidgetUpdated() { return widgetUpdated; }
    public LiveData<Boolean> getCheckListModeChanged() { return checkListModeChanged; }

    public WorkingNote getWorkingNote() { return mWorkingNote; }
    public int getFontSizeIdValue() { return mFontSizeId; }
    public String getUserQuery() { return mUserQuery; }
    public long getNoteId() { return mWorkingNote != null ? mWorkingNote.getNoteId() : 0; }
    public long getFolderId() { return mWorkingNote != null ? mWorkingNote.getFolderId() : 0; }
    public int getWidgetId() { return mWidgetId; }
    public int getWidgetType() { return mWidgetType; }
    public boolean noteExists() { return mWorkingNote != null && mWorkingNote.existInDatabase(); }

    // ── Initialization ──

    public boolean init(Intent intent) {
        Application app = getApplication();
        mWorkingNote = null;
        mUserQuery = "";

        if (TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
            long noteId = intent.getLongExtra(Intent.EXTRA_UID, 0);
            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY)) {
                noteId = Long.parseLong(intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));
                mUserQuery = intent.getStringExtra(SearchManager.USER_QUERY);
            }

            if (!DataUtils.visibleInNoteDatabase(app.getContentResolver(), noteId, Notes.TYPE_NOTE)) {
                return false;
            }
            mWorkingNote = WorkingNote.load(app, noteId);
            if (mWorkingNote == null) {
                Log.e(TAG, "load note failed with note id " + noteId);
                return false;
            }
        } else if (TextUtils.equals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction())) {
            long folderId = intent.getLongExtra(Notes.INTENT_EXTRA_FOLDER_ID, 0);
            mWidgetId = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            mWidgetType = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, Notes.TYPE_WIDGET_INVALIDE);
            int bgResId = intent.getIntExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, ResourceParser.getDefaultBgId(app));

            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            long callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0);
            if (callDate != 0 && phoneNumber != null) {
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.w(TAG, "The call record number is null");
                }
                long existingNoteId = DataUtils.getNoteIdByPhoneNumberAndCallDate(
                        app.getContentResolver(), phoneNumber, callDate);
                if (existingNoteId > 0) {
                    mWorkingNote = WorkingNote.load(app, existingNoteId);
                    if (mWorkingNote == null) {
                        Log.e(TAG, "load call note failed with note id " + existingNoteId);
                        return false;
                    }
                } else {
                    mWorkingNote = WorkingNote.createEmptyNote(app, folderId, mWidgetId, mWidgetType, bgResId);
                    mWorkingNote.convertToCallNote(phoneNumber, callDate);
                }
            } else {
                mWorkingNote = WorkingNote.createEmptyNote(app, folderId, mWidgetId, mWidgetType, bgResId);
            }
        } else {
            Log.e(TAG, "Intent not specified action, should not support");
            return false;
        }

        mWorkingNote.setOnSettingStatusChangedListener(this);
        updateStateFromNote();
        return true;
    }

    private void updateStateFromNote() {
        if (mWorkingNote == null) return;
        bgColorId.setValue(mWorkingNote.getBgColorId());
        isCheckListMode.setValue(mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST);
        modifiedDate.setValue(mWorkingNote.getModifiedDate());
        alertInfo.setValue(new AlertInfo(mWorkingNote.getAlertDate(), mWorkingNote.hasClockAlert()));
    }

    // ── Note actions ──

    public boolean saveNote() {
        if (mWorkingNote == null) return false;
        boolean saved = mWorkingNote.saveNote();
        if (saved) {
            updateStateFromNote();
            noteSaved.setValue(true);
            // sync inline #tags
            String content = mWorkingNote.getContent();
            long noteId = mWorkingNote.getNoteId();
            if (content != null && noteId > 0) {
                TagManager.getInstance(getApplication()).syncTagsForNote(noteId, content);
            }
        }
        return saved;
    }

    public void deleteNote() {
        if (mWorkingNote == null || !mWorkingNote.existInDatabase()) {
            mWorkingNote.markDeleted(true);
            return;
        }
        HashSet<Long> ids = new HashSet<>();
        long id = mWorkingNote.getNoteId();
        if (id == Notes.ID_ROOT_FOLDER) {
            Log.d(TAG, "Wrong note id, should not happen");
            return;
        }
        ids.add(id);
        Application app = getApplication();
        if (!isSyncMode()) {
            DataUtils.batchDeleteNotes(app.getContentResolver(), ids);
        } else {
            DataUtils.batchMoveToFolder(app.getContentResolver(), ids, Notes.ID_TRASH_FOLER);
        }
        mWorkingNote.markDeleted(true);
        noteDeleted.setValue(true);
    }

    public void setWorkingText(String text) {
        if (mWorkingNote != null) mWorkingNote.setWorkingText(text);
    }

    public void setBgColor(int colorId) {
        if (mWorkingNote != null) mWorkingNote.setBgColorId(colorId);
    }

    public void setFontSize(int sizeId) {
        mFontSizeId = sizeId;
        PreferenceManager.getDefaultSharedPreferences(getApplication())
                .edit().putInt(PREFERENCE_FONT_SIZE, mFontSizeId).commit();
        fontSizeId.setValue(mFontSizeId);
    }

    public void toggleCheckListMode() {
        if (mWorkingNote == null) return;
        int newMode = mWorkingNote.getCheckListMode() == 0 ? TextNote.MODE_CHECK_LIST : 0;
        mWorkingNote.setCheckListMode(newMode);
    }

    public void setAlertDate(long date, boolean set) {
        if (mWorkingNote != null) mWorkingNote.setAlertDate(date, set);
    }

    public String getContent() {
        return mWorkingNote != null ? mWorkingNote.getContent() : "";
    }

    public boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(getApplication()).trim().length() > 0;
    }

    public int getNoteBgRes(int colorId) {
        return ResourceParser.NoteBgResources.getNoteBgResource(colorId);
    }

    public int getNoteTitleBgRes(int colorId) {
        return ResourceParser.NoteBgResources.getNoteTitleBgResource(colorId);
    }

    public void consumeCheckListModeChanged() {
        checkListModeChanged.postValue(false);
    }

    // ── NoteSettingChangedListener implementation ──

    @Override
    public void onBackgroundColorChanged() {
        if (mWorkingNote != null) bgColorId.postValue(mWorkingNote.getBgColorId());
    }

    @Override
    public void onClockAlertChanged(long date, boolean set) {
        if (mWorkingNote == null) return;
        if (!mWorkingNote.existInDatabase()) saveNote();
        if (mWorkingNote.getNoteId() <= 0) return;

        Context ctx = getApplication();
        Intent intent = new Intent(ctx, AlarmReceiver.class);
        intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mWorkingNote.getNoteId()));
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);

        if (!set) {
            alarmManager.cancel(pendingIntent);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, date, pendingIntent);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, date, pendingIntent);
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, date, pendingIntent);
            }
        }
        alertInfo.postValue(new AlertInfo(date, set));
    }

    @Override
    public void onWidgetChanged() {
        widgetUpdated.postValue(true);
    }

    @Override
    public void onCheckListModeChanged(int oldMode, int newMode) {
        if (mWorkingNote != null) {
            isCheckListMode.postValue(newMode == TextNote.MODE_CHECK_LIST);
        }
        checkListModeChanged.postValue(true);
    }
}
