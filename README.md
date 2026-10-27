# 原料中心评审管理系统 (Raw Material Center Performance Appraisal System)

一套面向原料中心的 WEB 端综合绩效与项目评审管理平台。系统支持**月度部门负责人绩效考核**和**年度目标管理绩效（按项目）评审**两大核心业务场景，实现考核指标配置灵活化、材料查阅与评分线上化、结果自动加权计算与排名、历史数据可追溯导出。

> 当前版本：**v1.1.0**

---

## 一、 系统架构与技术栈

### 1. 技术架构选型
- **后端架构**：Java 17 + Spring Boot 3.2.5 + MyBatis-Plus + MySQL 8.0 + EasyExcel 3.3.4 + JJWT
- **前端架构**：React 18 + Vite 5 + Ant Design 5 + TypeScript + React Router 6 + Axios + Day.js
- **部署环境**：**Docker / Docker Compose** 容器化一键部署；本地开发环境支持 JDK、Node.js、Maven 直接运行

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

## 四、 Docker / Docker Compose 容器化一键部署（极简推荐）

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

### 2. 基础镜像加速（默认已启用）

项目默认通过 `DOCKER_REGISTRY` 使用 `docker.m.daocloud.io` 拉取构建基础镜像，避免直接从 Docker Hub 拉取 `eclipse-temurin`、`maven`、`node`、`nginx` 时长时间卡住。MySQL 仍使用 `mysql:8.0` 官方镜像，已部署环境不会因为更换镜像名而重新拉取。

如需切换镜像源，可在项目根目录创建 `.env`：

```bash
DOCKER_REGISTRY=docker.m.daocloud.io
```

或临时指定：

```bash
DOCKER_REGISTRY=docker.m.daocloud.io docker compose up -d --build
```

如需使用 Docker 官方源：

```bash
DOCKER_REGISTRY=docker.io docker compose up -d --build
```

如果某个镜像源不可用，可替换为其他可访问的 Docker Hub 镜像加速地址，或为 Docker 配置 `/etc/docker/daemon.json` 的 `registry-mirrors`。

本项目 Dockerfile 已内置以下下载加速与缓存策略：

- 后端 Maven 使用阿里云镜像；
- 前端 npm 使用 `registry.npmmirror.com` 镜像；
- 后端 Maven 本地仓库、前端 npm 缓存均使用 BuildKit 缓存挂载，后续构建无需重复下载依赖；
- `backend/.dockerignore`、`frontend/.dockerignore` 已排除 `target`、`node_modules`、`dist` 等目录，避免向 Docker 传输大量无关文件。

> 首次构建仍然需要下载基础镜像和全部依赖；从第二次构建开始会明显加速。Docker 23+ 默认启用 BuildKit；如果未启用，可执行 `DOCKER_BUILDKIT=1 docker compose build`。

如果基础镜像拉取仍然很慢，可先单独预拉取并确认镜像源可用：

```bash
docker pull docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-17
docker pull docker.m.daocloud.io/library/eclipse-temurin:17-jre-jammy
docker pull docker.m.daocloud.io/library/node:20-alpine
docker pull docker.m.daocloud.io/library/nginx:alpine
```

### 3. 拉取代码并一键启动集群
直接克隆仓库并一条命令拉起全部服务（Docker 容器内会自动拉取 Maven 镜像编译 Java 源码、拉取 Node 镜像构建前端静态资源）：
```bash
# 1. 克隆代码仓库
git clone https://github.com/duyuanfu/performance-appraisal.git
cd performance-appraisal

# 2. 一键构建并后台启动全套集群（MySQL + 后端 + 前端）
docker compose up -d --build
# （注：如果您的服务器使用的是老版本 docker-compose 命令，请执行：docker-compose up -d --build）
```

