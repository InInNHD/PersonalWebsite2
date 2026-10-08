USE personal_website;

-- 一行表示“一篇文章使用了一个标签”。
-- 已有文章无需修改：没有关联记录就表示没有标签。
CREATE TABLE article_tag (
                             article_id BIGINT NOT NULL COMMENT '文章编号',
                             tag_id BIGINT NOT NULL COMMENT '标签编号',

    -- 联合主键防止同一文章重复关联同一标签。
                             PRIMARY KEY (article_id, tag_id),

    -- 为按标签查询以及外键检查提供索引。
                             INDEX idx_article_tag_tag_id (tag_id),

    -- 将来删除文章时，只删除该文章的关联记录。
    -- 标签本身仍保留，其他文章可以继续使用。
                             CONSTRAINT fk_article_tag_article
                                 FOREIGN KEY (article_id) REFERENCES article (id)
                                     ON DELETE CASCADE,

    -- 不允许删除仍被文章引用的标签。
                             CONSTRAINT fk_article_tag_tag
                                 FOREIGN KEY (tag_id) REFERENCES tag (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;