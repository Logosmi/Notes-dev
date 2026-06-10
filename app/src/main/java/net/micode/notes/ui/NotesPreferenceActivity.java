package net.micode.notes.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;

import net.micode.notes.R;
import net.micode.notes.account.LocalAccountManager;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.model.SettingsViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class NotesPreferenceActivity extends AppCompatActivity {
    public static final String PREFERENCE_NAME = "notes_preferences";
    public static final String PREFERENCE_SYNC_ACCOUNT_NAME = "pref_key_account_name";
    public static final String PREFERENCE_LAST_SYNC_TIME = "pref_last_sync_time";
    public static final String PREFERENCE_SET_BG_COLOR_KEY = "pref_key_bg_random_appear";

    private static final String AUTHORITIES_FILTER_KEY = "authorities";
    private final static int REQUEST_PICK_BACKGROUND = 104;

    private SettingsViewModel viewModel;
    private Button syncButton;
    private TextView syncStatusView;
    private GTaskReceiver mReceiver;
    private Account[] mOriAccounts;
    private boolean mHasAddedAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        LinearLayout rootContainer = new LinearLayout(this);
        rootContainer.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.preferences_title);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        rootContainer.addView(toolbar);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        View header = LayoutInflater.from(this).inflate(R.layout.settings_header, null);
        syncButton = header.findViewById(R.id.preference_sync_button);
        syncStatusView = header.findViewById(R.id.prefenerece_sync_status_textview);
        container.addView(header);

        viewModel.getSyncButtonText().observe(this, syncButton::setText);
        viewModel.getSyncButtonEnabled().observe(this, syncButton::setEnabled);
        viewModel.getSyncStatusText().observe(this, syncStatusView::setText);
        viewModel.getSyncStatusVisible().observe(this, visible ->
                syncStatusView.setVisibility(visible != null && visible ? View.VISIBLE : View.GONE));

        syncButton.setOnClickListener(v -> {
            if (GTaskSyncService.isSyncing()) {
                GTaskSyncService.cancelSync(NotesPreferenceActivity.this);
            } else {
                GTaskSyncService.startSync(NotesPreferenceActivity.this);
            }
        });

        addCategory("账户管理", container);
        addPreferenceItem("修改密码", "修改当前账户的登录密码", v -> showChangePasswordDialog(), container);
        addPreferenceItem("修改安全问题", "重置密保问题及答案", v -> showChangeSecurityDialog(), container);
        addPreferenceItem("注销", "退出当前账户", v -> showLogoutConfirmDialog(), container);
        addPreferenceItem("删除账户", "彻底删除当前账户及所有数据", v -> showDeleteAccountConfirmDialog(), container);


        addCategory("显示", container);
        addPreferenceItem("设置背景", "选择一张图片作为主屏背景", v -> pickBackground(), container);
        addPreferenceItem("清除背景", "恢复纯白背景", v -> clearBackground(), container);

        addCategory("同步", container);
        addPreferenceItem("选择同步账户", "设置 Google 账户", v -> {
            if (!GTaskSyncService.isSyncing()) {
                String currentAccount = getSyncAccountName(this);
                if (TextUtils.isEmpty(currentAccount)) {
                    showSelectAccountAlertDialog();
                } else {
                    showChangeAccountConfirmAlertDialog();
                }
            } else {
                Toast.makeText(this, R.string.preferences_toast_cannot_change_account, Toast.LENGTH_SHORT).show();
            }
        }, container);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(container);
        rootContainer.addView(scrollView);

        setContentView(rootContainer);

        mReceiver = new GTaskReceiver();
        IntentFilter filter = new IntentFilter(GTaskSyncService.GTASK_SERVICE_BROADCAST_NAME);
        registerReceiver(mReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.refreshSyncUI();
        if (mHasAddedAccount) {
            Account[] accounts = getGoogleAccounts();
            if (mOriAccounts != null && accounts.length > mOriAccounts.length) {
                for (Account accountNew : accounts) {
                    boolean found = false;
                    for (Account accountOld : mOriAccounts) {
                        if (TextUtils.equals(accountOld.name, accountNew.name)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        setSyncAccount(accountNew.name);
                        break;
                    }
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReceiver != null) unregisterReceiver(mReceiver);
    }

    private void addCategory(String title, LinearLayout parent) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextAppearance(this, android.R.style.TextAppearance_Medium);
        tv.setPadding(48, 32, 48, 16);
        parent.addView(tv);
    }

    private void addPreferenceItem(String title, String summary, View.OnClickListener listener, LinearLayout parent) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(48, 16, 48, 16);
        item.setClickable(true);
        item.setFocusable(true);

        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        item.setBackgroundResource(outValue.resourceId);

        item.setOnClickListener(listener);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextAppearance(this, android.R.style.TextAppearance_Medium);
        item.addView(titleView);

        TextView summaryView = new TextView(this);
        summaryView.setText(summary);
        summaryView.setTextAppearance(this, android.R.style.TextAppearance_Small);
        item.addView(summaryView);

        parent.addView(item);
    }

    private void showSelectAccountAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_select_account_title));
        TextView subtitleTextView = titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_select_account_tips));

        dialogBuilder.setCustomTitle(titleView);
        dialogBuilder.setPositiveButton(null, null);

        Account[] accounts = getGoogleAccounts();
        String defAccount = getSyncAccountName(this);
        mOriAccounts = accounts;
        mHasAddedAccount = false;

        if (accounts.length > 0) {
            CharSequence[] items = new CharSequence[accounts.length];
            final CharSequence[] itemMapping = items;
            int checkedItem = -1;
            int index = 0;
            for (Account account : accounts) {
                if (TextUtils.equals(account.name, defAccount)) checkedItem = index;
                items[index++] = account.name;
            }
            dialogBuilder.setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                setSyncAccount(itemMapping[which].toString());
                dialog.dismiss();
                viewModel.refreshSyncUI();
            });
        }

        View addAccountView = LayoutInflater.from(this).inflate(R.layout.add_account_text, null);
        dialogBuilder.setView(addAccountView);
        final AlertDialog dialog = dialogBuilder.show();
        addAccountView.setOnClickListener(v -> {
            mHasAddedAccount = true;
            Intent intent = new Intent("android.settings.ADD_ACCOUNT_SETTINGS");
            intent.putExtra(AUTHORITIES_FILTER_KEY, new String[]{"gmail-ls"});
            startActivityForResult(intent, -1);
            dialog.dismiss();
        });
    }

    private void showChangeAccountConfirmAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_change_account_title, getSyncAccountName(this)));
        TextView subtitleTextView = titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_change_account_warn_msg));
        dialogBuilder.setCustomTitle(titleView);

        CharSequence[] menuItemArray = new CharSequence[]{
                getString(R.string.preferences_menu_change_account),
                getString(R.string.preferences_menu_remove_account),
                getString(R.string.preferences_menu_cancel)
        };
        dialogBuilder.setItems(menuItemArray, (dialog, which) -> {
            if (which == 0) showSelectAccountAlertDialog();
            else if (which == 1) { removeSyncAccount(); viewModel.refreshSyncUI(); }
        });
        dialogBuilder.show();
    }

    private Account[] getGoogleAccounts() {
        return AccountManager.get(this).getAccountsByType("com.google");
    }

    private void setSyncAccount(String account) {
        if (!getSyncAccountName(this).equals(account)) {
            SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
            settings.edit().putString(PREFERENCE_SYNC_ACCOUNT_NAME, account).apply();
            setLastSyncTime(this, 0);
            new Thread(() -> {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.GTASK_ID, "");
                values.put(NoteColumns.SYNC_ID, 0);
                getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
            }).start();
            Toast.makeText(this, getString(R.string.preferences_toast_success_set_accout, account), Toast.LENGTH_SHORT).show();
        }
    }

    private void removeSyncAccount() {
        SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        settings.edit().remove(PREFERENCE_SYNC_ACCOUNT_NAME).remove(PREFERENCE_LAST_SYNC_TIME).apply();
        new Thread(() -> {
            ContentValues values = new ContentValues();
            values.put(NoteColumns.GTASK_ID, "");
            values.put(NoteColumns.SYNC_ID, 0);
            getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
        }).start();
    }

    public static String getSyncAccountName(Context context) {
        return context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
                .getString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
    }

    public static void setLastSyncTime(Context context, long time) {
        context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
                .edit().putLong(PREFERENCE_LAST_SYNC_TIME, time).apply();
    }

    public static long getLastSyncTime(Context context) {
        return context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
                .getLong(PREFERENCE_LAST_SYNC_TIME, 0);
    }

    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);

        final EditText etCurrentPwd = new EditText(this);
        etCurrentPwd.setHint("当前密码");
        layout.addView(etCurrentPwd);
        final EditText etNewPwd = new EditText(this);
        etNewPwd.setHint("新密码");
        layout.addView(etNewPwd);
        final EditText etConfirmPwd = new EditText(this);
        etConfirmPwd.setHint("确认新密码");
        layout.addView(etConfirmPwd);

        builder.setTitle("修改密码")
                .setView(layout)
                .setPositiveButton("确认", (dialog, which) -> {
                    String current = etCurrentPwd.getText().toString().trim();
                    String newPwd = etNewPwd.getText().toString().trim();
                    String confirm = etConfirmPwd.getText().toString().trim();
                    if (current.isEmpty() || newPwd.isEmpty() || confirm.isEmpty()) {
                        Toast.makeText(this, "所有字段不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPwd.equals(confirm)) {
                        Toast.makeText(this, "两次密码不一致", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    LocalAccountManager am = new LocalAccountManager(this);
                    Toast.makeText(this, am.changePassword(current, newPwd) ? "密码修改成功" : "当前密码错误", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showChangeSecurityDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);

        final EditText etCurrentPwd = new EditText(this);
        etCurrentPwd.setHint("当前密码");
        layout.addView(etCurrentPwd);
        final EditText etNewQuestion = new EditText(this);
        etNewQuestion.setHint("新问题");
        layout.addView(etNewQuestion);
        final EditText etNewAnswer = new EditText(this);
        etNewAnswer.setHint("新答案");
        layout.addView(etNewAnswer);

        builder.setTitle("修改安全问题")
                .setView(layout)
                .setPositiveButton("确认", (dialog, which) -> {
                    String current = etCurrentPwd.getText().toString().trim();
                    String question = etNewQuestion.getText().toString().trim();
                    String answer = etNewAnswer.getText().toString().trim();
                    if (current.isEmpty() || question.isEmpty() || answer.isEmpty()) {
                        Toast.makeText(this, "所有字段不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    LocalAccountManager am = new LocalAccountManager(this);
                    Toast.makeText(this, am.changeSecurityQuestion(current, question, answer) ? "安全问题已更新" : "当前密码错误", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showLogoutConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("注销")
                .setMessage("确定要注销当前账户吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    LocalAccountManager am = new LocalAccountManager(this);
                    am.logout();
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteAccountConfirmDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 0);

        final EditText etPwd = new EditText(this);
        etPwd.setHint("输入当前密码");
        etPwd.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etPwd);

        final CheckBox cbDeleteNotes = new CheckBox(this);
        cbDeleteNotes.setText("同时删除所有便签及文件夹");
        cbDeleteNotes.setChecked(false);
        layout.addView(cbDeleteNotes);

        new AlertDialog.Builder(this)
                .setTitle("删除账户")
                .setMessage("此操作不可恢复！")
                .setView(layout)
                .setPositiveButton("确认删除", (dialog, which) -> {
                    String password = etPwd.getText().toString().trim();
                    if (password.isEmpty()) {
                        Toast.makeText(this, "密码不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    LocalAccountManager am = new LocalAccountManager(this);
                    boolean deleteNotes = cbDeleteNotes.isChecked();
                    boolean deleted = am.deleteAccount(password, deleteNotes);
                    if (deleted) {
                        Toast.makeText(this, "账户已删除", Toast.LENGTH_SHORT).show();
                        am.logout();
                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(this, "密码错误，删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }


    private void pickBackground() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_BACKGROUND);
    }

    private void clearBackground() {
        getSharedPreferences("notes_preferences", MODE_PRIVATE)
                .edit().remove("background_uri").apply();
        Toast.makeText(this, "背景已清除", Toast.LENGTH_SHORT).show();
    }

    private String copyImageToInternal(Uri sourceUri) {
        try {
            File bgDir = new File(getFilesDir(), "backgrounds");
            if (!bgDir.exists()) bgDir.mkdirs();
            String fileName = "bg_" + System.currentTimeMillis() + ".jpg";
            File destFile = new File(bgDir, fileName);
            InputStream in = getContentResolver().openInputStream(sourceUri);
            FileOutputStream out = new FileOutputStream(destFile);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > 0) out.write(buffer, 0, len);
            out.close();
            in.close();
            return destFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e("Prefs", "Copy background failed", e);
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_BACKGROUND && resultCode == RESULT_OK) {
            Uri sourceUri = data.getData();
            String savedPath = copyImageToInternal(sourceUri);
            if (savedPath != null) {
                getSharedPreferences("notes_preferences", MODE_PRIVATE)
                        .edit().putString("background_uri", savedPath).apply();
                Toast.makeText(this, "背景已设置", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class GTaskReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            viewModel.refreshSyncUI();
        }
    }
}