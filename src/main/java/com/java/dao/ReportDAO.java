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

    public boolean hasReport(Long postId, Long commentId, long reporterId) {
        String sql;
        if (postId != null) {
            sql = "SELECT COUNT(*) FROM Report WHERE reporter_id = ? AND post_id = ?";
        } else if (commentId != null) {
            sql = "SELECT COUNT(*) FROM Report WHERE reporter_id = ? AND comment_id = ?";
        } else {
            return false;
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, reporterId);
            ps.setLong(2, postId != null ? postId : commentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Check report exists error", e);
        }
    }

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
                     "WHERE r.post_id IS NOT NULL OR r.comment_id IS NOT NULL " +
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

    public boolean updateStatus(long reportId, String status, long moderatorId) {
        String sql = "UPDATE Report SET status = ?, moderator_id = ?, " +
                     "resolved_at = CASE WHEN ? IN ('RESOLVED','REJECTED') THEN GETDATE() ELSE NULL END " +
                     "WHERE report_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, moderatorId);
            ps.setString(3, status);
            ps.setLong(4, reportId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Update report status error", e);
        }
    }

    public List<Map<String, Object>> findSupportMessages() {
        List<Map<String, Object>> list = new ArrayList<>();

        String sql = "SELECT r.report_id, r.reporter_id, r.reason, r.status, " +
                     "r.moderator_id, r.created_at, r.resolved_at, " +
                     "u.username, u.full_name " +
                     "FROM Report r " +
                     "LEFT JOIN [User] u ON r.reporter_id = u.user_id " +
                     "WHERE r.post_id IS NULL AND r.comment_id IS NULL " +
                     "ORDER BY CASE WHEN r.status = 'RESOLVED' THEN 1 ELSE 0 END, " +
                     "r.created_at DESC, r.report_id DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String reason = rs.getString("reason");
                String fullName = rs.getString("full_name");
                String username = rs.getString("username");

                Map<String, Object> map = new HashMap<>();
                map.put("reportId", rs.getLong("report_id"));
                map.put("reporterId", rs.getLong("reporter_id"));
                map.put("username", username);
                map.put("fullName", fullName);
                map.put("reporterName", fullName != null && !fullName.isBlank() ? fullName : username);
                map.put("reason", reason);
                map.put("message", supportMessageText(reason));
                map.put("status", rs.getString("status"));

                long moderatorId = rs.getLong("moderator_id");
                map.put("moderatorId", rs.wasNull() ? null : moderatorId);
                map.put("createdAt", rs.getString("created_at"));
                map.put("resolvedAt", rs.getString("resolved_at"));
                list.add(map);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find support messages error", e);
        }
        return list;
    }

    public boolean markSupportResolved(long reportId, long moderatorId) {
        String sql = "UPDATE Report SET status='RESOLVED', moderator_id=?, resolved_at=GETDATE() " +
                     "WHERE report_id=? AND post_id IS NULL AND comment_id IS NULL";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, moderatorId);
            ps.setLong(2, reportId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Resolve support message error", e);
        }
    }

    private String supportMessageText(String reason) {
        if (reason == null) return "";

        String value = reason.trim();
        String prefix = "Hỗ trợ:";
        if (value.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return value.substring(prefix.length()).trim();
        }
        return value;
    }
}