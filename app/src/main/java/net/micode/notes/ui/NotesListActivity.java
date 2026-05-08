package net.micode.notes.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;
import android.app.SearchManager;
import android.Manifest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.appbar.MaterialToolbar;

import net.micode.notes.R;
import net.micode.notes.data.NoteRepository;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.BatchDeleteWorker;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ExportTextWorker;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;

public class NotesListActivity extends AppCompatActivity implements OnClickListener, OnItemLongClickListener, OnMenuItemClickListener {

    private static final int MENU_FOLDER_DELETE = 0;
    private static final int MENU_FOLDER_VIEW = 1;
    private static final int MENU_FOLDER_CHANGE_NAME = 2;
    private static final String PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction";

    private enum ListEditState {
        NOTE_LIST, SUB_FOLDER, CALL_RECORD_FOLDER
    }

    private ListEditState mState;
    private NotesListAdapter mNotesListAdapter;
    private ListView mNotesListView;
    private boolean mIsSearchResult = false;
    private boolean mIsInChoiceMode = false;
    private View mAddNewNote;
    private TextView mTitleBar;
    private NotesListViewModel viewModel;
    private MaterialToolbar mToolbar;
    private CharSequence mOriginalTitle;
    
    private static final String TAG = "NotesListActivity";
    private NoteItemData mFocusNoteDataItem;

