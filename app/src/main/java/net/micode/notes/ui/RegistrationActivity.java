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

public class RegistrationActivity extends Activity {
    private EditText etUsername, etPassword, etConfirm, etQuestion, etAnswer;
    private Button btnRegister;
    private TextView tvBack;
    private LocalAccountManager accountManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        accountManager = new LocalAccountManager(this);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etConfirm = findViewById(R.id.et_confirm_password);
        etQuestion = findViewById(R.id.et_question);
        etAnswer = findViewById(R.id.et_answer);
        btnRegister = findViewById(R.id.btn_register);
        tvBack = findViewById(R.id.tv_back_to_login);

        btnRegister.setOnClickListener(v -> register());
        tvBack.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void register() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirm = etConfirm.getText().toString().trim();
        String question = etQuestion.getText().toString().trim();
        String answer = etAnswer.getText().toString().trim();

        if (accountManager.isAnyUserRegistered()) {
            Toast.makeText(this, "已存在用户，无法重复注册", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        int result = accountManager.register(username, password, confirm, question, answer);

        switch (result) {
            case LocalAccountManager.REGISTER_SUCCESS:
                Toast.makeText(this, "注册成功，请登录", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                break;
            case LocalAccountManager.REGISTER_USERNAME_EXISTS:
                Toast.makeText(this, "用户名已存在", Toast.LENGTH_SHORT).show();
                break;
            case LocalAccountManager.REGISTER_PASSWORD_TOO_WEAK:
                Toast.makeText(this, "密码至少6位", Toast.LENGTH_SHORT).show();
                break;
            case LocalAccountManager.REGISTER_CONFIRM_MISMATCH:
                Toast.makeText(this, "两次密码不一致", Toast.LENGTH_SHORT).show();
                break;
            default:
                Toast.makeText(this, "注册失败", Toast.LENGTH_SHORT).show();
        }
    }
}