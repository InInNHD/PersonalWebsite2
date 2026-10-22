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

第三轮[第14节3A阅读基础](docs/14-v2-article-reading.md)已完成实际验收：文章头部、正文布局、唯一章节锚点与目录、约计阅读时长、阅读进度和回顶均正常。实际前端23项及构建通过，浏览器长文/四档宽度/错误恢复/B2返回检查通过，真实个人库公开详情、草稿404及五表校验值复核通过，见[3A验收记录](docs/V2-READING-FOUNDATION-ACCEPTANCE.md)。[第15节3B](docs/15-v2-reading-media.md)也已完成实际验收：代码高亮、语言标签、复制反馈和正文图片查看正常；实际28项前端测试、构建、四档宽度和3A/B2回归通过，见[3B验收记录](docs/V2-READING-MEDIA-ACCEPTANCE.md)。[第16节3C](docs/16-v2-reading-neighbors.md)及第三轮本地功能验收也已通过：实际前端34项、隔离MySQL后端18项及两端构建通过；整轮目录/代码/图片/前后篇组合、错误恢复、四档宽度、原筛选及629px位置恢复正常，个人库3篇公开导航和草稿404核对通过，五表校验值未变化。见[第三轮验收记录](docs/V2-READING-NAVIGATION-ACCEPTANCE.md)。第三轮已纳入v0.8.0阶段性预发布；后续第四轮内容导航与个人展示。

第四轮按4A分类/标签导航、4B归档与公开日记时间线、4C个人展示拆分。[第17节4A](docs/17-v2-topics.md)已完成实际验收：内容地图、公开计数、分类标签入口、主题分页与连续阅读返回正常；实际前端41项、隔离MySQL后端21项、两端构建、7组HTTP及四档宽度浏览器检查通过。个人库公开主题计数与数据库一致，五表校验值未变，业务代码无需修正，见[4A验收记录](docs/V2-TOPICS-ACCEPTANCE.md)。本节无迁移和新依赖，尚非第四轮新Release节点。[第18节4B](docs/18-v2-timeline.md)也已完成实际验收：前端46项、隔离MySQL后端24项、两端构建、7组HTTP及浏览器跨年/分页/连续阅读返回/错误重试/手机流程通过，个人库只读核对正常，见[4B验收记录](docs/V2-TIMELINE-ACCEPTANCE.md)。[第19节4C](docs/19-v2-profile-projects.md)及第四轮组合验收已于2026-10-21通过：实际前端54项、隔离MySQL后端24项、两端构建、7组HTTP和浏览器流程正常；6个公开项目及推送19条/Release17条/历史提交116条已生成本站快照。个人库零写入，五表校验值未变，详见[4C与整轮验收](docs/V2-PROFILE-ACCEPTANCE.md)。第四轮已纳入v0.9.0阶段性预发布，[发布说明](docs/RELEASE-v0.9.0.md)包含完整更新与验收范围。第五轮写作后台也已完成，第六轮资源与图片管理也已完成，第七轮完整状态与内容打磨也已完成，下一阶段为第八轮第二版候选验收。

归档页为 `/archive`，独立公开日记页为 `/diary`；公开接口 `/api/articles/archive` 返回月份及文章数，可选 `type=DIARY` 只统计公开日记。列表接口复用 `month=YYYY-MM`、`type=DIARY` 和分页参数。两页网址保存月份/页码，正文及连续阅读保留来源与返回位置；只包含已发布文章，以首次发布时间归属年月。

公开文章列表 `/api/articles` 现在返回分页对象：`items`、`total`、`page`、`size`、`totalPages`；默认第一页、每页 10 篇。升级时需同步前后端，旧数组解析方式不再适用。公开详情格式不变；第五轮管理员文章列表也已改为分页对象，后台详情新增 `deletedAt`，需同步升级前后端。

