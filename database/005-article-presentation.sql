-- 第二轮B1升级。先备份 personal_website；本脚本只执行一次。
-- IDEA控制台先执行 SELECT DATABASE(); 确认当前库为 personal_website。
-- 旧文章没有可追溯的修改时间，因此保留NULL，不伪造历史更新时间。
ALTER TABLE article
    ADD COLUMN cover_url VARCHAR(2048) NULL COMMENT '可选HTTP/HTTPS封面地址',
    ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否置顶',
    ADD COLUMN updated_at DATETIME(6) NULL COMMENT '最近保存或首次发布的时间；旧记录未知';

SHOW COLUMNS FROM article;
SELECT id, title, cover_url, pinned, updated_at FROM article ORDER BY id;
