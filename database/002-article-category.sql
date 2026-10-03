USE personal_website;

CREATE TABLE category (
                          id BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类编号',
                          name VARCHAR(50) NOT NULL COMMENT '分类名称',

                          PRIMARY KEY (id),

    -- 数据库保证分类名称不重复。
                          UNIQUE KEY uk_category_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

ALTER TABLE article
    -- 已有文章自动使用 NULL，表示未分类。
    ADD COLUMN category_id BIGINT NULL COMMENT '所属分类',

    ADD INDEX idx_article_category_id (category_id),

    -- 防止文章引用不存在的分类。
    ADD CONSTRAINT fk_article_category
        FOREIGN KEY (category_id) REFERENCES category (id);