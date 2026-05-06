package net.micode.notes.account;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.NotesProvider;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class LocalAccountManager {
    private static final String TAG = "AccountManager";
    private static final String PREF_SESSION = "account_session";
    private static final String KEY_LOGGED_IN_USER_ID = "logged_in_user_id";
    private static final String KEY_LOGGED_IN_USERNAME = "logged_in_username";

    private Context mContext;
    private SharedPreferences mPrefs;

    public static final int REGISTER_SUCCESS = 0;
    public static final int REGISTER_USERNAME_EXISTS = 1;
    public static final int REGISTER_PASSWORD_TOO_WEAK = 2;
    public static final int REGISTER_CONFIRM_MISMATCH = 3;

    public static final int LOGIN_SUCCESS = 0;
    public static final int LOGIN_USER_NOT_FOUND = 1;
    public static final int LOGIN_WRONG_PASSWORD = 2;

    public static final int RESET_SUCCESS = 0;
    public static final int RESET_ANSWER_INCORRECT = 1;
    public static final int RESET_USER_NOT_FOUND = 2;
    public static final int RESET_TOO_MANY_ATTEMPTS = 3;

    public LocalAccountManager(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = mContext.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE);
    }

    public int register(String username, String password, String confirmPassword,
                        String question, String answer) {
        if (isUsernameExists(username)) {
            return REGISTER_USERNAME_EXISTS;
        }
        if (!validatePassword(password)) {
            return REGISTER_PASSWORD_TOO_WEAK;
        }
        if (!password.equals(confirmPassword)) {
            return REGISTER_CONFIRM_MISMATCH;
        }

        ContentValues values = new ContentValues();
        values.put("username", username);
        values.put("passwordHash", sha256(password));
        values.put("securityQuestion", question);
        values.put("securityAnswerHash", sha256(answer));
        values.put("failCount", 0);
        values.put("createdTime", System.currentTimeMillis());

        Uri uri = Uri.parse("content://micode_notes/user");
        Uri result = mContext.getContentResolver().insert(uri, values);
        if (result != null) {
            return REGISTER_SUCCESS;
        }
        return -1;
    }

    public int login(String username, String password) {
        Cursor c = null;
        try {
            Uri uri = Uri.parse("content://micode_notes/user");
            c = mContext.getContentResolver().query(uri, null,
                    "username=?", new String[]{username}, null);
            if (c == null || !c.moveToFirst()) {
                return LOGIN_USER_NOT_FOUND;
            }
            long userId = c.getLong(c.getColumnIndex("_id"));
            String storedHash = c.getString(c.getColumnIndex("passwordHash"));
            String inputHash = sha256(password);
            if (inputHash.equals(storedHash)) {
                mPrefs.edit()
                        .putLong(KEY_LOGGED_IN_USER_ID, userId)
                        .putString(KEY_LOGGED_IN_USERNAME, username)
                        .apply();
                return LOGIN_SUCCESS;
            } else {
                return LOGIN_WRONG_PASSWORD;
            }
        } finally {
            if (c != null) c.close();
        }
    }

    public String requestSecurityQuestion(String username) {
        Cursor c = null;
        try {
            Uri uri = Uri.parse("content://micode_notes/user");
            c = mContext.getContentResolver().query(uri, null,
                    "username=?", new String[]{username}, null);
            if (c != null && c.moveToFirst()) {
                return c.getString(c.getColumnIndex("securityQuestion"));
            }
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    public boolean verifyAnswer(String username, String answer) {
        Cursor c = null;
        try {
            Uri uri = Uri.parse("content://micode_notes/user");
            c = mContext.getContentResolver().query(uri, null,
                    "username=?", new String[]{username}, null);
            if (c != null && c.moveToFirst()) {
                String storedAnswerHash = c.getString(c.getColumnIndex("securityAnswerHash"));
                int failCount = c.getInt(c.getColumnIndex("failCount"));
                long userId = c.getLong(c.getColumnIndex("_id"));

                if (failCount >= 3) {
                    return false;
                }

                String inputHash = sha256(answer);
                if (inputHash.equals(storedAnswerHash)) {
                    resetFailCount(userId);
                    return true;
                } else {
                    incrementFailCount(userId);
                    return false;
                }
            }
        } finally {
            if (c != null) c.close();
        }
        return false;
    }

    public int resetPassword(String username, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            return RESET_SUCCESS - 1;
        }
        if (!validatePassword(newPassword)) {
            return -1;
        }
        ContentValues values = new ContentValues();
        values.put("passwordHash", sha256(newPassword));
        Uri uri = Uri.parse("content://micode_notes/user");
        int rows = mContext.getContentResolver().update(uri, values,
                "username=?", new String[]{username});
        if (rows > 0) {
            return RESET_SUCCESS;
        }
        return -1;
    }

    public void logout() {
        mPrefs.edit().clear().apply();
    }

    public long getCurrentUserId() {
        return mPrefs.getLong(KEY_LOGGED_IN_USER_ID, -1);
    }

    public String getCurrentUsername() {
        return mPrefs.getString(KEY_LOGGED_IN_USERNAME, null);
    }

    public boolean isLoggedIn() {
        return getCurrentUserId() != -1;
    }

    private boolean isUsernameExists(String username) {
        Cursor c = null;
        try {
            Uri uri = Uri.parse("content://micode_notes/user");
            c = mContext.getContentResolver().query(uri, null,
                    "username=?", new String[]{username}, null);
            return c != null && c.getCount() > 0;
        } finally {
            if (c != null) c.close();
        }
    }

    private boolean validatePassword(String password) {
        return password != null && password.length() >= 6;
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }

    private void incrementFailCount(long userId) {
        Cursor c = null;
        try {
            Uri uri = Uri.parse("content://micode_notes/user/" + userId);
            c = mContext.getContentResolver().query(uri, new String[]{"failCount"}, null, null, null);
            if (c != null && c.moveToFirst()) {
                int current = c.getInt(0);
                ContentValues values = new ContentValues();
                values.put("failCount", current + 1);
                mContext.getContentResolver().update(uri, values, null, null);
            }
        } finally {
            if (c != null) c.close();
        }
    }

    private void resetFailCount(long userId) {
        Uri uri = Uri.parse("content://micode_notes/user/" + userId);
        ContentValues values = new ContentValues();
        values.put("failCount", 0);
        mContext.getContentResolver().update(uri, values, null, null);
    }

    public boolean isAnyUserRegistered() {
        Cursor c = null;
        try {
            Uri uri = Uri.parse("content://micode_notes/user");
            c = mContext.getContentResolver().query(uri, null, null, null, null);
            return c != null && c.getCount() > 0;
        } finally {
            if (c != null) c.close();
        }
    }

    public boolean changeSecurityQuestion(String currentPassword, String newQuestion, String newAnswer) {
        if (!isLoggedIn()) return false;
        long userId = getCurrentUserId();
        Cursor c = null;
        try {
            Uri uri = Uri.parse("content://micode_notes/user/" + userId);
            c = mContext.getContentResolver().query(uri, new String[]{"passwordHash"}, null, null, null);
            if (c != null && c.moveToFirst()) {
                String storedHash = c.getString(0);
                if (!sha256(currentPassword).equals(storedHash)) {
                    return false;
                }
            } else {
                return false;
            }
        } finally {
            if (c != null) c.close();
        }

        ContentValues values = new ContentValues();
        values.put("securityQuestion", newQuestion);
        values.put("securityAnswerHash", sha256(newAnswer));
        Uri uri = Uri.parse("content://micode_notes/user/" + userId);
        int rows = mContext.getContentResolver().update(uri, values, null, null);
        return rows > 0;
    }

    public boolean changePassword(String currentPassword, String newPassword) {
        if (!isLoggedIn()) return false;
        long userId = getCurrentUserId();
        Cursor c = null;
        try {
            Uri uri = Uri.parse("content://micode_notes/user/" + userId);
            c = mContext.getContentResolver().query(uri, new String[]{"passwordHash"}, null, null, null);
            if (c != null && c.moveToFirst()) {
                String storedHash = c.getString(0);
                if (!sha256(currentPassword).equals(storedHash)) {
                    return false;
                }
            } else {
                return false;
            }
        } finally {
            if (c != null) c.close();
        }
        ContentValues values = new ContentValues();
        values.put("passwordHash", sha256(newPassword));
        Uri uri = Uri.parse("content://micode_notes/user/" + userId);
        int rows = mContext.getContentResolver().update(uri, values, null, null);
        return rows > 0;
    }

    public boolean deleteAccount(String password, boolean deleteNotes) {
        if (!isLoggedIn()) return false;
        long userId = getCurrentUserId();
        Cursor c = null;
        try {
            Uri uri = Uri.parse("content://micode_notes/user/" + userId);
            c = mContext.getContentResolver().query(uri, new String[]{"passwordHash"}, null, null, null);
            if (c != null && c.moveToFirst()) {
                String storedHash = c.getString(0);
                if (!sha256(password).equals(storedHash)) {
                    return false;
                }
            } else {
                return false;
            }
        } finally {
            if (c != null) c.close();
        }

        if (deleteNotes) {
            Uri dataUri = Uri.parse("content://micode_notes/data");
            mContext.getContentResolver().delete(dataUri, null, null);
            Uri noteUri = Uri.parse("content://micode_notes/note");
            mContext.getContentResolver().delete(noteUri, "_id > 0", null);
        }

        Uri userUri = Uri.parse("content://micode_notes/user/" + userId);
        int rows = mContext.getContentResolver().delete(userUri, null, null);
        return rows > 0;
    }

    public boolean deleteAccount(String password) {
        return deleteAccount(password, false);
    }
}