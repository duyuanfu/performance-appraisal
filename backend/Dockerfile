# syntax=docker/dockerfile:1

# 默认使用 DaoCloud 公共镜像加速拉取基础镜像；可通过 build arg 覆盖
ARG DOCKER_REGISTRY=docker.m.daocloud.io

# 阶段 1：多阶段构建 - 使用容器内 Maven 自动打包（宿主机无需安装 Maven 和 JDK）
FROM ${DOCKER_REGISTRY}/library/maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 配置国内 Maven 镜像加速依赖下载
RUN mkdir -p /root/.m2 && \
    echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd"><mirrors><mirror><id>aliyunmaven</id><mirrorOf>central</mirrorOf><name>Aliyun Maven</name><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>' > /root/.m2/settings.xml

# 先复制 pom.xml 并预下载依赖，充分利用 Docker 层缓存
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -q dependency:go-offline

# 再复制源码并执行构建打包，Maven 本地仓库通过 BuildKit 缓存跨构建复用
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -q clean package -DskipTests

# 阶段 2：生产运行环境（轻量级 JRE 镜像）
FROM ${DOCKER_REGISTRY}/library/eclipse-temurin:17-jre-jammy

WORKDIR /app

# 从阶段 1 拷贝构建产物 Jar 包
COPY --from=builder /app/target/performance-appraisal-backend-1.1.0.jar app.jar

# 声明服务端口
EXPOSE 8080

# 环境变量默认配置
ENV JAVA_OPTS="-Xms256m -Xmx512m -Duser.timezone=Asia/Shanghai"
ENV MYSQL_HOST=mysql
ENV MYSQL_PORT=3306
ENV MYSQL_DB=appraisal_db
ENV MYSQL_USER=root
ENV MYSQL_PASSWORD=root

# 挂载材料上传物理存储目录
VOLUME /app/data/uploads

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
