/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.app.AlertDialog;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.model.NoteEditViewModel;
import net.micode.notes.model.NoteEditViewModel.AlertInfo;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.tool.ResourceParser.TextAppearanceResources;
import net.micode.notes.ui.DateTimePickerDialog.OnDateTimeSetListener;
import net.micode.notes.ui.NoteEditText.OnTextViewChangeListener;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class NoteEditActivity extends AppCompatActivity implements OnClickListener, OnTextViewChangeListener {
    private class HeadViewHolder {
        public TextView tvModified;
        public ImageView ivAlertIcon;
        public TextView tvAlertDate;
    }

    private static final Map<Integer, Integer> sBgSelectorBtnsMap = new HashMap<>();
    static {
        sBgSelectorBtnsMap.put(R.id.iv_bg_yellow, ResourceParser.YELLOW);
        sBgSelectorBtnsMap.put(R.id.iv_bg_red, ResourceParser.RED);
        sBgSelectorBtnsMap.put(R.id.iv_bg_blue, ResourceParser.BLUE);
        sBgSelectorBtnsMap.put(R.id.iv_bg_green, ResourceParser.GREEN);
        sBgSelectorBtnsMap.put(R.id.iv_bg_white, ResourceParser.WHITE);
    }

    private static final Map<Integer, Integer> sBgSelectorSelectionMap = new HashMap<>();
    static {
        sBgSelectorSelectionMap.put(ResourceParser.YELLOW, R.id.iv_bg_yellow_select);
        sBgSelectorSelectionMap.put(ResourceParser.RED, R.id.iv_bg_red_select);
        sBgSelectorSelectionMap.put(ResourceParser.BLUE, R.id.iv_bg_blue_select);
        sBgSelectorSelectionMap.put(ResourceParser.GREEN, R.id.iv_bg_green_select);
        sBgSelectorSelectionMap.put(ResourceParser.WHITE, R.id.iv_bg_white_select);
    }

    private static final Map<Integer, Integer> sFontSizeBtnsMap = new HashMap<>();
    static {
        sFontSizeBtnsMap.put(R.id.ll_font_large, ResourceParser.TEXT_LARGE);
        sFontSizeBtnsMap.put(R.id.ll_font_small, ResourceParser.TEXT_SMALL);
        sFontSizeBtnsMap.put(R.id.ll_font_normal, ResourceParser.TEXT_MEDIUM);
        sFontSizeBtnsMap.put(R.id.ll_font_super, ResourceParser.TEXT_SUPER);
    }

    private static final Map<Integer, Integer> sFontSelectorSelectionMap = new HashMap<>();
    static {
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_LARGE, R.id.iv_large_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SMALL, R.id.iv_small_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_MEDIUM, R.id.iv_medium_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SUPER, R.id.iv_super_select);
    }

    private static final String TAG = "NoteEditActivity";
    private static final int SHORTCUT_ICON_TITLE_MAX_LEN = 10;

    public static final String TAG_CHECKED = Notes.TAG_CHECKED;
    public static final String TAG_UNCHECKED = Notes.TAG_UNCHECKED;

    private HeadViewHolder mNoteHeaderHolder;
    private View mHeadViewPanel;
    private View mNoteBgColorSelector;
    private View mFontSizeSelector;
    private EditText mNoteEditor;
    private View mNoteEditorPanel;
    private LinearLayout mEditTextList;
    private String mUserQuery;
    private Pattern mPattern;
    private TextView mMenuMore;

    private NoteEditViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_edit);

        viewModel = new ViewModelProvider(this).get(NoteEditViewModel.class);

        if (savedInstanceState == null && !initFromIntent(getIntent())) {
            finish();
            return;
        }
        initResources();
        observeViewModel();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey(Intent.EXTRA_UID)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.putExtra(Intent.EXTRA_UID, savedInstanceState.getLong(Intent.EXTRA_UID));
            if (!initFromIntent(intent)) {
                finish();
                return;
            }
            Log.d(TAG, "Restoring from killed activity");
        }
    }

    private boolean initFromIntent(Intent intent) {
        if (!viewModel.init(intent)) {
            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY) || TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
                Intent jump = new Intent(this, NotesListActivity.class);
                startActivity(jump);
                showToast(R.string.error_note_not_exist);
            }
            return false;
        }
        if (TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        } else {
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        mUserQuery = viewModel.getUserQuery();
        return true;
    }

    private void observeViewModel() {
        viewModel.getBgColorId().observe(this, colorId -> {
            if (colorId == null) return;
            for (Integer id : sBgSelectorSelectionMap.keySet())
                findViewById(sBgSelectorSelectionMap.get(id)).setVisibility(View.GONE);
            findViewById(sBgSelectorSelectionMap.get(colorId)).setVisibility(View.VISIBLE);
            mNoteEditorPanel.setBackgroundResource(viewModel.getNoteBgRes(colorId));
            mHeadViewPanel.setBackgroundResource(viewModel.getNoteTitleBgRes(colorId));
        });

        viewModel.getNoteDeleted().observe(this, deleted -> {
            if (Boolean.TRUE.equals(deleted)) finish();
        });

        viewModel.getAlertInfo().observe(this, info -> {
            if (info != null) showAlertHeader(info.alertDate);
        });

        viewModel.getWidgetUpdated().observe(this, updated -> {
            if (Boolean.TRUE.equals(updated)) updateWidget();
        });

        viewModel.getCheckListModeChanged().observe(this, changed -> {
            if (Boolean.TRUE.equals(changed)) {
                getWorkingTextFromUi();
                WorkingNote note = viewModel.getWorkingNote();
                if (note != null && note.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                    switchToListMode(note.getContent());
                } else {
                    if (note != null)
                        mNoteEditor.setText(getHighlightQueryResult(note.getContent(), mUserQuery));
                    mEditTextList.setVisibility(View.GONE);
                    mNoteEditor.setVisibility(View.VISIBLE);
                }
                viewModel.consumeCheckListModeChanged();
            }
        });
    }

    // ── UI Init ──

    private void initResources() {
        mHeadViewPanel = findViewById(R.id.note_title);
        mNoteHeaderHolder = new HeadViewHolder();
        mNoteHeaderHolder.tvModified = findViewById(R.id.tv_modified_date);
        mNoteHeaderHolder.ivAlertIcon = findViewById(R.id.iv_alert_icon);
        mNoteHeaderHolder.tvAlertDate = findViewById(R.id.tv_alert_date);
        mNoteEditor = findViewById(R.id.note_edit_view);
        mNoteEditorPanel = findViewById(R.id.sv_note_edit);
        mNoteBgColorSelector = findViewById(R.id.note_bg_color_selector);
        mMenuMore = findViewById(R.id.menu_more);
        mMenuMore.setOnClickListener(v -> showOverflowMenu());
        for (int id : sBgSelectorBtnsMap.keySet()) findViewById(id).setOnClickListener(this);
        mFontSizeSelector = findViewById(R.id.font_size_selector);
        for (int id : sFontSizeBtnsMap.keySet()) findViewById(id).setOnClickListener(this);
        mEditTextList = findViewById(R.id.note_edit_list);
    }

    @Override
    protected void onResume() {
        super.onResume();
        initNoteScreen();
    }

    private void initNoteScreen() {
        WorkingNote note = viewModel.getWorkingNote();
        if (note == null) return;
        mNoteEditor.setTextAppearance(this,
                TextAppearanceResources.getTexAppearanceResource(viewModel.getFontSizeIdValue()));
        if (note.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            switchToListMode(note.getContent());
        } else {
            mNoteEditor.setText(getHighlightQueryResult(note.getContent(), mUserQuery));
            mNoteEditor.setSelection(mNoteEditor.getText().length());
        }
        for (Integer id : sBgSelectorSelectionMap.keySet())
            findViewById(sBgSelectorSelectionMap.get(id)).setVisibility(View.GONE);
        mHeadViewPanel.setBackgroundResource(note.getTitleBgResId());
        mNoteEditorPanel.setBackgroundResource(note.getBgColorResId());
        mNoteHeaderHolder.tvModified.setText(DateUtils.formatDateTime(this,
                note.getModifiedDate(), DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_SHOW_YEAR));
        showAlertHeader(note.getAlertDate());
    }

    private void showAlertHeader(long alertDate) {
        if (alertDate > 0) {
            long time = System.currentTimeMillis();
            if (time > alertDate) {
                mNoteHeaderHolder.tvAlertDate.setText(R.string.note_alert_expired);
            } else {
                mNoteHeaderHolder.tvAlertDate.setText(DateUtils.getRelativeTimeSpanString(
                        alertDate, time, DateUtils.MINUTE_IN_MILLIS));
            }
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.VISIBLE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.VISIBLE);
        } else {
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.GONE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.GONE);
        }
    }

    // ── Click Handling ──

    @Override
    public void onClick(View v) {
        int id = v.getId();
        WorkingNote note = viewModel.getWorkingNote();
        if (note == null) return;
        if (sBgSelectorBtnsMap.containsKey(id)) {
            findViewById(sBgSelectorSelectionMap.get(note.getBgColorId())).setVisibility(View.GONE);
            viewModel.setBgColor(sBgSelectorBtnsMap.get(id));
            mNoteBgColorSelector.setVisibility(View.GONE);
        } else if (sFontSizeBtnsMap.containsKey(id)) {
            int cur = viewModel.getFontSizeIdValue();
            findViewById(sFontSelectorSelectionMap.get(cur)).setVisibility(View.GONE);
            int newSize = sFontSizeBtnsMap.get(id);
            viewModel.setFontSize(newSize);
            findViewById(sFontSelectorSelectionMap.get(newSize)).setVisibility(View.VISIBLE);
            if (note.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                getWorkingTextFromUi();
                switchToListMode(note.getContent());
            } else {
                mNoteEditor.setTextAppearance(this,
                        TextAppearanceResources.getTexAppearanceResource(newSize));
            }
            mFontSizeSelector.setVisibility(View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        if (clearSettingState()) return;
        saveNote();
        super.onBackPressed();
    }

    private boolean clearSettingState() {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        }
        if (mFontSizeSelector.getVisibility() == View.VISIBLE) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE && !inRangeOfView(mNoteBgColorSelector, ev)) {
            mNoteBgColorSelector.setVisibility(View.GONE); return true;
        }
        if (mFontSizeSelector.getVisibility() == View.VISIBLE && !inRangeOfView(mFontSizeSelector, ev)) {
            mFontSizeSelector.setVisibility(View.GONE); return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean inRangeOfView(View view, MotionEvent ev) {
        int[] loc = new int[2]; view.getLocationOnScreen(loc);
        return ev.getX() >= loc[0] && ev.getX() <= loc[0] + view.getWidth()
                && ev.getY() >= loc[1] && ev.getY() <= loc[1] + view.getHeight();
    }

    // ── Menu ──

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        WorkingNote note = viewModel.getWorkingNote();
        if (note == null) return super.onOptionsItemSelected(item);
        if (id == R.id.menu_bg_color) {
            mNoteBgColorSelector.setVisibility(View.VISIBLE);
            findViewById(sBgSelectorSelectionMap.get(note.getBgColorId())).setVisibility(View.VISIBLE);
        } else if (id == R.id.menu_new_note) {
            createNewNote();
        } else if (id == R.id.menu_delete) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.alert_title_delete))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(getString(R.string.alert_message_delete_note))
                    .setPositiveButton(android.R.string.ok, (d, w) -> viewModel.deleteNote())
                    .setNegativeButton(android.R.string.cancel, null).show();
        } else if (id == R.id.menu_font_size) {
            mFontSizeSelector.setVisibility(View.VISIBLE);
            findViewById(sFontSelectorSelectionMap.get(viewModel.getFontSizeIdValue()))
                    .setVisibility(View.VISIBLE);
        } else if (id == R.id.menu_list_mode) {
            viewModel.toggleCheckListMode();
        } else if (id == R.id.menu_share) {
            getWorkingTextFromUi();
            sendTo(note.getContent());
        } else if (id == R.id.menu_send_to_desktop) {
            sendToDesktop();
        } else if (id == R.id.menu_alert) {
            setReminder();
        } else if (id == R.id.menu_delete_remind) {
            viewModel.setAlertDate(0, false);
        }
        return true;
    }

    private void setReminder() {
        DateTimePickerDialog d = new DateTimePickerDialog(this, System.currentTimeMillis());
        d.setOnDateTimeSetListener((dialog, date) -> viewModel.setAlertDate(date, true));
        d.show();
    }

    private void sendTo(String info) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.putExtra(Intent.EXTRA_TEXT, info);
        i.setType("text/plain");
        startActivity(i);
    }

    private void createNewNote() {
        saveNote();
        finish();
        Intent i = new Intent(this, NoteEditActivity.class);
        i.setAction(Intent.ACTION_INSERT_OR_EDIT);
        i.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, viewModel.getFolderId());
        startActivity(i);
    }

    // ── Save ──

    @Override
    protected void onPause() {
        super.onPause();
        if (saveNote()) {
            WorkingNote n = viewModel.getWorkingNote();
            if (n != null) Log.d(TAG, "Note data was saved with length:" + n.getContent().length());
        }
        clearSettingState();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        WorkingNote n = viewModel.getWorkingNote();
        if (n != null && !n.existInDatabase()) saveNote();
        if (n != null) {
            outState.putLong(Intent.EXTRA_UID, n.getNoteId());
        }
    }

    private boolean saveNote() {
        getWorkingTextFromUi();
        boolean saved = viewModel.saveNote();
        if (saved) setResult(RESULT_OK);
        return saved;
    }

    // ── Send to Desktop ──

    private void sendToDesktop() {
        WorkingNote n = viewModel.getWorkingNote();
        if (n == null) return;
        if (!n.existInDatabase()) saveNote();
        if (n.getNoteId() <= 0) {
            showToast(R.string.error_note_empty_for_send_to_desktop);
            return;
        }
        Intent shortcut = new Intent(this, NoteEditActivity.class);
        shortcut.setAction(Intent.ACTION_VIEW);
        shortcut.putExtra(Intent.EXTRA_UID, n.getNoteId());
        Intent s = new Intent();
        s.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcut);
        s.putExtra(Intent.EXTRA_SHORTCUT_NAME, makeShortcutIconTitle(n.getContent()));
        s.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app));
        s.putExtra("duplicate", true);
        s.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
        showToast(R.string.info_note_enter_desktop);
        sendBroadcast(s);
    }

    private String makeShortcutIconTitle(String content) {
        content = content.replace(TAG_CHECKED, "").replace(TAG_UNCHECKED, "");
        return content.length() > SHORTCUT_ICON_TITLE_MAX_LEN
                ? content.substring(0, SHORTCUT_ICON_TITLE_MAX_LEN) : content;
    }

    // ── Widget ──

    private void updateWidget() {
        WorkingNote n = viewModel.getWorkingNote();
        if (n == null) return;
        Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (n.getWidgetType() == Notes.TYPE_WIDGET_2X) i.setClass(this, NoteWidgetProvider_2x.class);
        else if (n.getWidgetType() == Notes.TYPE_WIDGET_4X) i.setClass(this, NoteWidgetProvider_4x.class);
        else return;
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{n.getWidgetId()});
        sendBroadcast(i);
        setResult(RESULT_OK, i);
    }

    // ── Checklist UI ──

    private void switchToListMode(String text) {
        mEditTextList.removeAllViews();
        String[] items = (text != null) ? text.split("\n") : new String[0];
        int idx = 0;
        for (String item : items) {
            if (!TextUtils.isEmpty(item)) mEditTextList.addView(getListItem(item, idx++));
        }
        mEditTextList.addView(getListItem("", idx));
        mEditTextList.getChildAt(idx).findViewById(R.id.et_edit_text).requestFocus();
        mNoteEditor.setVisibility(View.GONE);
        mEditTextList.setVisibility(View.VISIBLE);
    }

    private View getListItem(String item, int index) {
        View v = LayoutInflater.from(this).inflate(R.layout.note_edit_list_item, null);
        NoteEditText edit = v.findViewById(R.id.et_edit_text);
        edit.setTextAppearance(this,
                TextAppearanceResources.getTexAppearanceResource(viewModel.getFontSizeIdValue()));
        CheckBox cb = v.findViewById(R.id.cb_edit_item);
        cb.setOnCheckedChangeListener((b, checked) -> {
            edit.setPaintFlags(checked ? edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                    : Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
        });
        if (item.startsWith(TAG_CHECKED)) {
            cb.setChecked(true);
            edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            item = item.substring(TAG_CHECKED.length()).trim();
        } else if (item.startsWith(TAG_UNCHECKED)) {
            edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            item = item.substring(TAG_UNCHECKED.length()).trim();
        }
        edit.setOnTextViewChangeListener(this);
        edit.setIndex(index);
        edit.setText(getHighlightQueryResult(item, mUserQuery));
        return v;
    }

    private void getWorkingTextFromUi() {
        WorkingNote n = viewModel.getWorkingNote();
        if (n == null) return;
        if (n.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                View child = mEditTextList.getChildAt(i);
                NoteEditText edit = child.findViewById(R.id.et_edit_text);
                if (!TextUtils.isEmpty(edit.getText()))
                    sb.append(((CheckBox) child.findViewById(R.id.cb_edit_item)).isChecked()
                            ? TAG_CHECKED : TAG_UNCHECKED).append(" ").append(edit.getText()).append("\n");
            }
            viewModel.setWorkingText(sb.toString());
        } else {
            viewModel.setWorkingText(mNoteEditor.getText().toString());
        }
    }

    @Override
    public void onEditTextDelete(int index, String text) {
        int count = mEditTextList.getChildCount();
        if (count == 1) return;
        for (int i = index + 1; i < count; i++)
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text)).setIndex(i - 1);
        mEditTextList.removeViewAt(index);
        NoteEditText edit = (NoteEditText) mEditTextList.getChildAt(index > 0 ? index - 1 : 0)
                .findViewById(R.id.et_edit_text);
        edit.append(text);
        edit.requestFocus();
        edit.setSelection(edit.length());
    }

    @Override
    public void onEditTextEnter(int index, String text) {
        if (index > mEditTextList.getChildCount()) return;
        View v = getListItem(text, index);
        mEditTextList.addView(v, index);
        NoteEditText edit = v.findViewById(R.id.et_edit_text);
        edit.requestFocus();
        edit.setSelection(0);
        for (int i = index + 1; i < mEditTextList.getChildCount(); i++)
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text)).setIndex(i);
    }

    @Override
    public void onTextChange(int index, boolean hasText) {
        if (index < mEditTextList.getChildCount())
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item)
                    .setVisibility(hasText ? View.VISIBLE : View.GONE);
    }

    // ── Search Highlight ──

    private Spannable getHighlightQueryResult(String fullText, String userQuery) {
        SpannableString sp = new SpannableString(fullText == null ? "" : fullText);
        if (!TextUtils.isEmpty(userQuery)) {
            mPattern = Pattern.compile(userQuery);
            Matcher m = mPattern.matcher(fullText);
            int s = 0;
            while (m.find(s)) {
                sp.setSpan(new BackgroundColorSpan(getResources().getColor(R.color.user_query_highlight)),
                        m.start(), m.end(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                s = m.end();
            }
        }
        return sp;
    }

    // ── Overflow Menu ──

    private void showOverflowMenu() {
        WorkingNote n = viewModel.getWorkingNote();
        if (n == null) return;
        PopupMenu popup = new PopupMenu(this, mMenuMore);
        Menu m = popup.getMenu();
        getMenuInflater().inflate(
                n.getFolderId() == Notes.ID_CALL_RECORD_FOLDER ? R.menu.call_note_edit : R.menu.note_edit, m);
        m.add(Menu.NONE, R.id.menu_bg_color, 0, "背景颜色");
        m.findItem(R.id.menu_list_mode).setTitle(
                n.getCheckListMode() == TextNote.MODE_CHECK_LIST
                        ? R.string.menu_normal_mode : R.string.menu_list_mode);
        m.findItem(R.id.menu_alert).setVisible(!n.hasClockAlert());
        m.findItem(R.id.menu_delete_remind).setVisible(n.hasClockAlert());
        popup.setOnMenuItemClickListener(item -> { onOptionsItemSelected(item); return true; });
        popup.show();
    }

    private void showToast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }
}