### 4. 检查服务健康状态
```bash
docker compose ps
# 或 docker-compose ps
```
容器说明：
- **`appraisal-mysql`**：MySQL 8.0 容器，仅提供数据库服务，数据持久化挂载至 Docker Volume `mysql-data`；
- **`appraisal-backend`**：Spring Boot 3 服务，启动时由 Flyway 自动执行 `db/migration` 下的版本化脚本；材料 PDF 持久化映射至本地 `./data/uploads`；
- **`appraisal-frontend`**：Nginx 80 端口容器，内置前端生产页面并自动反向代理 API 流量至后端。

### 5. 访问系统
在浏览器中直接访问服务器公网 IP 或域名：
```text
http://<服务器公网IP>
```
默认管理员账号：`admin`，默认密码：`password123`。

### 6. 常用运维与维护命令
- 查看实时运行日志：`docker compose logs -f` 或 `docker compose logs -f backend`
- 重启所有服务：`docker compose restart`
- 停止服务（数据依然完整保留）：`docker compose down`
- 代码更新后的再次部署：见下方“五、再次部署 / 版本升级指南”

---

## 五、 再次部署 / 版本升级指南

适用于服务器上已经成功部署过、现在需要更新代码、配置或数据库结构的场景。核心原则：**先备份、再更新、后验证；不要删除数据库卷和上传目录。**

### 0. 部署前检查

1. **确认代码已推送**：服务器通过 `git pull` 更新代码时，本地提交必须先推送到远程仓库；否则服务器拉不到最新代码。
2. **备份数据库和上传文件**：升级前必须备份，尤其是包含 Flyway 数据库迁移的版本。
3. **检查是否有新迁移脚本**：查看 `backend/src/main/resources/db/migration/` 是否新增 `V*.sql`。Flyway 会在后端启动时自动执行；已经执行过的脚本不要修改，新增变更请新建更高版本脚本。

> 注意：如果服务器上有本地改动，请先备份或处理，避免 `git pull` 覆盖服务器上的临时修改。

### 1. 更新代码与备份

#### 1.1 更新代码

```bash
cd /opt/appraisal        # 替换为服务器实际项目目录
git status
git pull
```

#### 1.2 备份数据库和上传文件

```bash
mkdir -p backups

# 备份 MySQL 数据库
docker compose exec -T mysql mysqldump -uroot -proot \
  --single-transaction --routines --triggers appraisal_db \
  > backups/db_$(date +%F_%H%M%S).sql

# 备份上传的 PDF 材料
tar -czf backups/uploads_$(date +%F_%H%M%S).tar.gz data/uploads
```

#### 1.3 重新构建并启动

```bash
# 前后端都更新时，执行完整重建
docker compose up -d --build
```

如果只修改了后端或前端，也可以只重建对应服务：

```bash
docker compose up -d --build backend
docker compose up -d --build frontend
```

> 不要执行 `docker compose down -v`，`-v` 会删除 `mysql-data` 数据卷，导致数据库数据丢失。

#### 1.4 检查部署结果

```bash
docker compose ps
docker compose logs --tail=100 backend
curl -I http://127.0.0.1/
```

查看 Flyway 最近执行的迁移：

```bash
docker compose exec -T mysql mysql -uroot -proot appraisal_db \
  -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```

#### 1.5 回滚

```bash
git checkout <上一个稳定提交>
docker compose up -d --build
```

> Flyway 迁移是向前执行的。如果本次升级包含不兼容的数据库结构变更，仅回滚代码可能不够，需要结合升级前的数据库备份恢复数据。恢复数据库会覆盖当前数据，操作前务必再次确认。

### 2. 再次部署检查清单

| 检查项 | 操作 |
| :--- | :--- |
| 备份数据库 | `mysqldump` 到 `backups/` |
| 备份上传文件 | 打包 `data/uploads` |
| 更新代码 | `git pull` |
| 后端更新 | `docker compose up -d --build backend` |
| 前端更新 | `docker compose up -d --build frontend` |
| 数据库迁移 | 后端启动时 Flyway 自动执行 |
| 健康检查 | `docker compose ps` + 日志 |
| 页面验证 | 登录、批次、评分、汇总、导出 |

