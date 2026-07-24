package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionDAO {
    public boolean like(long userId, long postId) {
        String delSql = "DELETE FROM Interaction WHERE user_id = ? AND post_id = ? AND type = 'UNLIKE'";
        String insSql = "INSERT INTO Interaction (user_id, post_id, type) VALUES (?, ?, 'LIKE')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement psDel = conn.prepareStatement(delSql);
             PreparedStatement psIns = conn.prepareStatement(insSql)) {
            psDel.setLong(1, userId);
            psDel.setLong(2, postId);
            psDel.executeUpdate();
            psIns.setLong(1, userId);
            psIns.setLong(2, postId);
            return psIns.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Like error", e);
        }
    }

    public boolean unlike(long userId, long postId) {
        String sql = "DELETE FROM Interaction WHERE user_id = ? AND post_id = ? AND type = 'LIKE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, postId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Unlike error", e);
        }
    }

    public int countLikes(long postId) {
        String sql = "SELECT COUNT(*) FROM Interaction WHERE post_id = ? AND type = 'LIKE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, postId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException("Count likes error", e);
        }
        return 0;
    }

    public boolean hasLiked(long userId, long postId) {
        String sql = "SELECT COUNT(*) FROM Interaction WHERE user_id = ? AND post_id = ? AND type = 'LIKE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, postId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Check liked error", e);
        }
        return false;
    }
}