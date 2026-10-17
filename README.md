# PersonalWebsite2

个人网站第二版，包含 Vue/Vite 前端和 Spring Boot/MySQL 后端。当前支持文章阅读、Markdown、草稿与发布、管理员认证、资源链接管理、分类、文章多标签、组合筛选、标题/摘要搜索、文章分页、按年月归档、关于页及首页个人简介。

## 项目结构

- `frontend/`：前端应用及回归测试。
- `personalwebsite/`：后端应用及真实 MySQL 接口测试。
- `database/`：按编号执行的数据库初始化与升级脚本。
- `docs/`：手敲指南和项目进度。`05-tag-filter.md` 对应的标签筛选已完成，自动化测试通过。

第六轮搜索分页、第七轮文章归档及第八轮关于页与首页简介均已完成并验收。手敲指南见 `docs/06-article-search-pagination.md`、`docs/07-article-archive.md` 和 `docs/08-about-profile.md`；第八轮记录见 `docs/ABOUT-PROFILE-ACCEPTANCE.md`。个人文案集中在 `frontend/src/siteProfile.js`，本轮纳入 v0.6.0 预发布版本。

第九轮暗色技术风格及移动端布局已完成并验收：实际前端 9 项回归测试、生产构建，以及桌面、平板和手机视口的浏览器检查均通过；独立 MySQL 中的手机写作发布和资源保存流程通过。手敲指南见 `docs/09-site-style-mobile.md`，验收记录见 `docs/STYLE-MOBILE-ACCEPTANCE.md`。

第十轮备份恢复演练已完成：个人数据库五表、源码和配置已备份，并在独立库通过数据核对、应用启动、草稿隔离及写作发布验收。备份保留在已忽略的 `backups/` 中，演练环境已清理。详见 [操作指南](docs/10-backup-restore.md) 和 [验收记录](docs/BACKUP-RESTORE-ACCEPTANCE.md)。

第一版既有功能的本地验收已通过：当前源码后端 11 项、前端 9 项测试及两端构建通过；独立库上的生产预览完成 27 项真实 HTTP 断言、浏览器写作发布与故障恢复、25 组响应式检查。详细范围和执行记录见 [本地完整验收](docs/LOCAL-ACCEPTANCE.md)。该结论覆盖已实现流程，产品展示与长期写作体验仍需提升。

当前进入[第二版改进计划](docs/V2-IMPROVEMENT-PLAN.md)：对照最初参考项目，依次完善设计样稿、首页与全站框架、文章阅读、内容导航、写作后台、资源图片及完整状态。继续本地开发，云部署安排在第二版效果和功能共同验收之后。

第二版第一轮样稿已获用户确认，包含首页、文章详情、独立写作后台和组件规范。第二轮A「站点框架与真实首页」已完成手敲并通过功能验收：实际前端11项回归、生产构建、真实接口读取和26组响应式检查通过；SiteHeader.vue末尾多余标记已在B1验收收尾时清理。详见[手敲指南](docs/11-v2-home-foundation.md)和[实际验收记录](docs/V2-HOME-FOUNDATION-ACCEPTANCE.md)。该阶段已与后续B1、B2共同纳入v0.7.0预发布。独立样稿运行方式仍为 `node docs/design-v2/preview.mjs`，访问 `http://127.0.0.1:5177/`；详见[样稿说明与设计参数](docs/design-v2/README.md)。

第二轮B按手敲节奏分为B1数据和B2导航状态。[第12节：封面、置顶、更新时间与公开统计](docs/12-v2-article-presentation.md)已完成并验收：实际前端14项、独立库后端15项、两端构建、8组HTTP检查和浏览器写作流程通过。两处误写标记已清理；个人库在备份恢复验证后成功执行005，原有数据核对一致，迁移后7组只读接口及浏览器首页/详情复核通过。详见[验收记录](docs/V2-ARTICLE-PRESENTATION-ACCEPTANCE.md)。[第13节B2](docs/13-v2-list-navigation.md)也已完成实际验收：前端17项、构建、浏览器筛选网址/刷新/前进后退/正文返回位置与手机流程通过，真实个人库8组只读检查通过，五表校验值未变化，详见[B2验收记录](docs/V2-LIST-NAVIGATION-ACCEPTANCE.md)。第二轮已完成并纳入v0.7.0阶段性预发布；下一轮文章阅读体验，云部署仍后置。

归档页路径为 `/archive`；公开接口 `/api/articles/archive` 返回月份及文章数，列表接口支持可选的 `month=YYYY-MM` 参数。归档只包含已发布文章，以首次发布时间归属年月。

公开文章列表 `/api/articles` 现在返回分页对象：`items`、`total`、`page`、`size`、`totalPages`；默认第一页、每页 10 篇。升级时需同步前后端，旧数组解析方式不再适用。详情及管理员文章接口的返回格式不变。

## 环境准备

需要 Java 17、Node.js 20.19+ 或 22.12+ 和 npm，以及 MySQL（InnoDB、utf8mb4）。可先执行 `java -version` 和 `node --version` 检查；IDEA 的运行 JDK 和终端使用的 Java 可能不同。

以下命令在项目根目录 `E:\PersonalWebsite2` 执行，其他安装位置请替换该路径。

## 新安装：初始化数据库

先连接 MySQL，再启动后端。数据库初始化不是由 Spring Boot 自动完成的。新安装按以下顺序执行全部脚本：

