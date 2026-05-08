package net.micode.notes.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import net.micode.notes.data.Notes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class NotesListAdapter extends BaseAdapter {
    private Context mContext;
    private List<NoteItemData> mNoteList = new ArrayList<>();
    private HashMap<Integer, Boolean> mSelectedIndex = new HashMap<>();
    private int mNotesCount = 0;
    private boolean mChoiceMode = false;

    public static class AppWidgetAttribute {
        public int widgetId;
        public int widgetType;
    }

    public NotesListAdapter(Context context) {
        this.mContext = context;
    }

    public void setNoteList(List<NoteItemData> list) {
        mNoteList = list != null ? list : new ArrayList<>();
        calcNotesCount();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mNoteList.size();
    }

    @Override
    public NoteItemData getItem(int position) {
        return mNoteList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        NotesListItem view;
        if (convertView == null) {
            view = new NotesListItem(mContext);
        } else {
            view = (NotesListItem) convertView;
        }
        NoteItemData item = getItem(position);
        view.bind(mContext, item, mChoiceMode, isSelectedItem(position));
        return view;
    }

    public void setCheckedItem(int position, boolean checked) {
        mSelectedIndex.put(position, checked);
        notifyDataSetChanged();
    }

    public boolean isInChoiceMode() {
        return mChoiceMode;
    }

    public void setChoiceMode(boolean mode) {
        mSelectedIndex.clear();
        mChoiceMode = mode;
        notifyDataSetChanged();
    }

    public void selectAll(boolean checked) {
        for (int i = 0; i < mNoteList.size(); i++) {
            if (mNoteList.get(i).getType() == Notes.TYPE_NOTE) {
                setCheckedItem(i, checked);
            }
        }
    }

    public HashSet<Long> getSelectedItemIds() {
        HashSet<Long> ids = new HashSet<>();
        for (int i = 0; i < mNoteList.size(); i++) {
            if (Boolean.TRUE.equals(mSelectedIndex.get(i))) {
                long id = getItemId(i);
                if (id != Notes.ID_ROOT_FOLDER) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    public int getSelectedCount() {
        int count = 0;
        for (Boolean b : mSelectedIndex.values()) {
            if (Boolean.TRUE.equals(b)) count++;
        }
        return count;
    }

    public boolean isAllSelected() {
        int checked = getSelectedCount();
        return checked != 0 && checked == mNotesCount;
    }

    public boolean isSelectedItem(int position) {
        return Boolean.TRUE.equals(mSelectedIndex.get(position));
    }

    public HashSet<AppWidgetAttribute> getSelectedWidget() {
        HashSet<AppWidgetAttribute> result = new HashSet<>();
        for (int i = 0; i < mNoteList.size(); i++) {
            if (Boolean.TRUE.equals(mSelectedIndex.get(i))) {
                NoteItemData item = getItem(i);
                AppWidgetAttribute attr = new AppWidgetAttribute();
                attr.widgetId = item.getWidgetId();
                attr.widgetType = item.getWidgetType();
                result.add(attr);
            }
        }
        return result;
    }

    private void calcNotesCount() {
        mNotesCount = 0;
        for (NoteItemData item : mNoteList) {
            if (item.getType() == Notes.TYPE_NOTE) {
                mNotesCount++;
            }
        }
    }
}