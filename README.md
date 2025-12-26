# Fireworks Backend

🎆 **烟花商品展示小程序** - 后端服务

## 技术栈

- **框架**: Spring Boot 3.2.0
- **语言**: Java 17
- **ORM**: MyBatis-Plus 3.5.5
- **数据库**: MySQL 5.7
- **认证**: JWT
- **部署**: 微信云托管

## 项目结构

```
src/main/java/com/fireworks/
├── FireworksApplication.java    # 启动类
├── common/                      # 通用类
│   └── Result.java              # 统一响应封装
├── config/                      # 配置类
│   ├── CorsConfig.java          # 跨域配置
│   └── MybatisPlusConfig.java   # MyBatis-Plus 配置
├── controller/                  # 控制器层
│   └── HealthController.java    # 健康检查
├── dto/                         # 数据传输对象
├── entity/                      # 实体类
├── exception/                   # 异常处理
│   ├── BusinessException.java   # 业务异常
│   └── GlobalExceptionHandler.java  # 全局异常处理
├── mapper/                      # 数据访问层
├── service/                     # 服务层接口
│   └── impl/                    # 服务层实现
├── util/                        # 工具类
└── vo/                          # 视图对象
```

## 本地开发

### 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 5.7+

### 启动步骤

1. **配置数据库**

创建数据库：
```sql
CREATE DATABASE fireworks CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. **修改配置**（可选）

默认配置使用 localhost:3306，用户名密码为 root/root。
如需修改，编辑 `src/main/resources/application.yml`。

3. **启动服务**

```bash
mvn spring-boot:run
```

4. **验证运行**

访问 http://localhost:8080/api/health

## API 文档

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/health` | GET | 健康检查 |
| `/api/` | GET | 服务状态 |

## 部署

### 微信云托管部署

项目已包含 `Dockerfile`，可直接在微信云托管控制台部署。

**环境变量**（云托管自动注入）：
- `MYSQL_ADDRESS` - 数据库地址
- `MYSQL_DATABASE` - 数据库名
- `MYSQL_USERNAME` - 用户名
- `MYSQL_PASSWORD` - 密码

## 相关文档

- [架构文档](../docs/architecture.md)
- [PRD 文档](../docs/prd.md)
- [微信云托管调研](../docs/微信云托管调研/)

---

> **项目状态**: 🚧 开发中
