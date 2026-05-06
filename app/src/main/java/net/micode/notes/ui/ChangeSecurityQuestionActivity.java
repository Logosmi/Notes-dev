package net.micode.notes.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import net.micode.notes.R;
import net.micode.notes.account.LocalAccountManager;

public class ChangeSecurityQuestionActivity extends Activity {
    private EditText etCurrentPwd, etNewQuestion, etNewAnswer;
    private Button btnConfirm;
    private LocalAccountManager accountManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_security);
        accountManager = new LocalAccountManager(this);

        etCurrentPwd = findViewById(R.id.et_current_password);
        etNewQuestion = findViewById(R.id.et_new_question);
        etNewAnswer = findViewById(R.id.et_new_answer);
        btnConfirm = findViewById(R.id.btn_confirm_change);

        btnConfirm.setOnClickListener(v -> changeSecurity());
    }

    private void changeSecurity() {
        String currentPwd = etCurrentPwd.getText().toString().trim();
        String newQuestion = etNewQuestion.getText().toString().trim();
        String newAnswer = etNewAnswer.getText().toString().trim();

        if (currentPwd.isEmpty() || newQuestion.isEmpty() || newAnswer.isEmpty()) {
            Toast.makeText(this, "所有字段都不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean success = accountManager.changeSecurityQuestion(currentPwd, newQuestion, newAnswer);
        if (success) {
            Toast.makeText(this, "安全问题已更新", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "密码错误，修改失败", Toast.LENGTH_SHORT).show();
        }
    }
}