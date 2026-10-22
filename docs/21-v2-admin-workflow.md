# 第五轮：写作后台使用与升级说明

日期：2026-10-21。5A、5B、5C已按用户授权直接实现，并合并进行一次整轮验收。此文说明当前实际代码；第20节为历史手敲指南，不再用于覆盖新后台。

## 1. 页面与数据流

| 地址 | 用途 |
| --- | --- |
| `/admin/login` | 管理员登录，账号固定为 `admin`，页面只输入密码 |
| `/admin/articles` | 文章搜索、组合筛选、分页、回收站 |
| `/admin/articles/new` | 新建文章，首次保存后替换为正式编号地址 |
| `/admin/articles/编号/edit` | 编辑、预览、保存、发布、撤回、回收 |
| `/admin/taxonomy` | 分类、标签的新增与改名 |
| `/admin/resources` | 资源链接维护 |

后台使用独立布局。未登录访问后台会转到登录页，登录后回到合法原地址；认证头只保存在当前标签页内存，刷新需要重新登录。写入由共享请求方法携带认证并获取当前CSRF token，后端继续负责身份、角色和输入校验。退出调用真实的 `POST /api/admin/logout`，成功后清空内存认证。

## 2. 写作流程

1. 进入文章管理，可按标题或摘要关键词、草稿/公开状态、栏目、分类组合筛选。列表采用服务端分页，条件保存在网址中；回收末页最后一条后会纠正页码。
2. 点击新建，填写标题、摘要、正文、栏目、分类、标签、封面及置顶设置。Markdown工具栏提供标题、粗体、斜体、代码、链接及列表；可切换编辑、预览或分屏，窄屏自动单列。原始HTML按文字展示。
3. 点击保存，首次请求创建草稿，成功后地址变成正式编号，后续保存使用同一编号。发布会先保存；若第二步失败，页面明确提示“已保存，但发布未完成”。
4. 发布日记前会提示内容将向访客公开。撤回会转为草稿；回收仅设置回收标记，保留内容及关联；恢复一律先回草稿，需要再次主动发布。
5. 未保存离开使用站内确认框，可以取消或按Escape留在原页；关闭或刷新由浏览器提示。保存期间禁止离开。认证失败保留当前输入，可在原页重新验证后继续保存。

资源链接只接受合法HTTP/HTTPS地址。校验或网络失败后输入保留；编辑已有资源时切换记录也有未保存保护。

## 3. 数据库升级

已有版本升级前先做数据库备份。选中实际库，核对尚未存在 `deleted_at`，然后仅执行一次：

```sql
USE personal_website;
SELECT DATABASE();
SOURCE E:/PersonalWebsite2/database/006-article-recycle.sql;
SHOW COLUMNS FROM article LIKE 'deleted_at';
SHOW INDEX FROM article WHERE Key_name = 'idx_article_visibility';
```

IDEA数据库控制台不支持MySQL客户端的SOURCE命令时，直接打开006文件并执行其SQL内容。新安装按000～006顺序执行。

本工作区的个人库已在2026-10-21完成006，**不要重复执行**。备份位于 `E:\PersonalWebsite2\backups\20261021-fifth-round-194842`。迁移只新增可空的 `deleted_at DATETIME(6)` 及索引；五表原有字段与行逐字段一致，原文章回收标记均为NULL。备份含私有配置与内容，不提交到GitHub。

## 4. 接口兼容性

`GET /api/admin/articles` 从数组改为分页对象，前后端必须同步升级：

```json
{"items":[],"total":0,"page":1,"size":10,"totalPages":0}
```

支持 `keyword`（标题/摘要，按字面匹配）、`status=DRAFT|PUBLISHED`、`type=NOTE|THOUGHT|DIARY`、正整数 `categoryId`、`recycled=true|false`、`page`、`size`。默认只查未回收记录，页大小默认10、最大50。后台详情新增 `deletedAt`；公开详情格式保持不变。

| 操作 | 接口 | 结果 |
| --- | --- | --- |
| 撤回 | `POST /api/admin/articles/{id}/unpublish` | 转草稿，保留首次发布时间 |
| 回收 | `DELETE /api/admin/articles/{id}` | 设置回收时间，不物理删除 |
| 恢复 | `POST /api/admin/articles/{id}/restore` | 清除回收标记并转草稿 |

重复回收不重复修改；重复恢复不会撤回后来重新发布的文章。回收状态下保存、发布、撤回返回409。状态写入在事务中锁定同一文章行；公开列表、搜索、详情、归档、日记、主题计数、统计、前后篇统一排除回收记录。

## 5. 本地运行与验收

数据库升级后在IDEA重启自己的Java17后端，前端在 `frontend` 目录运行 `npm.cmd run dev`。配置及管理员密码沿用本机配置，无需写入源码。

本轮已完成整轮验收，无需再分5A/5B/5C重复验收。记录见 [第五轮验收](V2-ADMIN-WORKFLOW-ACCEPTANCE.md)。自动化前端60项、独立真实MySQL后端28项及两端构建通过；完整后台流程和四档宽度通过。`AdminWorkflowTests` 只允许专用库名前缀 `pw_v2admin_acceptance_`，不要对个人内容库运行写入测试。28项包含当前源码19项及隔离副本中的9项既有指南专项。

第五轮已发布为 `v0.10.0`。第六轮资源与图片管理现已完成，最新使用、007迁移和媒体备份说明见[第22节](22-v2-resources-media.md)；下一轮完整状态与内容打磨，云部署仍后置。
