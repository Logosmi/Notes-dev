package net.micode.notes.tool;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import net.micode.notes.R;
import net.micode.notes.ui.NotesListActivity;

public class ExportTextWorker extends Worker {
    public ExportTextWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        BackupUtils backup = BackupUtils.getInstance(getApplicationContext());
        int result = backup.exportToText();

        if (result == BackupUtils.STATE_SUCCESS) {
            return Result.success();
        } else {
            return Result.failure();
        }
    }
}