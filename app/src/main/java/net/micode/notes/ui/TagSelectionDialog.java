package net.micode.notes.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import net.micode.notes.R;
import net.micode.notes.tag.Tag;
import net.micode.notes.tag.TagManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TagSelectionDialog {

    public interface OnTagsSelectedListener {
        void onTagsSelected(List<Tag> selectedTags);
    }

    public static void show(Context context, long noteId, OnTagsSelectedListener listener) {
        TagManager tm = TagManager.getInstance(context);
        List<Tag> allTags = tm.getAllTags();
        List<Tag> noteTags = tm.getTagsByNoteId(noteId);

        Set<Long> selectedIds = new HashSet<>();
        for (Tag t : noteTags) selectedIds.add(t.getId());

        String[] names = new String[allTags.size()];
        boolean[] checked = new boolean[allTags.size()];
        for (int i = 0; i < allTags.size(); i++) {
            names[i] = allTags.get(i).getName();
            checked[i] = selectedIds.contains(allTags.get(i).getId());
        }

        final List<Tag> tagsRef = allTags;
        final boolean[] checkedRef = checked;

        new AlertDialog.Builder(context)
                .setTitle(R.string.tag_select_title)
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> {
                    checkedRef[which] = isChecked;
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    List<Tag> result = new ArrayList<>();
                    for (int i = 0; i < tagsRef.size(); i++) {
                        if (checkedRef[i]) result.add(tagsRef.get(i));
                    }
                    listener.onTagsSelected(result);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
