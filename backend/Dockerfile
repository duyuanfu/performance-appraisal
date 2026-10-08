# 阶段 1：多阶段构建 - 使用容器内 Maven 自动打包（宿主机无需安装 Maven 和 JDK）
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 配置国内 Maven 镜像加速依赖下载
RUN mkdir -p /root/.m2 && \
    echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd"><mirrors><mirror><id>aliyunmaven</id><mirrorOf>central</mirrorOf><name>Aliyun Maven</name><url>https://maven.aliyun.com/repository/public</url></mirror></mirrors></settings>' > /root/.m2/settings.xml

# 拷贝 pom.xml 和源码
COPY pom.xml .
COPY src ./src

# 执行构建打包
RUN mvn clean package -DskipTests

# 阶段 2：生产运行环境（轻量级 JRE 镜像）
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 从阶段 1 拷贝构建产物 Jar 包
COPY --from=builder /app/target/performance-appraisal-backend-1.0.0-SNAPSHOT.jar app.jar

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
