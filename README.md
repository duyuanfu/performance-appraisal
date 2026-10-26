# 原料中心评审管理系统 (Raw Material Center Performance Appraisal System)

一套面向原料中心的 WEB 端综合绩效与项目评审管理平台。系统支持**月度部门负责人绩效考核**和**年度目标管理绩效（按项目）评审**两大核心业务场景，实现考核指标配置灵活化、材料查阅与评分线上化、结果自动加权计算与排名、历史数据可追溯导出。

---

## 一、 系统架构与技术栈

### 1. 技术架构选型
- **后端架构**：Java 17 + Spring Boot 3.2.5 + MyBatis-Plus + MySQL 8.0 + EasyExcel 3.3.4 + JJWT
- **前端架构**：React 18 + Vite 5 + Ant Design 5 + TypeScript + React Router 6 + Axios + Day.js
- **部署环境**：支持标准云服务器（Linux + Nginx + Jar）部署及 **Docker / Docker Compose** 容器化一键部署

### 2. 核心设计亮点
1. **多角色与行级数据权限隔离**：基于 RBAC 模型（管理员、中心领导、评审专家、被考核人员），支持**单账号一人身兼多职并在顶部自由切换身份**。
2. **考核周期唯一性控制**：系统严格限制**一个月只能建立一个月度绩效考核流程**，**一年只能建立一个年度项目评审流程**，防止重复创建与数据混淆。
3. **打分全流程闭环**：
   - 支持实时 4 位小数精度计算；
   - 支持暂存草稿、正式提交、评分截止前自主撤回修改；
   - 批次归档后评分永久封存锁定，防止违规篡改；
   - 批次发布后支持实时查看领导/专家打分进度明细，支持管理员一键催办。
4. **项目申报材料分屏在线评审**：专家端左右分屏，左侧内嵌 PDF 阅读器在线查阅申报材料与附件，右侧即时打分，免除频繁切换窗口。
5. **结果严格保密机制**：被考核人仅在管理员归档后可见本人综合得分与部门排名，不泄露他人明细；年度项目评审汇总仅限管理员查看与导出。
6. **密码安全体系**：所有用户可在右上角个人中心自主修改密码（需校验原密码）；员工忘记密码时管理员可在后台一键重置为初始密码。

---

## 二、 预设测试账号（默认密码：`password123`）

登录页面底部内置了**一键快捷演示登录按钮**，也可以手动输入以下预设账号：

| 角色标识 | 登录账号 (Username) | 登录密码 | 姓名 / 部门 | 适用测试场景 |
| :--- | :--- | :--- | :--- | :--- |
| **系统管理员** | **`admin`** | `password123` | 系统管理员 (综合处) | 批次创建、发布、进度催办、汇总排名、Excel导出、账号管理 |
| **中心领导** | **`leader_zhang`** | `password123` | 张主任 (中心主任) | 月度绩效逐项评分、暂存、提交、截止前撤回、归档后查阅 |
| **中心领导** | **`leader_li`** | `password123` | 李副主任 (副主任) | 月度绩效独立评分与加权汇总 |
| **评审专家** | **`expert_wang`** | `password123` | 王专家 (评审委员会) | 年度项目分屏查阅 PDF 材料并即时打分 |
| **双重身份** | **`dept_qian`** | `password123` | 钱部长 (采购一处负责人) | **一人多角色**：顶部一键切换【被考核人员】与【评审专家】工作台 |
| **被考核员工** | **`dept_zhou`** | `password123` | 周部长 (采购二处负责人) | 查看本人月度最终综合得分及部门全员排名 |

---

## 三、 本地开发与运行指南

### 1. 环境准备
- JDK 17+
- Node.js 18+ (推荐 Node 20+)
- Maven 3.8+
- MySQL 8.0+

