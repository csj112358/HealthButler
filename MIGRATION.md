# 换电脑迁移备忘录

---

## 一、导出数据库（旧电脑）

### 安装 HeidiSQL

1. 打开 https://www.heidisql.com/download.php
2. 点 **"便携版 (Portable)"** 那个 zip 链接下载，不用安装，解压就能用
3. 解压后双击 `heidisql.exe` 打开

### 连接 MySQL

打开后会自动弹出连接窗口：

```
网络类型:      MySQL (TCP/IP)
主机名:        127.0.0.1
端口:          3306
用户名:        root
密码:          your_password
```

填好后点右下角 **"打开"**。

### 导出了 SQL

1. 左侧会出现数据库列表，找到 `health_butler`，**右键点击它**
2. 选 **"导出数据库为 SQL"**
3. 在弹出的窗口里，左侧确保勾选了 `health_butler`
4. 右侧 **"SQL 导出"** 栏，勾上：
   - ☑ 创建数据库（CREATE DATABASE）
   - ☑ 删除已有的（DROP）
5. 右下角文件名那里点 **"浏览"**，选 U 盘，文件名填 `health_butler.sql`
6. 点右下角 **"导出"**，等一两秒完成
7. 弹出提示说导出成功，搞定

---

## 二、导入数据库（新电脑）

新电脑上也下载 HeidiSQL 便携版 → 连接 MySQL（同样 root / your_password）→ 打开：

1. 顶部菜单栏点 **"文件"** → **"载入 SQL 文件…"**
2. 选 U 盘里的 `health_butler.sql`，点打开
3. 会弹出一个大文本框显示 SQL 内容，不用管，直接点右上角 **蓝色播放按钮 ▶**（或按键盘 F9）
4. 等待左下角进度条跑完，显示 "已执行完毕"
5. 关掉 SQL 窗口，回 HeidiSQL 主界面，按 F5 刷新，左侧就能看到 `health_butler` 库了

> 导入完就可以拔 U 盘了，数据已经在本地 MySQL 里。

---

## 三、复制项目文件夹（旧电脑 → 新电脑）

文件资源管理器操作：

| 操作 | 路径 |
|------|------|
| 旧电脑 | `your-project-path` |
| 新电脑 | `your-project-path`（同路径） |

复制时**跳过这三个文件夹**（太大，后面会重新生成）：

- `target`
- `node_modules`
- `.git`

---

## 四、安装依赖（新电脑）

在新电脑的 `your-project-path` 目录里，按住 Shift + 右键 → **在此处打开 PowerShell**，分别跑：

```bash
mvn dependency:resolve          # 后端依赖，一次性
cd frontend; npm install        # 前端依赖，一次性
```

之后日常启动双击 `start.bat` 即可。

---

## 五、新电脑需提前装好

- JDK 17+
- Maven
- Node.js + npm
- MySQL 8.0（root 密码设 `your_password`）
- Redis
