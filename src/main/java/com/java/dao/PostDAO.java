package com.java.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.java.model.Post;
import com.java.model.Tag;

public class PostDAO {
    private static final String SELECT_BASE =
        "SELECT p.*, u.full_name AS authorName, u.avatar AS authorAvatar, " +
        "c.category_name AS categoryName, " +
        "(SELECT COUNT(*) FROM Interaction i WHERE i.post_id = p.post_id AND i.type = 'LIKE') AS likeCount, " +
        "(SELECT COUNT(*) FROM Comment cm WHERE cm.post_id = p.post_id AND cm.status = 'VISIBLE') AS commentCount " +
        "FROM Post p " +
        "LEFT JOIN [User] u ON p.author_id = u.user_id " +
        "LEFT JOIN Category c ON p.category_id = c.category_id ";

    public List<Post> findApproved() {
        return queryPosts(SELECT_BASE + "WHERE p.status = 'APPROVED' ORDER BY p.created_at DESC");
    }

    public List<Post> findByAuthor(long authorId) {
        return queryPosts(
            SELECT_BASE + "WHERE p.author_id = ? AND p.status <> 'DELETED' ORDER BY p.created_at DESC",
            authorId
        );
    }

    public List<Post> findApprovedByAuthor(long authorId) {
        return queryPosts(
            SELECT_BASE + "WHERE p.author_id = ? AND p.status = 'APPROVED' ORDER BY p.created_at DESC",
            authorId
        );
    }

    public List<Post> findPending() {
        return queryPosts(SELECT_BASE + "WHERE p.status = 'PENDING' ORDER BY p.created_at ASC");
    }

    public List<Post> findAllForAdmin() {
        return queryPosts(SELECT_BASE + "ORDER BY p.created_at DESC");
    }

    public Post findById(long postId) {
        List<Post> posts = queryPosts(SELECT_BASE + "WHERE p.post_id = ?", postId);
        return posts.isEmpty() ? null : posts.get(0);
    }

    public List<Post> findRelated(long postId, long categoryId, int limit) {
        String sql = SELECT_BASE +
            "WHERE p.status='APPROVED' AND p.category_id=? AND p.post_id<>? " +
            "ORDER BY p.view_count DESC, p.created_at DESC OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
        return queryPosts(sql, categoryId, postId, Math.max(1, Math.min(limit, 8)));
    }

