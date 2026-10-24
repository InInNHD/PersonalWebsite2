# PersonalWebsite2 · 萤火手记

基于Vue 3、Java 17 / Spring Boot与MySQL的个人网站，前后端分离。本地开发完成后再单独规划云部署。

## 当前版本

当前阶段预发布 **v3.0.0-alpha.2**（2026-10-24），前后端构建版本均为 **3.0.0-alpha.2**。它包含第三版第三轮正文搜索与阅读体验；上一版为v3.0.0-alpha.1。第三版RC/正式版和云部署尚未完成，第二版正式v2.0.0也未发布。

[当前Alpha发布说明](docs/RELEASE-v3.0.0-alpha.2.md) · [上一版Alpha说明](docs/RELEASE-v3.0.0-alpha.1.md) · [上一版RC说明](docs/RELEASE-v2.0.0-rc.1.md) · [正式版说明草稿](docs/RELEASE-v2.0.0.md) · [发布准备与待办](docs/V2-RELEASE-READINESS.md) · [整站验收](docs/V2-FINAL-ACCEPTANCE.md) · [项目历史](docs/PROJECT-PROGRESS.md)

整站技术验收：前端74项、专用MySQL后端32项、恢复工具5项，64组四档宽度和桌面Edge复核，以及新安装、升级、数据库与媒体恢复通过。用户已认可暗色风格；实体手机按用户要求暂缓，作为未验证限制保留；正式内容草稿待审阅。GitHub资料已成功刷新；云部署另行规划。

## 功能

开发进度：[第三版差距核对与改进计划](docs/V3-IMPROVEMENT-PLAN.md)。已发布的v3.0.0-alpha.1包含第二轮首页、文章库与内容组织。工作区继续完成第三轮正文搜索、匹配片段、安全高亮、字号与专注阅读、干净分享链接；前端87项、真实MySQL42项、8组HTTP/产物检查及两端构建通过，12组响应式页面检查通过。第三轮纳入v3.0.0-alpha.2阶段预发布，无需SQL迁移，个人数据未改动。见[使用指南](docs/33-v3-search-reading.md)和[验收报告](docs/V3-ROUND3-ACCEPTANCE.md)。

第三版第一轮[独立交互样稿](docs/design-v3/README.md)继续用于设计对照，端口5188；真实业务按通常方式启动前后端。第二轮配置、升级和验收见[内容组织指南](docs/32-v3-content-organization.md)和[验收报告](docs/V3-ROUND2-ACCEPTANCE.md)。无需新增SQL，精选与专题默认留空，不会自动发布个人草稿。

- 独立首页和文章库、精选、学习专题顺序阅读、项目实践笔记；置顶/封面、Markdown正文、目录、代码高亮/复制、阅读进度、图片查看和连续阅读。
- 分类/多标签、标题/摘要/正文搜索与安全命中片段、分页、归档、公开精选日记、资源分组及个人项目展示。
- 阅读字号记忆、专注模式、章节位置保持和不含浏览状态的文章链接复制。
- 独立管理员工作台，草稿/发布/撤回/回收/恢复、编辑预览、未保存保护。
- 本地图片校验和公开隔离，数据库/源码配置/媒体离线备份与恢复失败回滚。
- 响应式暗色界面、加载/空/错误/404状态、动态标题和键盘入口。

## 目录

| 路径 | 内容 |
| --- | --- |
| frontend/ | Vue应用、公开GitHub快照及前端测试 |
| personalwebsite/ | Spring Boot后端与真实MySQL测试 |
| database/ | 000～007一次性初始化及升级脚本 |
| scripts/ | 本机备份恢复工具及标准库测试 |
| docs/ | 使用指南、验收报告、设计与阶段历史 |

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
- 已完成007的安装（v0.11.0及以后）：无需新迁移，只更新对应代码与产物；当前本机已完成007，不要重复执行。

已有数据库升级时跳过 `000-init-article.sql`。分类升级后，已有文章的 `category_id` 为 `NULL`，页面显示“未分类”；标签升级不会修改现有文章，没有关联记录的文章显示为无标签。

