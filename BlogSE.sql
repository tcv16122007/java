USE master;
GO

CREATE DATABASE BlogSE;
GO

USE BlogSE;
GO

CREATE TABLE [User] (
    user_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    full_name NVARCHAR(100) NOT NULL,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    avatar VARCHAR(255) NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER'
        CHECK (role IN ('USER', 'MODERATOR', 'ADMIN')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'BLOCKED', 'RESTRICTED')),
    warning_count INT NOT NULL DEFAULT 0,
    restricted_until DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT GETDATE()
);
GO

CREATE TABLE Category (
    category_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    category_name NVARCHAR(100) NOT NULL UNIQUE,
    description NVARCHAR(255) NULL,
    status BIT NOT NULL DEFAULT 1
);
GO

CREATE TABLE Post (
    post_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    title NVARCHAR(255) NOT NULL,
    summary NVARCHAR(500) NULL,
    content NVARCHAR(MAX) NOT NULL,
    thumbnail VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'DELETED')),
    view_count INT NOT NULL DEFAULT 0,
    reject_count INT NOT NULL DEFAULT 0,
    rejection_reason NVARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),
    updated_at DATETIME NULL,
    author_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    moderator_id BIGINT NULL,

    CONSTRAINT FK_Post_User_Author
        FOREIGN KEY (author_id) REFERENCES [User](user_id),

    CONSTRAINT FK_Post_Category
        FOREIGN KEY (category_id) REFERENCES Category(category_id),

    CONSTRAINT FK_Post_User_Moderator
        FOREIGN KEY (moderator_id) REFERENCES [User](user_id)
);
GO

CREATE TABLE Comment (
    comment_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    content NVARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),
    status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE'
        CHECK (status IN ('VISIBLE', 'HIDDEN', 'DELETED')),
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    parent_id BIGINT NULL,

    CONSTRAINT FK_Comment_User
        FOREIGN KEY (user_id) REFERENCES [User](user_id),

    CONSTRAINT FK_Comment_Post
        FOREIGN KEY (post_id) REFERENCES Post(post_id)
        ON DELETE CASCADE,

    CONSTRAINT FK_Comment_Parent
        FOREIGN KEY (parent_id) REFERENCES Comment(comment_id)
);
GO

CREATE TABLE Tag (
    tag_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    tag_name NVARCHAR(100) NOT NULL UNIQUE,
    description NVARCHAR(255) NULL,
    status BIT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT GETDATE()
);
GO

CREATE TABLE Post_Tag (
    post_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,

    CONSTRAINT PK_Post_Tag
        PRIMARY KEY (post_id, tag_id),

    CONSTRAINT FK_PostTag_Post
        FOREIGN KEY (post_id) REFERENCES Post(post_id)
        ON DELETE CASCADE,

    CONSTRAINT FK_PostTag_Tag
        FOREIGN KEY (tag_id) REFERENCES Tag(tag_id)
);
GO

CREATE TABLE Interaction (
    interaction_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL
        CHECK (type IN ('LIKE', 'UNLIKE')),
    created_at DATETIME NOT NULL DEFAULT GETDATE(),

    CONSTRAINT FK_Interaction_User
        FOREIGN KEY (user_id) REFERENCES [User](user_id),

    CONSTRAINT FK_Interaction_Post
        FOREIGN KEY (post_id) REFERENCES Post(post_id)
        ON DELETE CASCADE,

    CONSTRAINT UQ_User_Post
        UNIQUE (user_id, post_id)
);
GO

CREATE TABLE User_Settings (
    setting_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    theme VARCHAR(20) NOT NULL DEFAULT 'light',
    primary_color VARCHAR(7) NOT NULL DEFAULT '#667eea',
    secondary_color VARCHAR(7) NOT NULL DEFAULT '#764ba2',
    background_color VARCHAR(7) NOT NULL DEFAULT '#f4f6f9',
    text_color VARCHAR(7) NOT NULL DEFAULT '#1a1a2e',
    font_family VARCHAR(100) NOT NULL DEFAULT 'system-ui',
    cover_image VARCHAR(255) NULL,
    custom_css NVARCHAR(MAX) NULL,
    updated_at DATETIME NOT NULL DEFAULT GETDATE(),

    CONSTRAINT FK_Settings_User
        FOREIGN KEY (user_id) REFERENCES [User](user_id)
        ON DELETE CASCADE
);
GO

