package net.micode.notes.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import net.micode.notes.R;
import net.micode.notes.tag.Tag;
import net.micode.notes.tag.TagManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class TagManagementActivity extends AppCompatActivity implements MenuItem.OnMenuItemClickListener {

    private TagManager mTagManager;
    private TagAdapter mAdapter;
    private ListView mListView;
    private MaterialToolbar mToolbar;
    private CharSequence mOriginalTitle;
    private boolean mChoiceMode;
    private final HashSet<Integer> mSelected = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag_management);

        mToolbar = findViewById(R.id.toolbar);
        mOriginalTitle = mToolbar.getTitle();
        setSupportActionBar(mToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        findViewById(R.id.et_tag_name).setVisibility(View.GONE);
        findViewById(R.id.btn_create_tag).setVisibility(View.GONE);

        mTagManager = TagManager.getInstance(this);
        mListView = findViewById(R.id.lv_tag_list);
        mAdapter = new TagAdapter();
        mListView.setAdapter(mAdapter);

        mListView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!mChoiceMode) {
                enterChoiceMode();
                toggleSelection(position);
            }
            return true;
        });

        mListView.setOnItemClickListener((parent, view, position, id) -> {
            if (mChoiceMode) {
                toggleSelection(position);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTagList();
    }

    private void refreshTagList() {
        mAdapter.setData(mTagManager.getAllTags());
    }

    // ── Choice Mode ──

    private void enterChoiceMode() {
        mChoiceMode = true;
        mSelected.clear();
        mAdapter.notifyDataSetChanged();
        mListView.setLongClickable(false);
        mToolbar.setTitle("0 已选择");
        mToolbar.getMenu().clear();
        getMenuInflater().inflate(R.menu.note_list_options, mToolbar.getMenu());
        mToolbar.getMenu().findItem(R.id.move).setVisible(false);
        mToolbar.getMenu().findItem(R.id.delete).setOnMenuItemClickListener(this);

        MenuItem selectAll = mToolbar.getMenu().add(Menu.NONE, R.id.action_select_all, 0, R.string.menu_select_all);
        selectAll.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        selectAll.setOnMenuItemClickListener(item -> {
            selectAll(!isAllSelected());
            return true;
        });
    }

    private void exitChoiceMode() {
        mChoiceMode = false;
        mSelected.clear();
        mAdapter.notifyDataSetChanged();
        mListView.setLongClickable(true);
        mToolbar.setTitle(mOriginalTitle);
        mToolbar.getMenu().clear();
        invalidateOptionsMenu();
    }

    private void toggleSelection(int position) {
        if (mSelected.contains(position)) mSelected.remove(position);
        else mSelected.add(position);
        mAdapter.notifyDataSetChanged();
        updateToolbar();
    }

    private void selectAll(boolean checked) {
        mSelected.clear();
        if (checked) {
            for (int i = 0; i < mAdapter.getCount(); i++) mSelected.add(i);
        }
        mAdapter.notifyDataSetChanged();
        updateToolbar();
    }

    private boolean isAllSelected() {
        return mSelected.size() > 0 && mSelected.size() == mAdapter.getCount();
    }

    private void updateToolbar() {
        mToolbar.setTitle(mSelected.size() + " 已选择");
        MenuItem selAll = mToolbar.getMenu().findItem(R.id.action_select_all);
        if (selAll != null) {
            selAll.setTitle(isAllSelected() ? R.string.menu_deselect_all : R.string.menu_select_all);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.delete && !mSelected.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.tag_delete)
                    .setMessage("Delete " + mSelected.size() + " selected tags?")
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        for (int pos : mSelected) {
                            mTagManager.deleteTag(mAdapter.getItem(pos).getId());
                        }
                        exitChoiceMode();
                        refreshTagList();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (mChoiceMode) {
            exitChoiceMode();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (mChoiceMode) {
            exitChoiceMode();
            return true;
        }
        finish();
        return true;
    }

    // ── Adapter ──

    private class TagAdapter extends BaseAdapter {
        private List<Tag> mTags = new ArrayList<>();

        void setData(List<Tag> tags) {
            mTags = tags != null ? tags : new ArrayList<>();
            mSelected.clear();
            notifyDataSetChanged();
        }

        @Override public int getCount() { return mTags.size(); }
        @Override public Tag getItem(int pos) { return mTags.get(pos); }
        @Override public long getItemId(int pos) { return getItem(pos).getId(); }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            if (cv == null) {
                cv = LayoutInflater.from(parent.getContext())
                        .inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            Tag tag = getItem(pos);
            TextView tv = cv.findViewById(android.R.id.text1);
            tv.setText("#" + tag.getName());
            cv.setBackgroundColor(mChoiceMode && mSelected.contains(pos)
                    ? 0x220000FF : 0);
            return cv;
        }
    }
}
