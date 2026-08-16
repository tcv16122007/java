package com.java.model;

public class Interaction {
    private long interactionId;
    private long userId;
    private long postId;
    private String type;
    private String createdAt;

    public long getInteractionId() { return interactionId; }
    public void setInteractionId(long interactionId) { this.interactionId = interactionId; }
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public long getPostId() { return postId; }
    public void setPostId(long postId) { this.postId = postId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}