CREATE TABLE Bookmark (
    bookmark_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),

    CONSTRAINT FK_Bookmark_User
        FOREIGN KEY (user_id) REFERENCES [User](user_id),

    CONSTRAINT FK_Bookmark_Post
        FOREIGN KEY (post_id) REFERENCES Post(post_id)
        ON DELETE CASCADE,

    CONSTRAINT UQ_User_Post_Bookmark
        UNIQUE (user_id, post_id)
);
GO

CREATE TABLE Report (
    report_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    reporter_id BIGINT NOT NULL,
    post_id BIGINT NULL,
    comment_id BIGINT NULL,
    reason NVARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'RESOLVED', 'REJECTED')),
    moderator_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),
    resolved_at DATETIME NULL,

    CONSTRAINT FK_Report_Reporter
        FOREIGN KEY (reporter_id) REFERENCES [User](user_id),

    CONSTRAINT FK_Report_Post
        FOREIGN KEY (post_id) REFERENCES Post(post_id),

    CONSTRAINT FK_Report_Comment
        FOREIGN KEY (comment_id) REFERENCES Comment(comment_id),

    CONSTRAINT FK_Report_Moderator
        FOREIGN KEY (moderator_id) REFERENCES [User](user_id)
);
GO

CREATE TABLE Warning (
    warning_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    moderator_id BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,
    reason NVARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),

    FOREIGN KEY (moderator_id) REFERENCES [User](user_id),
    FOREIGN KEY (admin_id) REFERENCES [User](user_id)
);
GO

CREATE TABLE Restriction (
    restriction_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    moderator_id BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,
    reason NVARCHAR(255) NOT NULL,
    until_date DATETIME NOT NULL,
    is_active BIT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),

    FOREIGN KEY (moderator_id) REFERENCES [User](user_id),
    FOREIGN KEY (admin_id) REFERENCES [User](user_id)
);
GO

CREATE TABLE PasswordResetToken (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expiry_date DATETIME NOT NULL,
    used BIT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),

    FOREIGN KEY (user_id) REFERENCES [User](user_id)
        ON DELETE CASCADE
);
GO

CREATE TABLE ViewHistory (
    history_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    viewed_at DATETIME NOT NULL DEFAULT GETDATE(),

    FOREIGN KEY (user_id) REFERENCES [User](user_id),
    FOREIGN KEY (post_id) REFERENCES Post(post_id),

    CONSTRAINT UQ_User_Post_View
        UNIQUE (user_id, post_id)
);
GO

CREATE TABLE Comment_Interaction (
    interaction_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    comment_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL
        CHECK (type IN ('LIKE', 'DISLIKE')),
    created_at DATETIME NOT NULL DEFAULT GETDATE(),

    FOREIGN KEY (user_id) REFERENCES [User](user_id),

    FOREIGN KEY (comment_id) REFERENCES Comment(comment_id)
        ON DELETE CASCADE,

    CONSTRAINT UQ_User_Comment_Interaction
        UNIQUE (user_id, comment_id, type)
);
GO

CREATE TABLE Notification (
    notification_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL DEFAULT 'INFO',
    title NVARCHAR(150) NOT NULL,
    message NVARCHAR(500) NOT NULL,
    link VARCHAR(500) NULL,
    is_read BIT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),

    CONSTRAINT FK_Notification_User
        FOREIGN KEY (user_id) REFERENCES [User](user_id)
        ON DELETE CASCADE
);
GO

INSERT INTO [User]
(full_name, username, email, password, role, status, created_at)
VALUES
(N'Quản trị viên', 'admin', 'admin@blog.com',
 '$2a$12$SZpwXeuYrdonn3Y8.Essf.vcEyXmKjhqlTkVyJq6ET9JnAfhLQEXq',
 'ADMIN', 'ACTIVE', DATEADD(DAY,-180,GETDATE())),

