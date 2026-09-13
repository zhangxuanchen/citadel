# Citadel 权限中心

> 统一认证授权中心（Authorization & Authentication Center）。  
> 以 Agent 原生（Agent-Native）的权限隔离为理念：**每个接入方都是一座独立的城堡，权限是它的城墙**——在自身权限域内自由行动，跨域访问任何资源都必须经过显式授权的城门。权限边界贯穿应用的整个生命周期。

Citadel 是一套开箱即用的 Spring Boot 认证授权方案：统一账号、JWT 认证、基于「接入应用 → 权限 → 角色 → 用户」的可视化 RBAC，并提供 SSO 一键登录、审计日志、接口限流与登录防爆破等安全能力。业务系统通过配套的二方包（starter）即可快速接入并校验 JWT 与权限。

---

## 目录

- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始（本地开发）](#快速开始本地开发)
- [默认账号](#默认账号)
- [核心概念](#核心概念)
- [API 概览](#api-概览)
- [业务应用接入](#业务应用接入)
- [生产部署](#生产部署)
- [配置项速查](#配置项速查)
- [安全说明](#安全说明)
- [相关文档](#相关文档)

---

## 功能特性

- **统一认证**：注册 / 登录 / 刷新 Token / 退出登录，JWT 采用 **RS256 非对称签名**，业务应用只需持有公钥或 JWKS 即可验签。
- **图形验证码**：登录/注册需携带 kaptcha 图形验证码，防止机器自动爆破。
- **可视化 RBAC**：按「接入应用 → 权限 → 角色 → 用户」四层模型维护，权限必须归属到具体应用，账号可访问的应用由角色权限自动推导。
- **管理后台**：内置后台页面（`/admin/index.html`），应用、权限、角色、用户全部可视化维护。
- **SSO 一键登录**：已登录用户可为其他业务应用签发一次性 `ticket`，业务应用凭 ticket 换取用户 Token。
- **安全防护**：
  - 接口级限流（单 IP 在时间窗口内限制 `/api/**` 请求次数）
  - 登录防爆破（同一用户名 + IP 连续失败锁定，自动解锁）
  - JWT 登出黑名单、Refresh Token 撤销
- **审计日志**：登录、权限变更、操作、HTTP 请求全量记录，支持后台查询。
- **二方包接入**：`authz-client-spring-boot-starter` 提供自动配置，业务应用引入后即可用 `@PreAuthorize` 控制接口权限。

## 技术栈

| 领域 | 选型 |
| --- | --- |
| 语言 / 框架 | Java 24 · Spring Boot 3.5.3 · Spring Security |
| 持久层 | MyBatis（tk.mybatis mapper）+ 通用 Mapper |
| 认证 | JJWT 0.12（RS256）、kaptcha 验证码 |
| 数据库 | 本地 H2（内存，默认 profile）/ 生产 MySQL 8.4 |
| 部署 | Docker Compose（MySQL + App + Nginx）、一键上线脚本 |
| 前端 | 原生 HTML / CSS / JS（无外部依赖） |

## 项目结构

```text
citadel/
├── src/main/java/cn/com/app/security/
│   ├── api/          # Controller 与 DTO（auth / user / role / permission / client-app / sso / audit）
│   ├── config/       # 安全配置、JWT 属性、数据初始化
│   ├── domain/       # 领域实体（UserAccount、Role、Permission、ClientApp…）
│   ├── logging/      # 操作日志切面、HTTP 请求日志过滤器
│   ├── repository/   # 数据访问层
│   ├── security/     # JWT 过滤器与服务、限流过滤器、RSA 工具
│   └── service/      # 业务逻辑
├── src/main/resources/
│   ├── static/admin/ # 管理后台页面（HTML/CSS/JS）
│   ├── application.yml        # 本地默认配置（H2 + 开发密钥）
│   ├── application-prod.yml   # 生产配置（MySQL + 环境变量注入）
│   └── schema.sql             # H2 初始化脚本
├── authz-client-spring-boot-starter/   # 业务应用接入二方包
├── deploy/
│   ├── mysql/        # MySQL 初始化表结构与迁移脚本
│   ├── nginx/        # Nginx 反向代理配置
│   └── online.env.example     # 线上参数模板
├── Dockerfile
├── docker-compose.yml
├── deploy.sh         # 一键上线脚本
├── API.md            # 接口文档
├── USAGE.md          # 使用文档
└── DEPLOY.md         # 部署文档
```

## 快速开始（本地开发）

本地默认使用 H2 内存数据库，无需安装 MySQL，一条命令即可启动：

```bash
mvn spring-boot:run
```

启动后：

- 管理后台：<http://localhost:8080/admin/index.html>
- 认证发现接口：<http://localhost:8080/api/auth/discovery>
- JWKS 公钥：<http://localhost:8080/api/auth/jwks>
- H2 控制台：<http://localhost:8080/h2-console>（JDBC URL `jdbc:h2:mem:authz`）

> 提示：本地默认 profile 下，登录验证码为图形，需手动识别输入；数据存于内存，重启后自动重新初始化。

## 默认账号

| 用户名 | 密码 | 角色 / 权限 | 说明 |
| --- | --- | --- | --- |
| `admin` | `password` | ADMIN（ARTICLE_READ / ARTICLE_WRITE / **USER_MANAGE**） | 管理员，可操作后台全部功能 |
| `user` | `password` | USER（ARTICLE_READ） | 普通用户，仅可读示例接口 |

> 生产环境首次部署后**必须立即修改**默认管理员密码，并按需禁用/删除演示账号（见 [DEPLOY.md](DEPLOY.md#7-默认账号)）。

## 核心概念

权限模型采用四层结构，全部可在管理后台维护：

```text
接入应用 ClientApp  ── 拥有 ──>  权限 Permission
                                  │ 分配
用户 UserAccount <── 绑定 ── 角色 Role
```

- **接入应用（ClientApp）**：需要接入认证的业务系统入口（如订单系统、支付系统）。
- **权限（Permission）**：应用下的操作点（如 `ORDER_READ` 查看订单），必须归属到具体应用。
- **角色（Role）**：一组权限的集合（如 `OPERATOR` 运营角色）。
- **用户（UserAccount）**：绑定若干角色，最终可访问的应用 = 所拥有角色覆盖的应用并集。

维护顺序建议：先登记应用 → 在应用下建权限 → 创建角色并勾选权限 → 创建账号并分配角色。

## API 概览

完整接口见 [API.md](API.md)。

### 认证（`/api/auth`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/auth/discovery` | 授权中心发现信息（issuer / JWKS / userinfo） |
| GET | `/api/auth/jwks` | JWKS 公钥 |
| GET | `/api/auth/captcha` | 获取图形验证码 |
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录（账号 + 密码 + 验证码） |
| POST | `/api/auth/refresh` | 刷新 Token |
| POST | `/api/auth/logout` | 退出登录（黑名单 / 撤销） |
| GET | `/api/auth/userinfo` | 当前登录用户信息 |
| POST | `/api/auth/password/change` | 修改自己的密码 |
| POST | `/api/auth/password/reset/{userId}` | 管理员重置用户密码 |

### SSO 一键登录（`/api/sso`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/sso/tickets` | 为业务应用创建一次性 ticket |
| POST | `/api/sso/token` | 凭 ticket 换取用户 Token |

### 管理接口（均需管理员权限）

| 资源 | 路径 | 说明 |
| --- | --- | --- |
| 用户 | `/api/users` | 列表（分页搜索）/ 创建 |
| 用户 | `/api/users/{id}` | 详情 / 更新 / 删除 |
| 用户 | `/api/users/{id}/roles` | 给用户分配角色 |
| 角色 | `/api/roles` | 列表 / 创建 |
| 角色 | `/api/roles/{id}` | 详情 / 更新 / 删除 |
| 角色 | `/api/roles/{id}/permissions` | 给角色分配权限 |
| 接入应用 | `/api/client-apps` | 列表 / 创建 |
| 接入应用 | `/api/client-apps/{id}` | 详情 / 更新 / 删除 |
| 权限 | `/api/permissions` | 列表 / 创建 |
| 权限 | `/api/permissions/{id}` | 详情 / 更新 / 删除 |
| 审计日志 | `/api/audit-logs` | 查询审计日志 |

### 示例接口

- `GET/POST /api/articles`：演示业务接口（登录 / 权限控制）
- `GET /api/admin/dashboard`：演示管理员接口
- `GET /api/demo/permissions/*`：演示各权限控制规则的差异

## 业务应用接入

其他业务系统通过二方包 `authz-client-spring-boot-starter` 接入（已发布到 JitPack），接入后无需自己处理登录，只需校验 Citadel 签发的 JWT 并按权限控制接口。

**1. 引入依赖**（在 `pom.xml` 配置 JitPack 仓库与坐标）：

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.zhangxuanchen</groupId>
    <artifactId>citadel</artifactId>
    <version>v1.0.1</version>
</dependency>
```

**2. 配置 JWT 校验参数：**

```yaml
authz:
  client:
    issuer: citadel                 # 与授权中心 security.jwt.issuer 保持一致；授权中心未改则用默认值即可
    jwks-uri: https://你的域名/api/auth/jwks   # 方式一：从授权中心 JWKS 自动拉取公钥（推荐，支持公钥轮换）
    # public-key: ${AUTHZ_JWT_PUBLIC_KEY}     # 方式二：直接配置公钥 PEM（内网无法访问 JWKS 时）
    permit-paths:                              # 无需登录即可访问的路径
      - /actuator/health
      - /api/public/**
```

> `public-key` 与 `jwks-uri` **必须配置其一**，两个都未配置时业务应用启动会失败。`issuer` 默认 `citadel`，与授权中心默认配置一致，一般无需修改。

**3. 在业务接口上使用权限：**

```java
@PreAuthorize("hasAuthority('ORDER_READ')")
@GetMapping("/orders")
public List<Order> listOrders() { ... }
```

**starter 自动完成的事**：开启方法级安全（`@EnableMethodSecurity`）、注册无状态 JWT 过滤器、按 `permit-paths` 放行白名单、其余接口全部要求认证。若业务应用已自定义了 `SecurityFilterChain`，starter 的过滤器链不会自动生效，需自行集成 `AuthzJwtAuthenticationFilter`。

完整接入说明、starter 内部机制与联调验证见 [USAGE.md](USAGE.md#8-业务后端如何接入)。

## 生产部署

生产环境使用 MySQL + Nginx + 应用的容器化架构，推荐两种方式：

### 方式一：Docker Compose 手动启动

```bash
# 准备线上参数
cp deploy/online.env.example deploy/online.env
vim deploy/online.env

# 启动（需先提供 JWT 私钥/公钥、MySQL 密码等环境变量）
docker compose up -d --build
```

### 方式二：一键上线脚本（推荐）

```bash
./deploy.sh                 # 完整上线：环境检查、配置生成、测试、备份、构建、验收
./deploy.sh --skip-tests    # 跳过上线前测试
./deploy.sh --skip-backup   # 跳过数据库备份
./deploy.sh --skip-build    # 不重新构建，只重启容器
./deploy.sh --check         # 仅检查线上服务状态
```

脚本会自动完成：Docker 检查与安装、`.env` 生成、**RSA 密钥自动生成**、数据库初始化/迁移、容器启动和上线验收，并为已有数据自动备份。

> 详细步骤（ECS 准备、域名 HTTPS、运维命令、回滚、上线检查清单）见 [DEPLOY.md](DEPLOY.md)。

## 配置项速查

### 本地配置（`application.yml`）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8080` | 服务端口 |
| `security.jwt.issuer` | `citadel` | JWT 签发方标识 |
| `security.jwt.key-id` | `authz-demo-key-1` | 签名密钥 ID |
| `security.jwt.expiration` | `2h` | accessToken 有效期 |
| `security.jwt.refresh-expiration` | `7d` | refreshToken 有效期 |
| `authz.captcha.return-code` | `false` | 验证码接口是否明文返回（生产必须为 false） |
| `authz.security.rate-limit.enabled` | `true` | 是否启用接口限流 |
| `authz.security.rate-limit.max-requests` | `120` | 单 IP 在窗口内最大请求数 |
| `authz.security.rate-limit.window` | `1m` | 限流统计窗口 |
| `authz.security.login-protection.enabled` | `true` | 是否启用登录防爆破 |
| `authz.security.login-protection.max-failures` | `5` | 连续失败锁定阈值 |
| `authz.security.login-protection.lock-duration` | `15m` | 锁定持续时间 |

> 本地配置中的 RSA 密钥**仅供开发演示**，严禁用于生产。

### 生产环境变量（由 `deploy.sh` / `docker-compose.yml` 注入）

| 变量 | 说明 |
| --- | --- |
| `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD` / `MYSQL_ROOT_PASSWORD` | MySQL 库名、账号、密码（密码由脚本生成） |
| `JWT_ISSUER` | 生产 JWT 签发方，默认 `citadel` |
| `JWT_KEY_ID` | 生产签名密钥 ID |
| `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` | 生产 RSA 密钥（脚本自动生成，**必须注入**） |
| `JWT_EXPIRATION` / `JWT_REFRESH_EXPIRATION` | Token 有效期 |
| `AUTHZ_RATE_LIMIT_*` | 限流开关与参数 |
| `AUTHZ_LOGIN_*` | 登录防爆破参数 |
| `HTTP_PORT` | 对外 HTTP 端口，默认 `80` |
| `PUBLIC_BASE_URL` | 公网访问地址（用于上线验收与后台地址输出） |
| `TZ` | 时区，默认 `UTC` |

## 安全说明

- **JWT 签名**：使用 RS256 非对称签名，私钥仅存在于授权中心；业务应用只持有公钥，无法伪造 Token。
- **生产密钥**：`deploy.sh` 每次部署自动生成新的 RSA 密钥并写入 `.env`（权限 `600`），不落仓库。
- **验证码**：生产环境强制 `authz.captcha.return-code=false`，验证码接口不返回明文（部署脚本会校验并拒绝上线）。
- **默认凭据**：`admin/password`、`user/password` 仅用于演示，上线后必须改密或删除。
- **越权保护**：所有管理接口基于 `USER_MANAGE` 等权限做鉴权，普通用户无法越权。

## 相关文档

| 文档 | 内容 |
| --- | --- |
| [API.md](API.md) | 全部接口的请求 / 响应示例与联调流程 |
| [USAGE.md](USAGE.md) | 后台维护操作、登录 / SSO、业务应用接入、配置速查 |
| [DEPLOY.md](DEPLOY.md) | 阿里云 ECS 部署、运维命令、回滚与上线检查清单 |
