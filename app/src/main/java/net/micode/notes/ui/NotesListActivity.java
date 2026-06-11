package net.micode.notes.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.app.SearchManager;
import android.Manifest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;

import net.micode.notes.R;
import net.micode.notes.data.NoteItemData;
import net.micode.notes.data.NoteRepository;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.AppWidgetAttribute;
import net.micode.notes.model.NotesListViewModel;
import net.micode.notes.model.NotesListViewModel.ExportResult;
import net.micode.notes.model.NotesListViewModel.ListEditState;
import net.micode.notes.tag.Tag;
import net.micode.notes.tag.TagManager;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.util.HashSet;
import java.util.List;

public class NotesListActivity extends AppCompatActivity implements OnClickListener, OnItemLongClickListener, OnMenuItemClickListener {

    private static final int MENU_FOLDER_DELETE = 0;
    private static final int MENU_FOLDER_VIEW = 1;
    private static final int MENU_FOLDER_CHANGE_NAME = 2;

    private NotesListAdapter mNotesListAdapter;
    private ListView mNotesListView;
    private View mAddNewNote;
    private TextView mTitleBar;
    private View mTagFilterContainer;
    private LinearLayout mTagFilterBar;
    private NotesListViewModel viewModel;
    private MaterialToolbar mToolbar;
    private CharSequence mOriginalTitle;
    private NoteItemData mFocusNoteDataItem;

    private static final String TAG = "NotesListActivity";

    private final static int REQUEST_CODE_OPEN_NODE = 102;
    private final static int REQUEST_CODE_NEW_NODE  = 103;

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
        viewModel.getListState().observe(this, this::onListStateChanged);
        viewModel.isSearchMode().observe(this, isSearch -> {});
        viewModel.getInChoiceMode().observe(this, this::onChoiceModeChanged);
        viewModel.getSelectedCount().observe(this, count -> {
            mNotesListAdapter.setSelectedPositions(viewModel.getSelectedPositions());
            updateChoiceToolbar();
        });
        viewModel.getExportResult().observe(this, this::onExportResult);

        initResources();
        loadBackground();
        viewModel.createIntroductionNoteIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBackground();
        refreshTagFilter();
        Boolean searchMode = viewModel.isSearchMode().getValue();
        if (searchMode == null || !searchMode) {
            Long currentFolderId = viewModel.getCurrentFolderId().getValue();
            if (currentFolderId != null) {
                viewModel.loadNotes(currentFolderId);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            viewModel.searchNotes(intent.getStringExtra(SearchManager.QUERY));
        }
    }

    // ── UI 初始化 ──

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
        mTagFilterContainer = findViewById(R.id.tag_filter_container);
        mTagFilterBar = findViewById(R.id.tag_filter_bar);
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

    // ── LiveData 观察回调 ──

    private void onListStateChanged(ListEditState state) {
        if (state == null) return;
        switch (state) {
            case NOTE_LIST:
                mTitleBar.setVisibility(View.GONE);
                mAddNewNote.setVisibility(View.VISIBLE);
                break;
            case SUB_FOLDER:
                mTitleBar.setVisibility(View.VISIBLE);
                mAddNewNote.setVisibility(View.VISIBLE);
                break;
            case CALL_RECORD_FOLDER:
                mTitleBar.setVisibility(View.VISIBLE);
                mTitleBar.setText(R.string.call_record_folder_name);
                mAddNewNote.setVisibility(View.GONE);
                break;
        }
    }

    private void onChoiceModeChanged(Boolean inChoice) {
        if (inChoice == null) return;
        if (inChoice) {
            setupChoiceModeUI();
        } else {
            restoreNormalUI();
        }
    }

    private void onExportResult(ExportResult result) {
        if (result == null) return;
        if (result == ExportResult.SUCCESS) {
            showExportSuccessDialog();
        } else {
            showExportErrorDialog();
        }
    }

    // ── 多选模式 UI ──

