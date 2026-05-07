package net.micode.notes.tag;

public class Tag {
    private long _id;
    private String name;
    private int color;
    private long createdTime;

    public Tag() {}

    public Tag(long _id, String name, int color, long createdTime) {
        this._id = _id;
        this.name = name;
        this.color = color;
        this.createdTime = createdTime;
    }

    public long getId() { return _id; }
    public void setId(long _id) { this._id = _id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }

    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }
}