# 第十轮：本地备份与恢复演练

本轮从已发布的 v0.6.0（5c24d4c）继续，目标是获得一份可以恢复的数据库、源码和个人配置备份。

**2026-10-15：按你的授权，助手已执行首次个人备份、恢复及应用验收，全部通过。备份目录为 `E:\PersonalWebsite2\backups\20261015-162523`，详细记录见 [备份恢复验收](BACKUP-RESTORE-ACCEPTANCE.md)。下列命令保留供今后重复备份使用。**

## 1. 要保存什么

| 内容 | 保存方式 | 恢复用途 |
| --- | --- | --- |
| 五张表的结构和数据 | `database.sql` | 文章、日记草稿、资源、分类、标签及关联记录 |
| 当前已提交的源码 | `source.zip`、`commit.txt` | 取得与备份对应的程序版本 |
| 本地后端配置 | `application.properties` | 找回数据库连接及管理员配置方式 |
| 配置引用的环境变量 | 需要时手动创建 `private-env.txt` | 找回 `${...}` 引用的实际值 |
| 个人介绍 | `siteProfile.js` | 保存当前昵称、介绍和 GitHub 链接 |
| 数据核对信息 | `before.txt`、`SHA256SUMS.txt` | 检查文件完整性及恢复后的内容 |

文件保存在 `E:\PersonalWebsite2\backups\时间戳\`。助手已在根目录 `.gitignore` 加入 `/backups/`。备份包含草稿日记及私密配置，留在本地保管；以后应另外复制到你控制的独立存储设备。与项目放在同一磁盘的副本不能防止整块磁盘损坏。

本轮先停止 Java 后端，完成备份和数据比对期间暂停写作、资源修改及数据库结构调整。MySQL 服务保持运行。

## 2. 在同一个 PowerShell 终端准备变量

打开 IDEA 底部“终端”，确认使用 PowerShell。下面所有 PowerShell 代码在同一个终端按顺序执行，变量会供后面的步骤使用。代码块外面的三个反引号不要输入。

本机目前的客户端目录为 `C:\Program Files\MySQL\MySQL Server 8.0\bin`。用户名、地址和端口按你当前 IDEA 数据库连接填写；示例使用本机 3306 和 root。

```powershell
cd E:\PersonalWebsite2

$ErrorActionPreference = 'Stop'
$mysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$mysqlExe = Join-Path $mysqlBin 'mysql.exe'
$dumpExe = Join-Path $mysqlBin 'mysqldump.exe'
$sourceDb = 'personal_website'
$dbHost = '127.0.0.1'
$dbPort = 3306
$dbUser = 'root'

# 时间戳目录每次新建；如果同名目录已经存在，停止并重新生成变量。
$backupStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupDir = Join-Path 'E:\PersonalWebsite2\backups' $backupStamp
if (Test-Path -LiteralPath $backupDir) { throw '备份目录已存在，请重新生成时间戳。' }
New-Item -ItemType Directory -Path $backupDir | Out-Null

& $mysqlExe --version
if ($LASTEXITCODE -ne 0) { throw 'mysql 客户端无法运行。' }
& $dumpExe --version
if ($LASTEXITCODE -ne 0) { throw 'mysqldump 客户端无法运行。' }

# --password 后面不写值，MySQL 会在需要时提示输入密码。
$connectionArgs = @(
  "--host=$dbHost"
  "--port=$dbPort"
  "--user=$dbUser"
  '--password'
  '--default-character-set=utf8mb4'
)
```

应看到两个客户端的版本，以及新建的时间戳目录。后面提示 `Enter password:` 时，输入的是 **MySQL 连接密码**，不是网页管理端密码；输入过程不显示字符是正常的。后续命令可能分别询问密码。

## 3. 保存备份前的数据核对结果

把以下整段写入终端。`@'` 和 `'@` 是 PowerShell 多行字符串标记，结束的 `'@` 单独顶格写一行。

```powershell
$verifySql = @'
SELECT 'article' AS table_name, COUNT(*) AS row_count FROM article
UNION ALL SELECT 'resource_link', COUNT(*) FROM resource_link
UNION ALL SELECT 'category', COUNT(*) FROM category
UNION ALL SELECT 'tag', COUNT(*) FROM tag
UNION ALL SELECT 'article_tag', COUNT(*) FROM article_tag;
CHECKSUM TABLE article, resource_link, category, tag, article_tag EXTENDED;
'@

& $mysqlExe @connectionArgs "--database=$sourceDb" '--batch' '--skip-column-names' "--execute=$verifySql" |
  Set-Content -LiteralPath (Join-Path $backupDir 'before.txt') -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw '备份前的数据检查失败。' }

Get-Content -LiteralPath (Join-Path $backupDir 'before.txt')
```