(N'Nguyễn Minh Khoa', 'moderator', 'mod@blog.com',
 '$2a$12$Va9.8Yth0LT8JpeySU0Emu5COOjN3JUEdN2z32vAsMpKVLJ142YnS',
 'MODERATOR', 'ACTIVE', DATEADD(DAY,-150,GETDATE())),

(N'Trần Văn User', 'user1', 'user1@blog.com',
 '$2a$12$UZvhU6EovEHoFCtZKlTXluDERGHL7cCHfhmLjHzks7enXN/WPare.',
 'USER', 'ACTIVE', DATEADD(DAY,-120,GETDATE())),

(N'Lê Bảo An', 'user2', 'user2@blog.com',
 '$2a$12$UZvhU6EovEHoFCtZKlTXluDERGHL7cCHfhmLjHzks7enXN/WPare.',
 'USER', 'ACTIVE', DATEADD(DAY,-100,GETDATE())),

(N'Phạm Thu Hà', 'user3', 'user3@blog.com',
 '$2a$12$UZvhU6EovEHoFCtZKlTXluDERGHL7cCHfhmLjHzks7enXN/WPare.',
 'USER', 'ACTIVE', DATEADD(DAY,-80,GETDATE())),

(N'Võ Hoàng Nam', 'moderator2', 'mod2@blog.com',
 '$2a$12$Va9.8Yth0LT8JpeySU0Emu5COOjN3JUEdN2z32vAsMpKVLJ142YnS',
 'MODERATOR', 'ACTIVE', DATEADD(DAY,-70,GETDATE())),

(N'Nguyễn Khánh Linh', 'user4', 'user4@blog.com',
 '$2a$12$UZvhU6EovEHoFCtZKlTXluDERGHL7cCHfhmLjHzks7enXN/WPare.',
 'USER', 'ACTIVE', DATEADD(DAY,-50,GETDATE())),

(N'Đỗ Minh Quân', 'user5', 'user5@blog.com',
 '$2a$12$UZvhU6EovEHoFCtZKlTXluDERGHL7cCHfhmLjHzks7enXN/WPare.',
 'USER', 'RESTRICTED', DATEADD(DAY,-40,GETDATE()));
GO

INSERT INTO Category (category_name, description)
VALUES
(N'Kỹ thuật phần mềm',
 N'Quy trình, mô hình và phương pháp phát triển phần mềm'),

(N'Lập trình',
 N'Ngôn ngữ, framework, thư viện và kỹ thuật viết mã'),

(N'Cơ sở dữ liệu',
 N'SQL, thiết kế dữ liệu, tối ưu truy vấn'),

(N'Frontend',
 N'HTML, CSS, JavaScript và trải nghiệm người dùng'),

(N'Backend',
 N'API, Servlet, bảo mật và kiến trúc hệ thống'),

(N'DevOps',
 N'CI/CD, Docker, Cloud và tự động hóa'),

(N'Kiểm thử',
 N'Kiểm thử phần mềm, QA và tự động hóa kiểm thử');
GO

INSERT INTO Tag (tag_name, description)
VALUES
(N'Java', N'Ngôn ngữ Java'),
(N'Servlet', N'Jakarta Servlet'),
(N'JDBC', N'Kết nối cơ sở dữ liệu trong Java'),
(N'SQL Server', N'Hệ quản trị cơ sở dữ liệu Microsoft SQL Server'),
(N'JavaScript', N'Ngôn ngữ JavaScript'),
(N'HTML & CSS', N'Xây dựng giao diện web'),
(N'Bootstrap', N'CSS framework Bootstrap'),
(N'UI/UX', N'Trải nghiệm và giao diện người dùng'),
(N'Git', N'Quản lý phiên bản'),
(N'Docker', N'Container hóa ứng dụng'),
(N'Testing', N'Kiểm thử phần mềm'),
(N'Agile', N'Phát triển phần mềm linh hoạt'),
(N'Clean Code', N'Nguyên tắc viết mã sạch'),
(N'API', N'Thiết kế và sử dụng API');
GO

