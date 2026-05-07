package net.micode.notes.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.tag.Tag;
import net.micode.notes.tag.TagManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TagManagementActivity extends Activity {
    private TagManager tagManager;
    private ListView listView;
    private Button addButton;
    private List<Tag> tagList;
    private SimpleAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_management);

        tagManager = new TagManager(this);
        listView = findViewById(R.id.tag_list_view);
        addButton = findViewById(R.id.add_tag_button);

        loadTags();

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddTagDialog();
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Tag tag = tagList.get(position);
                showEditDeleteDialog(tag);
            }
        });
    }

    private void loadTags() {
        tagList = tagManager.getAllTags();
        List<Map<String, String>> data = new ArrayList<>();
        for (Tag tag : tagList) {
            Map<String, String> map = new HashMap<>();
            map.put("name", tag.getName());
            map.put("color", String.format("#%06X", (0xFFFFFF & tag.getColor())));
            data.add(map);
        }
        adapter = new SimpleAdapter(this, data,
                android.R.layout.simple_list_item_2,
                new String[]{"name", "color"},
                new int[]{android.R.id.text1, android.R.id.text2});
        listView.setAdapter(adapter);
    }

    private void showAddTagDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.tag_add));
        View view = getLayoutInflater().inflate(R.layout.dialog_tag_edit, null);
        final EditText nameEdit = view.findViewById(R.id.tag_name_edit);
        final EditText colorEdit = view.findViewById(R.id.tag_color_edit);
        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = nameEdit.getText().toString().trim();
                String colorStr = colorEdit.getText().toString().trim();
                if (name.isEmpty()) {
                    Toast.makeText(TagManagementActivity.this, R.string.tag_name_required, Toast.LENGTH_SHORT).show();
                    return;
                }
                int color = 0xFF66BB6A; // 默认绿色
                try {
                    if (!colorStr.isEmpty()) {
                        color = Integer.parseInt(colorStr.replace("#", ""), 16) | 0xFF000000;
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(TagManagementActivity.this, R.string.tag_color_invalid, Toast.LENGTH_SHORT).show();
                    return;
                }
                tagManager.createTag(name, color);
                loadTags();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void showEditDeleteDialog(final Tag tag) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(tag.getName());
        builder.setItems(new String[]{getString(R.string.tag_edit), getString(R.string.tag_delete)},
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showEditTagDialog(tag);
                } else {
                    showDeleteConfirmDialog(tag);
                }
            }
        });
        builder.show();
    }

    private void showEditTagDialog(final Tag tag) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.tag_edit));
        View view = getLayoutInflater().inflate(R.layout.dialog_tag_edit, null);
        final EditText nameEdit = view.findViewById(R.id.tag_name_edit);
        final EditText colorEdit = view.findViewById(R.id.tag_color_edit);
        nameEdit.setText(tag.getName());
        colorEdit.setText(String.format("#%06X", (0xFFFFFF & tag.getColor())));
        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = nameEdit.getText().toString().trim();
                String colorStr = colorEdit.getText().toString().trim();
                if (name.isEmpty()) {
                    Toast.makeText(TagManagementActivity.this, R.string.tag_name_required, Toast.LENGTH_SHORT).show();
                    return;
                }
                int color = tag.getColor();
                try {
                    if (!colorStr.isEmpty()) {
                        color = Integer.parseInt(colorStr.replace("#", ""), 16) | 0xFF000000;
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(TagManagementActivity.this, R.string.tag_color_invalid, Toast.LENGTH_SHORT).show();
                    return;
                }
                tagManager.updateTag(tag.getId(), name, color);
                loadTags();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void showDeleteConfirmDialog(final Tag tag) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.tag_delete_confirm)
                .setMessage(getString(R.string.tag_delete_message, tag.getName()))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        tagManager.deleteTag(tag.getId());
                        loadTags();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}