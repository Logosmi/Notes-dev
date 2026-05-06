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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import android.text.InputType;
import android.preference.PreferenceScreen;


import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.account.LocalAccountManager;


public class NotesPreferenceActivity extends PreferenceActivity {
    public static final String PREFERENCE_NAME = "notes_preferences";

    public static final String PREFERENCE_SYNC_ACCOUNT_NAME = "pref_key_account_name";

    public static final String PREFERENCE_LAST_SYNC_TIME = "pref_last_sync_time";

    public static final String PREFERENCE_SET_BG_COLOR_KEY = "pref_key_bg_random_appear";

    private static final String PREFERENCE_SYNC_ACCOUNT_KEY = "pref_sync_account_key";

    private static final String AUTHORITIES_FILTER_KEY = "authorities";

    private PreferenceCategory mAccountCategory;

    private GTaskReceiver mReceiver;

    private Account[] mOriAccounts;

    private boolean mHasAddedAccount;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        /* using the app icon for navigation */
        getActionBar().setDisplayHomeAsUpEnabled(true);

        addPreferencesFromResource(R.xml.preferences);
        mAccountCategory = (PreferenceCategory) findPreference(PREFERENCE_SYNC_ACCOUNT_KEY);
        mReceiver = new GTaskReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(GTaskSyncService.GTASK_SERVICE_BROADCAST_NAME);
        registerReceiver(mReceiver, filter);

        mOriAccounts = null;
        View header = LayoutInflater.from(this).inflate(R.layout.settings_header, null);
        getListView().addHeaderView(header, null, true);

        PreferenceScreen preferenceScreen = getPreferenceScreen();

        PreferenceCategory accountCategory = new PreferenceCategory(this);
        accountCategory.setTitle("账户管理");
        preferenceScreen.addPreference(accountCategory);

        Preference changePasswordPref = new Preference(this);
        changePasswordPref.setTitle("修改密码");
        changePasswordPref.setSummary("修改当前账户的登录密码");
        changePasswordPref.setOnPreferenceClickListener(preference -> {
            showChangePasswordDialog();
            return true;
        });
        accountCategory.addPreference(changePasswordPref);

        Preference changeSecurityPref = new Preference(this);
        changeSecurityPref.setTitle("修改安全问题");
        changeSecurityPref.setSummary("重置密保问题及答案");
        changeSecurityPref.setOnPreferenceClickListener(preference -> {
            showChangeSecurityDialog();
            return true;
        });
        accountCategory.addPreference(changeSecurityPref);

        Preference logoutPref = new Preference(this);
        logoutPref.setTitle("注销");
        logoutPref.setSummary("退出当前账户");
        logoutPref.setOnPreferenceClickListener(preference -> {
            showLogoutConfirmDialog();
            return true;
        });
        accountCategory.addPreference(logoutPref);

        Preference deleteAccountPref = new Preference(this);
        deleteAccountPref.setTitle("删除账户");
        deleteAccountPref.setSummary("彻底删除当前账户及所有数据");
        deleteAccountPref.setOnPreferenceClickListener(preference -> {
            showDeleteAccountConfirmDialog();
            return true;
        });
        accountCategory.addPreference(deleteAccountPref);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // need to set sync account automatically if user has added a new
        // account
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