## 配置并启动后端

先完成上述数据库步骤。配置示例引用四个环境变量：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`ADMIN_PASSWORD`。其中 `ADMIN_PASSWORD` 是 `admin` 账号的登录密码。真实密码只放在本地环境变量或被忽略的配置文件中。

首次克隆后复制配置示例；如果已有 `application.properties`，跳过复制命令：

```powershell
cd E:\PersonalWebsite2\personalwebsite
if (-not (Test-Path src/main/resources/application.properties)) {
    Copy-Item src/main/resources/application.properties.example src/main/resources/application.properties
}
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
npm.cmd ci
npm.cmd run dev
```

打开 Vite 输出的地址。进入“管理”或 `/admin`，独立登录页只需输入管理员密码，账号固定为 `admin`。密码仅保存在当前标签页内存，刷新需重新登录。文章管理、分类标签和资源各有独立入口。

## 测试与构建

在根目录运行前端和工具测试：

```powershell
node --test frontend/tests/*.test.mjs
python -m unittest discover -s scripts/tests -v
npm.cmd --prefix frontend run build
```

后端测试需要已初始化000～007的独立真实MySQL库。配置DB_URL到 `pw_v2admin_acceptance_` 开头的专用库；应用账号只给该库读写权限，不允许访问个人库。再进入personalwebsite运行 `.\mvnw.cmd test`。测试会写入数据、自增编号可能前进，不能对个人内容库执行。当前常规src/test有33项；第三版第三轮42项还包含docs/checks中的9项导航、主题及时间线专项，执行范围见docs/V3-ROUND3-ACCEPTANCE.md。

发布JAR必须在不含真实配置的干净副本打包，将示例配置复制为application.properties后使用环境变量。执行 `.\mvnw.cmd -DskipTests package` 得到 `target/personalwebsite-2.0.0-rc.1.jar`；此命令跳过测试，不能作为测试通过的依据。前端dist需使用HTTP服务、SPA页面回退及同源/api代理；API错误不能回退成HTML。

## GitHub公开资料

关于页读取frontend/public/github-profile.json。每次推送或Release后在根目录运行：

```powershell
node frontend/scripts/sync-github.mjs
```

完整成功才替换快照，再构建前端；失败保留旧文件。推送、Release、历史提交区分记录，超出公开活动窗口且从未保存的推送不能恢复。2026-10-23已成功同步7个公开项目、172条更新记录（推送25、Release 21、历史提交126）。全部公开仓库保留，fork历史提交在API端按本人过滤；原创仓库仍遍历公开分支历史。可选GITHUB_TOKEN仅供本机同步进程使用，不写入前端、JSON或Git。访客无需令牌。

## 图片、备份与恢复

建议使用MEDIA_DIRECTORY指定绝对路径，避免随Java工作目录变化。图片通过受控/api接口读取，不能直接公开磁盘目录。保存文章的bodyMediaUrls明确授权正文媒体，前端自动生成。

备份前停止数据库和媒体写入。scripts/local-backup.py需要Python3.12+及MySQL客户端，包含SQL、工作树源码、配置与媒体；配置和内容只留本机。恢复只能使用不存在的pw_media_restore_专用库和不存在的媒体目录，核对完整性后再规划业务切换。

[图片及备份使用说明](docs/22-v2-resources-media.md) · [整站安装升级与恢复说明](docs/24-v2-final-acceptance.md)

GitHub源码、前端ZIP和JAR均不包含个人数据库、真实配置、备份或上传图片，不能代替内容备份。开发历史和手敲指南保留在docs/，不要用早期指南覆盖当前业务文件。


## 正式内容与手机验收

站点介绍已应用，3篇文章已保存为未公开草稿，见[录入状态](docs/V2-CONTENT-ENTRY.md)。完整文章草稿文档仅保留本机，不随Git或Release附件发布；数据库内容也不包含在源码中。[实体手机操作清单](docs/V2-PHONE-ACCEPTANCE.md)仍未执行，按用户要求作为本次RC的已知限制保留。
