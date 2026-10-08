# PersonalWebsite2

个人网站第二版，包含 Vue/Vite 前端和 Spring Boot/MySQL 后端。当前支持文章阅读、Markdown、草稿与发布、管理员认证、资源链接管理、分类、文章多标签及栏目＋分类＋标签组合筛选。

## 项目结构

- `frontend/`：前端应用及回归测试。
- `personalwebsite/`：后端应用及真实 MySQL 接口测试。
- `database/`：按编号执行的数据库初始化与升级脚本。
- `docs/`：手敲指南和项目进度。`05-tag-filter.md` 对应的标签筛选已完成，自动化测试通过。

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
SHOW TABLES;
```

结果应包含 `article`、`resource_link`、`category`、`tag`、`article_tag` 五张表。也可用 IDEA 数据库工具或其他 MySQL 客户端，按表格顺序打开并执行脚本。初始化账号需要建库、建表、建索引和外键权限；应用账号需要访问这些表的读写权限。

脚本默认数据库名为 `personal_website`。使用其他库名时，先将五份脚本中的库名统一替换，再将 `DB_URL` 指向该库。

## 已有数据库：升级

先备份数据库，并确认当前表结构，再执行尚未应用的脚本。脚本属于一次性迁移，不能重复执行；任何一步报错都应先处理，再继续。

- 从 `v0.1.0` 升级：已有初始文章表，依次执行 `001`、`002`、`003`、`004`；如果部分脚本已经应用，跳过对应项。
- 从 `v0.2.0` 升级：已有文章、资源、分类表，只执行 `003-tag.sql` 和 `004-article-tag.sql`。
- 从 `v0.3.0` 升级：数据库结构不变，更新代码并重启前后端即可。
- 本地已完成标签表和关联表：数据库无需再次执行脚本。

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
node --test tests/category-flow.test.mjs
npm run build
```

后端测试与构建（Java 17，配置好数据库及上述环境变量）：

```powershell
cd E:\PersonalWebsite2\personalwebsite
.\mvnw.cmd test
.\mvnw.cmd package
```

后端流程测试连接真实 MySQL，需要完整的五张表。测试中的写入通过事务回滚，MySQL 自增编号仍可能前进；建议使用独立测试数据库。`CategoryArticleFlowTests` 使用的固定密码仅属于测试上下文。

最新版本验证记录见 `docs/RELEASE-v0.4.0.md`，此前版本见 `docs/RELEASE-v0.3.0.md`。Release 提供源码下载，运行时仍需自行准备环境和数据库。