### 2. 数据库配置
在 MySQL 中无需手动建表。确保 MySQL 服务已启动（默认端口 3306），后端连接配置位于 [`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml)：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/appraisal_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false
    username: root
    password: root # 替换为您的 MySQL 密码
```
*(系统首次启动时会自动创建 `appraisal_db` 数据库及所需数据表并灌入预设账号)*。

### 3. 启动后端 (Spring Boot)
```bash
cd backend
mvn clean spring-boot:run
```
后端服务端口：`http://localhost:8080`

### 4. 启动前端 (React + Vite)
```bash
cd frontend
npm install
npm run dev
```
前端访问入口：`http://localhost:3000`

---

## 四、 云服务器传统方式部署 (Linux + Nginx + Jar)

适用系统：Ubuntu 20.04+ / Debian 11+ / CentOS 7.9+ / Rocky Linux 9+

### 1. 安装基础环境 (以 Ubuntu 为例)
```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless nginx mysql-server
```

### 2. 部署后端服务
1. **打包后端 Jar**：
   ```bash
   cd backend
   mvn clean package -DskipTests
   # 生成产物：backend/target/performance-appraisal-backend-1.0.0-SNAPSHOT.jar
   ```
2. **上传至服务器**（例如上传至 `/opt/appraisal/backend/`）：
   ```bash
   mkdir -p /opt/appraisal/backend /opt/appraisal/data/uploads
   cp target/performance-appraisal-backend-1.0.0-SNAPSHOT.jar /opt/appraisal/backend/app.jar
   ```
3. **配置 Systemd 服务自启动**（`/etc/systemd/system/appraisal.service`）：
   ```ini
   [Unit]
   Description=Performance Appraisal Backend Service
   After=syslog.target network.target mysql.service

   [Service]
   Type=simple
   User=root
   WorkingDirectory=/opt/appraisal/backend
   Environment="MYSQL_HOST=127.0.0.1"
   Environment="MYSQL_PORT=3306"
   Environment="MYSQL_DB=appraisal_db"
   Environment="MYSQL_USER=root"
   Environment="MYSQL_PASSWORD=YourPassword"
   ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar /opt/appraisal/backend/app.jar
   Restart=always
   RestartSec=10

   [Install]
   WantedBy=multi-user.target
   ```
4. **启动后端服务**：
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable appraisal
   sudo systemctl start appraisal
   sudo systemctl status appraisal
   ```

### 3. 部署前端静态网站与 Nginx 反向代理
1. **前端本地打包**：
   ```bash
   cd frontend
   npm run build
   # 生成产物目录：frontend/dist/
   ```
2. **上传至服务器**（例如上传至 `/opt/appraisal/frontend/dist/`）：
   ```bash
   mkdir -p /opt/appraisal/frontend
   cp -r dist /opt/appraisal/frontend/
   ```
3. **配置 Nginx 站点**（`/etc/nginx/conf.d/appraisal.conf`）：
   ```nginx
   server {
       listen 80;
       server_name your-domain.com; # 替换为您的域名或公网IP

       client_max_body_size 50M;

       # 前端页面
       location / {
           root /opt/appraisal/frontend/dist;
           index index.html index.htm;
           try_files $uri $uri/ /index.html;
       }

       # 后端 API 反向代理
       location /api/ {
           proxy_pass http://127.0.0.1:8080/api/;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }

       # 材料静态文件反向代理
       location /files/ {
           proxy_pass http://127.0.0.1:8080/files/;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
       }
   }
   ```
4. **重载 Nginx**：
   ```bash
   sudo nginx -t
   sudo systemctl restart nginx
   ```

---

## 五、 Docker / Docker Compose 容器化一键部署（极简推荐）

前端与后端均已配置 **Docker 多阶段构建（Multi-Stage Build）**。**云服务器宿主机无需安装任何 Maven、JDK、Node.js、npm 或 MySQL**！只要服务器安装了 Docker，即可实现真正意义上的零环境依赖一键自动化部署。

### 1. 安装 Docker & Docker Compose
在云服务器上执行官方一键安装脚本（若已安装可跳过）：
```bash
# 官方 Docker 一键安装脚本
curl -fsSL https://get.docker.com | bash -s docker
sudo systemctl enable docker
sudo systemctl start docker

# 若尚未安装 docker-compose 插件（Ubuntu / Debian）：
sudo apt install -y docker-compose-plugin || sudo apt install -y docker-compose
```

### 2. 拉取代码并一键启动集群
直接克隆仓库并一条命令拉起全部服务（Docker 容器内会自动拉取 Maven 镜像编译 Java 源码、拉取 Node 镜像构建前端静态资源）：
```bash
# 1. 克隆代码仓库
git clone https://github.com/duyuanfu/performance-appraisal.git
cd performance-appraisal

# 2. 一键构建并后台启动全套集群（MySQL + 后端 + 前端）
docker compose up -d --build
# （注：如果您的服务器使用的是老版本 docker-compose 命令，请执行：docker-compose up -d --build）
```

### 3. 检查服务健康状态
```bash
docker compose ps
# 或 docker-compose ps
```
容器说明：
- **`appraisal-mysql`**：MySQL 8.0 容器，仅提供数据库服务，数据持久化挂载至 Docker Volume `mysql-data`；
- **`appraisal-backend`**：Spring Boot 3 服务，启动时由 Flyway 自动执行 `db/migration` 下的版本化脚本；材料 PDF 持久化映射至本地 `./data/uploads`；
- **`appraisal-frontend`**：Nginx 80 端口容器，内置前端生产页面并自动反向代理 API 流量至后端。

### 4. 访问系统
在浏览器中直接访问服务器公网 IP 或域名：
```text
http://<服务器公网IP>
```
默认管理员账号：`admin`，默认密码：`password123`。

### 5. 常用运维与维护命令
- 查看实时运行日志：`docker compose logs -f` 或 `docker compose logs -f backend`
- 重启所有服务：`docker compose restart`
- 停止服务（数据依然完整保留）：`docker compose down`
- 代码更新后重新编译拉起：
  ```bash
  git pull
  docker compose up -d --build
  ```

---

## 六、 目录工程结构一览

```text
performance-appraisal/
├── backend/                                   # 后端 Java 工程
│   ├── src/main/java/com/rawcenter/appraisal/
│   │   ├── common/                            # JWT 工具、全局统一响应、密码哈希、异常处理
│   │   ├── config/                            # 跨域拦截、动态业务指标配置、MyBatis-Plus 分页
│   │   ├── controller/                        # 认证、用户管理、批次、打分、项目、导出接口
│   │   ├── entity/                            # 实体层 (精度保留 4 位小数)
│   │   ├── mapper/                            # MyBatis-Plus 数据操作层
│   │   ├── service/                           # 核心加权计算与状态流转业务层
│   │   └── vo/ & dto/                         # 视图层与传输层对象
│   ├── src/main/resources/
│   │   ├── application.yml                    # 核心配置文件（含业务指标参数）
│   │   └── db/migration/                      # Flyway 版本化脚本（V1 建表、V2 初始化账号）
│   ├── Dockerfile                             # 后端 Docker 镜像构建文件
│   └── pom.xml
│
├── frontend/                                  # 前端 React 工程
│   ├── src/
│   │   ├── api/                               # 统一 Axios 请求接口层
│   │   ├── components/                        # 全局布局 (MainLayout, 顶部多角色切换), MetricConfigTable
│   │   ├── context/                           # AuthContext (登录态、多角色切换持久化)
│   │   ├── pages/
│   │   │   ├── Login/                         # 企业登录页面（含一键快捷演示登录）
│   │   │   ├── Users/                         # 用户账号管理与管理员一键重置密码
│   │   │   ├── MonthlyAppraisal/              # 月度考核管理、进度催办、领导打分工作台、个人总分查看
│   │   │   └── AnnualProject/                 # 年度项目评审、材料分屏查阅、专家打分、发布前总览
│   │   └── App.tsx                            # 动态路由与角色守卫
│   ├── Dockerfile                             # 前端多阶段构建 Dockerfile
│   ├── nginx.conf                             # 生产级 Nginx 配置
│   └── package.json
│
├── docs/                                      # 需求与业务确认文档
│   ├── 需求文档.md
│   ├── 需求疑问与确认清单.md
│   └── 项目评审界面.xlsx
│
├── openspec/                                  # OpenSpec 规范规约工程
│   └── specs/                                 # 4 项系统核心能力规范文档
│
├── docker-compose.yml                         # Docker 容器化一键编排脚本
├── .gitignore                                 # Git 忽略文件配置
└── README.md                                  # 项目说明与部署运维手册
```

---

## 七、 常见问题排查 (FAQ)

1. **Q：为什么在 Windows 上上传 PDF 报错找不到路径？**
   * A：已通过 `new File(uploadDir).getAbsoluteFile()` 与 Java NIO `Files.copy` 彻底解决，避免了 Tomcat 拼接触发临时路径不存在的问题。
2. **Q：重启后端服务后，创建的考核流程数据会丢失吗？**
   * A：不会。数据库结构与初始账号由 Flyway 版本化脚本管理，升级不会清空业务数据。
3. **Q：为什么新建的草稿批次，领导登录后看不到？**
   * A：这是系统严格的保密与防越权机制。草稿 (`DRAFT`) 状态仅供管理员配置，只有当管理员点击【发布】（变为 `IN_PROGRESS`）后，评分领导和专家才会在工作台中看到该任务。
4. **Q：归档后领导还能撤回打分吗？**
   * A：不能。一旦批次归档 (`COMPLETED`)，打分数据全面只读封存，所有打分人仅可查阅历史记录，禁止任何修改与撤回。