    public Map<String, Object> getAuthorStats(long authorId) {
        String sql = "SELECT " +
            "(SELECT COUNT(*) FROM Post p WHERE p.author_id=? AND p.status='APPROVED') AS postCount, " +
            "(SELECT COALESCE(SUM(p.view_count),0) FROM Post p WHERE p.author_id=? AND p.status='APPROVED') AS viewCount, " +
            "(SELECT COUNT(*) FROM Interaction i INNER JOIN Post p ON i.post_id=p.post_id WHERE p.author_id=? AND p.status='APPROVED' AND i.type='LIKE') AS likeCount, " +
            "(SELECT COUNT(*) FROM Comment c INNER JOIN Post p ON c.post_id=p.post_id WHERE p.author_id=? AND p.status='APPROVED' AND c.status='VISIBLE') AS commentCount";
        Map<String, Object> result = new HashMap<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 1; i <= 4; i++) ps.setLong(i, authorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("postCount", rs.getInt("postCount"));
                    result.put("viewCount", rs.getLong("viewCount"));
                    result.put("likeCount", rs.getLong("likeCount"));
                    result.put("commentCount", rs.getLong("commentCount"));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Author stats error", ex);
        }
        return result;
    }

    public void incrementViewCount(long postId) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE Post SET view_count = view_count + 1 WHERE post_id = ? AND status='APPROVED'")) {
            ps.setLong(1, postId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("incrementViewCount error", e);
        }
    }

    public boolean insert(Post post) {
        return insert(post, post.getTags());
    }

    public boolean insert(Post post, List<Tag> tags) {
        String sql = "INSERT INTO Post (title, summary, content, thumbnail, author_id, category_id, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, post.getTitle());
                ps.setString(2, post.getSummary());
                ps.setString(3, post.getContent());
                ps.setString(4, post.getThumbnail());
                ps.setLong(5, post.getAuthorId());
                ps.setLong(6, post.getCategoryId());
                ps.setString(7, normalizeCreateStatus(post.getStatus()));
                if (ps.executeUpdate() == 0) {
                    conn.rollback();
                    return false;
                }
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }
                    post.setPostId(rs.getLong(1));
                }
                replaceTags(conn, post.getPostId(), tags);
                conn.commit();
                return true;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("insert post error", e);
        }
    }

    public boolean update(Post post) {
        return update(post, post.getTags(), false);
    }

    public boolean updateRejectedAndResubmit(Post post) {
        return update(post, post.getTags(), true);
    }

    public boolean updateDraftAndSubmit(Post post) {
        String sql = "UPDATE Post SET title=?, summary=?, content=?, thumbnail=?, category_id=?, " +
            "status='PENDING', moderator_id=NULL, rejection_reason=NULL, updated_at=GETDATE() " +
            "WHERE post_id=? AND author_id=? AND status='DRAFT'";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, post.getTitle());
                ps.setString(2, post.getSummary());
                ps.setString(3, post.getContent());
                ps.setString(4, post.getThumbnail());
                ps.setLong(5, post.getCategoryId());
                ps.setLong(6, post.getPostId());
                ps.setLong(7, post.getAuthorId());
                if (ps.executeUpdate() == 0) {
                    conn.rollback();
                    return false;
                }
                replaceTags(conn, post.getPostId(), post.getTags());
                conn.commit();
                return true;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("submit draft error", ex);
        }
    }

    private boolean update(Post post, List<Tag> tags, boolean resubmit) {
        String statusClause = resubmit
            ? ", status='PENDING', moderator_id=NULL, rejection_reason=NULL"
            : "";
        String conditions = resubmit
            ? " WHERE post_id=? AND author_id=? AND status='REJECTED' AND reject_count < 3"
            : " WHERE post_id=?";
        String sql = "UPDATE Post SET title=?, summary=?, content=?, thumbnail=?, category_id=?, updated_at=GETDATE()" +
            statusClause + conditions;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int index = 1;
                ps.setString(index++, post.getTitle());
                ps.setString(index++, post.getSummary());
                ps.setString(index++, post.getContent());
                ps.setString(index++, post.getThumbnail());
                ps.setLong(index++, post.getCategoryId());
                ps.setLong(index++, post.getPostId());
                if (resubmit) ps.setLong(index, post.getAuthorId());
                if (ps.executeUpdate() == 0) {
                    conn.rollback();
                    return false;
                }
                replaceTags(conn, post.getPostId(), tags);
                conn.commit();
                return true;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("update post error", ex);
        }
    }

    public boolean approve(long postId, long moderatorId) {
        return executeUpdate(
            "UPDATE Post SET status='APPROVED', moderator_id=?, rejection_reason=NULL, updated_at=GETDATE() " +
            "WHERE post_id=? AND status='PENDING'",
            moderatorId, postId
        );
    }

    public boolean reject(long postId, long moderatorId, String reason) {
        return executeUpdate(
            "UPDATE Post SET status='REJECTED', moderator_id=?, rejection_reason=?, reject_count=reject_count+1, " +
            "updated_at=GETDATE() WHERE post_id=? AND status='PENDING' AND reject_count < 3",
            moderatorId, reason, postId
        );
    }

    public boolean delete(long postId) {
        return executeUpdate("UPDATE Post SET status='DELETED', updated_at=GETDATE() WHERE post_id=?", postId);
    }

    public boolean restore(long postId) {
        return executeUpdate(
            "UPDATE Post SET status='PENDING', moderator_id=NULL, updated_at=GETDATE() WHERE post_id=? AND status='DELETED'",
            postId
        );
    }

    public boolean resubmit(long postId) {
        return executeUpdate(
            "UPDATE Post SET status='PENDING', moderator_id=NULL, rejection_reason=NULL, updated_at=GETDATE() " +
            "WHERE post_id=? AND status='REJECTED' AND reject_count < 3",
            postId
        );
    }

    public Map<String, Object> filterWithPaging(
        String categoryId,
        String tagId,
        String authorId,
        String keyword,
        String sort,
        int page,
        int limit
    ) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        int offset = (safePage - 1) * safeLimit;

        StringBuilder fromWhere = new StringBuilder(
            " FROM Post p " +
            "LEFT JOIN [User] u ON p.author_id=u.user_id " +
            "LEFT JOIN Category c ON p.category_id=c.category_id "
        );
        List<Object> params = new ArrayList<>();
        if (tagId != null && !tagId.isBlank()) {
            fromWhere.append("INNER JOIN Post_Tag pt ON p.post_id=pt.post_id ");
        }
        fromWhere.append("WHERE p.status='APPROVED' ");

        if (authorId != null && !authorId.isBlank()) {
            fromWhere.append("AND p.author_id=? ");
            params.add(Long.valueOf(authorId));
        }
        if (categoryId != null && !categoryId.isBlank()) {
            fromWhere.append("AND p.category_id=? ");
            params.add(Long.valueOf(categoryId));
        }
        if (tagId != null && !tagId.isBlank()) {
            fromWhere.append("AND pt.tag_id=? ");
            params.add(Long.valueOf(tagId));
        }
        if (keyword != null && !keyword.isBlank()) {
            fromWhere.append(
                "AND (" +
                "p.title LIKE ? OR " +
                "p.summary LIKE ? OR " +
                "p.content LIKE ? OR " +
                "u.full_name LIKE ? OR " +
                "u.username LIKE ? OR " +
                "c.category_name LIKE ? OR " +
                "EXISTS (" +
                "SELECT 1 FROM Post_Tag pts " +
                "INNER JOIN Tag t ON pts.tag_id=t.tag_id " +
                "WHERE pts.post_id=p.post_id AND t.status=1 AND t.tag_name LIKE ?" +
                ")" +
                ") "
            );
            String like = "%" + keyword.trim() + "%";
            for (int i = 0; i < 7; i++) params.add(like);
        }

        int total = countDistinct(fromWhere.toString(), params);
        String orderBy = switch (sort == null ? "newest" : sort) {
            case "most_viewed" -> " ORDER BY p.view_count DESC, p.created_at DESC";
            case "most_liked" -> " ORDER BY likeCount DESC, p.created_at DESC";
            case "most_commented" -> " ORDER BY commentCount DESC, p.created_at DESC";
            case "oldest" -> " ORDER BY p.created_at ASC";
            default -> " ORDER BY p.created_at DESC";
        };

        String dataSql =
            "SELECT DISTINCT p.*, u.full_name AS authorName, u.avatar AS authorAvatar, c.category_name AS categoryName, " +
            "(SELECT COUNT(*) FROM Interaction i WHERE i.post_id=p.post_id AND i.type='LIKE') AS likeCount, " +
            "(SELECT COUNT(*) FROM Comment cm WHERE cm.post_id=p.post_id AND cm.status='VISIBLE') AS commentCount" +
            fromWhere + orderBy + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        List<Post> posts = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(dataSql)) {
            int index = bind(ps, params, 1);
            ps.setInt(index++, offset);
            ps.setInt(index, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) posts.add(mapRow(rs));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Filter posts paging error", ex);
        }
        loadTags(posts);

        Map<String, Object> result = new HashMap<>();
        result.put("posts", posts);
        result.put("total", total);
        result.put("page", safePage);
        result.put("totalPages", Math.max(1, (int) Math.ceil((double) total / safeLimit)));
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<Post> filter(String categoryId, String tagId, String authorId, String keyword, String sort) {
        return (List<Post>) filterWithPaging(categoryId, tagId, authorId, keyword, sort, 1, 50).get("posts");
    }

    private int countDistinct(String fromWhere, List<Object> params) {
        String sql = "SELECT COUNT(DISTINCT p.post_id)" + fromWhere;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params, 1);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException ex) {
            throw new RuntimeException("Count posts error", ex);
        }
    }

    private List<Post> queryPosts(String sql, Object... params) {
        List<Post> posts = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) posts.add(mapRow(rs));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Query posts error", ex);
        }
        loadTags(posts);
        return posts;
    }

    private void loadTags(List<Post> posts) {
        if (posts == null || posts.isEmpty()) return;
        try (Connection conn = DBConnection.getConnection()) {
            for (Post post : posts) {
                post.setTags(findTagsByPostId(conn, post.getPostId()));
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Load post tags error", ex);
        }
    }

    private void replaceTags(Connection conn, long postId, List<Tag> tags) throws SQLException {
        try (PreparedStatement delete = conn.prepareStatement("DELETE FROM Post_Tag WHERE post_id=?")) {
            delete.setLong(1, postId);
            delete.executeUpdate();
        }
        if (tags == null || tags.isEmpty()) return;
        try (PreparedStatement insert = conn.prepareStatement("INSERT INTO Post_Tag(post_id, tag_id) VALUES (?,?)")) {
            for (Tag tag : tags) {
                if (tag == null || tag.getTagId() <= 0) continue;
                insert.setLong(1, postId);
                insert.setLong(2, tag.getTagId());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private List<Tag> findTagsByPostId(Connection conn, long postId) throws SQLException {
        List<Tag> tags = new ArrayList<>();
        String sql = "SELECT t.* FROM Tag t INNER JOIN Post_Tag pt ON t.tag_id=pt.tag_id " +
            "WHERE pt.post_id=? AND t.status=1 ORDER BY t.tag_name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, postId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Tag tag = new Tag();
                tag.setTagId(rs.getLong("tag_id"));
                tag.setTagName(rs.getString("tag_name"));
                tag.setDescription(rs.getString("description"));
                tag.setStatus(rs.getBoolean("status"));
                tags.add(tag);
            }
        }
        return tags;
    }

    private boolean executeUpdate(String sql, Object... params) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new RuntimeException("Post update error", ex);
        }
    }

    private int bind(PreparedStatement ps, List<Object> params, int start) throws SQLException {
        int index = start;
        for (Object param : params) ps.setObject(index++, param);
        return index;
    }

    private String normalizeCreateStatus(String status) {
        return "DRAFT".equals(status) ? "DRAFT" : "PENDING";
    }

    private Post mapRow(ResultSet rs) throws SQLException {
        Post p = new Post();
        p.setPostId(rs.getLong("post_id"));
        p.setTitle(rs.getString("title"));
        p.setSummary(rs.getString("summary"));
        p.setContent(rs.getString("content"));
        p.setThumbnail(rs.getString("thumbnail"));
        p.setStatus(rs.getString("status"));
        p.setViewCount(rs.getInt("view_count"));
        p.setRejectCount(rs.getInt("reject_count"));
        p.setRejectionReason(rs.getString("rejection_reason"));
        p.setCreatedAt(rs.getString("created_at"));
        p.setUpdatedAt(rs.getString("updated_at"));
        p.setAuthorId(rs.getLong("author_id"));
        p.setCategoryId(rs.getLong("category_id"));
        long moderatorId = rs.getLong("moderator_id");
        if (!rs.wasNull()) p.setModeratorId(moderatorId);
        p.setAuthorName(rs.getString("authorName"));
        try { p.setAuthorAvatar(rs.getString("authorAvatar")); } catch (SQLException ignored) { }
        p.setCategoryName(rs.getString("categoryName"));
        try { p.setLikeCount(rs.getInt("likeCount")); } catch (SQLException ignored) { }
        try { p.setCommentCount(rs.getInt("commentCount")); } catch (SQLException ignored) { }
        return p;
    }
}