> 当前版本为 **v1.1.0**，Docker 镜像标签为 `appraisal-backend:v1.1.0` 和 `appraisal-frontend:v1.1.0`。首次构建需要下载基础镜像和依赖，后续构建会复用 BuildKit 缓存；如依赖未变化但构建异常，可执行 `docker compose build --no-cache` 重新构建。

> 上传的 PDF 材料属于业务数据，升级时不要删除 `data/uploads`。如果是迁移到新服务器，还需要同时迁移数据库备份和上传目录。

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
│   │   └── db/migration/                      # Flyway 版本化脚本（V1 建表、V2 初始化账号、V3 项目字段与权重调整）
│   ├── Dockerfile                             # 后端 Docker 镜像构建文件
│   ├── .dockerignore                          # 排除 target 等无关构建上下文
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
│   ├── .dockerignore                          # 排除 node_modules、dist 等无关构建上下文
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

## 七、数据库表说明

数据库结构与基础数据由 **Flyway** 自动管理，脚本位于 `backend/src/main/resources/db/migration/`：

- `V1__init_schema.sql`：初始化全部业务表、索引、外键与检查约束；
- `V2__seed_reference_data.sql`：初始化系统角色、测试账号及用户角色关系；
- `V3__simplify_project_department_and_leader_weights.sql`：精简项目录入字段，移除项目负责人/分管领导，并调整项目中心领导细分权重结构。

### 1. 权限与组织

| 表名 | 说明 | 关键字段 / 关系 |
| :--- | :--- | :--- |
| `sys_user` | 系统用户及组织信息 | `position_category` 标识人员类别（中心主要领导、中心领导、部门正职、部门副职、专家、普通职工）；`supervisor_id`、`dept_head_id` 为自关联外键，并保留姓名快照 |
| `sys_role` | 系统角色字典 | `role_code` 唯一，包含 `SYS_ADMIN`、`ADMIN`、`LEADER`、`DEPT_HEAD`、`DEPT_DEPUTY`、`EXPERT`、`EMPLOYEE` |
| `sys_user_role` | 用户与角色多对多关系 | 唯一键 `(user_id, role_id)`；支持同一账号拥有多个角色并在前端切换身份 |

### 2. 批次与指标

| 表名 | 说明 | 关键字段 / 关系 |
| :--- | :--- | :--- |
| `appraisal_batch` | 统一评审批次主表 | `batch_type`：`MONTHLY`、`TARGET_PROJECT`、`SPECIAL_INNOVATION`、`ANNUAL_PROJECT`；`status`：`DRAFT`、`IN_PROGRESS`、`COMPLETED`；月度批次使用 `monthly_category`，项目批次使用 `project_category`；支持逻辑删除 `deleted` |
| `appraisal_metric` | 批次级评审指标 | 外键 `batch_id`；唯一键 `(batch_id, metric_name)`；保存 `max_score`、`weight`、`sort_order`，服务层校验权重合计为 100 |

> 默认指标模板由 `backend/src/main/resources/application.yml` 的 `app.default-metrics` 配置：目标绩效任务为“完成度 / 完成质量 / 完成时效性”（30% / 50% / 20%）；专项与创新任务为“特色亮点 / 产生质效 / 难度系数”（30% / 50% / 20%）。

### 3. 月度考核

