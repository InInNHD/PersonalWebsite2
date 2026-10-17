# 第二轮 B1 实际源码验收

日期：2026-10-17。

## 结论

**B1 已完成并通过验收。** 用户授权助手修正两处标记并升级个人库；修正后前端14项测试、生产构建和后端打包通过，迁移后的个人环境7组只读接口检查及首页、旧文章详情浏览器检查通过。下一节为B2导航状态完善，整个第二轮完成后再评估Release。

## 修正与个人库复核

- 已删除005脚本首行HTML标记及SiteHeader末尾三个反引号。
- 迁移前备份保留于 `E:\PersonalWebsite2\backups\20261017-005151-before-b1`。导出SQL包含结构与数据，已生成SHA-256，并恢复到专用空库逐字段比较，结果一致；恢复验证库已删除。
- 005已在 `personal_website` 成功执行一次。新增列为 `cover_url varchar(2048)` 可空、`pinned tinyint(1)` 非空默认0、`updated_at datetime(6)` 可空。既有五表的原有字段逐字段比较未变化；旧文章新字段均为NULL/false/NULL，未伪造更新时间。
- 修正后实际前端14项回归及Vite构建通过；实际后端Maven package通过。本次未重复运行已有15项独立库后端测试，业务逻辑未变化。
- 实际个人库接口检查7组通过：文章列表、管理员认证、CSRF、旧文章公开及后台详情的新字段、草稿404隔离、归档、资源与统计。4篇文章中公开3篇，公开资源3条，分类与标签统计均为0。
- 浏览器首页显示真实3篇公开文章与3/0/0/3统计，第一篇笔记正文可打开，返回列表正常。本轮仅执行结构升级和GET读取，没有修改或新增个人内容。
- 当前终端没有ADMIN_PASSWORD，验收后端使用进程级临时密码，仅监听127.0.0.1:18082；原配置和IDEA运行配置未修改。前端预览5178及专用后端在验收后停止。正常启动时仍使用你在IDEA配置的ADMIN_PASSWORD。

本次证据：`artifacts/v2-metadata-acceptance/personal-migration-result.json`、`personal-readonly-checks.json`、`personal-package.log`、`personal-home.png`。以下保留首次验收发现与独立环境测试范围。

## 首次验收发现（已修正）

1. `database/005-article-presentation.sql` 第 1 行误写了 `<!-- file: ... -->`。这是指南的文件定位标记，不是 SQL。原样在独立库执行复现 MySQL 1064，新增三列均未创建；仅在测试执行内容中移除该行后迁移成功。
2. 只读检查确认 `personal_website.article` 尚无 `cover_url`、`pinned`、`updated_at`。删除 SQL 首行后，按备份指南备份，确认 `SELECT DATABASE()` 返回 `personal_website`，执行该文件一次。已有列时不要重复 ALTER TABLE。
3. `frontend/src/components/SiteHeader.vue` 第 41 行、`</template>` 后仍有一行三个反引号，应删除。这处不阻断当前构建。

迁移后 `SHOW COLUMNS FROM article` 应新增：`cover_url varchar(2048)` 可空；`pinned tinyint(1)` 非空、默认 0；`updated_at datetime(6)` 可空。旧文章封面和更新时间为 NULL，置顶为 0，符合预期。随后重启后端，复查个人环境列表、统计和后台保存。

检查时个人后端 8080 没有运行；没有将连接失败记作实际 HTTP 500。代码依赖新增列，个人库完成迁移前不能视为可正常使用。

## 已通过的检查

| 范围 | 结果 |
| --- | --- |
| 实际前端测试 | 14/14；新增 3 项组件检查，覆盖封面回退、地址变化恢复、统计失败/重试/重复请求/卸载 |
| 实际前端生产构建 | 通过 |
| 实际后端测试 | Java 17，独立 MySQL 中 15/15；包含 4 项展示字段与统计检查 |
| Maven package | 通过 |
| 真实 HTTP | 8 组通过：空统计、12 篇分为 10+2 页、置顶在分页前、无重复、草稿 404、公开统计、非法标签更新整体回滚、非法封面不改变保存值 |
| 浏览器首页 | 自定义封面、失效封面回退、置顶首位、更新时间、12/1/2/1 公开统计、第二页 2 篇及分页按钮状态正常 |
| 浏览器写作 | 新建带封面和置顶的草稿，切换后重新打开回显正确；FTP 地址保存被拒绝；清空封面、取消置顶并发布成功，首页显示该文章且统计增加到 13 |
| 响应式 | 首页与登录后后台各在 320、390、768、1440px 检查，共 8 组无整页横向溢出 |

后端验证还覆盖旧文章空值、首次/重复发布时间、归档时间顺序、草稿关联分类标签和隐藏资源不计入统计。事务测试的失败更新回滚另以真实 HTTP 请求验证，避免测试外层事务影响观察。

首次沙箱内 Maven 测试因本地 MySQL socket 权限失败；允许访问本机数据库后重跑全部 15 项通过。最终日志为 `artifacts/v2-metadata-acceptance/maven-test-access.log`。

## 修改与隔离

首次验收时助手新增实际源码回归文件 `frontend/tests/presentation-flow.test.mjs`、`personalwebsite/src/test/java/com/firefly/personalwebsite/PresentationFlowTests.java`，同步指南与进度文档；当时业务源码及个人数据库未修改。随后按用户授权完成本页上方记录的两行清理与个人库迁移。

浏览器与 HTTP 使用独立库 `pw_v2b1_acceptance_20261017` 和专用端口 5178/18082。测试结束后专用服务已退出、测试库已删除，临时连接文件及包含凭据的 JAR 已清理；原库五表校验值与测试前一致。没有停止用户的其他进程。截图中的文章均为临时验收样例。

本地证据：`artifacts/v2-metadata-acceptance/` 中的 `http-checks.json`、`viewports.json`、`browser-home.png`、`cleanup-result.json`。该目录被 Git 忽略。

本轮B1已完成；筛选 URL、返回列表位置、后续阅读页和写作后台改版不在本次完成范围。下一节继续B2。
