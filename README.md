# 终身体检报告管家

一款基于 AI 的体检报告管理 Web 应用，支持**文字粘贴**和**图片上传**两种方式录入数据，提供可视化追踪和 AI 健康诊断。

**简单来说**：用手机识别体检报告后粘贴文字（或直接拍照片上传），AI 自动提取指标数据并存档。之后可以查看历年趋势图，还能让 AI 给你做健康分析。

---

## 目录

- [项目结构](#项目结构)
- [一键启动 / 停止](#一键启动--停止)
- [技术栈](#技术栈)
- [核心功能](#核心功能)
- [部署前准备（环境安装）](#部署前准备环境安装)
- [第三方服务注册](#第三方服务注册)
- [部署步骤（手把手教程）](#部署步骤手把手教程)
- [常见问题排查](#常见问题排查)
- [API 接口文档](#api-接口文档)
- [数据库设计](#数据库设计)
- [AI 提示词](#ai-提示词)
- [开发计划](#开发计划)
- [注意事项](#注意事项)

---

## 项目结构

> ⚠️ 注意：后端代码直接在项目根目录下，不是在 `backend/` 子目录里。

```
E:\Github\C_Check_Up_Home\
├── src/                              # 后端 Java 代码（Spring Boot）
│   └── main/
│       ├── java/com/healthbutler/
│       │   ├── controller/           # API 接口层（处理 HTTP 请求）
│       │   ├── service/              # 业务逻辑层
│       │   ├── service/impl/         # 业务逻辑实现
│       │   ├── mapper/               # 数据库操作层
│       │   ├── entity/               # 数据库表对应的 Java 对象
│       │   ├── dto/                  # 数据传输对象
│       │   ├── vo/                   # 返回给前端的视图对象
│       │   ├── config/               # 配置类
│       │   ├── security/             # 登录认证相关
│       │   ├── exception/            # 错误处理
│       │   ├── util/                 # 工具类
│       │   └── HealthButlerApplication.java  # 启动入口
│       └── resources/
│           ├── mapper/               # MyBatis SQL 映射文件
│           ├── application.yml       # ⭐ 核心配置文件（数据库、AI 等都在这里配）
│           └── logback-spring.xml    # 日志配置
├── frontend/                         # 前端 Vue 代码
│   ├── src/
│   │   ├── api/                      # 调用后端接口的封装
│   │   ├── components/               # 可复用的页面组件
│   │   ├── router/                   # 页面路由配置
│   │   ├── store/                    # 全局状态管理（Pinia）
│   │   ├── utils/                    # 工具函数
│   │   ├── views/                    # 各个页面
│   │   ├── App.vue                   # 根组件
│   │   └── main.ts                   # 前端入口
│   ├── package.json                  # 前端依赖配置
│   ├── vite.config.ts                # Vite 构建配置
│   └── .env                          # 环境变量
├── database/
│   └── schema.sql                    # ⭐ 数据库建表脚本
├── pom.xml                           # 后端 Maven 依赖配置
├── start.bat                         # 一键启动脚本（自动检测 MySQL/Redis）
├── stop.bat                          # 一键停止脚本
├── MIGRATION.md                      # 换电脑迁移备忘录
└── README.md                         # 就是本文件
```

---

## 一键启动 / 停止

如果你的环境已装好（JDK 17、Maven、Node.js、MySQL、Redis），直接双击：

```cmd
start.bat
```

脚本会自动完成：
1. 检测并启动 MySQL（如未运行）
2. 检测并启动 Redis（支持 Memurai / Docker / WSL）
3. 启动后端（Spring Boot → `:8080`）
4. 启动前端（Vite → `:3000`）
5. 打开浏览器访问 http://localhost:3000

停止所有服务：

```cmd
stop.bat
```

会同时按窗口标题和端口号清理进程，确保彻底释放 8080/3000 端口。

> ⚠️ 如果开机第一次运行 `start.bat` 提示 MySQL 端口被占用，可能是 Windows 快速启动导致的端口残留。解决方案见 [常见问题排查 → MySQL 端口占用](#-开机后-mysql-端口-3306-被占用)。

---

## 技术栈

### 后端（Java）
| 技术 | 版本 | 作用 |
|------|------|------|
| Java | 17 | 编程语言 |
| Spring Boot | 3.2 | Web 框架，帮你快速搭建后端服务 |
| Spring Security | - | 用户登录认证、权限控制 |
| MyBatis-Plus | 3.5 | 操作数据库的工具 |
| MySQL | 8.0+ | 数据库，存所有数据 |
| Redis | 6.0+ | 缓存，加速查询 |
| JWT | - | 登录令牌，保持用户登录状态 |
| OkHttp | - | 调用 AI 接口的 HTTP 客户端 |

### 前端（Vue）
| 技术 | 版本 | 作用 |
|------|------|------|
| Vue | 3.4 | 前端框架 |
| TypeScript | 5.3 | JavaScript 的超集，更安全 |
| Vite | 5.0 | 构建工具，启动快 |
| Element Plus | 2.4 | UI 组件库（按钮、表格、表单等） |
| ECharts | 5.4 | 图表库（画趋势折线图） |
| Pinia | 2.1 | 全局状态管理 |
| Axios | 1.6 | 发 HTTP 请求调后端接口 |

### AI 接入
- 使用 OpenAI 兼容 API 格式（支持 GPT-4、通义千问、DeepSeek 等多种大模型）
- **文本模型**：用于解析体检报告文字、生成健康诊断报告

---

## 核心功能

1. **智能录入**：支持两种方式录入体检数据
   - **文字粘贴**（推荐）：用手机识别报告后粘贴文字，AI 解析提取指标数据
   - **图片上传**：直接上传报告照片，调用多模态 AI 识别
2. **数据归档**：按用户、年份、检查类目分类存储
3. **可视化追踪**：支持单个指标或同类指标的历年趋势折线图
4. **AI 健康顾问**：结合历年走势和最新数据，生成个性化健康诊断报告
5. **用户认证**：JWT Token 认证，支持注册登录

---

## 部署前准备（环境安装）

你需要安装以下 5 个软件。**不要跳过任何一步！**

### 1. 安装 JDK 17

> 后端是 Java 写的，必须先装 JDK。

**Windows 用户：**
1. 打开 https://adoptium.net/ （Eclipse Temurin，推荐）
2. 点击 "Latest LTS Release" 下载 JDK 17 的 Windows 安装包（.msi 文件）
3. 双击安装，**勾选 "Add to PATH"**（很重要！）
4. 安装完成后，打开命令提示符（Win+R 输入 `cmd` 回车），输入：
   ```cmd
   java -version
   ```
   应该显示 `openjdk version "17.x.x"` 就算成功。

**macOS 用户：**
```bash
# 如果装了 Homebrew：
brew install openjdk@17
```

**Linux 用户（Ubuntu/Debian）：**
```bash
sudo apt update
sudo apt install openjdk-17-jdk
```

---

### 2. 安装 MySQL 8.0

> 数据库，所有数据都存在这里。

**Windows 用户：**
1. 打开 https://dev.mysql.com/downloads/installer/
2. 下载 MySQL Installer（选较大的那个完整包，约 300MB）
3. 安装时选择 "Developer Default" 或 "Server only"
4. **设置 root 密码**：会让你输入密码，**记住这个密码，后面要用！** 比如设成 `123456`
5. 端口保持默认 `3306`
6. 安装完成后，打开命令提示符测试：
   ```bash
   mysql -u root -p
   ```
   输入密码能进去就成功了。输入 `exit` 退出。

**macOS 用户：**
```bash
brew install mysql@8.0
brew services start mysql@8.0
```

**Linux 用户：**
```bash
sudo apt update
sudo apt install mysql-server
sudo systemctl start mysql
sudo systemctl enable mysql
# 设置 root 密码
sudo mysql_secure_installation
```

---

### 3. 安装 Redis

> 缓存数据库，用来加速查询。

**Windows 用户：**

Windows 上官方不直接支持 Redis，推荐用以下方式之一：

**方式一：使用 Memurai（Redis 的 Windows 替代品，推荐）**
1. 打开 https://www.memurai.com/get-memurai
2. 下载安装，一路下一步即可
3. 默认端口是 6379，不需要改

**方式二：使用 WSL（Windows Subsystem for Linux）**
1. 先启用 WSL：管理员打开 PowerShell，运行 `wsl --install`
2. 重启电脑
3. 在 WSL 中安装 Redis：
   ```bash
   sudo apt update
   sudo apt install redis-server
   sudo systemctl start redis
   ```

**方式三：使用 Docker（如果你装了 Docker）**
```bash
docker run -d --name redis -p 6379:6379 redis:7
```

**macOS 用户：**
```bash
brew install redis
brew services start redis
```

**Linux 用户：**
```bash
sudo apt update
sudo apt install redis-server
sudo systemctl start redis
sudo systemctl enable redis
```

验证是否安装成功：
```cmd
:: Memurai 用户
memurai-cli ping

:: WSL 用户
wsl redis-cli ping

:: Docker 用户
docker exec -it redis redis-cli ping
```
返回 `PONG` 就成功了。

---

### 4. 安装 Node.js

> 前端是用 Vue 写的，需要 Node.js 来运行。

1. 打开 https://nodejs.org/
2. 下载 **LTS 版本**（长期支持版，推荐 18 或 20）
3. 双击安装，一路下一步
4. 安装完成后，打开**新的**命令提示符，输入：
   ```cmd
   node -v
   npm -v
   ```
   应该分别显示 `v18.x.x` 或 `v20.x.x` 和 `9.x.x` 或 `10.x.x`。两个都有版本号输出就成功了。

---

### 5. 安装 Maven

> 后端的包管理工具，用来下载 Java 依赖。

**如果你用 IntelliJ IDEA 或 Eclipse，IDE 自带 Maven，可以跳过这步。**

**Windows 用户：**
1. 打开 https://maven.apache.org/download.cgi
2. 下载 `apache-maven-3.9.x-bin.zip`
3. 解压到一个目录，比如 `C:\Program Files\Maven`
4. 添加环境变量：
   - 右键"此电脑" → 属性 → 高级系统设置 → 环境变量
   - 在 "系统变量" 中找到 `Path`，点编辑，新增一行：`C:\Program Files\Maven\bin`
5. 打开新的命令提示符，输入：
   ```cmd
   mvn -version
   ```
   显示版本号就成功了。

**macOS / Linux 用户：**
```bash
# macOS
brew install maven

# Linux (Ubuntu/Debian)
sudo apt update
sudo apt install maven
```

---

### 6. 安装 IDE（可选）

> 写代码的工具，不装也能跑，但装了更方便。

推荐 **IntelliJ IDEA**（写 Java 最好用的 IDE）：
- 社区版（免费）：https://www.jetbrains.com/idea/download/
- 安装后打开项目，选择 `pom.xml` 所在的目录即可

前端代码可以用 **VS Code**：
- 下载：https://code.visualstudio.com/
- 安装插件：Volar（Vue 语言支持）

---

## 第三方服务注册

### 获取 AI API Key

本项目需要一个支持 OpenAI 格式的大模型 API。你可以选择以下任一平台：

**选项一：OpenAI（需要海外信用卡）**
1. 打开 https://platform.openai.com/
2. 注册账号
3. 进入 API Keys 页面，创建一个 Key
4. 以 `sk-` 开头的字符串就是你的 API Key

**选项二：通义千问（阿里云，国内推荐）**
1. 打开 https://dashscope.console.aliyun.com/
2. 用支付宝/淘宝账号登录
3. 开通 DashScope 服务
4. 在 API-KEY 管理中创建 Key

**选项三：DeepSeek（国内，性价比高）**
1. 打开 https://platform.deepseek.com/
2. 注册账号
3. 在 API Keys 页面创建 Key

> 💡 **提示**：你需要的 API Key 后面填配置文件时会用到。不同的平台 API 地址和模型名称不同，配置时注意对应。

---

## 部署步骤（手把手教程）

所有环境都装好之后，按顺序执行以下步骤。

> 💡 **提示**：日常使用直接双击 `start.bat` 即可一键启动，无需每次手动执行以下步骤。以下步骤仅首次部署时需要。

### 第一步：初始化数据库

1. 打开命令提示符（CMD），进入项目目录：
   ```cmd
   cd /d E:\Github\C_Check_Up_Home
   ```
   > ⚠️ 注意：如果项目在其他盘符（如 E:），必须加 `/d` 参数才能切换盘符。

2. 执行建表脚本：
   ```cmd
   mysql -u root -p --default-character-set=utf8mb4 < database\schema.sql
   ```
   输入你的 MySQL root 密码。
   > ⚠️ 必须加 `--default-character-set=utf8mb4`，否则中文数据会因编码问题报错。

3. 验证是否成功：
   ```cmd
   mysql -u root -p --default-character-set=utf8mb4 -e "USE health_butler; SHOW TABLES;"
   ```
   应该看到 5 张表：`user`、`item_dictionary`、`health_report`、`health_item`、`ai_diagnosis`。

> 💡 **如果报错 "mysql 不是内部命令"**：说明 MySQL 没有加入 PATH 环境变量。你可以用完整路径执行，比如：
> ```cmd
> "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p --default-character-set=utf8mb4 < database\schema.sql
> ```

---

### 第二步：配置后端

这是**最关键的一步**，你需要修改配置文件，填入你自己的账号密码和 Key。

用记事本或 IDE 打开 `src/main/resources/application.yml`，逐项修改：

```yaml
server:
  port: 8080                    # 后端端口，一般不用改

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/health_butler?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root              # ← 改成你的 MySQL 用户名
    password: your_password     # ← 改成你的 MySQL 密码
  data:
    redis:
      host: localhost           # Redis 地址，本地就是 localhost
      port: 6379                # Redis 端口，一般不用改
      password:                 # Redis 密码，没设密码就留空
      database: 0

# AI API 配置
ai:
  api:
    key: your_api_key           # ← 改成你申请的 AI API Key
    url: https://api.openai.com/v1  # ← 改成你的 AI 平台地址（见下方说明）
  model:
    vision: gpt-4-vision-preview    # ← 多模态模型（仅图片上传模式需要）
    text: gpt-4                     # ← 改成你的文本模型名称

# JWT 配置
jwt:
  secret: your_jwt_secret_key_at_least_256_bits_long_for_hs256_algorithm  # ← 随便改一个长字符串，越长越好
  expiration: 86400             # Token 有效期（秒），86400 = 24小时

# 阿里云 OSS 配置（仅图片上传模式需要，文字模式可跳过）
aliyun:
  oss:
    endpoint: oss-cn-hangzhou.aliyuncs.com
    access-key-id: your_access_key_id
    access-key-secret: your_access_key_secret
    bucket-name: your_bucket_name
```

**AI 平台地址和模型名称对照表：**

| 平台 | api.url | vision 模型（图片模式） | text 模型 |
|------|---------|-------------|-----------|
| OpenAI | `https://api.openai.com/v1` | `gpt-4o` | `gpt-4o` |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-vl-max` | `qwen-max` |
| DeepSeek | `https://api.deepseek.com/v1` | 不支持 | `deepseek-chat` |

> 💡 **如果只使用文字粘贴模式**，只需配置 `text` 模型即可，不需要 `vision` 模型和阿里云 OSS。

---

### 第三步：启动后端

1. 打开命令提示符（CMD），进入项目根目录：
   ```cmd
   cd /d E:\Github\C_Check_Up_Home
   ```

2. 用 Maven 启动后端：
   ```cmd
   mvn spring-boot:run
   ```

3. 等待启动完成，看到类似这样的日志就成功了：
   ```
   Started HealthButlerApplication in x.xx seconds
   ```

4. 后端默认运行在 `http://localhost:8080`

> 💡 **第一次启动会比较慢**，因为 Maven 需要下载很多依赖包（可能需要几分钟），请耐心等待。

> ⚠️ **如果启动失败**，先看 [常见问题排查](#常见问题排查) 章节。

**保持这个窗口不要关闭！** 后端需要一直运行。

---

### 第四步：启动前端

**打开一个新的命令提示符窗口**（后端那个不要关），执行：

1. 进入前端目录：
   ```cmd
   cd /d E:\Github\C_Check_Up_Home\frontend
   ```

2. 安装依赖：
   ```cmd
   npm install
   ```
   第一次需要下载依赖，可能需要 1-3 分钟。

3. 启动开发服务器：
   ```cmd
   npm run dev
   ```

4. 看到类似这样的输出就成功了：
   ```
   VITE v5.x.x  ready in xxx ms

   ➜  Local:   http://localhost:3000/
   ```

---

### 第五步：访问应用

打开浏览器，访问 **http://localhost:3000**

1. 先注册一个账号
2. 登录
3. 进入"上传报告"页面，选择"粘贴报告文字"或"上传报告图片"

🎉 **恭喜，部署完成！**

---

## 常见问题排查

### ❌ "java 不是内部或外部命令"
JDK 没装好，或者没加到 PATH。重新安装 JDK，确保勾选 "Add to PATH"，然后**重新打开**命令提示符。

### ❌ "mvn 不是内部或外部命令"
Maven 没装好。重新安装或手动加 PATH。或者用 IDE 自带的 Maven。

### ❌ 后端启动报 "Communications link failure" / 连接数据库失败
- 确认 MySQL 服务正在运行：`net start mysql80`（Windows）或 `sudo systemctl start mysql`（Linux）
- 确认 `application.yml` 里的数据库密码正确
- 确认数据库 `health_butler` 已经创建（执行了 schema.sql）

### ❌ 后端启动报 "Connection refused" / Redis 连接失败
- Memurai 用户：`memurai-cli ping` 应该返回 `PONG`
- WSL 用户：`wsl redis-cli ping` 应该返回 `PONG`
- Docker 用户：`docker exec -it redis redis-cli ping` 应该返回 `PONG`

### ❌ 后端启动报 "Access denied for user"
MySQL 用户名或密码错了。检查 `application.yml` 里的 `username` 和 `password`。

### ❌ 登录报 "Public Key Retrieval is not allowed"

**现象**：MySQL 9.0+ 版本下，后端连接数据库时报错，日志中出现 `Public Key Retrieval is not allowed`。

**原因**：MySQL 8.0.4+ 默认使用 `caching_sha2_password` 认证插件，JDBC 驱动连接时需要获取公钥，但默认不允许。

**解决**：在 `application.yml` 的数据库 URL 末尾加上 `&allowPublicKeyRetrieval=true`：
```yaml
url: jdbc:mysql://localhost:3306/health_butler?...&allowPublicKeyRetrieval=true
```

### ❌ 后端用 JDK 26 启动后崩溃（Process terminated with exit code: 1）

**现象**：`mvn spring-boot:run` 启动后显示 Spring Boot banner，随即 `BUILD FAILURE`。

**原因**：Spring Boot 3.2 不兼容 JDK 26。JDK 26 加强了本机访问限制，Tomcat 等组件无法正常运行。

**解决**：使用 JDK 17。项目已配置 `run_backend.bat` 自动切换到 `C:\Program Files\Java\jdk-17`，直接双击启动即可。

### ❌ 前端中文（用户名、报告等）提交后报 500 错误

**现象**：英文内容正常，但提交含中文的请求时后端返回 `系统内部错误`。

**原因**：Tomcat 默认使用系统编码解析请求体，Windows 中文系统使用 GBK，而前端发送的是 UTF-8，导致中文乱码后 Jackson 解析失败。

**解决**：已在 `application.yml` 配置强制 UTF-8：
```yaml
server:
  servlet:
    encoding:
      charset: UTF-8
      enabled: true
      force: true
```
并在 `SecurityConfig` 中注册了 `CharacterEncodingFilter`。

### ❌ 开机后 MySQL 未自动启动

**现象**：重启电脑后 MySQL 服务未运行，需手动打开 HeidiSQL 连接才能启动。

**原因**：MySQL 9.7 的 Windows 服务在开机时可能启动失败（端口残留或时序问题）。

**解决**：`run_backend.bat` 已内置自动检测和拉起逻辑——先尝试 `net start MySQL80`，失败则直接运行 `mysqld.exe`。日常使用直接双击 `start.bat` 或 `run_backend.bat` 即可。

### ❌ npm install 报错
- 确认 Node.js 安装成功：`node -v`
- 尝试清除缓存：`npm cache clean --force`
- 如果网络慢，可以换淘宝镜像：
  ```cmd
  npm config set registry https://registry.npmmirror.com
  ```

### ❌ 前端启动后页面空白 / 接口报错
- 确认后端已经启动（端口 8080 有服务）
- 确认 `frontend/vite.config.ts` 里的 proxy 目标地址是 `http://localhost:8080`

### ❌ AI 分析失败
- 检查 `application.yml` 里的 `ai.api.key` 是否正确
- 检查 API 余额是否充足
- 确认模型名称和 API 地址匹配（参考上方对照表）

---

## API 接口文档

> 以下接口都需要在请求头中携带 Token（登录后获取）：
> `Authorization: Bearer <你的token>`

### 认证接口

#### 1. 用户注册
```
POST /api/auth/register
Body: { "username": "用户名", "password": "密码", "phone": "手机号(可选)", "gender": "性别(可选,0/1/2)", "birthDate": "出生日期(可选,格式YYYY-MM-DD)" }
返回: { "userId": 1 }
```

#### 2. 用户登录
```
POST /api/auth/login
Body: { "username": "用户名", "password": "密码" }
返回: { "userId": 1, "username": "test", "token": "eyJhbG..." }
```

#### 3. 刷新 Token
```
POST /api/auth/refresh
Header: Authorization: Bearer <旧token>
返回: { "token": "新token" }
```

### 报告接口

#### 4. 上传体检报告（图片模式）
```
POST /api/report/upload
参数: multipart/form-data
  - userId: 用户ID
  - file: 体检报告图片文件
返回: { "reportId": 1 }
```

#### 5. 文字录入体检报告（文字模式）
```
POST /api/report/text
Body: { "userId": 1, "text": "体检报告文字内容..." }
说明: 用户用手机识别报告后粘贴文字，AI 解析提取指标数据
返回: { "reportId": 1 }
```

#### 6. 确认报告数据
```
POST /api/report/confirm/{reportId}
说明: AI 识别后会先暂存数据，用户确认后才正式入库
返回: { "success": true }
```

#### 7. 获取用户报告列表
```
GET /api/report/list/{userId}
返回: 报告列表
```

#### 8. 获取报告详情
```
GET /api/report/detail/{reportId}
返回: 报告详情（包含所有指标明细）
```

#### 9. 获取指标趋势数据
```
GET /api/report/trend/{userId}/{standardName}
说明: 获取某个指标的历年数据，用于画折线图
返回: 趋势数据
```

#### 10. 获取同类指标趋势数据
```
GET /api/report/trend/category/{userId}/{category}
说明: 比如获取所有"肝功能"类指标的趋势
返回: 同类指标趋势数据列表
```

#### 11. 生成 AI 健康诊断
```
POST /api/report/diagnosis
Body: { "userId": 1, "reportIds": [1, 2, 3] }
说明: 基于选定的报告，AI 生成健康诊断
返回: 诊断结果（Markdown 格式）
```

---

## 数据库设计

### 核心表结构

| 表名 | 说明 |
|------|------|
| `user` | 用户表 |
| `item_dictionary` | 指标字典表（标准化指标名称映射，解决不同医院叫法不同的问题） |
| `health_report` | 体检报告表 |
| `health_item` | 检测指标明细表 |
| `ai_diagnosis` | AI 诊断报告表 |

### 指标字典设计

指标字典表用于解决不同医院指标名称差异问题。比如 "ALT"、"GPT"、"丙氨酸氨基转移酶" 其实是同一个指标，通过别名机制统一映射到 "谷丙转氨酶"：

```sql
CREATE TABLE `item_dictionary` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `standard_name` VARCHAR(100) NOT NULL COMMENT '标准指标名称',
    `category` VARCHAR(50) NOT NULL COMMENT '指标分类',
    `aliases` JSON COMMENT '别名列表',
    `unit` VARCHAR(20) NOT NULL COMMENT '标准单位',
    `reference_range` VARCHAR(100) COMMENT '参考范围',
    `description` TEXT COMMENT '指标说明'
);
```

---

## AI 提示词

### 体检报告解析提示词（文字模式）
```
你是一个高精度的医疗单据提取系统。以下是从体检报告中提取的文字内容，请从中提取所有的检测项目、结果、单位及参考范围，严格输出以下 JSON 格式，不含任何多余文字和 Markdown 标记：
{ "report_date": "YYYY-MM-DD", "hospital": "医院名称", "items": [ { "name": "指标名称", "category": "所属分类(如肝功能)", "value": 浮点数, "unit": "单位", "reference_range": "参考范围", "status": "normal/high/low" } ] }
```

### 健康诊断提示词
```
# Role: 终身私人健康顾问
## Profile: 拥有 20 年经验的全科医生，擅长通过纵向对比历年体检数据发现潜在风险，给出专业的建议。
## Rules:
1. 准确引用数据，指出好转、恶化或异常的指标。
2. 进行多指标的交叉关联分析（如血压与血脂的综合风险）。
3. 术语必须通俗易懂，并在回答末尾强制附加医疗免责声明。
## Output Format:
请输出 Markdown 格式，严格包含以下结构：### 📊 核心指标变化概览、### 🩺 异常指标深度解读、### 💡 个性化健康建议、### ⚠️ 免责声明。
```

---

## 开发计划

- [x] 后端项目骨架搭建
- [x] 数据库设计
- [x] 核心业务代码实现
- [x] 前端 Vue 项目搭建
- [x] 文字粘贴录入（推荐方式，无需 OSS）
- [x] 图片上传录入（阿里云 OSS + 多模态 AI）
- [x] ECharts 可视化组件
- [x] 用户认证与授权（JWT）
- [x] 全局异常处理和日志记录
- [x] 性能优化（Redis 缓存）
- [ ] 生产环境部署

---

## 注意事项

1. **本项目仅为演示用途**，生产环境需要根据实际需求进行安全加固
2. **AI API 调用需要付费**，请确保账户有足够的余额
3. **如果只使用文字粘贴模式**，不需要阿里云 OSS，配置文件中 OSS 部分可忽略
4. **不要把 `application.yml` 中的真实密码和 Key 提交到 Git**，建议使用 `application-local.yml` 存放敏感配置（已在 `.gitignore` 中排除）
5. **JWT 密钥**要足够长（至少 32 字符），建议用随机生成的字符串
6. **生产环境建议使用 HTTPS**，否则密码和 Token 在传输过程中可能被窃取
7. **定期备份数据库**，体检数据很重要

---

## 许可证

MIT License
