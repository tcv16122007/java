package com.java.model;

import java.util.List;

public class Comment {
    private long commentId;
    private String content;
    private String createdAt;
    private String status; // VISIBLE, HIDDEN, DELETED
    private long userId;
    private long postId;
    private Long parentId; // null nếu là comment gốc
    private String username;
    private String postTitle;
    private int likeCount;
    private int dislikeCount;
    private boolean likedByCurrentUser;
    private boolean dislikedByCurrentUser;
    private List<Comment> replies;

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

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPostTitle() { return postTitle; }
    public void setPostTitle(String postTitle) { this.postTitle = postTitle; }

    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }

    public int getDislikeCount() { return dislikeCount; }
    public void setDislikeCount(int dislikeCount) { this.dislikeCount = dislikeCount; }

    public boolean isLikedByCurrentUser() { return likedByCurrentUser; }
    public void setLikedByCurrentUser(boolean likedByCurrentUser) { this.likedByCurrentUser = likedByCurrentUser; }

    public boolean isDislikedByCurrentUser() { return dislikedByCurrentUser; }
    public void setDislikedByCurrentUser(boolean dislikedByCurrentUser) { this.dislikedByCurrentUser = dislikedByCurrentUser; }

    public List<Comment> getReplies() { return replies; }
    public void setReplies(List<Comment> replies) { this.replies = replies; }
}