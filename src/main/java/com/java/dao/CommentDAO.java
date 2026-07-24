package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.java.model.Comment;

public class CommentDAO {

    // ==================== FIND BY POST ====================
    public List<Comment> findByPost(long postId) {
        List<Comment> list = new ArrayList<>();
        String sql = "SELECT c.*, u.username FROM Comment c " +
                     "LEFT JOIN [User] u ON c.user_id = u.user_id " +
                     "WHERE c.post_id = ? AND c.status = 'VISIBLE' " +
                     "ORDER BY c.created_at ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, postId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find comments by post error", e);
        }
        return list;
    }

    // ==================== FIND ALL (for Admin/Mod) ====================
    public List<Comment> findAll() {
        List<Comment> list = new ArrayList<>();
        String sql = "SELECT c.*, u.username, p.title as postTitle FROM Comment c " +
                     "LEFT JOIN [User] u ON c.user_id = u.user_id " +
                     "LEFT JOIN Post p ON c.post_id = p.post_id " +
                     "ORDER BY c.created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Comment c = mapRow(rs);
                c.setPostTitle(rs.getString("postTitle"));
                list.add(c);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find all comments error", e);
        }
        return list;
    }

    // ==================== FIND BY ID ====================
    public Comment findById(long id) {
        String sql = "SELECT * FROM Comment WHERE comment_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find comment by id error", e);
        }
        return null;
    }

    // ==================== INSERT ====================
    public boolean insert(Comment comment) {
        String sql = "INSERT INTO Comment (content, user_id, post_id) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, comment.getContent());
            ps.setLong(2, comment.getUserId());
            ps.setLong(3, comment.getPostId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Insert comment error", e);
        }
    }

    // ==================== UPDATE STATUS ====================
    public boolean updateStatus(long commentId, String status) {
        String sql = "UPDATE Comment SET status = ? WHERE comment_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, commentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Update comment status error", e);
        }
    }

    // ==================== DELETE (soft) ====================
    public boolean delete(long id) {
        return updateStatus(id, "DELETED");
    }

    // ==================== HIDE ====================
    public boolean hide(long id) {
        return updateStatus(id, "HIDDEN");
    }

    // ==================== RESTORE ====================
    public boolean restore(long id) {
        return updateStatus(id, "VISIBLE");
    }

    // ==================== MAP ROW ====================
    private Comment mapRow(ResultSet rs) throws SQLException {
        Comment c = new Comment();
        c.setCommentId(rs.getLong("comment_id"));
        c.setContent(rs.getString("content"));
        c.setCreatedAt(rs.getString("created_at"));
        c.setStatus(rs.getString("status"));
        c.setUserId(rs.getLong("user_id"));
        c.setPostId(rs.getLong("post_id"));
        try { c.setUsername(rs.getString("username")); } catch (SQLException ignore) {}
        return c;
    }
}