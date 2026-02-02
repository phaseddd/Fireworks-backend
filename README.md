# Fireworks Backend 🎆

<p align="center">
  <strong>面向南澳县烟花店的微信小程序 · 后端服务</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-WeChat%20Cloud%20Hosting-07C160?style=flat-square&logo=wechat" alt="Platform" />
  <img src="https://img.shields.io/badge/Deploy-Docker%20Container-2496ED?style=flat-square&logo=docker" alt="Deploy" />
  <img src="https://img.shields.io/badge/Dev%20Cycle-2025.12--2026.01-blue?style=flat-square" alt="Dev Cycle" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2.0-6DB33F?style=flat-square&logo=springboot" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Java-17-007396?style=flat-square&logo=openjdk" alt="Java" />
  <img src="https://img.shields.io/badge/MyBatis--Plus-3.5.5-blue?style=flat-square" alt="MyBatis-Plus" />
  <img src="https://img.shields.io/badge/MySQL-5.7-4479A1?style=flat-square&logo=mysql" alt="MySQL" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Docs-79-success?style=flat-square" alt="Docs" />
  <img src="https://img.shields.io/badge/Stories-22-success?style=flat-square" alt="Stories" />
  <img src="https://img.shields.io/badge/Commits-40+-blueviolet?style=flat-square" alt="Commits" />
  <img src="https://img.shields.io/badge/Research%20Topics-10+-orange?style=flat-square" alt="Research" />
</p>

---

## TL;DR

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE fireworks CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 启动服务
mvn spring-boot:run

# 3. 验证运行
curl http://localhost:8080/api/health
```

---

## 阅读导航

| 目标 | 入口 |
|------|------|
| 🚀 **想快速了解** | 继续阅读下方「项目概览」和「核心亮点」 |
| 💻 **想本地运行** | 跳转至「本地开发指南」 |
| 🤖 **想看 AI 工程实践** | 跳转至「AI Engineering Story Talk」 |
| ☁️ **想了解部署** | 跳转至「微信云托管部署」 |
| 📚 **想看完整文档** | 查看 `../docs/index.md` |

---

## 项目概览

### 业务场景

**Fireworks** 是一款面向南澳县烟花店的微信小程序，后端服务提供：

- **商品管理**：CRUD + 图片上传 + 分类管理
- **代理商系统**：专属码生成 + 来源追踪 + 业绩统计
- **询价系统**：意向单管理 + 分享功能
- **认证鉴权**：JWT Token + 适老化登录

### 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                     微信小程序 (Taro)                         │
└─────────────────────────┬───────────────────────────────────┘
                          │ wx.cloud.callContainer
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                   微信云托管 (容器服务)                        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              Spring Boot API (0.25核 0.5GB)            │  │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────────┐  │  │
│  │  │ Product │ │  Agent  │ │ Inquiry │ │   Category  │  │  │
│  │  │ Service │ │ Service │ │ Service │ │   Service   │  │  │
│  │  └────┬────┘ └────┬────┘ └────┬────┘ └──────┬──────┘  │  │
│  │       └───────────┴───────────┴─────────────┘         │  │
│  │                         │                              │  │
│  │              ┌──────────┴──────────┐                   │  │
│  │              │  MyBatis-Plus ORM   │                   │  │
│  │              └──────────┬──────────┘                   │  │
│  └──────────────────────── │ ────────────────────────────┘  │
│                            │                                 │
│  ┌─────────────────────────┴─────────────────────────────┐  │
│  │           Serverless MySQL 5.7 (自动暂停)               │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              对象存储 (CDN 加速)                         │  │
│  │              • 商品图片  • 小程序码                      │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 关键约束

| 约束 | 说明 |
|------|------|
| 部署平台 | 微信云托管，支持缩容到零 |
| 数据库 | MySQL 5.7 Serverless，自动暂停 |
| 成本目标 | 年费约 400 元 |
| API 调用 | 通过 `wx.cloud.callContainer` 免鉴权调用 |

---

## 核心亮点

### 🏗️ 后端架构

```
src/main/java/com/fireworks/
├── FireworksApplication.java       # 启动类
├── common/                         # 通用类
│   └── Result.java                 # 统一响应封装
├── config/                         # 配置类
│   ├── CorsConfig.java             # 跨域配置
│   ├── MybatisPlusConfig.java      # ORM 配置
│   ├── JwtAuthInterceptor.java     # JWT 拦截器
│   └── WebMvcConfig.java           # MVC 配置
├── controller/                     # 控制器层
│   ├── ProductController.java      # 商品管理 API
│   ├── ProductPublicController.java# 商品公开 API
│   ├── AgentController.java        # 代理商 API
│   ├── InquiryController.java      # 询价 API
│   ├── CategoryController.java     # 分类 API
│   └── AuthController.java         # 认证 API
├── service/                        # 服务层
│   ├── ProductService.java
│   ├── AgentService.java
│   ├── InquiryService.java
│   ├── CategoryService.java
│   ├── VideoExtractService.java    # 🎬 视频提取服务
│   └── WechatCloudService.java     # 微信云调用服务
├── entity/                         # 实体类
├── mapper/                         # MyBatis Mapper
├── dto/                            # 数据传输对象
├── vo/                             # 视图对象
└── util/                           # 工具类
    ├── JwtTokenProvider.java       # JWT 工具
    ├── PasswordUtil.java           # 密码加密
    └── QrCodeUtil.java             # 二维码工具
