package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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
}