INSERT INTO Post
(
    title,
    summary,
    content,
    thumbnail,
    status,
    view_count,
    reject_count,
    rejection_reason,
    created_at,
    updated_at,
    author_id,
    category_id,
    moderator_id
)
VALUES

(
    N'Giới thiệu Scrum cho người mới',
    N'Tổng quan vai trò, sự kiện và artefact trong Scrum.',
    N'## Scrum là gì?
Scrum là một framework Agile giúp nhóm phát triển sản phẩm theo từng Sprint.

## Ba vai trò chính
Product Owner, Scrum Master và Developers phối hợp để tạo ra giá trị.',
    NULL,
    'APPROVED',
    268,
    0,
    NULL,
    DATEADD(DAY,-40,GETDATE()),
    DATEADD(DAY,-39,GETDATE()),
    3,
    1,
    2
),

(
    N'Clean Code: 7 nguyên tắc nên áp dụng',
    N'Các nguyên tắc giúp mã nguồn dễ đọc và dễ bảo trì hơn.',
    N'## Đặt tên có ý nghĩa
Tên biến và hàm cần thể hiện đúng mục đích.

## Hàm ngắn gọn
Mỗi hàm nên đảm nhiệm một nhiệm vụ rõ ràng.',
    NULL,
    'APPROVED',
    421,
    0,
    NULL,
    DATEADD(DAY,-35,GETDATE()),
    NULL,
    4,
    2,
    2
),

(
    N'JDBC với SQL Server từ A đến Z',
    N'Hướng dẫn kết nối, PreparedStatement và transaction.',
    N'## Kết nối JDBC
Sử dụng DriverManager để mở kết nối.

## PreparedStatement
Luôn ưu tiên PreparedStatement để truyền tham số an toàn.',
    NULL,
    'APPROVED',
    356,
    0,
    NULL,
    DATEADD(DAY,-30,GETDATE()),
    NULL,
    3,
    3,
    2
),

(
    N'Thiết kế REST API dễ sử dụng',
    N'Nguyên tắc đặt endpoint, status code và response thống nhất.',
    N'## Tài nguyên
Endpoint nên dùng danh từ.

## Phản hồi
Response cần có cấu trúc nhất quán và thông báo rõ ràng.',
    NULL,
    'APPROVED',
    305,
    0,
    NULL,
    DATEADD(DAY,-27,GETDATE()),
    NULL,
    5,
    5,
    6
),

(
    N'CSS Grid hay Flexbox?',
    N'Chọn công cụ bố cục phù hợp cho từng tình huống.',
    N'## Flexbox
Phù hợp bố cục một chiều.

## Grid
Phù hợp bố cục hai chiều và layout tổng thể.',
    NULL,
    'APPROVED',
    289,
    0,
    NULL,
    DATEADD(DAY,-24,GETDATE()),
    NULL,
    7,
    4,
    2
),

(
    N'Git workflow cho nhóm sinh viên',
    N'Quy trình branch, pull request và xử lý conflict.',
    N'## Nhánh tính năng
Mỗi chức năng nên phát triển trên một nhánh riêng.

## Pull request
Review trước khi merge giúp giảm lỗi.',
    NULL,
    'APPROVED',
    198,
    0,
    NULL,
    DATEADD(DAY,-21,GETDATE()),
    NULL,
    4,
    1,
    2
),

(
    N'Kiểm thử đơn vị trong Java',
    N'Cách tổ chức test case bằng JUnit.',
    N'## Arrange Act Assert
Mỗi test nên có ba phần rõ ràng.

## Tên test
Tên test cần mô tả hành vi được kiểm tra.',
    NULL,
    'APPROVED',
    174,
    0,
    NULL,
    DATEADD(DAY,-18,GETDATE()),
    NULL,
    5,
    7,
    6
),

(
    N'Docker cơ bản cho ứng dụng Java',
    N'Đóng gói và chạy ứng dụng Java trong container.',
    N'## Dockerfile
Dockerfile mô tả cách tạo image.

## Container
Container giúp môi trường chạy đồng nhất.',
    NULL,
    'APPROVED',
    247,
    0,
    NULL,
    DATEADD(DAY,-15,GETDATE()),
    NULL,
    7,
    6,
    6
),