输出前五行是每张表的记录数，后五行是带库名前缀的表名和内容校验值。记录数按你的实际内容变化，不要求等于某个固定数字。任意表的校验值为 `NULL` 或命令报错时，先处理原因再继续。

`CHECKSUM TABLE ... EXTENDED` 会读取表中数据，适合本轮的小型个人数据库。在同一 MySQL 服务上恢复并保留相同表结构后，校验值应一致；它不应作为跨 MySQL 版本、跨存储格式迁移时的唯一判据。[MySQL CHECKSUM TABLE 文档](https://dev.mysql.com/doc/refman/8.0/en/checksum-table.html)

## 4. 导出数据库

继续在同一个终端执行：

```powershell
$sqlFile = Join-Path $backupDir 'database.sql'
$dumpArgs = $connectionArgs + @(
  '--single-transaction'
  '--quick'
  '--no-tablespaces'
  '--column-statistics=0'
  '--set-gtid-purged=OFF'
  "--result-file=$sqlFile"
  $sourceDb
)

& $dumpExe @dumpArgs
if ($LASTEXITCODE -ne 0) { throw '数据库导出失败，本次目录不能视为有效备份。' }
if ((Get-Item -LiteralPath $sqlFile).Length -eq 0) { throw '导出文件为空。' }

# 确认转储没有携带创建数据库或切换数据库的命令。
# 这样之后才能明确导入新建的演练库。
$databaseCommands = Select-String -LiteralPath $sqlFile -Pattern '^CREATE DATABASE', '^USE '
if ($databaseCommands) { throw '导出文件含数据库切换命令，请停止并检查导出参数。' }

Get-Item -LiteralPath $sqlFile | Select-Object Name, Length, LastWriteTime
```

参数说明：

- `--single-transaction` 为当前 InnoDB 表取得一致的数据快照；备份期间保持表结构稳定。
- `--quick` 逐行读取数据。
- `--no-tablespaces` 省去表空间信息。
- `--column-statistics=0` 关闭本轮不需要的列统计导出，避免服务端不支持时出现警告。
- `--set-gtid-purged=OFF` 适用于这次本地单库备份和新库恢复。
- `--result-file` 让 MySQL 直接写文件，避免旧版 Windows PowerShell 输出重定向影响 SQL 文件编码。
- 最后的 `$sourceDb` 是单个数据库名；本轮使用不带 `CREATE DATABASE`、`USE` 的导出方式，恢复目标由后续命令指定。

以上参数依据 [MySQL mysqldump 文档](https://dev.mysql.com/doc/refman/8.0/en/mysqldump.html)。SQL 中会包含建表与恢复数据的语句，后续只向新建的空演练库导入。

## 5. 保存源码和配置

```powershell
git archive --format=zip "--output=$backupDir\source.zip" HEAD
if ($LASTEXITCODE -ne 0) { throw '源码归档失败。' }
git rev-parse HEAD | Set-Content -LiteralPath (Join-Path $backupDir 'commit.txt') -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw '读取源码提交号失败。' }
git status --short | Set-Content -LiteralPath (Join-Path $backupDir 'working-tree.txt') -Encoding utf8

Copy-Item -LiteralPath 'personalwebsite\src\main\resources\application.properties' -Destination $backupDir
Copy-Item -LiteralPath 'frontend\src\siteProfile.js' -Destination $backupDir
```

`source.zip` 是当前 **已提交版本** 的源码。`working-tree.txt` 记录备份时的未提交文件；未提交的业务修改需要另行保存后再将此目录作为完整的源码备份。本轮在 v0.6.0 发布后进行，新增的操作指南和 `.gitignore` 变更不影响已发布程序的恢复。

检查备份目录中的 `application.properties`：

- 配置项已经是实际值的，由该文件保存。
- 配置中仍有 `${DB_URL}`、`${DB_USERNAME}`、`${DB_PASSWORD}` 或 `${ADMIN_PASSWORD}` 的，在备份目录下手动新建 `private-env.txt`，记录对应变量名和实际值。
- 若变量只在 IDEA 的运行配置中设置，从该运行配置核对。空白值或“替换为你的密码”等样稿不能作为恢复资料。

`private-env.txt` 由你在本机编辑，无需贴到对话。恢复时按该文件重新设置环境变量；不要把其中内容直接当作 PowerShell 脚本运行。

配置补齐后生成文件校验清单：

```powershell
$backupFiles = @('database.sql', 'source.zip', 'commit.txt', 'working-tree.txt', 'application.properties', 'siteProfile.js', 'before.txt')
if (Test-Path -LiteralPath (Join-Path $backupDir 'private-env.txt')) {
  $backupFiles += 'private-env.txt'
}

$hashLines = foreach ($name in $backupFiles) {
  $fileHash = (Get-FileHash -LiteralPath (Join-Path $backupDir $name) -Algorithm SHA256).Hash
  "$fileHash  $name"
}
$hashLines | Set-Content -LiteralPath (Join-Path $backupDir 'SHA256SUMS.txt') -Encoding utf8
```

SHA-256 用于发现备份文件是否改变，不会加密文件，也不能替代数据库恢复检查。

## 6. 恢复到新的演练库

下面的新库名统一以 `pw_restore_check_` 开头，不复用 `personal_website`。

```powershell
$restoreDb = 'pw_restore_check_' + (Get-Date -Format 'yyyyMMddHHmmss')
if ($restoreDb -notmatch '^pw_restore_check_[0-9]{14}$') { throw '演练库名称异常。' }

# 不使用 IF NOT EXISTS：同名库已存在时停止，避免导入已有数据。
$createSql = "CREATE DATABASE $restoreDb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
& $mysqlExe @connectionArgs "--execute=$createSql"
if ($LASTEXITCODE -ne 0) { throw '演练库创建失败，请停止。' }

# SOURCE 是 mysql 客户端命令；将本次路径转换为正斜杠。
$sourcePath = $sqlFile.Replace('\', '/')
& $mysqlExe @connectionArgs "--database=$restoreDb" "--execute=SOURCE $sourcePath"
if ($LASTEXITCODE -ne 0) { throw '恢复失败，请保留当前目录和演练库以便检查。' }
```

首次演练按本指南路径操作，路径不含空格。需要改成含空格的目录时，先让助手核对 `SOURCE` 的路径处理方式。

这里直接恢复 SQL 中已经包含的表结构，演练库无需预先执行 000～004 初始化脚本。[MySQL SQL 格式备份恢复文档](https://dev.mysql.com/doc/refman/8.0/en/reloading-sql-format-dumps.html)

## 7. 比较恢复后的数据

```powershell
& $mysqlExe @connectionArgs "--database=$restoreDb" '--batch' '--skip-column-names' "--execute=$verifySql" |
  Set-Content -LiteralPath (Join-Path $backupDir 'after.txt') -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw '恢复后的数据检查失败。' }

# CHECKSUM 输出会带数据库名前缀，先消除正常的库名差异。
$before = (Get-Content -LiteralPath (Join-Path $backupDir 'before.txt')) -replace ([regex]::Escape("$sourceDb.")), ''
$after = (Get-Content -LiteralPath (Join-Path $backupDir 'after.txt')) -replace ([regex]::Escape("$restoreDb.")), ''
$difference = Compare-Object -ReferenceObject $before -DifferenceObject $after
if ($difference) {
  $difference
  throw '备份前后结果不一致，先检查，不要替换业务库。'
}
'五张表的记录数与校验值一致。'
"备份目录：$backupDir"
"演练数据库：$restoreDb"
```

应输出“五张表的记录数与校验值一致。”。前面的比较不一致、密码或导入报错时，不要继续清理，回复助手检查即可。

接下来回复“备份恢复操作完成”，并告知终端显示的 `$backupDir` 和 `$restoreDb`。无需发送备份文件内容或任何密码。**保留演练库，助手会完成应用连接恢复库、公开/草稿隔离及恢复后写入检查，再清理演练环境。**

## 8. 助手后续验收

1. 检查备份目录、SQL 文件以及 SHA-256 文件清单，确认五表结构与数据可以恢复。
2. 用独立服务连接演练库，核对中文、Emoji、Markdown、分类标签、公开文章与草稿、公开资源与隐藏资源。
3. 验证恢复后的数据库可以正常写入，并保持外键、唯一约束及自增编号有效。
4. 完成后停止专用服务，清理本轮演练库，保留个人备份目录。
5. 更新验收报告和项目进度，再进入本地完整验收。

当前备份保存的是恢复所需的数据与配置，不会因为创建 GitHub Release 自动上传到 GitHub。

## 9. 指南预检记录

2026-10-15：使用本机 MySQL 8.0.46 客户端和专用样例库完成预检。七段 PowerShell 命令语法检查通过；数据库导出、源码归档、示例配置复制、SHA-256 清单生成及新库恢复通过。自动执行时使用临时客户端配置代替交互式密码输入，配置备份使用公开示例，未复制个人密码。

样例包含两篇文章（公开笔记、草稿日记）、两个资源（公开、隐藏）、一个分类、两个标签和三条关联。中文、Emoji、引号、反斜杠、多行及较长 Markdown 恢复完整。五表记录数、内容校验值、逐字段数据及建表定义均一致；故意导入错误 SQL 时，客户端退出码为 1，指南会停止。专用样例库、恢复库和临时连接配置已清理。

以上为此前的指南预检记录。随后已按用户授权完成真实个人数据备份、恢复和应用验收；原库五表核对结果未变化，演练环境已清理，备份保留。完整结果见 [备份恢复验收](BACKUP-RESTORE-ACCEPTANCE.md)。
