-- 仅用于新安装：创建数据库和最初的文章表。
-- 已有数据库升级时跳过本文件，按 README 执行尚未应用的后续脚本。
CREATE DATABASE IF NOT EXISTS personal_website
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE personal_website;

CREATE TABLE article (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文章编号',
    title VARCHAR(200) NOT NULL COMMENT '文章标题',
    summary VARCHAR(500) NOT NULL COMMENT '文章摘要',
    content_markdown LONGTEXT NOT NULL COMMENT 'Markdown 正文',
    type VARCHAR(20) NOT NULL COMMENT '栏目：NOTE、THOUGHT 或 DIARY',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT 或 PUBLISHED',
    published_at DATETIME NULL COMMENT '首次发布时间；草稿为空',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
