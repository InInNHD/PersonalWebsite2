# PersonalWebsite2

个人网站第二版。仓库包含 Vue/Vite 前端和 Spring Boot/MySQL 后端。

## 项目结构

- `frontend/`：前端应用。
- `personalwebsite/`：后端应用。

## 本地运行

前端需要 Node.js 和 npm。从仓库根目录运行：

```powershell
cd frontend
npm ci
npm run dev
```

后端需要 Java 17 和 MySQL。使用配置示例时，启动前需要设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 和 `ADMIN_PASSWORD` 四个环境变量，其中 `ADMIN_PASSWORD` 是 `admin` 账号的登录密码。真实密码不要写入 README 或配置示例。

首次克隆项目后，从仓库根目录打开一个终端，进入后端目录：

```powershell
cd personalwebsite
```

首次运行时，将配置示例复制为本地配置。**已有 `application.properties` 时，跳过下面的复制命令，不要覆盖已有配置。** 真实配置文件已被 Git 忽略，也可以只在该本地文件中填写数据库连接信息。

```powershell
Copy-Item src/main/resources/application.properties.example src/main/resources/application.properties
```

使用 IDEA 启动时，在 `PersonalwebsiteApplication` 的运行配置中打开“环境变量”，设置上述四个环境变量，再启动应用。如果数据库连接信息已直接写在本地配置中，只需补充 `ADMIN_PASSWORD`。

使用 PowerShell 启动时，在上述后端目录的同一个终端中，先设置配置文件引用的 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，再设置管理员密码并启动：

```powershell
$env:ADMIN_PASSWORD = '替换为你自己的管理员密码'
.\mvnw.cmd spring-boot:run
```

PowerShell 中设置的环境变量仅对当前终端及其启动的进程生效，不会自动传给已经打开的 IDEA。

前端构建：`npm run build`

后端构建：`.\mvnw.cmd package`。