```

### 🎬 视频自动提取

商品视频提取功能亮点：

| 特性 | 实现 |
|------|------|
| **动态页面解析** | HtmlUnit 无头浏览器渲染 SPA |
| **二维码识别** | ZXing 解析商品包装二维码 |
| **异步处理** | Spring Async 后台提取 |
| **状态追踪** | 提取状态枚举管理 |

### 🔐 安全设计

| 层面 | 实现 |
|------|------|
| **认证** | JWT Token (jjwt 0.12.3) |
| **密码** | BCrypt 加密 (spring-security-crypto) |
| **鉴权** | 拦截器 + 注解组合 |
| **API** | 管理端/公开端分离 |

---

## AI Engineering Story Talk

> **这不是"用 AI 写代码"，而是一套可复用、可审计的工程流程。**

### 1. 项目目标与约束

| 维度 | 说明 |
|------|------|
| **业务场景** | 面向南澳县烟花店的微信小程序后端服务 |
| **交付周期** | 2025-12 ~ 2026-01（短周期高密度迭代） |
| **关键约束** | 微信云托管 + Serverless MySQL + 低成本运维 |

### 2. BMad 框架：需求工程化

采用 [BMad](https://github.com/bmad-method) 方法论进行需求管理：

- **产出物**：Brief → PRD → Architecture → Stories
- **验收标准**：每个 Story 都有明确的 Done 定义
- **文档规模**：79 个文档，22 个 Story

```
docs/
├── brief.md           # 项目简报 v1.5
├── prd.md             # 产品需求 v0.8
├── architecture.md    # 架构文档 v1.2
└── stories/           # 22 个 Story 文档
    ├── 1.1-project-init.md
    ├── 1.2-database-schema.md
    ├── 1.3-admin-login.md
    └── ...
```

### 3. Claude Code + Codex 工作流

| 角色 | 职责 | 典型任务 |
|------|------|----------|
| **Claude Code** | 主力开发 | 功能实现、架构决策、代码重构 |
| **Codex** | 工程加速 | 代码审阅、重复劳动压缩、测试补全 |

**工作流程**：

```
需求分析 → 任务拆分 → 实现代码 → 自检审阅 → 文档沉淀
    ↓          ↓          ↓          ↓          ↓
  BMad      Story      Claude    Codex      docs/
           模板       Code      Review
```

### 4. 可验证成果

| 指标 | 数量 |
|------|------|
| Backend commits | 40+ |
| 文档数量 | 79 |
| Story 数量 | 22 |
| API 接口 | 30+ |
| 技术调研专题 | 10+ |

**文档索引**：`../docs/index.md`

### 5. 技术调研沉淀

| 专题 | 结论 | 文档 |
|------|------|------|
| 微信云托管 | ✅ 推荐（年费约400元） | `docs/微信云托管调研/` |
| 视频提取方案 | ✅ HtmlUnit + ZXing | `docs/读取页面提取视频优化调研/` |
| 小程序码生成 | ✅ wxacode.getUnlimited | `docs/中介交互调研/` |

---

## 本地开发指南

### 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 17+ |
| Maven | 3.9+ |
| MySQL | 5.7+ |

### 数据库初始化

```sql
-- 创建数据库
CREATE DATABASE fireworks CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE fireworks;

-- 执行初始化脚本（位于 src/main/resources/db/）
```

### 配置说明

默认配置使用 `application-local.yml`（本地开发 profile）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/fireworks
    username: root
    password: <your-password>  # 请替换为本地数据库密码
```

