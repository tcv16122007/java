package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.java.model.User;

public class UserDAO {

    // ==================== INSERT ====================
    public boolean insert(User user) {
        String sql = "INSERT INTO [User] (full_name, username, email, password, role, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPassword());
            ps.setString(5, user.getRole() != null ? user.getRole() : "USER");
            ps.setString(6, user.getStatus() != null ? user.getStatus() : "ACTIVE");
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Insert user error", e);
        }
    }

    // ==================== LOGIN ====================
    public User login(String username, String password) {
        String sql = "SELECT * FROM [User] WHERE username = ? AND password = ? AND status = 'ACTIVE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            throw new RuntimeException("Login error", e);
        }
        return null;
    }

    // ==================== FIND BY ID ====================
    public User findById(long id) {
        String sql = "SELECT * FROM [User] WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            throw new RuntimeException("Find user by id error", e);
        }
        return null;
    }

    // ==================== FIND BY USERNAME ====================
    public User findByUsername(String username) {
        String sql = "SELECT * FROM [User] WHERE username = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            throw new RuntimeException("Find by username error", e);
        }
        return null;
    }

    // ==================== FIND BY EMAIL ====================
    public User findByEmail(String email) {
        String sql = "SELECT * FROM [User] WHERE email = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            throw new RuntimeException("Find by email error", e);
        }
        return null;
    }

    // ==================== FIND ALL ====================
    public List<User> findAll() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM [User] ORDER BY created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Find all users error", e);
        }
        return list;
    }

    // ==================== SEARCH ====================
    public List<User> search(String keyword) {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM [User] WHERE username LIKE ? OR full_name LIKE ? OR email LIKE ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String like = "%" + keyword + "%";
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Search users error", e);
        }
        return list;
    }

    // ==================== UPDATE STATUS ====================
    public boolean updateStatus(long userId, String status) {
        String sql = "UPDATE [User] SET status = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Update status error", e);
        }
    }

    // ==================== UPDATE ROLE ====================
    public boolean updateRole(long userId, String role) {
        String sql = "UPDATE [User] SET role = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Update role error", e);
        }
    }

    // ==================== UPDATE PASSWORD ====================
    public boolean updatePassword(long userId, String newPassword) {
        String sql = "UPDATE [User] SET password = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPassword);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Update password error", e);
        }
    }

    // ==================== UPDATE PROFILE ====================
    public boolean updateProfile(User user) {
        String sql = "UPDATE [User] SET full_name = ?, email = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setLong(3, user.getUserId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Update profile error", e);
        }
    }

    // ==================== UPDATE AVATAR ====================
    public boolean updateAvatar(long userId, String avatarUrl) {
        String sql = "UPDATE [User] SET avatar = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, avatarUrl);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Update avatar error", e);
        }
    }

    // ==================== MAP ROW ====================
    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getLong("user_id"));
        u.setFullName(rs.getString("full_name"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setPassword(rs.getString("password"));
        u.setAvatar(rs.getString("avatar"));
        u.setRole(rs.getString("role"));
        u.setStatus(rs.getString("status"));
        u.setWarningCount(rs.getInt("warning_count"));
        u.setRestrictedUntil(rs.getString("restricted_until"));
        u.setCreatedAt(rs.getString("created_at"));
        return u;
    }
}