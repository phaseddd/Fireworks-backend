# ========== 构建阶段 ==========
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 复制 pom.xml 先下载依赖（利用 Docker 缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并构建
COPY src ./src
RUN mvn clean package -DskipTests

# ========== 运行阶段 ==========
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 设置时区为上海（解决时区问题）
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 安装证书（云调用 HTTPS 需要）
RUN apk add --no-cache ca-certificates

# 复制构建产物
COPY --from=builder /app/target/*.jar app.jar

# 暴露端口（与服务配置中的端口一致）
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/api/health || exit 1

# 启动命令
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