第五轮5A/5B/5C已按用户授权直接实现并完成统一验收：独立登录与后台布局、组合筛选和服务端分页、Markdown编辑器、分类标签站内表单、撤回/软回收/恢复、未保存保护及原页重新验证。实际前端60项、隔离真实MySQL后端28项及两端构建通过，完整浏览器流程和四档宽度检查通过；个人库备份后已执行006，原有数据逐字段一致。见[使用及升级说明](docs/21-v2-admin-workflow.md)与[统一验收记录](docs/V2-ADMIN-WORKFLOW-ACCEPTANCE.md)。已发布[v0.10.0阶段性预发布](docs/RELEASE-v0.10.0.md)；第20节为历史5A指南，不再用于覆盖新后台。

第六轮已完成并验收：资源分组/组内搜索、本站SVG图标和推荐理由、后台分组排序、本地PNG/JPEG图片库、封面/正文插入、媒体公开隔离，以及数据库＋配置＋媒体备份恢复。实际前端67项、独立真实MySQL后端32项、两端构建、四档宽度和恢复演练通过。个人库已备份后完成007，原五表原字段和行数据一致。见[使用及升级说明](docs/22-v2-resources-media.md)、[统一验收记录](docs/V2-RESOURCES-MEDIA-ACCEPTANCE.md)。已发布[v0.11.0阶段性预发布](docs/RELEASE-v0.11.0.md)。

第七轮已直接实现并完成统一验收：统一加载骨架、空内容/无结果、错误重试和404，动态页面/文章标题与基础描述、键盘焦点及Escape返回；修复旧文章404覆盖新正文和未知深层文章路径等待。实际前端74项、构建、52组四档页面检查及故障恢复通过；个人库仅只读核对，数据校验值未变。21个Java源码/测试与上轮一致，本轮重新打包但未重跑32项后端测试。见[第23节使用说明](docs/23-v2-page-states.md)与[验收记录](docs/V2-PAGE-STATES-ACCEPTANCE.md)。第七轮已纳入[v0.12.0阶段性预发布](docs/RELEASE-v0.12.0.md)；无迁移，不要重跑007。下一第八轮整站候选验收，云部署仍后置。

## GitHub公开项目资料同步

关于页读取`frontend/public/github-profile.json`，该公开快照随源码提交。首次生成及每次推送/发布后，在项目根目录运行：

```powershell
node frontend/scripts/sync-github.mjs
```

命令使用`siteProfile.githubName`读取全部公开仓库及可核对更新；完整成功才替换快照，失败保留旧文件。生成后重新构建前端。访客浏览无需GitHub token或Java接口；可选token只放本机同步进程环境，不写入前端配置或JSON。推送、Release、历史提交分别记录日期，未保存且已超出GitHub活动窗口的推送无法恢复。

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
| 7 | `database/006-article-recycle.sql` | 新增文章回收时间及可见性索引 |
| 8 | `database/007-resources-media.sql` | 资源分组/图标/推荐理由，媒体及文章关联表 |

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
SOURCE E:/PersonalWebsite2/database/006-article-recycle.sql;
SOURCE E:/PersonalWebsite2/database/007-resources-media.sql;
SHOW TABLES;
```

结果应包含 `article`、`resource_link`、`category`、`tag`、`article_tag`、`resource_group`、`media_asset`、`article_media` 八张表。也可用 IDEA 数据库工具或其他 MySQL 客户端，按表格顺序打开并执行脚本。初始化账号需要建库、建表、建索引和外键权限；应用账号需要访问这些表的读写权限。图片目录需要当前Java进程具备读写权限，不能在Web服务器中直接公开为静态目录。

脚本默认数据库名为 `personal_website`。使用其他库名时，统一替换脚本中的库名，执行005～007前确认选中目标库，再将 `DB_URL` 指向该库。

## 已有数据库：升级

先备份数据库，并确认当前表结构，再执行尚未应用的脚本。脚本属于一次性迁移，不能重复执行；任何一步报错都应先处理，再继续。

- 从 `v0.1.0` 升级：已有初始文章表，依次执行 `001`～`007`；如果部分脚本已经应用，跳过对应项。
- 从 `v0.2.0` 升级：已有文章、资源、分类表，依次执行 `003`、`004`、`005`、`006`、`007`。
- 从 `v0.3.0`～`v0.6.0` 升级到当前第二版源码：备份后依次执行 `005-article-presentation.sql`、`006-article-recycle.sql`、`007-resources-media.sql`，再同步更新并重启前后端。
- 从 `v0.7.0`～`v0.9.0` 升级：备份后依次执行一次 `006-article-recycle.sql`、`007-resources-media.sql`。
- 从 `v0.10.0` 升级：备份后执行一次 `007-resources-media.sql`，同步更新前后端。
- 本工作区个人库已完成005、006和007：跳过已执行脚本。可用 `SHOW COLUMNS FROM article` 核对 `cover_url`、`pinned`、`updated_at`、`deleted_at`。第六轮迁移前备份在 `backups/20261021-sixth-round-personal-233702`；原有五表原字段和行数据逐字段一致。

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
npm.cmd run dev
```

