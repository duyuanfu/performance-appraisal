# 后端 Dockerfile (Java 17 JRE)
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 复制已打包的 Jar 产物
COPY target/performance-appraisal-backend-1.0.0-SNAPSHOT.jar app.jar

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
