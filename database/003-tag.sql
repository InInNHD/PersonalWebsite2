USE personal_website;

CREATE TABLE tag (
                     id BIGINT NOT NULL AUTO_INCREMENT COMMENT '标签编号',
                     name VARCHAR(50) NOT NULL COMMENT '标签名称',

                     PRIMARY KEY (id),

    -- 同一个标签名称只保存一份，之后可以被多篇文章引用。
                     UNIQUE KEY uk_tag_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;