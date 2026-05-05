package net.micode.notes.tool;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import net.micode.notes.data.Notes;
import java.util.HashSet;

public class BatchDeleteWorker extends Worker {
    public static final String KEY_ITEM_IDS = "item_ids";

    public BatchDeleteWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        long[] itemIds = getInputData().getLongArray(KEY_ITEM_IDS);
        if (itemIds == null || itemIds.length == 0) return Result.failure();

        HashSet<Long> ids = new HashSet<>();
        for (long id : itemIds) ids.add(id);

        try {
            // 注意：这里不区分同步模式，直接调用批量删除；实际逻辑应与原 AsyncTask 保持一致
            boolean success = DataUtils.batchDeleteNotes(
                    getApplicationContext().getContentResolver(), ids);
            if (success) {
                return Result.success();
            } else {
                return Result.failure();
            }
        } catch (Exception e) {
            return Result.retry();
        }
    }
}