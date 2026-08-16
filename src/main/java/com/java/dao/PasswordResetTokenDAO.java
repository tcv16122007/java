package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import com.java.model.PasswordResetToken;
import com.java.util.TokenUtil;

public class PasswordResetTokenDAO {

    public boolean save(PasswordResetToken token) {
        String sql = "INSERT INTO PasswordResetToken (user_id, token, expiry_date) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, token.getUserId());
            ps.setString(2, TokenUtil.sha256(token.getToken()));
            ps.setTimestamp(3, new Timestamp(token.getExpiryDate().getTime()));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Save token error", e);
        }
    }

    public PasswordResetToken findByToken(String rawToken) {
        String sql = "SELECT * FROM PasswordResetToken WHERE token = ? AND used = 0 AND expiry_date > GETDATE()";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, TokenUtil.sha256(rawToken));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                PasswordResetToken token = new PasswordResetToken();
                token.setId(rs.getLong("id"));
                token.setUserId(rs.getLong("user_id"));
                token.setToken(rawToken);
                token.setExpiryDate(rs.getTimestamp("expiry_date"));
                token.setUsed(rs.getBoolean("used"));
                return token;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find token error", e);
        }
        return null;
    }

    public void markUsed(long id) {
        execute("UPDATE PasswordResetToken SET used = 1 WHERE id = ?", id);
    }

    public void deleteByUserId(long userId) {
        execute("DELETE FROM PasswordResetToken WHERE user_id = ?", userId);
    }

    public void deleteExpired() {
        execute("DELETE FROM PasswordResetToken WHERE used = 1 OR expiry_date <= GETDATE()");
    }

    private void execute(String sql, long... values) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) ps.setLong(i + 1, values[i]);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Password reset token update error", e);
        }
    }
}