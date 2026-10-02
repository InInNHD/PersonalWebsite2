USE personal_website;

CREATE TABLE resource_link (
                               id BIGINT NOT NULL AUTO_INCREMENT COMMENT '资源唯一编号',
                               title VARCHAR(200) NOT NULL COMMENT '资源名称',
                               url VARCHAR(2048) NOT NULL COMMENT '完整的外部链接',
                               description VARCHAR(500) NOT NULL DEFAULT '' COMMENT '资源说明',

    -- 新增资源默认不公开，由管理员确认后再展示。
                               published BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否公开',

    -- 数字越小越靠前；相同数字按编号倒序排列。
                               sort_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序',

                               PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;