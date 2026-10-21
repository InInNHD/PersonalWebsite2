-- 第五轮升级；先备份，确认选中目标数据库，仅执行一次。
-- 回收保留正文、分类、标签和首次发布时间；恢复时先回草稿。
ALTER TABLE article
    ADD COLUMN deleted_at DATETIME(6) NULL COMMENT '回收时间；NULL表示未回收',
    ADD INDEX idx_article_visibility (deleted_at, status, id);