打开 Vite 输出的地址。进入“管理”或 `/admin`，独立登录页只需输入管理员密码，账号固定为 `admin`。密码仅保存在当前标签页内存，刷新需重新登录。文章管理、分类标签和资源各有独立入口。

## 验证与构建

前端回归测试和生产构建：

```powershell
cd E:\PersonalWebsite2\frontend
node --test tests/*.test.mjs
npm.cmd run build
```

后端测试必须使用独立的真实MySQL库，先在专用库应用000～007并配置Java17与本地环境变量；不要对个人内容库执行写入测试。`AdminWorkflowTests` 和 `ResourceMediaFlowTests` 会强制校验库名前缀为 `pw_v2admin_acceptance_`，因此直接使用个人库运行test/package会被拒绝。

```powershell
cd E:\PersonalWebsite2\personalwebsite
# 确认当前终端的DB_URL指向已初始化的pw_v2admin_acceptance_开头专用库
.\mvnw.cmd test
.\mvnw.cmd package
```

导航、主题、归档日记共9项专项位于 `docs/checks/ArticleNavigationGuideTests.java`、`TopicGuideTests.java` 和 `TimelineGuideTests.java`，在适配专用库名前缀的隔离副本中执行；上述Maven命令运行 `personalwebsite/src/test` 内的23项测试。本轮32项包含23项实际测试与这9项专项，范围见 `docs/V2-RESOURCES-MEDIA-ACCEPTANCE.md`。

测试写入通过事务回滚，但MySQL自增编号仍可能前进。测试中的固定密码仅属于隔离测试上下文，不是实际管理员密码。

最新 `v0.12.0` 预发布包含统一页面状态、404、动态元信息与键盘体验，完整说明见 `docs/RELEASE-v0.12.0.md`；此前版本见 `docs/RELEASE-v0.11.0.md`。从v0.11.0升级只需更新并重新构建/启动前端，无新增依赖或SQL迁移，后端可沿用；更早安装仍需补齐尚未执行的迁移。本机已完成006和007，不要重复执行。Release提供源码，不包含个人数据库、配置、备份或上传图片，运行及恢复时需自行准备匹配的数据和媒体目录。

第五轮、第六轮分别纳入v0.10.0和v0.11.0；第七轮纳入本轮v0.12.0。下一第八轮整站候选验收，云部署仍后置。

本地图片通过 `/api` 同源代理。`app.media.directory` 默认相对Java工作目录，建议按[第22节](docs/22-v2-resources-media.md)设置绝对路径；备份必须同时包含该媒体目录。备份脚本为 `scripts/local-backup.py`，Windows建议Python3.12+，示例及隔离恢复流程见第22节。文章保存请求新增 `bodyMediaUrls` 显式授权列表，当前前端自动生成，其他客户端也需同步适配本站媒体。
