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

后端需要 Java 17 和 MySQL。从仓库根目录运行以下命令。首次克隆后，复制配置示例并设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 环境变量。真实配置文件已被 Git 忽略，也可以只在该本地文件中填写连接信息：

```powershell
cd personalwebsite
Copy-Item src/main/resources/application.properties.example src/main/resources/application.properties
.\mvnw.cmd spring-boot:run
```

前端构建：`npm run build`。后端构建：`.\mvnw.cmd package`。