| 表名 | 说明 | 关键字段 / 关系 |
| :--- | :--- | :--- |
| `monthly_role_weight` | 月度考评主体权重 | 外键 `batch_id`；`role_category` 区分中心主要领导、分管领导、其他中心领导、部门正职、互评正副职等 |
| `monthly_evaluator` | 月度中心领导评分人配置 | 外键 `batch_id`、`leader_user_id`；保存领导评分权重。部门正副职互评人由人员岗位类别和参评人员动态计算，不完全依赖本表 |
| `monthly_examinee` | 月度被考核人员 | 外键 `batch_id`、`examinee_user_id`；保存部门、岗位快照；唯一键 `(batch_id, examinee_user_id)` |
| `monthly_score_record` | 月度评分主记录 | 记录某评分人对某被考核人的总分；唯一键 `(batch_id, leader_user_id, examinee_user_id)`；`status` 为 `DRAFT` 或 `SUBMITTED`；`version` 用于乐观锁 |
| `monthly_score_detail` | 月度指标级评分明细 | 外键 `record_id`、`metric_id`；唯一键 `(record_id, metric_id)`；保存单项指标得分 |

### 4. 项目评审

| 表名 | 说明 | 关键字段 / 关系 |
| :--- | :--- | :--- |
| `project_item` | 参评项目信息 | 外键 `batch_id`；`department` 为自由文本部门名称，仅用于汇总排名展示；保存申报材料/附件 PDF 路径；支持逻辑删除 `deleted`。V3 后不再保存项目负责人和分管领导 |
| `project_role_weight` | 项目考评主体权重 | 外键 `batch_id`；`role_category` 包含 `CENTER_LEADER_TOTAL`、`PRIMARY_LEADER`、`OTHER_LEADER`、`DEPT_HEAD`、`EXPERT`；专项与创新任务默认 40%/60%，目标绩效任务默认 30%/70%（由业务层初始化） |
| `project_expert` | 项目评审专家配置 | 外键 `batch_id`、`expert_user_id`；保存专家权重；唯一键 `(batch_id, expert_user_id)` |
| `project_score_record` | 项目评分主记录 | 记录某评分人对某项目的总分；唯一键 `(batch_id, expert_user_id, project_id)`；`evaluator_role_type` 标识评分主体角色；`status` 为 `DRAFT` 或 `SUBMITTED`；`version` 用于乐观锁 |
| `project_score_detail` | 项目指标级评分明细 | 外键 `record_id`、`metric_id`；唯一键 `(record_id, metric_id)`；保存单项指标得分 |

### 5. 核心关系概览

```text
sys_user ──< sys_user_role >── sys_role
appraisal_batch ──< appraisal_metric
appraisal_batch ──< monthly_evaluator / monthly_examinee / monthly_score_record
monthly_score_record ──< monthly_score_detail >── appraisal_metric
appraisal_batch ──< project_item / project_expert / project_score_record
project_score_record ──< project_score_detail >── appraisal_metric
```

> 说明：`appraisal_batch` 是月度考核与项目评审的统一批次主表；批次删除时，其下的指标、评分记录和明细按外键级联清理。用户被评分记录引用时采用受限删除，避免破坏历史评分数据。

---

## 八、 常见问题排查 (FAQ)

1. **Q：为什么在 Windows 上上传 PDF 报错找不到路径？**
   * A：已通过 `new File(uploadDir).getAbsoluteFile()` 与 Java NIO `Files.copy` 彻底解决，避免了 Tomcat 拼接触发临时路径不存在的问题。
2. **Q：重启后端服务后，创建的考核流程数据会丢失吗？**
   * A：不会。数据库结构与初始账号由 Flyway 版本化脚本管理，升级不会清空业务数据。
3. **Q：为什么新建的草稿批次，领导登录后看不到？**
   * A：这是系统严格的保密与防越权机制。草稿 (`DRAFT`) 状态仅供管理员配置，只有当管理员点击【发布】（变为 `IN_PROGRESS`）后，评分领导和专家才会在工作台中看到该任务。
4. **Q：归档后领导还能撤回打分吗？**
   * A：不能。一旦批次归档 (`COMPLETED`)，打分数据全面只读封存，所有打分人仅可查阅历史记录，禁止任何修改与撤回。