    private final static int REQUEST_CODE_OPEN_NODE = 102;
    private final static int REQUEST_CODE_NEW_NODE  = 103;
    private final static int REQUEST_PICK_BACKGROUND = 104;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            viewModel.searchNotes(query);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_list);

        mToolbar = findViewById(R.id.toolbar);
        mOriginalTitle = mToolbar.getTitle();
        setSupportActionBar(mToolbar);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
            }, 1);
        }

        viewModel = new ViewModelProvider(this).get(NotesListViewModel.class);

        viewModel.getNoteList().observe(this, list -> mNotesListAdapter.setNoteList(list));
        viewModel.getCurrentFolderId().observe(this, folderId -> { /* 可扩展 */ });
        viewModel.isSearchMode().observe(this, isSearch -> mIsSearchResult = isSearch != null && isSearch);

        initResources();
        loadBackground();
        setAppInfoFromRawRes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBackground();

        Boolean searchMode = viewModel.isSearchMode().getValue();
        if (searchMode == null || !searchMode) {
            Long currentFolderId = viewModel.getCurrentFolderId().getValue();
            if (currentFolderId != null) {
                viewModel.loadNotes(currentFolderId);
            }
        }
    }

    private void initResources() {
        mNotesListView = findViewById(R.id.notes_list);
        mNotesListView.addFooterView(LayoutInflater.from(this).inflate(R.layout.note_list_footer, null),
                null, false);
        mNotesListView.setOnItemClickListener(new OnListItemClickListener());
        mNotesListView.setOnItemLongClickListener(this);
        mNotesListAdapter = new NotesListAdapter(this);
        mNotesListView.setAdapter(mNotesListAdapter);
        mAddNewNote = findViewById(R.id.btn_new_note);
        mAddNewNote.setOnClickListener(this);
        mTitleBar = findViewById(R.id.tv_title_bar);
        mState = ListEditState.NOTE_LIST;
    }

    private void setAppInfoFromRawRes() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sp.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {
            StringBuilder sb = new StringBuilder();
            InputStream in = null;
            try {
                in = getResources().openRawResource(R.raw.introduction);
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
                Log.e(TAG, "Read introduction file error", e);
                return;
            } finally {
                if (in != null) {
                    try { in.close(); } catch (IOException ignored) {}
                }
            }

            WorkingNote note = WorkingNote.createEmptyNote(this, Notes.ID_ROOT_FOLDER,
                    AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                    ResourceParser.RED);
            note.setWorkingText(sb.toString());
            if (note.saveNote()) {
                sp.edit().putBoolean(PREFERENCE_ADD_INTRODUCTION, true).commit();
            } else {
                Log.e(TAG, "Save introduction note error");
            }
        }
    }

    private void loadBackground() {
        SharedPreferences sp = getSharedPreferences("notes_preferences", MODE_PRIVATE);
        String bgUri = sp.getString("background_uri", "");
        ImageView ivBg = findViewById(R.id.iv_background);
        if (ivBg == null) return;
        if (!TextUtils.isEmpty(bgUri)) {
            ivBg.setImageURI(Uri.parse(bgUri));
            ivBg.setVisibility(View.VISIBLE);
        } else {
            ivBg.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_new_note) {
            createNewNote();
        }
    }

    private void createNewNote() {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, viewModel.getCurrentFolderId().getValue());
        startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
    }

    private void openNode(NoteItemData data) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, data.getId());
        startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }

    private void openFolder(NoteItemData data) {
        long folderId = data.getId();
        viewModel.loadNotes(folderId);
        if (folderId == Notes.ID_CALL_RECORD_FOLDER) {
            mState = ListEditState.CALL_RECORD_FOLDER;
            mAddNewNote.setVisibility(View.GONE);
            mTitleBar.setText(R.string.call_record_folder_name);
        } else {
            mState = ListEditState.SUB_FOLDER;
            mTitleBar.setText(data.getSnippet());
        }
        mTitleBar.setVisibility(View.VISIBLE);
    }

    private void enterChoiceMode() {
        mIsInChoiceMode = true;
        mNotesListAdapter.setChoiceMode(true);
        mNotesListView.setLongClickable(false);
        mAddNewNote.setVisibility(View.GONE);
        mOriginalTitle = mToolbar.getTitle();
        mToolbar.getMenu().clear();
        getMenuInflater().inflate(R.menu.note_list_options, mToolbar.getMenu());

        MenuItem deleteItem = mToolbar.getMenu().findItem(R.id.delete);
        deleteItem.setOnMenuItemClickListener(this);
        MenuItem moveItem = mToolbar.getMenu().findItem(R.id.move);
        if (mFocusNoteDataItem != null && 
            (mFocusNoteDataItem.getParentId() == Notes.ID_CALL_RECORD_FOLDER || viewModel.getUserFolderCount() == 0)) {
            moveItem.setVisible(false);
        } else {
            moveItem.setVisible(true);
            moveItem.setOnMenuItemClickListener(this);
        }

        MenuItem selectAll = mToolbar.getMenu().add(Menu.NONE, R.id.action_select_all, 0, R.string.menu_select_all);
        selectAll.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        selectAll.setOnMenuItemClickListener(item -> {
            mNotesListAdapter.selectAll(!mNotesListAdapter.isAllSelected());
            updateChoiceToolbar();
            return true;
        });

        updateChoiceToolbar();
    }

    private void exitChoiceMode() {
        mIsInChoiceMode = false;
        mNotesListAdapter.setChoiceMode(false);
        mNotesListView.setLongClickable(true);
        mAddNewNote.setVisibility(View.VISIBLE);
        mToolbar.setTitle(mOriginalTitle);
        mToolbar.getMenu().clear();
        invalidateOptionsMenu();
    }

    private void updateChoiceToolbar() {
        int count = mNotesListAdapter.getSelectedCount();
        mToolbar.setTitle(count + " 已选择");
        MenuItem selectAll = mToolbar.getMenu().findItem(R.id.action_select_all);
        if (selectAll != null) {
            selectAll.setTitle(mNotesListAdapter.isAllSelected() ? R.string.menu_deselect_all : R.string.menu_select_all);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (mNotesListAdapter.getSelectedCount() == 0) {
            Toast.makeText(this, getString(R.string.menu_select_none), Toast.LENGTH_SHORT).show();
            return true;
        }
        int itemId = item.getItemId();
        if (itemId == R.id.delete) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.alert_title_delete));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getString(R.string.alert_message_delete_notes, mNotesListAdapter.getSelectedCount()));
            builder.setPositiveButton(android.R.string.ok, (dialog, which) -> batchDelete());
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.show();
        } else if (itemId == R.id.move) {
            startQueryDestinationFolders();
        }
        return true;
    }

    private void batchDelete() {
        HashSet<Long> ids = mNotesListAdapter.getSelectedItemIds();
        viewModel.deleteNotes(ids);
        exitChoiceMode();   // 删除后退出多选模式
        long[] itemIds = ids.stream().mapToLong(Long::longValue).toArray();
        Data inputData = new Data.Builder().putLongArray(BatchDeleteWorker.KEY_ITEM_IDS, itemIds).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BatchDeleteWorker.class).setInputData(inputData).build();
        WorkManager.getInstance(this).enqueue(request);
    }

    private void startQueryDestinationFolders() {
        viewModel.getFoldersForMove().observe(this, folders -> {
            if (folders != null && !folders.isEmpty()) {
                showFolderListMenu(folders);
            }
        });
    }

    private void showFolderListMenu(final List<NoteRepository.FolderItem> folders) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.menu_title_select_folder);
        String[] names = new String[folders.size()];
        for (int i = 0; i < folders.size(); i++) names[i] = folders.get(i).name;
        builder.setItems(names, (dialog, which) -> {
            long targetId = folders.get(which).id;
            HashSet<Long> ids = mNotesListAdapter.getSelectedItemIds();
            viewModel.moveNotes(ids, targetId);
            Toast.makeText(NotesListActivity.this,
                    getString(R.string.format_move_notes_to_folder, ids.size(), folders.get(which).name),
                    Toast.LENGTH_SHORT).show();
            exitChoiceMode();   // 移动后退出多选模式
        });
        builder.show();
    }

    private void deleteFolder(long folderId) {
        if (folderId == Notes.ID_ROOT_FOLDER) return;
        HashSet<AppWidgetAttribute> widgets = DataUtils.getFolderNoteWidget(getContentResolver(), folderId);
        viewModel.deleteFolderAndRefresh(folderId);
        if (widgets != null) {
            for (AppWidgetAttribute widget : widgets) {
                if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                        && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                    updateWidget(widget.widgetId, widget.widgetType);
                }
            }
        }
    }

    private void showCreateOrModifyFolderDialog(final boolean create) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        final EditText etName = view.findViewById(R.id.et_foler_name);
        showSoftInput();

        if (!create) {
            if (mFocusNoteDataItem != null) {
                etName.setText(mFocusNoteDataItem.getSnippet());
                builder.setTitle(getString(R.string.menu_folder_change_name));
            } else {
                Log.e(TAG, "The long click data item is null");
                return;
            }
        } else {
            etName.setText("");
            builder.setTitle(getString(R.string.menu_create_folder));
        }

        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> hideSoftInput(etName));

        final Dialog dialog = builder.setView(view).show();
        final Button positive = dialog.findViewById(android.R.id.button1);
        positive.setOnClickListener(v -> {
            hideSoftInput(etName);
            String name = etName.getText().toString().trim();
            if (TextUtils.isEmpty(name)) return;

            if (viewModel.checkFolderNameExists(name)) {
                Toast.makeText(NotesListActivity.this, getString(R.string.folder_exist, name), Toast.LENGTH_LONG).show();
                etName.setSelection(0, etName.length());
                return;
            }

            if (!create && mFocusNoteDataItem != null) {
                viewModel.renameFolder(mFocusNoteDataItem.getId(), name, viewModel.getCurrentFolderId().getValue());
            } else {
                viewModel.createFolder(name, viewModel.getCurrentFolderId().getValue());
            }
            dialog.dismiss();
        });

        etName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                positive.setEnabled(!TextUtils.isEmpty(s));
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        positive.setEnabled(false);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mIsInChoiceMode) return true;
        menu.clear();
        if (mState == ListEditState.NOTE_LIST) {
            getMenuInflater().inflate(R.menu.note_list, menu);
        } else if (mState == ListEditState.SUB_FOLDER) {
            getMenuInflater().inflate(R.menu.sub_folder, menu);
        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_record_folder, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_new_folder) {
            showCreateOrModifyFolderDialog(true);
        } else if (itemId == R.id.menu_export_text) {
            exportNoteToText();
        } else if (itemId == R.id.menu_setting) {
            startPreferenceActivity();
        } else if (itemId == R.id.menu_search) {
            onSearchRequested();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null, false);
        return true;
    }

    private void exportNoteToText() {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ExportTextWorker.class).build();
        WorkManager.getInstance(this).enqueue(request);
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.getId())
                .observe(this, workInfo -> {
                    if (workInfo != null && workInfo.getState().isFinished()) {
                        if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                            showExportSuccessDialog();
                        } else {
                            showExportErrorDialog();
                        }
                    }
                });
    }

    private void showExportSuccessDialog() {
        BackupUtils backup = BackupUtils.getInstance(this);
        new AlertDialog.Builder(this)
                .setTitle(R.string.success_sdcard_export)
                .setMessage(getString(R.string.format_exported_file_location,
                        backup.getExportedTextFileName(), backup.getExportedTextFileDir()))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showExportErrorDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.failed_sdcard_export)
                .setMessage(R.string.error_sdcard_export)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    private void startPreferenceActivity() {
        Activity from = getParent() != null ? getParent() : this;
        Intent intent = new Intent(from, NotesPreferenceActivity.class);
        from.startActivityIfNeeded(intent, -1);
    }

    private void showSoftInput() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    private void hideSoftInput(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        if (mNotesListView != null) mNotesListView.setOnCreateContextMenuListener(null);
        super.onContextMenuClosed(menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mFocusNoteDataItem == null) return false;
        switch (item.getItemId()) {
            case MENU_FOLDER_VIEW:
                openFolder(mFocusNoteDataItem);
                break;
            case MENU_FOLDER_DELETE:
                new AlertDialog.Builder(this)
                        .setTitle(R.string.alert_title_delete)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.alert_message_delete_folder)
                        .setPositiveButton(android.R.string.ok, (d, w) -> deleteFolder(mFocusNoteDataItem.getId()))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                break;
            case MENU_FOLDER_CHANGE_NAME:
                showCreateOrModifyFolderDialog(false);
                break;
        }
        return true;
    }

    private class OnListItemClickListener implements OnItemClickListener {
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (view instanceof NotesListItem) {
                NoteItemData item = ((NotesListItem) view).getItemData();
                // 多选模式：切换选中状态
                if (mIsInChoiceMode) {
                    if (item.getType() == Notes.TYPE_NOTE) {
                        int realPos = position - mNotesListView.getHeaderViewsCount();
                        boolean current = mNotesListAdapter.isSelectedItem(realPos);
                        mNotesListAdapter.setCheckedItem(realPos, !current);
                        mNotesListAdapter.notifyDataSetChanged();
                        updateChoiceToolbar();
                    }
                    return;
                }

                switch (mState) {
                    case NOTE_LIST:
                        if (item.getType() == Notes.TYPE_FOLDER || item.getType() == Notes.TYPE_SYSTEM)
                            openFolder(item);
                        else if (item.getType() == Notes.TYPE_NOTE)
                            openNode(item);
                        break;
                    case SUB_FOLDER:
                    case CALL_RECORD_FOLDER:
                        if (item.getType() == Notes.TYPE_NOTE) openNode(item);
                        break;
                }
            }
        }
    }

    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (view instanceof NotesListItem) {
            mFocusNoteDataItem = ((NotesListItem) view).getItemData();
            if (mFocusNoteDataItem.getType() == Notes.TYPE_NOTE && !mIsInChoiceMode) {
                enterChoiceMode();
                int realPosition = position - mNotesListView.getHeaderViewsCount();
                mNotesListAdapter.setCheckedItem(realPosition, true);
                mNotesListAdapter.notifyDataSetChanged();
                updateChoiceToolbar();
                return true;
            } else if (mFocusNoteDataItem.getType() == Notes.TYPE_FOLDER) {
                PopupMenu popup = new PopupMenu(this, view);
                popup.getMenu().add(0, MENU_FOLDER_VIEW, 0, R.string.menu_folder_view);
                popup.getMenu().add(0, MENU_FOLDER_DELETE, 0, R.string.menu_folder_delete);
                popup.getMenu().add(0, MENU_FOLDER_CHANGE_NAME, 0, R.string.menu_folder_change_name);
                popup.setOnMenuItemClickListener(item -> {
                    onContextItemSelected(item);
                    return true;
                });
                popup.show();
            }
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (mIsInChoiceMode) {
            exitChoiceMode();
            return;
        }
        if (viewModel.handleBackPressed()) {
            mState = ListEditState.NOTE_LIST;
            mTitleBar.setVisibility(View.GONE);
            mAddNewNote.setVisibility(View.VISIBLE);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CODE_OPEN_NODE || requestCode == REQUEST_CODE_NEW_NODE) {
                viewModel.loadNotes(viewModel.getCurrentFolderId().getValue());
            }
        }
    }

    private void updateWidget(int appWidgetId, int appWidgetType) {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (appWidgetType == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (appWidgetType == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else return;
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }
}