(
    N'Tối ưu truy vấn SQL Server',
    N'Index, execution plan và những lỗi phổ biến.',
    N'## Index
Tạo index dựa trên truy vấn thực tế.

## Execution plan
Đọc execution plan để tìm điểm nghẽn.',
    NULL,
    'APPROVED',
    330,
    0,
    NULL,
    DATEADD(DAY,-13,GETDATE()),
    NULL,
    3,
    3,
    2
),

(
    N'Bootstrap 5: xây giao diện responsive',
    N'Grid system, utility class và component thường dùng.',
    N'## Grid system
Bootstrap sử dụng hệ thống 12 cột.

## Utility
Các utility giúp viết giao diện nhanh và nhất quán.',
    NULL,
    'APPROVED',
    210,
    0,
    NULL,
    DATEADD(DAY,-10,GETDATE()),
    NULL,
    4,
    4,
    2
),

(
    N'Bảo mật form đăng nhập cơ bản',
    N'Validate dữ liệu và quản lý session đúng cách.',
    N'## Validate
Kiểm tra dữ liệu cả frontend và backend.

## Session
Không lưu thông tin nhạy cảm trực tiếp trong trình duyệt.',
    NULL,
    'APPROVED',
    192,
    0,
    NULL,
    DATEADD(DAY,-8,GETDATE()),
    NULL,
    5,
    5,
    6
),

(
    N'Checklist trước khi deploy đồ án',
    N'Các bước kiểm tra giao diện, API và cơ sở dữ liệu.',
    N'## Kiểm tra giao diện
Thử trên nhiều kích thước màn hình.

## Kiểm tra dữ liệu
Đảm bảo script SQL có đủ dữ liệu mẫu.',
    NULL,
    'APPROVED',
    154,
    0,
    NULL,
    DATEADD(DAY,-6,GETDATE()),
    NULL,
    7,
    6,
    2
),

(
    N'Tạo trang Dashboard dễ sử dụng',
    N'Bố cục dashboard rõ ràng cho từng vai trò.',
    N'## Sidebar
Menu cần thể hiện mục đang chọn.

## Trạng thái
Loading, empty và error phải được hiển thị rõ.',
    NULL,
    'PENDING',
    0,
    0,
    NULL,
    DATEADD(DAY,-3,GETDATE()),
    NULL,
    3,
    4,
    NULL
),

(
    N'Sử dụng transaction trong JDBC',
    N'Đảm bảo dữ liệu nhất quán khi có nhiều câu lệnh SQL.',
    N'## Commit
Chỉ commit khi toàn bộ thao tác thành công.

## Rollback
Rollback khi có lỗi để tránh dữ liệu dở dang.',
    NULL,
    'PENDING',
    0,
    0,
    NULL,
    DATEADD(DAY,-2,GETDATE()),
    NULL,
    4,
    3,
    NULL
),

(
    N'Kiểm thử API bằng Postman',
    N'Tổ chức collection và kiểm tra response API.',
    N'## Collection
Nhóm request theo chức năng.

## Test script
Có thể viết script kiểm tra status và dữ liệu trả về.',
    NULL,
    'PENDING',
    0,
    0,
    NULL,
    DATEADD(DAY,-1,GETDATE()),
    NULL,
    5,
    7,
    NULL
),

(
    N'JavaScript DOM nâng cao',
    N'Bài viết cần bổ sung ví dụ và giải thích rõ hơn.',
    N'## DOM
DOM biểu diễn cấu trúc tài liệu HTML.

## Sự kiện
Có thể lắng nghe sự kiện bằng addEventListener.',
    NULL,
    'REJECTED',
    0,
    1,
    N'Phần ví dụ còn quá ngắn, cần bổ sung đoạn mã minh họa và kết quả.',
    DATEADD(HOUR,-20,GETDATE()),
    DATEADD(HOUR,-8,GETDATE()),
    3,
    4,
    2
),