    private void setupChoiceModeUI() {
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
            viewModel.selectAll(!viewModel.isAllSelected());
            return true;
        });

        updateChoiceToolbar();
    }

    private void restoreNormalUI() {
        mNotesListAdapter.setChoiceMode(false);
        mNotesListAdapter.setSelectedPositions(null);
        mNotesListView.setLongClickable(true);
        mAddNewNote.setVisibility(View.VISIBLE);
        mToolbar.setTitle(mOriginalTitle);
        mToolbar.getMenu().clear();
        invalidateOptionsMenu();
    }

    private void updateChoiceToolbar() {
        Integer count = viewModel.getSelectedCount().getValue();
        int c = count != null ? count : 0;
        mToolbar.setTitle(c + " 已选择");
        MenuItem selectAll = mToolbar.getMenu().findItem(R.id.action_select_all);
        if (selectAll != null) {
            selectAll.setTitle(viewModel.isAllSelected() ? R.string.menu_deselect_all : R.string.menu_select_all);
        }
    }

    // ── 导航 ──

    private void createNewNote() {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, viewModel.getCurrentFolderIdValue());
        startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
    }

    private void openNode(NoteItemData data) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, data.getId());
        startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }

    private void openFolder(NoteItemData data) {
        mTitleBar.setText(data.getSnippet());
        viewModel.openFolder(data.getId(), data.getSnippet());
    }

    // ── 标签过滤 ──

    private void refreshTagFilter() {
        TagManager tm = TagManager.getInstance(this);
        List<Tag> tags = tm.getAllTags();
        mTagFilterBar.removeAllViews();
        if (tags.isEmpty()) {
            mTagFilterContainer.setVisibility(View.GONE);
            return;
        }
        mTagFilterContainer.setVisibility(View.VISIBLE);
        for (Tag tag : tags) {
            Button chip = new Button(this);
            chip.setText("#" + tag.getName());
            chip.setTextSize(12);
            chip.setPadding(16, 4, 16, 4);
            chip.setOnClickListener(v -> {
                List<Long> noteIds = tm.getNoteIdsByTagId(tag.getId());
                viewModel.filterByNoteIds(noteIds);
            });
            mTagFilterBar.addView(chip);
        }
        Button allBtn = new Button(this);
        allBtn.setText(R.string.tag_filter_all);
        allBtn.setTextSize(12);
        allBtn.setPadding(16, 4, 16, 4);
        allBtn.setOnClickListener(v -> viewModel.loadNotes(viewModel.getCurrentFolderIdValue()));
        mTagFilterBar.addView(allBtn);
    }

    // ── 选项菜单 ──

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (Boolean.TRUE.equals(viewModel.getInChoiceMode().getValue())) return true;
        menu.clear();
        ListEditState state = viewModel.getListState().getValue();
        if (state == ListEditState.NOTE_LIST) {
            getMenuInflater().inflate(R.menu.note_list, menu);
        } else if (state == ListEditState.SUB_FOLDER) {
            getMenuInflater().inflate(R.menu.sub_folder, menu);
        } else if (state == ListEditState.CALL_RECORD_FOLDER) {
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
            viewModel.exportNotes();
        } else if (itemId == R.id.menu_setting) {
            startPreferenceActivity();
        } else if (itemId == R.id.menu_search) {
            onSearchRequested();
        } else if (itemId == R.id.menu_manage_tags) {
            startActivity(new Intent(this, TagManagementActivity.class));
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

    // ── 批量操作 ──

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (viewModel.getSelectedCount().getValue() == null || viewModel.getSelectedCount().getValue() == 0) {
            Toast.makeText(this, getString(R.string.menu_select_none), Toast.LENGTH_SHORT).show();
            return true;
        }
        int itemId = item.getItemId();
        if (itemId == R.id.delete) {
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.alert_title_delete))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(getString(R.string.alert_message_delete_notes, viewModel.getSelectedCount().getValue()))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> batchDelete())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        } else if (itemId == R.id.move) {
            viewModel.getFoldersForMove().observe(this, folders -> {
                if (folders != null && !folders.isEmpty()) {
                    showFolderListMenu(folders);
                }
            });
        }
        return true;
    }

    private void batchDelete() {
        HashSet<Long> ids = viewModel.getSelectedIds();
        viewModel.deleteNotes(ids);
        viewModel.exitChoiceMode();
    }

    private void showFolderListMenu(final List<NoteRepository.FolderItem> folders) {
        String[] names = new String[folders.size()];
        for (int i = 0; i < folders.size(); i++) names[i] = folders.get(i).name;
        new AlertDialog.Builder(this)
            .setTitle(R.string.menu_title_select_folder)
            .setItems(names, (dialog, which) -> {
                long targetId = folders.get(which).id;
                HashSet<Long> ids = viewModel.getSelectedIds();
                viewModel.moveNotes(ids, targetId);
                Toast.makeText(NotesListActivity.this,
                        getString(R.string.format_move_notes_to_folder, ids.size(), folders.get(which).name),
                        Toast.LENGTH_SHORT).show();
                viewModel.exitChoiceMode();
            })
            .show();
    }

    // ── 文件夹操作 ──

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
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        final EditText etName = view.findViewById(R.id.et_foler_name);
        showSoftInput();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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

        final AlertDialog dialog = builder.setView(view).show();
        final Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
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
                viewModel.renameFolder(mFocusNoteDataItem.getId(), name, viewModel.getCurrentFolderIdValue());
            } else {
                viewModel.createFolder(name, viewModel.getCurrentFolderIdValue());
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

    // ── 上下文菜单 ──

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

    // ── 列表点击/长按 ──

    private class OnListItemClickListener implements OnItemClickListener {
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (view instanceof NotesListItem) {
                NoteItemData item = ((NotesListItem) view).getItemData();
                if (Boolean.TRUE.equals(viewModel.getInChoiceMode().getValue())) {
                    viewModel.toggleSelection(position - mNotesListView.getHeaderViewsCount(), item);
                    return;
                }
                ListEditState state = viewModel.getListState().getValue();
                if (state == ListEditState.NOTE_LIST) {
                    if (item.getType() == Notes.TYPE_FOLDER || item.getType() == Notes.TYPE_SYSTEM)
                        openFolder(item);
                    else if (item.getType() == Notes.TYPE_NOTE)
                        openNode(item);
                } else if (state == ListEditState.SUB_FOLDER || state == ListEditState.CALL_RECORD_FOLDER) {
                    if (item.getType() == Notes.TYPE_NOTE) openNode(item);
                }
            }
        }
    }

    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (view instanceof NotesListItem) {
            mFocusNoteDataItem = ((NotesListItem) view).getItemData();
            if (mFocusNoteDataItem.getType() == Notes.TYPE_NOTE
                    && !Boolean.TRUE.equals(viewModel.getInChoiceMode().getValue())) {
                viewModel.enterChoiceMode();
                int realPosition = position - mNotesListView.getHeaderViewsCount();
                viewModel.toggleSelection(realPosition, mFocusNoteDataItem);
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

    // ── 点击事件 ──

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_new_note) {
            createNewNote();
        }
    }

    // ── 返回键 ──

    @Override
    public void onBackPressed() {
        if (Boolean.TRUE.equals(viewModel.getInChoiceMode().getValue())) {
            viewModel.exitChoiceMode();
            return;
        }
        if (!viewModel.handleBackPressed()) {
            super.onBackPressed();
        }
    }

    // ── Activity 结果 ──

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && (requestCode == REQUEST_CODE_OPEN_NODE || requestCode == REQUEST_CODE_NEW_NODE)) {
            viewModel.loadNotes(viewModel.getCurrentFolderIdValue());
        }
    }

    // ── 导出 ──

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

    // ── 设置 ──

    private void startPreferenceActivity() {
        Activity from = getParent() != null ? getParent() : this;
        Intent intent = new Intent(from, NotesPreferenceActivity.class);
        from.startActivityIfNeeded(intent, -1);
    }

    // ── 输入法 ──

    private void showSoftInput() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    private void hideSoftInput(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    // ── Widget ──

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
