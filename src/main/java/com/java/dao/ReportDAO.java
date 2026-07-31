package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportDAO {

    public boolean insertReport(Long postId, Long commentId, long reporterId, String reason) {
        String sql = "INSERT INTO Report (reporter_id, post_id, comment_id, reason) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, reporterId);
            if (postId != null) ps.setLong(2, postId);
            else ps.setNull(2, java.sql.Types.BIGINT);
            if (commentId != null) ps.setLong(3, commentId);
            else ps.setNull(3, java.sql.Types.BIGINT);
            ps.setString(4, reason);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Insert report error", e);
        }
    }

    public List<Map<String, Object>> findAll() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT r.*, " +
                     "u.username as reporter_name, " +
                     "p.title as post_title, " +
                     "c.content as comment_content, " +
                     "cu.username as comment_author " +
                     "FROM Report r " +
                     "LEFT JOIN [User] u ON r.reporter_id = u.user_id " +
                     "LEFT JOIN Post p ON r.post_id = p.post_id " +
                     "LEFT JOIN Comment c ON r.comment_id = c.comment_id " +
                     "LEFT JOIN [User] cu ON c.user_id = cu.user_id " +
                     "ORDER BY r.created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("reportId", rs.getLong("report_id"));
                map.put("reporterId", rs.getLong("reporter_id"));
                map.put("reporterName", rs.getString("reporter_name"));
                map.put("postId", rs.getLong("post_id"));
                if (rs.wasNull()) map.put("postId", null);
                map.put("postTitle", rs.getString("post_title"));
                map.put("commentId", rs.getLong("comment_id"));
                if (rs.wasNull()) map.put("commentId", null);
                map.put("commentContent", rs.getString("comment_content"));
                map.put("commentAuthor", rs.getString("comment_author"));
                map.put("reason", rs.getString("reason"));
                map.put("status", rs.getString("status"));
                map.put("createdAt", rs.getString("created_at"));
                list.add(map);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find all reports error", e);
        }
        return list;
    }

    public boolean deleteReport(long reportId) {
        String sql = "DELETE FROM Report WHERE report_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, reportId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Delete report error", e);
        }
    }

    public boolean updateStatus(long reportId, String status) {
        String sql = "UPDATE Report SET status = ?, resolved_at = GETDATE() WHERE report_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, reportId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Update report status error", e);
        }
    }

    // ==== Hỗ trợ: Lưu tin nhắn hỗ trợ từ người dùng ====
    public boolean insertSupportMessage(long userId, String message) {
        String sql = "INSERT INTO Report (reporter_id, post_id, comment_id, reason) VALUES (?, NULL, NULL, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, "Hỗ trợ: " + message);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Insert support message error", e);
        }
    }
}