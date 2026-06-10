package net.micode.notes.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import net.micode.notes.data.NoteItemData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NotesListAdapter extends BaseAdapter {
    private Context mContext;
    private List<NoteItemData> mNoteList = new ArrayList<>();
    private boolean mChoiceMode = false;
    private Set<Integer> mSelectedPositions = new HashSet<>();

    public NotesListAdapter(Context context) {
        this.mContext = context;
    }

    public void setNoteList(List<NoteItemData> list) {
        mNoteList = list != null ? list : new ArrayList<>();
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
        view.bind(mContext, item, mChoiceMode, mSelectedPositions.contains(position));
        return view;
    }

    public void setChoiceMode(boolean mode) {
        mChoiceMode = mode;
        notifyDataSetChanged();
    }

    public void setSelectedPositions(Set<Integer> positions) {
        mSelectedPositions = positions != null ? positions : new HashSet<>();
        notifyDataSetChanged();
    }

    public boolean isInChoiceMode() {
        return mChoiceMode;
    }
}
