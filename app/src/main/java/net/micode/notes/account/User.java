package net.micode.notes.account;

public class User {
    private long _id;
    private String username;
    private String passwordHash;
    private String securityQuestion;
    private String securityAnswerHash;
    private int failCount;
    private long createdTime;

    public User() {}

    public User(long _id, String username, String passwordHash, String securityQuestion,
                String securityAnswerHash, int failCount, long createdTime) {
        this._id = _id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.securityQuestion = securityQuestion;
        this.securityAnswerHash = securityAnswerHash;
        this.failCount = failCount;
        this.createdTime = createdTime;
    }

    public long getId() { return _id; }
    public void setId(long _id) { this._id = _id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getSecurityQuestion() { return securityQuestion; }
    public void setSecurityQuestion(String securityQuestion) { this.securityQuestion = securityQuestion; }
    public String getSecurityAnswerHash() { return securityAnswerHash; }
    public void setSecurityAnswerHash(String securityAnswerHash) { this.securityAnswerHash = securityAnswerHash; }
    public int getFailCount() { return failCount; }
    public void setFailCount(int failCount) { this.failCount = failCount; }
    public void incrementFailCount() { this.failCount++; }
    public void resetFailCount() { this.failCount = 0; }
    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }
}