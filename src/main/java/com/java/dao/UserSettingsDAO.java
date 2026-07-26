package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.java.model.UserSettings;

public class UserSettingsDAO {

    public UserSettings findByUserId(long userId) {
        String sql = "SELECT * FROM User_Settings WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                UserSettings s = new UserSettings();
                s.setUserId(rs.getLong("user_id"));
                s.setTheme(rs.getString("theme"));
                s.setPrimaryColor(rs.getString("primary_color"));
                s.setSecondaryColor(rs.getString("secondary_color"));
                s.setBackgroundColor(rs.getString("background_color"));
                s.setTextColor(rs.getString("text_color"));
                s.setFontFamily(rs.getString("font_family"));
                s.setCoverImage(rs.getString("cover_image"));
                s.setCustomCss(rs.getString("custom_css"));
                return s;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Find settings error", e);
        }
        return null;
    }

    public boolean upsert(UserSettings s) {
        UserSettings existing = findByUserId(s.getUserId());
        String sql;
        if (existing != null) {
            sql = "UPDATE User_Settings SET theme=?, primary_color=?, secondary_color=?, background_color=?, text_color=?, font_family=?, cover_image=?, custom_css=?, updated_at=GETDATE() WHERE user_id=?";
        } else {
            sql = "INSERT INTO User_Settings (user_id, theme, primary_color, secondary_color, background_color, text_color, font_family, cover_image, custom_css) VALUES (?,?,?,?,?,?,?,?,?)";
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            if (existing != null) {
                ps.setString(idx++, s.getTheme());
                ps.setString(idx++, s.getPrimaryColor());
                ps.setString(idx++, s.getSecondaryColor());
                ps.setString(idx++, s.getBackgroundColor());
                ps.setString(idx++, s.getTextColor());
                ps.setString(idx++, s.getFontFamily());
                ps.setString(idx++, s.getCoverImage());
                ps.setString(idx++, s.getCustomCss());
                ps.setLong(idx++, s.getUserId());
            } else {
                ps.setLong(idx++, s.getUserId());
                ps.setString(idx++, s.getTheme());
                ps.setString(idx++, s.getPrimaryColor());
                ps.setString(idx++, s.getSecondaryColor());
                ps.setString(idx++, s.getBackgroundColor());
                ps.setString(idx++, s.getTextColor());
                ps.setString(idx++, s.getFontFamily());
                ps.setString(idx++, s.getCoverImage());
                ps.setString(idx++, s.getCustomCss());
            }
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Upsert settings error", e);
        }
    }
}