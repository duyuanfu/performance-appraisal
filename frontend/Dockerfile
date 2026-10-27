# syntax=docker/dockerfile:1

# 默认使用 DaoCloud 公共镜像加速拉取基础镜像；可通过 build arg 覆盖
ARG DOCKER_REGISTRY=docker.m.daocloud.io

# 前端多阶段构建 Dockerfile
FROM ${DOCKER_REGISTRY}/library/node:20-alpine AS builder

WORKDIR /app

# 使用国内 npm 镜像，并通过 BuildKit 缓存复用依赖下载
COPY package*.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --registry=https://registry.npmmirror.com --prefer-offline --no-audit --no-fund

# 复制源码并执行构建
COPY . .
RUN npm run build

# 生产运行阶段 (Nginx 基础镜像)
FROM ${DOCKER_REGISTRY}/library/nginx:alpine

# 拷贝静态资源产物
COPY --from=builder /app/dist /usr/share/nginx/html

# 拷贝 Nginx 反向代理配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