        refreshUI();
    }

    @Override
    protected void onDestroy() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        super.onDestroy();
    }

    private void loadAccountPreference() {
        mAccountCategory.removeAll();

        Preference accountPref = new Preference(this);
        final String defaultAccount = getSyncAccountName(this);
        accountPref.setTitle(getString(R.string.preferences_account_title));
        accountPref.setSummary(getString(R.string.preferences_account_summary));
        accountPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                if (!GTaskSyncService.isSyncing()) {
                    if (TextUtils.isEmpty(defaultAccount)) {
                        // the first time to set account
                        showSelectAccountAlertDialog();
                    } else {
                        // if the account has already been set, we need to promp
                        // user about the risk
                        showChangeAccountConfirmAlertDialog();
                    }
                } else {
                    Toast.makeText(NotesPreferenceActivity.this,
                            R.string.preferences_toast_cannot_change_account, Toast.LENGTH_SHORT)
                            .show();
                }
                return true;
            }
        });

        mAccountCategory.addPreference(accountPref);
    }

    private void loadSyncButton() {
        Button syncButton = (Button) findViewById(R.id.preference_sync_button);
        TextView lastSyncTimeView = (TextView) findViewById(R.id.prefenerece_sync_status_textview);

        // set button state
        if (GTaskSyncService.isSyncing()) {
            syncButton.setText(getString(R.string.preferences_button_sync_cancel));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.cancelSync(NotesPreferenceActivity.this);
                }
            });
        } else {
            syncButton.setText(getString(R.string.preferences_button_sync_immediately));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.startSync(NotesPreferenceActivity.this);
                }
            });
        }
        syncButton.setEnabled(!TextUtils.isEmpty(getSyncAccountName(this)));

        // set last sync time
        if (GTaskSyncService.isSyncing()) {
            lastSyncTimeView.setText(GTaskSyncService.getProgressString());
            lastSyncTimeView.setVisibility(View.VISIBLE);
        } else {
            long lastSyncTime = getLastSyncTime(this);
            if (lastSyncTime != 0) {
                lastSyncTimeView.setText(getString(R.string.preferences_last_sync_time,
                        DateFormat.format(getString(R.string.preferences_last_sync_time_format),
                                lastSyncTime)));
                lastSyncTimeView.setVisibility(View.VISIBLE);
            } else {
                lastSyncTimeView.setVisibility(View.GONE);
            }
        }
    }

    private void refreshUI() {
        loadAccountPreference();
        loadSyncButton();
    }

    private void showSelectAccountAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_select_account_title));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
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
                if (TextUtils.equals(account.name, defAccount)) {
                    checkedItem = index;
                }
                items[index++] = account.name;
            }
            dialogBuilder.setSingleChoiceItems(items, checkedItem,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            setSyncAccount(itemMapping[which].toString());
                            dialog.dismiss();
                            refreshUI();
                        }
                    });
        }

        View addAccountView = LayoutInflater.from(this).inflate(R.layout.add_account_text, null);
        dialogBuilder.setView(addAccountView);

        final AlertDialog dialog = dialogBuilder.show();
        addAccountView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mHasAddedAccount = true;
                Intent intent = new Intent("android.settings.ADD_ACCOUNT_SETTINGS");
                intent.putExtra(AUTHORITIES_FILTER_KEY, new String[] {
                    "gmail-ls"
                });
                startActivityForResult(intent, -1);
                dialog.dismiss();
            }
        });
    }

    private void showChangeAccountConfirmAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        View titleView = LayoutInflater.from(this).inflate(R.layout.account_dialog_title, null);
        TextView titleTextView = (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(R.string.preferences_dialog_change_account_title,
                getSyncAccountName(this)));
        TextView subtitleTextView = (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(R.string.preferences_dialog_change_account_warn_msg));
        dialogBuilder.setCustomTitle(titleView);

        CharSequence[] menuItemArray = new CharSequence[] {
                getString(R.string.preferences_menu_change_account),
                getString(R.string.preferences_menu_remove_account),
                getString(R.string.preferences_menu_cancel)
        };
        dialogBuilder.setItems(menuItemArray, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showSelectAccountAlertDialog();
                } else if (which == 1) {
                    removeSyncAccount();
                    refreshUI();
                }
            }
        });
        dialogBuilder.show();
    }

    private Account[] getGoogleAccounts() {
        AccountManager accountManager = AccountManager.get(this);
        return accountManager.getAccountsByType("com.google");
    }

    private void setSyncAccount(String account) {
        if (!getSyncAccountName(this).equals(account)) {
            SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            if (account != null) {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, account);
            } else {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
            }
            editor.commit();

            // clean up last sync time
            setLastSyncTime(this, 0);

            // clean up local gtask related info
            new Thread(new Runnable() {
                public void run() {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.GTASK_ID, "");
                    values.put(NoteColumns.SYNC_ID, 0);
                    getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
                }
            }).start();

            Toast.makeText(NotesPreferenceActivity.this,
                    getString(R.string.preferences_toast_success_set_accout, account),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void removeSyncAccount() {
        SharedPreferences settings = getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        if (settings.contains(PREFERENCE_SYNC_ACCOUNT_NAME)) {
            editor.remove(PREFERENCE_SYNC_ACCOUNT_NAME);
        }
        if (settings.contains(PREFERENCE_LAST_SYNC_TIME)) {
            editor.remove(PREFERENCE_LAST_SYNC_TIME);
        }
        editor.commit();

        // clean up local gtask related info
        new Thread(new Runnable() {
            public void run() {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.GTASK_ID, "");
                values.put(NoteColumns.SYNC_ID, 0);
                getContentResolver().update(Notes.CONTENT_NOTE_URI, values, null, null);
            }
        }).start();
    }

    public static String getSyncAccountName(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
    }

    public static void setLastSyncTime(Context context, long time) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong(PREFERENCE_LAST_SYNC_TIME, time);
        editor.commit();
    }

    public static long getLastSyncTime(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFERENCE_NAME,
                Context.MODE_PRIVATE);
        return settings.getLong(PREFERENCE_LAST_SYNC_TIME, 0);
    }

    private class GTaskReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUI();
            if (intent.getBooleanExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_IS_SYNCING, false)) {
                TextView syncStatus = (TextView) findViewById(R.id.prefenerece_sync_status_textview);
                syncStatus.setText(intent
                        .getStringExtra(GTaskSyncService.GTASK_SERVICE_BROADCAST_PROGRESS_MSG));
            }

        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this, NotesListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            default:
                return false;
        }
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
                String confirmPwd = etConfirmPwd.getText().toString().trim();
                if (current.isEmpty() || newPwd.isEmpty() || confirmPwd.isEmpty()) {
                    Toast.makeText(this, "所有字段不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!newPwd.equals(confirmPwd)) {
                    Toast.makeText(this, "两次密码不一致", Toast.LENGTH_SHORT).show();
                    return;
                }
                LocalAccountManager am = new LocalAccountManager(this);
                boolean success = am.changePassword(current, newPwd);
                if (success) {
                    Toast.makeText(this, "密码修改成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "当前密码错误", Toast.LENGTH_SHORT).show();
                }
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
                boolean success = am.changeSecurityQuestion(current, question, answer);
                if (success) {
                    Toast.makeText(this, "安全问题已更新", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "当前密码错误", Toast.LENGTH_SHORT).show();
                }
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
        etPwd.setId(android.R.id.edit);
        etPwd.setHint("输入当前密码");
        etPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
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

    private LinearLayout createPasswordInput() {
        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(48, 16, 48, 0);
        EditText etPwd = new EditText(this);
        etPwd.setId(android.R.id.edit);
        etPwd.setHint("输入密码");
        etPwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etPwd);
        return layout;
    }
}
