package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import com.java.model.PasswordResetToken;

public class PasswordResetTokenDAO {

    public boolean save(PasswordResetToken token) {
        String sql = "INSERT INTO PasswordResetToken (user_id, token, expiry_date) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, token.getUserId());
            ps.setString(2, token.getToken());
            ps.setTimestamp(3, new Timestamp(token.getExpiryDate().getTime()));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Save token error", e);
        }
    }

    public PasswordResetToken findByToken(String token) {
        String sql = "SELECT * FROM PasswordResetToken WHERE token = ? AND used = 0 AND expiry_date > GETDATE()";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                PasswordResetToken t = new PasswordResetToken();
                t.setId(rs.getLong("id"));
                t.setUserId(rs.getLong("user_id"));
                t.setToken(rs.getString("token"));
                t.setExpiryDate(rs.getTimestamp("expiry_date"));
                t.setUsed(rs.getBoolean("used"));
                return t;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find token error", e);
        }
        return null;
    }

    public void markUsed(long id) {
        String sql = "UPDATE PasswordResetToken SET used = 1 WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Mark token used error", e);
        }
    }

    public void deleteByUserId(long userId) {
        String sql = "DELETE FROM PasswordResetToken WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Delete tokens error", e);
        }
    }
}