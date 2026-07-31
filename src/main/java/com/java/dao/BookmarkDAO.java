package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.java.model.Post;

public class BookmarkDAO {

    public boolean addBookmark(long userId, long postId) {
        String sql = "INSERT INTO Bookmark (user_id, post_id) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, postId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Add bookmark error", e);
        }
    }

    public boolean removeBookmark(long userId, long postId) {
        String sql = "DELETE FROM Bookmark WHERE user_id = ? AND post_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, postId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Remove bookmark error", e);
        }
    }

    public boolean isBookmarked(long userId, long postId) {
        String sql = "SELECT COUNT(*) FROM Bookmark WHERE user_id = ? AND post_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, postId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Check bookmark error", e);
        }
        return false;
    }

    public List<Post> getBookmarksByUser(long userId) {
        List<Post> list = new ArrayList<>();
        String sql = "SELECT p.*, u.full_name as authorName, c.category_name as categoryName " +
                     "FROM Bookmark b " +
                     "INNER JOIN Post p ON b.post_id = p.post_id " +
                     "LEFT JOIN [User] u ON p.author_id = u.user_id " +
                     "LEFT JOIN Category c ON p.category_id = c.category_id " +
                     "WHERE b.user_id = ? AND p.status = 'APPROVED' " +
                     "ORDER BY b.created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Post p = new Post();
                p.setPostId(rs.getLong("post_id"));
                p.setTitle(rs.getString("title"));
                p.setSummary(rs.getString("summary"));
                p.setContent(rs.getString("content"));
                p.setThumbnail(rs.getString("thumbnail"));
                p.setStatus(rs.getString("status"));
                p.setViewCount(rs.getInt("view_count"));
                p.setCreatedAt(rs.getString("created_at"));
                p.setAuthorName(rs.getString("authorName"));
                p.setCategoryName(rs.getString("categoryName"));
                list.add(p);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Get bookmarks error", e);
        }
        return list;
    }
}