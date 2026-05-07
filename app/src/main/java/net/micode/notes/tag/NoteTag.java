package net.micode.notes.tag;

public class NoteTag {
    private long noteId;
    private long tagId;

    public NoteTag() {}

    public NoteTag(long noteId, long tagId) {
        this.noteId = noteId;
        this.tagId = tagId;
    }

    public long getNoteId() { return noteId; }
    public void setNoteId(long noteId) { this.noteId = noteId; }

    public long getTagId() { return tagId; }
    public void setTagId(long tagId) { this.tagId = tagId; }
}