如需修改，编辑 `src/main/resources/application-local.yml`。

### 启动服务

```bash
# 方式一：Maven 启动
mvn spring-boot:run

# 方式二：打包后启动
mvn clean package -DskipTests
java -jar target/fireworks-backend-0.0.1-SNAPSHOT.jar
```

### 验证运行

```bash
# 健康检查
curl http://localhost:8080/api/health

# 服务状态
curl http://localhost:8080/api/
```

---

## API 概览

### 公开接口（无需认证）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/health` | GET | 健康检查 |
| `/api/v1/auth/login` | POST | 管理员登录 |
| `/api/v1/products/public` | GET | 商品列表（公开） |
| `/api/v1/products/public/{id}` | GET | 商品详情（公开） |
| `/api/v1/categories/active` | GET | 活跃分类列表（公开） |

### 管理接口（需要 JWT）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/v1/products` | GET/POST | 商品列表/新增 |
| `/api/v1/products/{id}` | PUT/DELETE | 商品更新/删除 |
| `/api/v1/agents` | GET/POST | 代理商列表/新增 |
| `/api/v1/agents/{code}/qrcode` | POST | 代理商专属码生成 |
| `/api/v1/agents/{code}/bind-code` | POST | 代理商绑定码生成 |
| `/api/v1/inquiries` | GET | 询价列表 |
| `/api/v1/categories` | GET/POST | 分类列表/新增 |
| `/api/v1/categories/{id}` | PUT/DELETE | 分类更新/删除 |

---

## 微信云托管部署

### 部署方式

项目已包含 `Dockerfile`，可直接在微信云托管控制台部署。

### 环境变量

云托管需要配置以下环境变量：

| 变量 | 说明 | 来源 |
|------|------|------|
| `MYSQL_ADDRESS` | 数据库地址 | 云托管自动注入 |
| `MYSQL_DATABASE` | 数据库名 | 云托管自动注入 |
| `MYSQL_USERNAME` | 用户名 | 云托管自动注入 |
| `MYSQL_PASSWORD` | 密码 | 云托管自动注入 |
| `JWT_SECRET` | JWT 签名密钥 | 手动配置 |
| `WX_CLOUD_ENV_ID` | 微信云托管环境 ID | 手动配置 |

### 成本估算

| 服务 | 规格 | 预估成本 |
|------|------|----------|
| 容器服务 | 0.25核 0.5GB，缩容到零 | ~200 元/年 |
| Serverless MySQL | 自动暂停 | ~150 元/年 |
| 对象存储 | CDN 加速 | ~50 元/年 |
| **总计** | | **~400 元/年** |

---

## 技术栈

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **框架** | Spring Boot | 3.2.0 | 核心框架 |
| **语言** | Java | 17 | LTS 版本 |
| **ORM** | MyBatis-Plus | 3.5.5 | 增强 CRUD |
| **数据库** | MySQL | 5.7 | 关系型数据库 |
| **认证** | JWT (jjwt) | 0.12.3 | Token 认证 |
| **加密** | BCrypt | - | 密码加密 |
| **爬虫** | HtmlUnit | 4.21.0 | 动态页面解析 |
| **二维码** | ZXing | 3.5.2 | 二维码解析 |

---

## 项目结构

```
Fireworks-backend/
├── src/
│   ├── main/
│   │   ├── java/com/fireworks/    # Java 源码
│   │   └── resources/
│   │       ├── application.yml     # 默认配置
│   │       ├── application-local.yml  # 本地开发配置
│   │       ├── application-prod.yml   # 生产环境配置
│   │       └── db/                 # 数据库脚本
│   └── test/                       # 测试代码
├── Dockerfile                      # Docker 构建文件
├── pom.xml                         # Maven 配置
└── README.md                       # 本文档
```

---

## 相关文档

| 文档 | 说明 |
|------|------|
| [项目简报](../docs/brief.md) | 业务背景与目标 |
| [PRD](../docs/prd.md) | 产品需求文档 |
| [架构文档](../docs/architecture.md) | 全栈架构设计 |
| [微信云托管调研](../docs/微信云托管调研/) | 部署方案调研 |
| [文档索引](../docs/index.md) | 完整文档导航 |

---

## License

MIT License

---

<p align="center">
  <sub>Built with ❤️ using Claude Code + Codex</sub>
</p>
