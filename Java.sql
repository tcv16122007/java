-- ====================================================
-- 1. TẠO DATABASE
-- ====================================================
DROP DATABASE IF EXISTS BlogSE;
CREATE DATABASE BlogSE;
GO
USE BlogSE;
GO

-- ====================================================
-- 2. BẢNG USER
-- ====================================================
CREATE TABLE [User] (
    user_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    full_name NVARCHAR(100) NOT NULL,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    avatar VARCHAR(255) NULL,
    role VARCHAR(20) DEFAULT 'USER' CHECK (role IN ('USER', 'MODERATOR', 'ADMIN')),
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'BLOCKED', 'RESTRICTED')),
    warning_count INT DEFAULT 0,
    restricted_until DATETIME NULL,
    created_at DATETIME DEFAULT GETDATE()
);

-- ====================================================
-- 3. BẢNG CATEGORY
-- ====================================================
CREATE TABLE Category (
    category_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    category_name NVARCHAR(100) NOT NULL,
    description NVARCHAR(255) NULL,
    status BIT DEFAULT 1
);

-- ====================================================
-- 4. BẢNG POST
-- ====================================================
CREATE TABLE Post (
    post_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    title NVARCHAR(255) NOT NULL,
    summary NVARCHAR(500) NULL,
    content NVARCHAR(MAX) NOT NULL,
    thumbnail VARCHAR(255) NULL,
    status VARCHAR(20) DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'DELETED')),
    view_count INT DEFAULT 0,
    reject_count INT DEFAULT 0,
    created_at DATETIME DEFAULT GETDATE(),
    updated_at DATETIME NULL,
    author_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    moderator_id BIGINT NULL,
    FOREIGN KEY (author_id) REFERENCES [User](user_id),
    FOREIGN KEY (category_id) REFERENCES Category(category_id),
    FOREIGN KEY (moderator_id) REFERENCES [User](user_id)
);

-- ====================================================
-- 5. BẢNG COMMENT
-- ====================================================
CREATE TABLE Comment (
    comment_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    content NVARCHAR(1000) NOT NULL,
    created_at DATETIME DEFAULT GETDATE(),
    status VARCHAR(20) DEFAULT 'VISIBLE' CHECK (status IN ('VISIBLE', 'HIDDEN', 'DELETED')),
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES [User](user_id),
    FOREIGN KEY (post_id) REFERENCES Post(post_id) ON DELETE CASCADE
);

-- ====================================================
-- 6. BẢNG TAG
-- ====================================================
CREATE TABLE Tag (
    tag_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    tag_name NVARCHAR(100) NOT NULL UNIQUE,
    description NVARCHAR(255) NULL,
    status BIT DEFAULT 1,
    created_at DATETIME DEFAULT GETDATE()
);

-- ====================================================
-- 7. BẢNG POST_TAG
-- ====================================================
CREATE TABLE Post_Tag (
    post_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (post_id, tag_id),
    FOREIGN KEY (post_id) REFERENCES Post(post_id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id) REFERENCES Tag(tag_id)
);

-- ====================================================
-- 8. BẢNG INTERACTION (like/unlike)
-- ====================================================
CREATE TABLE Interaction (
    interaction_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('LIKE', 'UNLIKE')),
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (user_id) REFERENCES [User](user_id),
    FOREIGN KEY (post_id) REFERENCES Post(post_id) ON DELETE CASCADE,
    CONSTRAINT UQ_User_Post UNIQUE (user_id, post_id)
);

-- ====================================================
-- 9. BẢNG USER_SETTINGS
-- ====================================================
CREATE TABLE User_Settings (
    setting_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    theme VARCHAR(20) DEFAULT 'light',
    primary_color VARCHAR(7) DEFAULT '#0d6efd',
    secondary_color VARCHAR(7) DEFAULT '#6c757d',
    background_color VARCHAR(7) DEFAULT '#ffffff',
    font_family VARCHAR(100) DEFAULT 'system-ui, -apple-system, sans-serif',
    cover_image VARCHAR(255) NULL,
    custom_css TEXT NULL,
    updated_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (user_id) REFERENCES [User](user_id) ON DELETE CASCADE
);

