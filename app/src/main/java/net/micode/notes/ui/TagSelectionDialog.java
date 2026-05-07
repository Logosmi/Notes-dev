package net.micode.notes.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import net.micode.notes.R;
import net.micode.notes.tag.Tag;
import net.micode.notes.tag.TagManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TagSelectionDialog extends DialogFragment {
    private long noteId;
    private TagManager tagManager;
    private List<Tag> allTags;
    private Map<Long, Boolean> checkedMap; // tagId -> isChecked
    private OnTagsSelectedListener listener;

    public interface OnTagsSelectedListener {
        void onTagsSelected(List<Long> selectedTagIds);
    }

    public static TagSelectionDialog newInstance(long noteId) {
        TagSelectionDialog dialog = new TagSelectionDialog();
        Bundle args = new Bundle();
        args.putLong("noteId", noteId);
        dialog.setArguments(args);
        return dialog;
    }

    public void setOnTagsSelectedListener(OnTagsSelectedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        noteId = getArguments().getLong("noteId");
        tagManager = new TagManager(getActivity());
        allTags = tagManager.getAllTags();
        checkedMap = new HashMap<>();

        // 获取该便签已有的标签
        List<Tag> noteTags = tagManager.getTagsForNote(noteId);
        for (Tag tag : allTags) {
            boolean checked = false;
            for (Tag nt : noteTags) {
                if (nt.getId() == tag.getId()) {
                    checked = true;
                    break;
                }
            }
            checkedMap.put(tag.getId(), checked);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.tag_select_title);
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_tag_selection, null);
        LinearLayout checkBoxContainer = view.findViewById(R.id.checkbox_container);

        for (final Tag tag : allTags) {
            CheckBox checkBox = new CheckBox(getActivity());
            checkBox.setText(tag.getName());
            checkBox.setChecked(checkedMap.get(tag.getId()));
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    checkedMap.put(tag.getId(), isChecked);
                }
            });
            checkBoxContainer.addView(checkBox);
        }

        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                List<Long> selected = new ArrayList<>();
                for (Map.Entry<Long, Boolean> entry : checkedMap.entrySet()) {
                    if (entry.getValue()) {
                        selected.add(entry.getKey());
                    }
                }
                // 清空原有标签关联，并批量添加新选中的
                tagManager.removeAllTagsFromNote(noteId);
                for (long tagId : selected) {
                    tagManager.addTagToNote(noteId, tagId);
                }
                if (listener != null) {
                    listener.onTagsSelected(selected);
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        return builder.create();
    }
}