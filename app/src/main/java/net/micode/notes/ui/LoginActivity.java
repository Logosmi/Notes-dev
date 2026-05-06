package net.micode.notes.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.account.LocalAccountManager;

public class LoginActivity extends Activity {
    private EditText etUsername, etPassword;
    private Button btnLogin;
    private TextView tvRegister, tvForgot;
    private LocalAccountManager accountManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        accountManager = new LocalAccountManager(this);

        // 如果已经登录，直接进入主界面
        if (accountManager.isLoggedIn()) {
            goToMain();
            return;
        }

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        tvRegister = findViewById(R.id.tv_register);
        tvForgot = findViewById(R.id.tv_forgot_password);

        btnLogin.setOnClickListener(v -> login());
        tvRegister.setOnClickListener(v -> {
            if (accountManager.isAnyUserRegistered()) {
                Toast.makeText(LoginActivity.this, "已存在用户，请直接登录", Toast.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent(this, RegistrationActivity.class));
            }
        });
        tvForgot.setOnClickListener(v -> startActivity(new Intent(this, ForgotPasswordActivity.class)));

        if (accountManager.isAnyUserRegistered()) {
            tvRegister.setVisibility(View.INVISIBLE);
        }
    }

    private void login() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "用户名或密码不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        int result = accountManager.login(username, password);
        if (result == LocalAccountManager.LOGIN_SUCCESS) {
            goToMain();
        } else if (result == LocalAccountManager.LOGIN_USER_NOT_FOUND) {
            Toast.makeText(this, "用户不存在", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show();
        }
    }

    private void goToMain() {
        startActivity(new Intent(this, NotesListActivity.class));
        finish();
    }
}