-- ====================================================
-- 10. BẢNG BOOKMARK
-- ====================================================
CREATE TABLE Bookmark (
    bookmark_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (user_id) REFERENCES [User](user_id),
    FOREIGN KEY (post_id) REFERENCES Post(post_id) ON DELETE CASCADE,
    CONSTRAINT UQ_User_Post_Bookmark UNIQUE (user_id, post_id)
);

-- ====================================================
-- 11. BẢNG REPORT
-- ====================================================
CREATE TABLE Report (
    report_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    reporter_id BIGINT NOT NULL,
    post_id BIGINT NULL,
    comment_id BIGINT NULL,
    reason NVARCHAR(255) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'RESOLVED', 'REJECTED')),
    moderator_id BIGINT NULL,
    created_at DATETIME DEFAULT GETDATE(),
    resolved_at DATETIME NULL,
    FOREIGN KEY (reporter_id) REFERENCES [User](user_id),
    FOREIGN KEY (post_id) REFERENCES Post(post_id),
    FOREIGN KEY (comment_id) REFERENCES Comment(comment_id),
    FOREIGN KEY (moderator_id) REFERENCES [User](user_id)
);

-- ====================================================
-- 12. BẢNG WARNING (cảnh cáo moderator)
-- ====================================================
CREATE TABLE Warning (
    warning_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    moderator_id BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,
    reason NVARCHAR(255) NOT NULL,
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (moderator_id) REFERENCES [User](user_id),
    FOREIGN KEY (admin_id) REFERENCES [User](user_id)
);

-- ====================================================
-- 13. BẢNG RESTRICTION (hạn chế moderator)
-- ====================================================
CREATE TABLE Restriction (
    restriction_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    moderator_id BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,
    reason NVARCHAR(255) NOT NULL,
    until_date DATETIME NOT NULL,
    is_active BIT DEFAULT 1,
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (moderator_id) REFERENCES [User](user_id),
    FOREIGN KEY (admin_id) REFERENCES [User](user_id)
);

-- ====================================================
-- 14. BẢNG PASSWORD_RESET_TOKEN (quên mật khẩu)
-- ====================================================
CREATE TABLE PasswordResetToken (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date DATETIME NOT NULL,
    used BIT DEFAULT 0,
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (user_id) REFERENCES [User](user_id) ON DELETE CASCADE
);

-- ====================================================
-- 15. DỮ LIỆU MẪU
-- ====================================================
INSERT INTO [User] (full_name, username, email, password, role) VALUES
(N'Quản trị viên', 'admin', 'admin@blog.com', 'admin123', 'ADMIN'),
(N'Nguyễn Văn Mod', 'moderator', 'mod@blog.com', 'mod123', 'MODERATOR'),
(N'Trần Văn User', 'user1', 'user1@blog.com', 'user123', 'USER');

INSERT INTO Category (category_name, description) VALUES
(N'Kỹ thuật phần mềm', N'Quy trình, phương pháp phát triển'),
(N'Lập trình', N'Ngôn ngữ, framework, thư viện'),
(N'DevOps', N'CI/CD, Cloud, Container, Automation');

INSERT INTO Tag (tag_name) VALUES ('Java'), ('C++'), ('C#'), ('Python'), ('Pascal'), ('HTML & CSS'), ('Database');

INSERT INTO Post (title, summary, content, author_id, category_id, status) VALUES
(N'Giới thiệu Scrum', N'Tổng quan về Scrum trong phát triển phần mềm', N'<p>Scrum là một framework phát triển phần mềm theo phương pháp Agile, giúp đội ngũ làm việc hiệu quả và linh hoạt.</p>', 1, 1, 'APPROVED'),
(N'Clean Code - Nguyên tắc viết code sạch', N'Những nguyên tắc cơ bản để viết code dễ bảo trì', N'<p>Clean Code giúp code dễ đọc, dễ hiểu, dễ bảo trì và ít lỗi. Các nguyên tắc bao gồm đặt tên rõ ràng, hàm ngắn gọn, và không lặp lại code.</p>', 3, 2, 'APPROVED');

INSERT INTO User_Settings (user_id) VALUES (1), (2), (3);