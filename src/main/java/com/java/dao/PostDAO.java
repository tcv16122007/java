package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.java.model.Post;
import com.java.model.Tag;

public class PostDAO {

    // ==== Tìm bài viết đã được duyệt (cho guest/user) ====
    public List<Post> findApproved() {
        List<Post> list = new ArrayList<>();
        String sql = "SELECT p.*, u.full_name as authorName, c.category_name as categoryName " +
                     "FROM Post p " +
                     "LEFT JOIN [User] u ON p.author_id = u.user_id " +
                     "LEFT JOIN Category c ON p.category_id = c.category_id " +
                     "WHERE p.status = 'APPROVED' " +
                     "ORDER BY p.created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRowWithNames(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findApproved error", e);
        }
        return list;
    }

    // ==== Tìm bài viết theo tác giả (chỉ lấy bài chưa bị xóa) ====
    public List<Post> findByAuthor(long authorId) {
        List<Post> list = new ArrayList<>();
        String sql = "SELECT p.*, u.full_name as authorName, c.category_name as categoryName " +
                     "FROM Post p " +
                     "LEFT JOIN [User] u ON p.author_id = u.user_id " +
                     "LEFT JOIN Category c ON p.category_id = c.category_id " +
                     "WHERE p.author_id = ? AND p.status != 'DELETED' " +
                     "ORDER BY p.created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, authorId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRowWithNames(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByAuthor error", e);
        }
        return list;
    }

    // ==== Tìm bài viết chờ duyệt (cho moderator/admin) ====
    public List<Post> findPending() {
        List<Post> list = new ArrayList<>();
        String sql = "SELECT p.*, u.full_name as authorName, c.category_name as categoryName " +
                     "FROM Post p " +
                     "LEFT JOIN [User] u ON p.author_id = u.user_id " +
                     "LEFT JOIN Category c ON p.category_id = c.category_id " +
                     "WHERE p.status = 'PENDING' " +
                     "ORDER BY p.created_at ASC";
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRowWithNames(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findPending error", e);
        }
        return list;
    }

    // ==== Tìm bài viết theo ID (kèm thông tin tác giả, danh mục, like, comment) ====
    public Post findById(long postId) {
        String sql = "SELECT p.*, u.full_name as authorName, c.category_name as categoryName, " +
                     "(SELECT COUNT(*) FROM Interaction i WHERE i.post_id = p.post_id AND i.type = 'LIKE') as likeCount, " +
                     "(SELECT COUNT(*) FROM Comment cmt WHERE cmt.post_id = p.post_id AND cmt.status = 'VISIBLE') as commentCount " +
                     "FROM Post p " +
                     "LEFT JOIN [User] u ON p.author_id = u.user_id " +
                     "LEFT JOIN Category c ON p.category_id = c.category_id " +
                     "WHERE p.post_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, postId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Post p = mapRowWithNames(rs);
                p.setTags(findTagsByPostId(postId));
                return p;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findById error", e);
        }
        return null;
    }

    // ==== Lấy tags của bài viết ====
    private List<Tag> findTagsByPostId(long postId) {
        List<Tag> list = new ArrayList<>();
        String sql = "SELECT t.* FROM Tag t INNER JOIN Post_Tag pt ON t.tag_id = pt.tag_id WHERE pt.post_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, postId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Tag t = new Tag();
                t.setTagId(rs.getLong("tag_id"));
                t.setTagName(rs.getString("tag_name"));
                t.setDescription(rs.getString("description"));
                t.setStatus(rs.getBoolean("status"));
                list.add(t);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // ==== Tăng view count ====
    public void incrementViewCount(long postId) {
        String sql = "UPDATE Post SET view_count = view_count + 1 WHERE post_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, postId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("incrementViewCount error", e);
        }
    }

    // ==== Thêm bài viết mới (trạng thái PENDING) ====
    public boolean insert(Post post) {
        String sql = "INSERT INTO Post (title, summary, content, thumbnail, author_id, category_id, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, post.getTitle());
            ps.setString(2, post.getSummary());
            ps.setString(3, post.getContent());
            ps.setString(4, post.getThumbnail());
            ps.setLong(5, post.getAuthorId());
            ps.setLong(6, post.getCategoryId());
            ps.setString(7, "PENDING");
            int affected = ps.executeUpdate();
            if (affected > 0) {
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    long id = rs.getLong(1);
                    post.setPostId(id);
                }
                return true;
            }
        } catch (SQLException e) {
            throw new RuntimeException("insert post error", e);
        }
        return false;
    }

    // ==== Duyệt bài (moderator) ====
    public boolean approve(long postId, long moderatorId) {
        String sql = "UPDATE Post SET status = 'APPROVED', moderator_id = ? WHERE post_id = ? AND status = 'PENDING'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, moderatorId);
            ps.setLong(2, postId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("approve error", e);
        }
    }

    // ==== Từ chối bài (moderator) ====
    public boolean reject(long postId, long moderatorId) {
        String sql = "UPDATE Post SET status = 'REJECTED', moderator_id = ?, reject_count = reject_count + 1 WHERE post_id = ? AND status = 'PENDING'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, moderatorId);
            ps.setLong(2, postId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("reject error", e);
        }
    }

    // ==== Xóa bài (soft delete) ====
    public boolean delete(long postId) {
        String sql = "UPDATE Post SET status = 'DELETED' WHERE post_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, postId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("delete error", e);
        }
    }

    // ==== Gửi lại bài (từ REJECTED sang PENDING) ====
    public boolean resubmit(long postId) {
        String sql = "UPDATE Post SET status = 'PENDING', moderator_id = NULL WHERE post_id = ? AND status = 'REJECTED'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, postId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("resubmit error", e);
        }
    }

    // ==== Lọc bài viết ====
    public List<Post> filter(String categoryId, String tagId, String authorId, String keyword, String sort) {
        List<Post> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT p.*, u.full_name as authorName, c.category_name as categoryName, " +
            "(SELECT COUNT(*) FROM Interaction i WHERE i.post_id = p.post_id AND i.type = 'LIKE') as likeCount, " +
            "(SELECT COUNT(*) FROM Comment cmt WHERE cmt.post_id = p.post_id AND cmt.status = 'VISIBLE') as commentCount " +
            "FROM Post p LEFT JOIN [User] u ON p.author_id = u.user_id LEFT JOIN Category c ON p.category_id = c.category_id "
        );
        if (tagId != null && !tagId.isEmpty()) {
            sql.append("INNER JOIN Post_Tag pt ON p.post_id = pt.post_id ");
        }
        sql.append("WHERE p.status = 'APPROVED' ");
        List<Object> params = new ArrayList<>();

        if (categoryId != null && !categoryId.isEmpty()) {
            sql.append("AND p.category_id = ? ");
            params.add(Long.parseLong(categoryId));
        }
        if (tagId != null && !tagId.isEmpty()) {
            sql.append("AND pt.tag_id = ? ");
            params.add(Long.parseLong(tagId));
        }
        if (authorId != null && !authorId.isEmpty()) {
            sql.append("AND p.author_id = ? ");
            params.add(Long.parseLong(authorId));
        }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (p.title LIKE ? OR p.summary LIKE ?) ");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }

        if ("most_viewed".equals(sort)) {
            sql.append("ORDER BY p.view_count DESC");
        } else if ("most_liked".equals(sort)) {
            sql.append("ORDER BY likeCount DESC");
        } else if ("most_commented".equals(sort)) {
            sql.append("ORDER BY commentCount DESC");
        } else {
            sql.append("ORDER BY p.created_at DESC");
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i+1, params.get(i));
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRowWithNames(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Filter posts error", e);
        }
        return list;
    }

    // ==== Hàm map dòng kết quả (có tên tác giả, danh mục, like, comment) ====
    private Post mapRowWithNames(ResultSet rs) throws SQLException {
        Post p = new Post();
        p.setPostId(rs.getLong("post_id"));
        p.setTitle(rs.getString("title"));
        p.setSummary(rs.getString("summary"));
        p.setContent(rs.getString("content"));
        p.setThumbnail(rs.getString("thumbnail"));
        p.setStatus(rs.getString("status"));
        p.setViewCount(rs.getInt("view_count"));
        p.setRejectCount(rs.getInt("reject_count"));
        p.setCreatedAt(rs.getString("created_at"));
        p.setUpdatedAt(rs.getString("updated_at"));
        p.setAuthorId(rs.getLong("author_id"));
        p.setCategoryId(rs.getLong("category_id"));
        Long modId = rs.getLong("moderator_id");
        if (!rs.wasNull()) p.setModeratorId(modId);
        p.setAuthorName(rs.getString("authorName"));
        p.setCategoryName(rs.getString("categoryName"));
        try {
            p.setLikeCount(rs.getInt("likeCount"));
        } catch (SQLException ignore) { p.setLikeCount(0); }
        try {
            p.setCommentCount(rs.getInt("commentCount"));
        } catch (SQLException ignore) { p.setCommentCount(0); }
        return p;
    }
}