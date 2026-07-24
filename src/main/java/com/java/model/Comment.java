package com.java.model;

public class Comment {
    private long commentId;
    private String content;
    private String createdAt;
    private String status;
    private long userId;
    private long postId;
    private String username;      // từ join với User
    private String postTitle;     // từ join với Post

    // Getters & Setters
    public long getCommentId() { return commentId; }
    public void setCommentId(long commentId) { this.commentId = commentId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public long getPostId() { return postId; }
    public void setPostId(long postId) { this.postId = postId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPostTitle() { return postTitle; }
    public void setPostTitle(String postTitle) { this.postTitle = postTitle; }
}