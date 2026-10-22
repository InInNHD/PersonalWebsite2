-- 第六轮；备份后选择目标库，只执行一次。旧资源保持未分组，说明和顺序不变。
CREATE TABLE resource_group (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_resource_group_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE resource_link
    ADD COLUMN group_id BIGINT NULL,
    ADD COLUMN icon_key VARCHAR(20) NOT NULL DEFAULT 'link',
    ADD COLUMN recommendation VARCHAR(300) NOT NULL DEFAULT '',
    ADD CONSTRAINT fk_resource_group FOREIGN KEY (group_id) REFERENCES resource_group(id);

CREATE TABLE media_asset (
    id CHAR(36) NOT NULL PRIMARY KEY,
    filename VARCHAR(41) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    byte_size BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_media_filename (filename)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE article_media (
    article_id BIGINT NOT NULL,
    media_id CHAR(36) NOT NULL,
    usage_role VARCHAR(10) NOT NULL,
    PRIMARY KEY (article_id, media_id, usage_role),
    CONSTRAINT fk_media_article FOREIGN KEY (article_id) REFERENCES article(id),
    CONSTRAINT fk_article_media FOREIGN KEY (media_id) REFERENCES media_asset(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
