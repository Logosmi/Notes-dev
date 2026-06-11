package net.micode.notes.tag;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import net.micode.notes.data.Notes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TagManager {
    private static TagManager sInstance;
    private Context mContext;

    private TagManager(Context context) {
        mContext = context.getApplicationContext();
    }

    public static synchronized TagManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new TagManager(context);
        }
        return sInstance;
    }

    // ── Tag CRUD ──

    public long createTag(String name, int color) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("color", color);
        values.put("createdTime", System.currentTimeMillis());
        Uri uri = mContext.getContentResolver().insert(Notes.CONTENT_TAG_URI, values);
        if (uri != null) {
            return ContentUris.parseId(uri);
        }
        return -1;
    }

    public boolean updateTag(long tagId, String name, int color) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("color", color);
        Uri uri = ContentUris.withAppendedId(Notes.CONTENT_TAG_URI, tagId);
        int rows = mContext.getContentResolver().update(uri, values, null, null);
        return rows > 0;
    }

    public boolean deleteTag(long tagId) {
        // 1. find all notes associated with this tag
        List<Long> noteIds = getNoteIdsByTagId(tagId);
        // 2. get tag name for content cleanup
        Tag tag = getTagById(tagId);
        String tagName = tag != null ? tag.getName() : null;
        // 3. remove #tag from associated note contents
        if (tagName != null && tagName.length() > 0) {
            for (long noteId : noteIds) {
                cleanLabelFromNoteContent(noteId, tagName);
            }
        }
        // 4. remove note_tag associations
        mContext.getContentResolver().delete(Notes.CONTENT_NOTE_TAG_URI,
                "tagId=?", new String[]{String.valueOf(tagId)});
        // 5. delete the tag itself
        Uri tagUri = ContentUris.withAppendedId(Notes.CONTENT_TAG_URI, tagId);
        int rows = mContext.getContentResolver().delete(tagUri, null, null);
        return rows > 0;
    }

    public Tag getTagById(long tagId) {
        Cursor c = null;
        try {
            Uri uri = ContentUris.withAppendedId(Notes.CONTENT_TAG_URI, tagId);
            c = mContext.getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                return cursorToTag(c);
            }
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    public List<Tag> getAllTags() {
        List<Tag> tags = new ArrayList<>();
        Cursor c = null;
        try {
            c = mContext.getContentResolver().query(Notes.CONTENT_TAG_URI, null,
                    null, null, "createdTime ASC");
            if (c != null) {
                while (c.moveToNext()) {
                    tags.add(cursorToTag(c));
                }
            }
        } finally {
            if (c != null) c.close();
        }
        return tags;
    }

    private Tag cursorToTag(Cursor c) {
        long id = c.getLong(c.getColumnIndexOrThrow("_id"));
        String name = c.getString(c.getColumnIndexOrThrow("name"));
        int color = c.getInt(c.getColumnIndexOrThrow("color"));
        long createdTime = c.getLong(c.getColumnIndexOrThrow("createdTime"));
        return new Tag(id, name, color, createdTime);
    }

    // ── Note-Tag Association ──

    public boolean associateTagWithNote(long noteId, long tagId) {
        ContentValues values = new ContentValues();
        values.put("noteId", noteId);
        values.put("tagId", tagId);
        Uri uri = mContext.getContentResolver().insert(Notes.CONTENT_NOTE_TAG_URI, values);
        return uri != null;
    }

    public boolean removeTagFromNote(long noteId, long tagId) {
        int rows = mContext.getContentResolver().delete(Notes.CONTENT_NOTE_TAG_URI,
                "noteId=? AND tagId=?", new String[]{String.valueOf(noteId), String.valueOf(tagId)});
        return rows > 0;
    }

    public List<Tag> getTagsByNoteId(long noteId) {
        List<Tag> tags = new ArrayList<>();
        Cursor c = null;
        try {
            String selection = "tagId IN (SELECT tagId FROM note_tag WHERE noteId=?)";
            c = mContext.getContentResolver().query(Notes.CONTENT_TAG_URI,
                    null, selection, new String[]{String.valueOf(noteId)}, "createdTime ASC");
            if (c != null) {
                while (c.moveToNext()) {
                    tags.add(cursorToTag(c));
                }
            }
        } finally {
            if (c != null) c.close();
        }
        return tags;
    }

    public List<Long> getNoteIdsByTagId(long tagId) {
        List<Long> noteIds = new ArrayList<>();
        Cursor c = null;
        try {
            c = mContext.getContentResolver().query(Notes.CONTENT_NOTE_TAG_URI,
                    new String[]{"noteId"}, "tagId=?",
                    new String[]{String.valueOf(tagId)}, null);
            if (c != null) {
                while (c.moveToNext()) {
                    noteIds.add(c.getLong(0));
                }
            }
        } finally {
            if (c != null) c.close();
        }
        return noteIds;
    }

    public boolean isTagAssociatedWithNote(long noteId, long tagId) {
        Cursor c = null;
        try {
            c = mContext.getContentResolver().query(Notes.CONTENT_NOTE_TAG_URI,
                    new String[]{"noteId"}, "noteId=? AND tagId=?",
                    new String[]{String.valueOf(noteId), String.valueOf(tagId)}, null);
            return c != null && c.getCount() > 0;
        } finally {
            if (c != null) c.close();
        }
    }

    public boolean isTagNameExists(String name) {
        Cursor c = null;
        try {
            c = mContext.getContentResolver().query(Notes.CONTENT_TAG_URI,
                    new String[]{"_id"}, "name=?",
                    new String[]{name}, null);
            return c != null && c.getCount() > 0;
        } finally {
            if (c != null) c.close();
        }
    }

    // ── Inline #tag parsing ──

    private static final Pattern TAG_PATTERN = Pattern.compile("#(\\S+)");

    public static List<String> parseTags(String content) {
        List<String> tags = new ArrayList<>();
        if (content == null) return tags;
        Matcher m = TAG_PATTERN.matcher(content);
        Set<String> seen = new HashSet<>();
        while (m.find()) {
            String tag = m.group(1);
            if (tag.length() > 0 && tag.length() <= 32 && !seen.contains(tag)) {
                seen.add(tag);
                tags.add(tag);
            }
        }
        return tags;
    }

    /**
     * Build a per-tag-name removal pattern: (?<!\w)#QuotedName(?!\w)
     */
    private static Pattern buildTagRemovalPattern(String tagName) {
        return Pattern.compile("(?<!\\w)#" + Pattern.quote(tagName) + "(?!\\w)");
    }

    private void cleanLabelFromNoteContent(long noteId, String tagName) {
        // read current content from data table
        Cursor c = null;
        try {
            c = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI,
                    new String[]{"_id", "content"},
                    "note_id=? AND mime_type='vnd.android.cursor.item/text_note'",
                    new String[]{String.valueOf(noteId)}, null);
            if (c == null || !c.moveToFirst()) return;

            long dataId = c.getLong(0);
            String content = c.getString(1);
            if (content == null) return;

            Pattern p = buildTagRemovalPattern(tagName);
            Matcher m = p.matcher(content);
            String cleaned = m.replaceAll("").replaceAll("  +", " ").trim();

            if (!cleaned.equals(content)) {
                ContentValues values = new ContentValues();
                values.put("content", cleaned);
                mContext.getContentResolver().update(
                        ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId),
                        values, null, null);
            }
        } finally {
            if (c != null) c.close();
        }
    }

    private int getNoteCountForTag(long tagId) {
        Cursor c = null;
        try {
            c = mContext.getContentResolver().query(Notes.CONTENT_NOTE_TAG_URI,
                    new String[]{"COUNT(*)"}, "tagId=?",
                    new String[]{String.valueOf(tagId)}, null);
            if (c != null && c.moveToFirst()) return c.getInt(0);
        } finally {
            if (c != null) c.close();
        }
        return 0;
    }

    public void syncTagsForNote(long noteId, String content) {
        List<String> tagNames = parseTags(content);

        // get existing tags for this note
        Set<Long> existingTagIds = new HashSet<>();
        Cursor c = null;
        try {
            c = mContext.getContentResolver().query(Notes.CONTENT_NOTE_TAG_URI,
                    new String[]{"tagId"}, "noteId=?",
                    new String[]{String.valueOf(noteId)}, null);
            if (c != null) {
                while (c.moveToNext()) existingTagIds.add(c.getLong(0));
            }
        } finally {
            if (c != null) c.close();
        }

        // resolve/create tags and track which should be associated
        Set<Long> wantedTagIds = new HashSet<>();
        for (String name : tagNames) {
            long tagId = findOrCreateTag(name);
            if (tagId > 0) wantedTagIds.add(tagId);
        }

        // remove associations that are no longer wanted
        for (long tagId : existingTagIds) {
            if (!wantedTagIds.contains(tagId)) {
                removeTagFromNote(noteId, tagId);
                // auto-delete tag if it no longer belongs to any note
                if (getNoteCountForTag(tagId) == 0) {
                    Uri tagUri = ContentUris.withAppendedId(Notes.CONTENT_TAG_URI, tagId);
                    mContext.getContentResolver().delete(tagUri, null, null);
                }
            }
        }

        // add new associations
        for (long tagId : wantedTagIds) {
            if (!existingTagIds.contains(tagId)) {
                associateTagWithNote(noteId, tagId);
            }
        }
    }

    private long findOrCreateTag(String name) {
        Cursor c = null;
        try {
            c = mContext.getContentResolver().query(Notes.CONTENT_TAG_URI,
                    new String[]{"_id"}, "name=?", new String[]{name}, null);
            if (c != null && c.moveToFirst()) return c.getLong(0);
        } finally {
            if (c != null) c.close();
        }
        return createTag(name, 0);
    }
}
