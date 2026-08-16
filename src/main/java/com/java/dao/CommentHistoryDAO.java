package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.java.model.Comment;

public class CommentHistoryDAO {

    public List<Comment> getCommentHistoryByUser(long userId) {
        List<Comment> list = new ArrayList<>();
        String sql = "SELECT c.*, u.username, p.title as postTitle " +
                     "FROM Comment c " +
                     "LEFT JOIN [User] u ON c.user_id = u.user_id " +
                     "LEFT JOIN Post p ON c.post_id = p.post_id " +
                     "WHERE c.user_id = ? " +
                     "ORDER BY c.created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Comment c = new Comment();
                c.setCommentId(rs.getLong("comment_id"));
                c.setContent(rs.getString("content"));
                c.setCreatedAt(rs.getString("created_at"));
                c.setStatus(rs.getString("status"));
                c.setUserId(rs.getLong("user_id"));
                c.setPostId(rs.getLong("post_id"));
                c.setUsername(rs.getString("username"));
                c.setPostTitle(rs.getString("postTitle"));
                list.add(c);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Get comment history error", e);
        }
        return list;
    }
}