(
    N'Giới thiệu Microservice',
    N'Bản thảo về kiến trúc microservice.',
    N'## Microservice
Mỗi service đảm nhiệm một nghiệp vụ độc lập.',
    NULL,
    'REJECTED',
    0,
    2,
    N'Nội dung chưa phân tích ưu nhược điểm và chưa có sơ đồ kiến trúc.',
    DATEADD(HOUR,-16,GETDATE()),
    DATEADD(HOUR,-7,GETDATE()),
    4,
    5,
    6
),

(
    N'Ý tưởng bài viết về CI/CD',
    N'Ghi chú nội dung sẽ viết.',
    N'## Nội dung dự kiến
Pipeline build, test và deploy.',
    NULL,
    'DRAFT',
    0,
    0,
    NULL,
    DATEADD(HOUR,-5,GETDATE()),
    NULL,
    5,
    6,
    NULL
);
GO

INSERT INTO Post_Tag (post_id, tag_id)
VALUES
(1,12),(1,11),
(2,13),(2,1),
(3,1),(3,3),(3,4),
(4,14),(4,1),
(5,6),(5,5),(5,8),
(6,9),(6,12),
(7,1),(7,11),
(8,1),(8,10),
(9,4),(9,3),
(10,6),(10,7),(10,8),
(11,1),(11,14),
(12,9),(12,10),
(13,7),(13,8),
(14,3),(14,4),
(15,11),(15,14),
(16,5),(16,6),
(17,14),
(18,9),(18,10);
GO

INSERT INTO Comment
(content, created_at, status, user_id, post_id, parent_id)
VALUES
(N'Bài viết giải thích rất dễ hiểu.', DATEADD(DAY,-20,GETDATE()), 'VISIBLE', 4, 1, NULL),
(N'Mình thích phần mô tả vai trò Scrum Master.', DATEADD(DAY,-19,GETDATE()), 'VISIBLE', 5, 1, NULL),
(N'Cảm ơn bạn đã góp ý!', DATEADD(DAY,-18,GETDATE()), 'VISIBLE', 3, 1, 1),
(N'Có thể thêm ví dụ tên biến tốt và xấu không?', DATEADD(DAY,-17,GETDATE()), 'VISIBLE', 7, 2, NULL),
(N'Phần transaction rất hữu ích.', DATEADD(DAY,-14,GETDATE()), 'VISIBLE', 4, 3, NULL),
(N'Mình bị lỗi driver, bài này giúp mình tìm ra nguyên nhân.', DATEADD(DAY,-13,GETDATE()), 'VISIBLE', 5, 3, NULL),
(N'Endpoint nên dùng số nhiều hay số ít?', DATEADD(DAY,-12,GETDATE()), 'VISIBLE', 3, 4, NULL),
(N'Thường nên dùng danh từ số nhiều để nhất quán.', DATEADD(DAY,-11,GETDATE()), 'VISIBLE', 5, 4, 7),
(N'Grid phù hợp layout tổng thể hơn thật.', DATEADD(DAY,-10,GETDATE()), 'VISIBLE', 3, 5, NULL),
(N'Có thể làm thêm bài về CSS Container Query.', DATEADD(DAY,-9,GETDATE()), 'VISIBLE', 4, 5, NULL),
(N'Nhóm mình đang dùng Git Flow, khá dễ quản lý.', DATEADD(DAY,-8,GETDATE()), 'VISIBLE', 5, 6, NULL),
(N'Test name nên viết theo dạng given-when-then.', DATEADD(DAY,-7,GETDATE()), 'VISIBLE', 7, 7, NULL),
(N'Docker Compose có phù hợp cho đồ án nhỏ không?', DATEADD(DAY,-6,GETDATE()), 'VISIBLE', 3, 8, NULL),
(N'Có, đặc biệt khi có app và database riêng.', DATEADD(DAY,-5,GETDATE()), 'VISIBLE', 7, 8, 13),
(N'Execution plan là phần mình hay bỏ qua.', DATEADD(DAY,-4,GETDATE()), 'VISIBLE', 4, 9, NULL),
(N'Bootstrap utility giúp giảm khá nhiều CSS.', DATEADD(DAY,-3,GETDATE()), 'VISIBLE', 5, 10, NULL),
(N'Phần session nên nói thêm timeout.', DATEADD(DAY,-2,GETDATE()), 'VISIBLE', 7, 11, NULL),
(N'Checklist rất thực tế cho buổi bảo vệ.', DATEADD(DAY,-1,GETDATE()), 'VISIBLE', 3, 12, NULL),
(N'Nội dung này có dấu hiệu spam.', DATEADD(HOUR,-10,GETDATE()), 'HIDDEN', 8, 2, NULL),
(N'Bình luận đã bị xóa mẫu.', DATEADD(HOUR,-6,GETDATE()), 'DELETED', 8, 3, NULL);
GO


