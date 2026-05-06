package net.micode.notes.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import net.micode.notes.R;
import net.micode.notes.account.LocalAccountManager;

public class ForgotPasswordActivity extends Activity {
    private LocalAccountManager accountManager;
    private String currentUsername = null;

    // Step 1
    private LinearLayout step1;
    private EditText etUsername;
    private Button btnGetQuestion;

    // Step 2
    private LinearLayout step2;
    private TextView tvQuestion;
    private EditText etAnswer;
    private Button btnCheckAnswer;

    // Step 3
    private LinearLayout step3;
    private EditText etNewPass, etConfirmNewPass;
    private Button btnReset;

    private TextView tvBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        accountManager = new LocalAccountManager(this);

        step1 = findViewById(R.id.step1);
        step2 = findViewById(R.id.step2);
        step3 = findViewById(R.id.step3);
        etUsername = findViewById(R.id.et_username);
        btnGetQuestion = findViewById(R.id.btn_get_question);
        tvQuestion = findViewById(R.id.tv_question);
        etAnswer = findViewById(R.id.et_answer);
        btnCheckAnswer = findViewById(R.id.btn_check_answer);
        etNewPass = findViewById(R.id.et_new_password);
        etConfirmNewPass = findViewById(R.id.et_confirm_new_password);
        btnReset = findViewById(R.id.btn_reset_password);
        tvBack = findViewById(R.id.tv_back_to_login);

        btnGetQuestion.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            if (username.isEmpty()) {
                Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show();
                return;
            }
            String question = accountManager.requestSecurityQuestion(username);
            if (question == null) {
                Toast.makeText(this, "用户不存在", Toast.LENGTH_SHORT).show();
            } else {
                currentUsername = username;
                tvQuestion.setText("问题：" + question);
                step1.setVisibility(View.GONE);
                step2.setVisibility(View.VISIBLE);
            }
        });

        btnCheckAnswer.setOnClickListener(v -> {
            String answer = etAnswer.getText().toString().trim();
            if (answer.isEmpty()) {
                Toast.makeText(this, "请输入答案", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean correct = accountManager.verifyAnswer(currentUsername, answer);
            if (correct) {
                step2.setVisibility(View.GONE);
                step3.setVisibility(View.VISIBLE);
            } else {
                Toast.makeText(this, "答案错误或尝试次数过多", Toast.LENGTH_SHORT).show();
            }
        });

        btnReset.setOnClickListener(v -> {
            String newPass = etNewPass.getText().toString().trim();
            String confirm = etConfirmNewPass.getText().toString().trim();
            if (newPass.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "密码不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            int res = accountManager.resetPassword(currentUsername, newPass, confirm);
            if (res == LocalAccountManager.RESET_SUCCESS) {
                Toast.makeText(this, "密码重置成功，请登录", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            } else {
                Toast.makeText(this, "重置失败，请重试", Toast.LENGTH_SHORT).show();
            }
        });

        tvBack.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }
}