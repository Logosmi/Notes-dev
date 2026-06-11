package net.micode.notes.tag;

public class Tag {
    private long id;
    private String name;
    private int color;
    private long createdTime;

    public Tag() {}

    public Tag(long id, String name, int color, long createdTime) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.createdTime = createdTime;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }

    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }
}
