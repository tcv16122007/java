package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.java.model.Notification;

public class NotificationDAO {

    public List<Notification> findByUserId(long userId, int limit) {
        List<Notification> list = new ArrayList<>();
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String sql = "SELECT TOP " + safeLimit + " notification_id, user_id, type, title, message, link, is_read, created_at " +
                     "FROM Notification WHERE user_id = ? ORDER BY created_at DESC, notification_id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find notifications error", e);
        }
        return list;
    }

    public int countUnread(long userId) {
        String sql = "SELECT COUNT(*) FROM Notification WHERE user_id = ? AND is_read = 0";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Count unread notifications error", e);
        }
    }

    public boolean insert(long userId, String type, String title, String message, String link) {
        String sql = "INSERT INTO Notification (user_id, type, title, message, link) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, type);
            ps.setString(3, title);
            ps.setString(4, message);
            ps.setString(5, link);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Insert notification error", e);
        }
    }

    public boolean markRead(long notificationId, long userId) {
        String sql = "UPDATE Notification SET is_read = 1 WHERE notification_id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, notificationId);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Mark notification read error", e);
        }
    }

    public boolean markAllRead(long userId) {
        String sql = "UPDATE Notification SET is_read = 1 WHERE user_id = ? AND is_read = 0";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            return ps.executeUpdate() >= 0;
        } catch (SQLException e) {
            throw new RuntimeException("Mark all notifications read error", e);
        }
    }

    private Notification mapRow(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setNotificationId(rs.getLong("notification_id"));
        n.setUserId(rs.getLong("user_id"));
        n.setType(rs.getString("type"));
        n.setTitle(rs.getString("title"));
        n.setMessage(rs.getString("message"));
        n.setLink(rs.getString("link"));
        n.setRead(rs.getBoolean("is_read"));
        n.setCreatedAt(rs.getString("created_at"));
        return n;
    }
}