INSERT INTO Interaction (user_id, post_id, type, created_at)
VALUES
(3,2,'LIKE',DATEADD(DAY,-15,GETDATE())),
(3,4,'LIKE',DATEADD(DAY,-10,GETDATE())),
(3,8,'LIKE',DATEADD(DAY,-4,GETDATE())),
(4,1,'LIKE',DATEADD(DAY,-18,GETDATE())),
(4,3,'LIKE',DATEADD(DAY,-12,GETDATE())),
(4,7,'LIKE',DATEADD(DAY,-5,GETDATE())),
(5,1,'LIKE',DATEADD(DAY,-17,GETDATE())),
(5,2,'LIKE',DATEADD(DAY,-14,GETDATE())),
(5,5,'LIKE',DATEADD(DAY,-8,GETDATE())),
(7,3,'LIKE',DATEADD(DAY,-11,GETDATE())),
(7,4,'LIKE',DATEADD(DAY,-9,GETDATE())),
(7,9,'LIKE',DATEADD(DAY,-3,GETDATE())),
(8,6,'UNLIKE',DATEADD(DAY,-2,GETDATE()));
GO

INSERT INTO Bookmark (user_id, post_id, created_at)
VALUES
(3,2,DATEADD(DAY,-12,GETDATE())),
(3,8,DATEADD(DAY,-3,GETDATE())),
(3,11,DATEADD(DAY,-1,GETDATE())),
(4,1,DATEADD(DAY,-10,GETDATE())),
(4,7,DATEADD(DAY,-4,GETDATE())),
(5,3,DATEADD(DAY,-8,GETDATE())),
(5,9,DATEADD(DAY,-2,GETDATE())),
(7,4,DATEADD(DAY,-6,GETDATE())),
(7,10,DATEADD(DAY,-1,GETDATE()));
GO

INSERT INTO User_Settings
(
    user_id,
    theme,
    primary_color,
    secondary_color,
    background_color,
    text_color,
    font_family
)
VALUES
(1,'dark','#5b6cf9','#a855f7','#111827','#f9fafb','system-ui'),
(2,'light','#2563eb','#06b6d4','#f4f6f9','#1f2937','system-ui'),
(3,'light','#667eea','#764ba2','#f4f6f9','#1a1a2e','system-ui'),
(4,'light','#ec4899','#8b5cf6','#fff7fb','#312e3f','Inter, sans-serif'),
(5,'light','#16a34a','#0ea5e9','#f5fff7','#17351f','Roboto, sans-serif'),
(6,'dark','#f59e0b','#ef4444','#18181b','#fafafa','system-ui'),
(7,'light','#0f766e','#3b82f6','#f0fdfa','#134e4a','Georgia, serif'),
(8,'light','#64748b','#475569','#f8fafc','#1e293b','system-ui');
GO

INSERT INTO ViewHistory (user_id, post_id, viewed_at)
VALUES
(3,1,DATEADD(DAY,-4,GETDATE())),
(3,2,DATEADD(DAY,-3,GETDATE())),
(3,8,DATEADD(HOUR,-8,GETDATE())),
(4,3,DATEADD(DAY,-2,GETDATE())),
(4,6,DATEADD(HOUR,-12,GETDATE())),
(5,4,DATEADD(DAY,-1,GETDATE())),
(5,9,DATEADD(HOUR,-5,GETDATE())),
(7,5,DATEADD(DAY,-2,GETDATE())),
(7,10,DATEADD(HOUR,-4,GETDATE()));
GO

