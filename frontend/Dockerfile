# 前端多阶段构建 Dockerfile
FROM node:20-alpine AS builder

WORKDIR /app

# 安装依赖
COPY package*.json ./
RUN npm install

# 复制源码并执行构建
COPY . .
RUN npm run build

# 生产运行阶段 (Nginx 基础镜像)
FROM nginx:alpine

# 拷贝静态资源产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 拷贝 Nginx 反向代理配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
