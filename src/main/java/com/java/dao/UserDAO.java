package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.java.model.User;
import com.java.util.PasswordUtil;

public class UserDAO {

    public boolean insert(User user) {
        PasswordUtil.validate(user.getPassword());
        String sql = "INSERT INTO [User] (full_name, username, email, password, role, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getEmail());
            ps.setString(4, PasswordUtil.hash(user.getPassword()));
            ps.setString(5, user.getRole() != null ? user.getRole() : "USER");
            ps.setString(6, user.getStatus() != null ? user.getStatus() : "ACTIVE");
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Insert user error", e);
        }
    }

    public User login(String username, String password) {
        String sql = "SELECT * FROM [User] WHERE username = ? AND status = 'ACTIVE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;

            String storedPassword = rs.getString("password");
            if (!PasswordUtil.verify(password, storedPassword)) return null;

            User user = mapRow(rs);
            if (!PasswordUtil.isBcrypt(storedPassword)) {
                upgradeLegacyPassword(conn, user.getUserId(), password);
            }
            return user;
        } catch (SQLException e) {
            throw new RuntimeException("Login error", e);
        }
    }

    public boolean verifyPassword(long userId, String rawPassword) {
        String sql = "SELECT password FROM [User] WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && PasswordUtil.verify(rawPassword, rs.getString("password"));
        } catch (SQLException e) {
            throw new RuntimeException("Verify password error", e);
        }
    }

    private void upgradeLegacyPassword(Connection conn, long userId, String rawPassword) throws SQLException {
        String sql = "UPDATE [User] SET password = ? WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, PasswordUtil.hash(rawPassword));
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    public User findById(long id) {
        return findOne("SELECT * FROM [User] WHERE user_id = ?", id);
    }

    public User findByUsername(String username) {
        return findOne("SELECT * FROM [User] WHERE username = ?", username);
    }

    public User findByEmail(String email) {
        return findOne("SELECT * FROM [User] WHERE email = ?", email);
    }

    private User findOne(String sql, Object value) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, value);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            throw new RuntimeException("Find user error", e);
        }
        return null;
    }

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

    public List<User> findActiveModeratorsAndAdmins() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM [User] WHERE status = 'ACTIVE' AND role IN ('MODERATOR', 'ADMIN') ORDER BY user_id";
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Find moderators and admins error", e);
        }
        return list;
    }

    public List<User> search(String keyword) {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM [User] WHERE username LIKE ? OR full_name LIKE ? OR email LIKE ? ORDER BY created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
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

    public boolean updateStatus(long userId, String status) {
        String sql = "UPDATE [User] SET status = ?, " +
                     "warning_count = CASE WHEN ? = 'ACTIVE' THEN 0 ELSE warning_count END, " +
                     "restricted_until = CASE WHEN ? = 'ACTIVE' THEN NULL ELSE restricted_until END " +
                     "WHERE user_id = ?";
        return executeUpdate(sql, status, status, status, userId);
    }

    public User addReportStrike(long userId) {
        String sql = "UPDATE [User] SET " +
                     "warning_count = warning_count + 1, " +
                     "status = CASE WHEN status = 'ACTIVE' AND warning_count + 1 >= 3 THEN 'RESTRICTED' ELSE status END, " +
                     "restricted_until = CASE WHEN status = 'ACTIVE' AND warning_count + 1 >= 3 THEN NULL ELSE restricted_until END " +
                     "WHERE user_id = ? AND role = 'USER'";
        executeUpdate(sql, userId);
        return findById(userId);
    }

    public boolean updateRole(long userId, String role) {
        return executeUpdate("UPDATE [User] SET role = ? WHERE user_id = ?", role, userId);
    }

    public boolean updatePassword(long userId, String newPassword) {
        PasswordUtil.validate(newPassword);
        return executeUpdate("UPDATE [User] SET password = ? WHERE user_id = ?", PasswordUtil.hash(newPassword), userId);
    }

    public boolean updateProfile(User user) {
        return executeUpdate(
            "UPDATE [User] SET full_name = ?, email = ? WHERE user_id = ?",
            user.getFullName(), user.getEmail(), user.getUserId()
        );
    }

    public boolean updateAvatar(long userId, String avatarUrl) {
        return executeUpdate("UPDATE [User] SET avatar = ? WHERE user_id = ?", avatarUrl, userId);
    }

    private boolean executeUpdate(String sql, Object... params) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Update user error", e);
        }
    }

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