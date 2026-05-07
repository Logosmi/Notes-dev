package net.micode.notes.tag;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import net.micode.notes.data.Notes;

import java.util.ArrayList;
import java.util.List;

public class TagManager {
    private Context context;
    private ContentResolver resolver;

    // 依赖于 NotesProvider 中定义好的 URI 常量，这里直接引用
    public static final Uri URI_TAG = Notes.URI_TAG;
    public static final Uri URI_NOTE_TAG = Notes.URI_NOTE_TAG;

    public TagManager(Context context) {
        this.context = context;
        this.resolver = context.getContentResolver();
    }

    /**
     * 创建新标签
     */
    public Uri createTag(String name, int color) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("color", color);
        values.put("createdTime", System.currentTimeMillis());
        return resolver.insert(URI_TAG, values);
    }

    /**
     * 删除标签，并删除所有关联关系
     */
    public int deleteTag(long tagId) {
        // 先删除关联
        resolver.delete(URI_NOTE_TAG, "tagId=?", new String[]{String.valueOf(tagId)});
        // 再删除标签
        return resolver.delete(URI_TAG, "_id=?", new String[]{String.valueOf(tagId)});
    }

    /**
     * 更新标签（名称或颜色）
     */
    public int updateTag(long tagId, String name, int color) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("color", color);
        return resolver.update(URI_TAG, values, "_id=?", new String[]{String.valueOf(tagId)});
    }

    /**
     * 获取所有标签
     */
    public List<Tag> getAllTags() {
        List<Tag> tags = new ArrayList<>();
        Cursor cursor = resolver.query(URI_TAG, null, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                Tag tag = new Tag();
                tag.setId(cursor.getLong(cursor.getColumnIndex("_id")));
                tag.setName(cursor.getString(cursor.getColumnIndex("name")));
                tag.setColor(cursor.getInt(cursor.getColumnIndex("color")));
                tag.setCreatedTime(cursor.getLong(cursor.getColumnIndex("createdTime")));
                tags.add(tag);
            }
            cursor.close();
        }
        return tags;
    }

    /**
     * 为便签关联标签
     */
    public Uri addTagToNote(long noteId, long tagId) {
        ContentValues values = new ContentValues();
        values.put("noteId", noteId);
        values.put("tagId", tagId);
        return resolver.insert(URI_NOTE_TAG, values);
    }

    /**
     * 移除便签的某个标签
     */
    public int removeTagFromNote(long noteId, long tagId) {
        return resolver.delete(URI_NOTE_TAG,
                "noteId=? AND tagId=?",
                new String[]{String.valueOf(noteId), String.valueOf(tagId)});
    }

    /**
     * 移除便签的所有标签（编辑时重新设置）
     */
    public int removeAllTagsFromNote(long noteId) {
        return resolver.delete(URI_NOTE_TAG,
                "noteId=?",
                new String[]{String.valueOf(noteId)});
    }

    /**
     * 获取某个便签的所有标签
     */
    public List<Tag> getTagsForNote(long noteId) {
        List<Tag> tags = new ArrayList<>();
        String selection = "noteId=?";
        Cursor cursor = resolver.query(URI_NOTE_TAG, null, selection,
                new String[]{String.valueOf(noteId)}, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                long tagId = cursor.getLong(cursor.getColumnIndex("tagId"));
                // 再查标签详情
                Cursor tagCursor = resolver.query(URI_TAG, null,
                        "_id=?", new String[]{String.valueOf(tagId)}, null);
                if (tagCursor != null && tagCursor.moveToFirst()) {
                    Tag tag = new Tag();
                    tag.setId(tagCursor.getLong(tagCursor.getColumnIndex("_id")));
                    tag.setName(tagCursor.getString(tagCursor.getColumnIndex("name")));
                    tag.setColor(tagCursor.getInt(tagCursor.getColumnIndex("color")));
                    tag.setCreatedTime(tagCursor.getLong(tagCursor.getColumnIndex("createdTime")));
                    tags.add(tag);
                    tagCursor.close();
                }
            }
            cursor.close();
        }
        return tags;
    }

    /**
     * 根据标签筛选便签ID列表
     */
    public List<Long> getNoteIdsByTag(long tagId) {
        List<Long> noteIds = new ArrayList<>();
        Cursor cursor = resolver.query(URI_NOTE_TAG, null,
                "tagId=?", new String[]{String.valueOf(tagId)}, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                noteIds.add(cursor.getLong(cursor.getColumnIndex("noteId")));
            }
            cursor.close();
        }
        return noteIds;
    }

    /**
     * 获取所有便签及其标签关联（用于列表展示）
     */
    public Cursor getAllNoteTags() {
        return resolver.query(URI_NOTE_TAG, null, null, null, null);
    }
}