| 顺序 | 脚本 | 作用 |
| --- | --- | --- |
| 1 | `database/000-init-article.sql` | 创建 `personal_website` 数据库和初始 `article` 表 |
| 2 | `database/001-resource-link.sql` | 创建资源链接表 |
| 3 | `database/002-article-category.sql` | 创建分类表，给文章增加分类字段、索引和外键 |
| 4 | `database/003-tag.sql` | 创建标签表 |
| 5 | `database/004-article-tag.sql` | 创建文章与标签的关联表 |
| 6 | `database/005-article-presentation.sql` | 新增封面地址、置顶与更新时间 |

使用 MySQL 命令行时，在 PowerShell 运行：

```powershell
mysql --default-character-set=utf8mb4 -u root -p
```

在随后打开的 MySQL 提示符中执行（`SOURCE` 是 MySQL 命令，不是 PowerShell 命令）：

```sql
SOURCE E:/PersonalWebsite2/database/000-init-article.sql;
SOURCE E:/PersonalWebsite2/database/001-resource-link.sql;
SOURCE E:/PersonalWebsite2/database/002-article-category.sql;
SOURCE E:/PersonalWebsite2/database/003-tag.sql;
SOURCE E:/PersonalWebsite2/database/004-article-tag.sql;
USE personal_website;
SOURCE E:/PersonalWebsite2/database/005-article-presentation.sql;
SHOW TABLES;
```

结果应包含 `article`、`resource_link`、`category`、`tag`、`article_tag` 五张表。也可用 IDEA 数据库工具或其他 MySQL 客户端，按表格顺序打开并执行脚本。初始化账号需要建库、建表、建索引和外键权限；应用账号需要访问这些表的读写权限。

脚本默认数据库名为 `personal_website`。使用其他库名时，统一替换脚本中的库名，执行005前确认选中目标库，再将 `DB_URL` 指向该库。

## 已有数据库：升级

先备份数据库，并确认当前表结构，再执行尚未应用的脚本。脚本属于一次性迁移，不能重复执行；任何一步报错都应先处理，再继续。

- 从 `v0.1.0` 升级：已有初始文章表，依次执行 `001`～`005`；如果部分脚本已经应用，跳过对应项。
- 从 `v0.2.0` 升级：已有文章、资源、分类表，依次执行 `003`、`004`、`005`。
- 从 `v0.3.0`～`v0.6.0` 升级到当前第二版源码：备份后执行 `005-article-presentation.sql`，再同步更新并重启前后端。
- 本地已完成005：跳过已执行脚本。可用 `SHOW COLUMNS FROM article` 核对 `cover_url`、`pinned`、`updated_at` 三列。

已有数据库升级时跳过 `000-init-article.sql`。分类升级后，已有文章的 `category_id` 为 `NULL`，页面显示“未分类”；标签升级不会修改现有文章，没有关联记录的文章显示为无标签。

## 配置并启动后端

先完成上述数据库步骤。配置示例引用四个环境变量：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`ADMIN_PASSWORD`。其中 `ADMIN_PASSWORD` 是 `admin` 账号的登录密码。真实密码只放在本地环境变量或被忽略的配置文件中。

首次克隆后复制配置示例；如果已有 `application.properties`，跳过复制命令：

```powershell
cd E:\PersonalWebsite2\personalwebsite
Copy-Item src/main/resources/application.properties.example src/main/resources/application.properties
```

使用 PowerShell 时，在同一个终端设置环境变量后启动：

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/personal_website?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME = '替换为你的数据库账号'
$env:DB_PASSWORD = '替换为你的数据库密码'
$env:ADMIN_PASSWORD = '替换为你的管理员密码'
.\mvnw.cmd spring-boot:run
```

使用 IDEA 时，在 `PersonalwebsiteApplication` 的运行配置中选择 Java 17，并在“环境变量”中设置这四项，再启动应用。如果连接信息已直接写在本地配置中，只需设置该文件仍引用的环境变量。

PowerShell 的环境变量仅对当前终端和它启动的进程生效，不会自动传给已经打开的 IDEA。后端默认地址为 `http://localhost:8080`。

## 启动前端

另开一个 PowerShell 终端：

```powershell
cd E:\PersonalWebsite2\frontend
npm ci
npm run dev
```

打开 Vite 输出的地址。进入“管理”，使用账号 `admin` 和上面设置的管理员密码登录。

## 验证与构建

前端回归测试和生产构建：

```powershell
cd E:\PersonalWebsite2\frontend
node --test tests/category-flow.test.mjs tests/presentation-flow.test.mjs tests/navigation-flow.test.mjs
npm run build
```

后端测试与构建（Java 17，配置好数据库及上述环境变量）：

```powershell
cd E:\PersonalWebsite2\personalwebsite
.\mvnw.cmd test
.\mvnw.cmd package
```

后端流程测试连接真实 MySQL，需要完整的五张表。测试中的写入通过事务回滚，MySQL 自增编号仍可能前进；建议使用独立测试数据库。`CategoryArticleFlowTests` 使用的固定密码仅属于测试上下文。

最新 `v0.7.0` 预发布包含第二版首页、文章展示字段、公开统计与列表导航，完整说明见 `docs/RELEASE-v0.7.0.md`；此前版本见 `docs/RELEASE-v0.6.0.md`。从 v0.6.0 升级需备份后执行尚未应用的 `005-article-presentation.sql`，已完成005时跳过；同步更新并重启前后端。Release 提供源码下载，运行时仍需自行准备环境、数据库及本地配置。
