-- 第三版4A：先停写并备份，选定目标库后执行一次。兼容MySQL5.7。
-- article和原关联表继续保存公开版本；编辑稿按需建立，不改旧文内容或发布状态。
ALTER TABLE article ADD COLUMN write_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE article_edit (
    article_id BIGINT NOT NULL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    content_markdown LONGTEXT NOT NULL,
    type VARCHAR(20) NOT NULL,
    category_id BIGINT NULL,
    cover_url VARCHAR(2048) NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_edit_article FOREIGN KEY(article_id) REFERENCES article(id),
    CONSTRAINT fk_edit_category FOREIGN KEY(category_id) REFERENCES category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE article_edit_tag (
    article_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY(article_id,tag_id),
    CONSTRAINT fk_edit_tag_article FOREIGN KEY(article_id) REFERENCES article_edit(article_id) ON DELETE CASCADE,
    CONSTRAINT fk_edit_tag FOREIGN KEY(tag_id) REFERENCES tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE article_edit_media (
    article_id BIGINT NOT NULL,
    media_id CHAR(36) NOT NULL,
    usage_role VARCHAR(10) NOT NULL,
    PRIMARY KEY(article_id,media_id,usage_role),
    CONSTRAINT fk_edit_media_article FOREIGN KEY(article_id) REFERENCES article_edit(article_id) ON DELETE CASCADE,
    CONSTRAINT fk_edit_media FOREIGN KEY(media_id) REFERENCES media_asset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