INSERT INTO Comment_Interaction (user_id, comment_id, type)
VALUES
(3,1,'LIKE'),
(5,1,'LIKE'),
(4,3,'LIKE'),
(7,5,'LIKE'),
(3,8,'LIKE'),
(4,13,'LIKE'),
(5,14,'LIKE');
GO

INSERT INTO Report
(
    reporter_id,
    post_id,
    comment_id,
    reason,
    status,
    moderator_id,
    created_at,
    resolved_at
)
VALUES
(
    3,
    NULL,
    19,
    N'Bình luận có nội dung spam.',
    'PENDING',
    NULL,
    DATEADD(HOUR,-10,GETDATE()),
    NULL
),
(
    4,
    6,
    NULL,
    N'Tiêu đề chưa phản ánh đúng nội dung.',
    'PROCESSING',
    2,
    DATEADD(DAY,-2,GETDATE()),
    NULL
),
(
    5,
    NULL,
    20,
    N'Bình luận không phù hợp.',
    'RESOLVED',
    6,
    DATEADD(DAY,-4,GETDATE()),
    DATEADD(DAY,-3,GETDATE())
),
(
    7,
    NULL,
    NULL,
    N'Hỗ trợ: Không thể thay ảnh đại diện.',
    'PENDING',
    NULL,
    DATEADD(HOUR,-3,GETDATE()),
    NULL
);
GO

INSERT INTO Notification
(
    user_id,
    type,
    title,
    message,
    link,
    is_read,
    created_at
)
VALUES
(
    3,
    'POST_REJECTED',
    N'Bài viết cần chỉnh sửa',
    N'Bài “JavaScript DOM nâng cao” bị từ chối. Lý do: Phần ví dụ còn quá ngắn.',
    'dashboard.html#posts',
    0,
    DATEADD(HOUR,-8,GETDATE())
),
(
    4,
    'POST_REJECTED',
    N'Bài viết cần chỉnh sửa',
    N'Bài “Giới thiệu Microservice” cần bổ sung ưu nhược điểm và sơ đồ.',
    'dashboard.html#posts',
    0,
    DATEADD(HOUR,-7,GETDATE())
),
(
    3,
    'POST_APPROVED',
    N'Bài viết đã được duyệt',
    N'Bài “Tối ưu truy vấn SQL Server” đã được xuất bản.',
    'post-detail.html?id=9',
    1,
    DATEADD(DAY,-13,GETDATE())
),
(
    4,
    'POST_APPROVED',
    N'Bài viết đã được duyệt',
    N'Bài “Bootstrap 5: xây giao diện responsive” đã được xuất bản.',
    'post-detail.html?id=10',
    0,
    DATEADD(DAY,-10,GETDATE())
),
(
    5,
    'INFO',
    N'Chào mừng trở lại',
    N'Bạn có bài viết mới đang chờ kiểm duyệt.',
    'dashboard.html#posts',
    1,
    DATEADD(DAY,-1,GETDATE())
);
GO

CREATE INDEX IX_Post_Status_CreatedAt
    ON Post(status, created_at DESC);

CREATE INDEX IX_Post_Author_Status
    ON Post(author_id, status);

CREATE INDEX IX_Comment_Post_Status
    ON Comment(post_id, status, created_at);

CREATE INDEX IX_Notification_User_Read
    ON Notification(user_id, is_read, created_at DESC);
GO

SELECT
    'User' AS TableName,
    COUNT(*) AS RecordCount
FROM [User]

UNION ALL

SELECT
    'Category',
    COUNT(*)
FROM Category

UNION ALL

SELECT
    'Post',
    COUNT(*)
FROM Post

UNION ALL

SELECT
    'Comment',
    COUNT(*)
FROM Comment

UNION ALL

SELECT
    'Tag',
    COUNT(*)
FROM Tag

UNION ALL

SELECT
    'Interaction',
    COUNT(*)
FROM Interaction

UNION ALL

SELECT
    'Bookmark',
    COUNT(*)
FROM Bookmark

UNION ALL

SELECT
    'Notification',
    COUNT(*)
FROM Notification;
GO