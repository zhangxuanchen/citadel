# Citadel 使用文档

## 目录

- [1. 平台定位](#1-平台定位)
- [2. 后台管理入口](#2-后台管理入口)
- [3. 接入应用维护](#3-接入应用维护)
- [4. 权限维护](#4-权限维护)
- [5. 角色授权](#5-角色授权)
- [6. 用户授权](#6-用户授权)
- [7. 用户登录](#7-用户登录)
- [7.1 一键登录 SSO](#71-一键登录-sso)
- [7.2 限流和防爆破登录](#72-限流和防爆破登录)
- [8. 业务后端如何接入](#8-业务后端如何接入)
- [9. 获取当前用户信息](#9-获取当前用户信息)
- [10. 授权中心发现接口](#10-授权中心发现接口)
- [11. Token 刷新和退出](#11-token-刷新和退出)
- [12. 部署使用](#12-部署使用)
- [13. 生产注意事项](#13-生产注意事项)
- [14. 常见字段和配置项速查](#14-常见字段和配置项速查)

## 1. 平台定位

本项目作为统一认证授权中心使用，负责：

- 用户登录、注册、退出
- JWT Token 签发和刷新
- 用户、角色、权限维护
- 接入应用维护
- 给角色分配权限
- 给用户分配角色
- 接口限流和防爆破登录保护
- 记录审计日志

其他业务应用不再单独维护登录、用户、角色和权限，只需要使用本平台签发的 JWT，并根据 JWT 中的权限控制自己的接口。

整体流程：

```text
用户 -> Citadel 登录 -> 获取 JWT -> 携带 JWT 访问业务应用 -> 业务应用校验 JWT 和权限
```

## 2. 后台管理入口

启动服务后访问：

```text
http://localhost:8080/admin/index.html
```

默认管理员账号：

```text
admin / password
```

生产环境部署后访问：

```text
http://服务器IP/admin/index.html
```

## 3. 接入应用维护

如果一个业务系统需要使用本授权中心，需要先在后台登记为“接入应用”。

例如订单系统：

```text
应用编码：ORDER
应用名称：订单系统
描述：订单业务应用
状态：启用
```

接口方式：

```bash
curl -X POST http://localhost:8080/api/client-apps \
  -H "Authorization: Bearer <adminAccessToken>" \
  -H "Content-Type: application/json" \
  -d '{"code":"ORDER","name":"订单系统","description":"订单业务应用","enabled":true}'
```

参数说明：

| 参数 | 示例 | 目的 |
| --- | --- | --- |
| `Authorization` | `Bearer <adminAccessToken>` | 证明当前调用者是管理员，只有拥有 `USER_MANAGE` 权限的用户才能维护接入应用。 |
| `code` | `ORDER` | 接入应用唯一编码，后续权限通过 `appCode` 归属到这个应用。 |
| `name` | `订单系统` | 应用展示名称，方便后台管理页面识别。 |
| `description` | `订单业务应用` | 应用说明，可选，用来描述这个系统的用途。 |
| `enabled` | `true` | 是否启用该应用；停用后不建议继续给它新增权限。 |

## 4. 权限维护

权限建议按业务应用拆分，例如：

```text
ORDER_READ      订单查询
ORDER_WRITE     订单写入
PRODUCT_READ    商品查询
PRODUCT_WRITE   商品写入
PAYMENT_REFUND  支付退款
```

创建权限时指定所属应用：

```bash
curl -X POST http://localhost:8080/api/permissions \
  -H "Authorization: Bearer <adminAccessToken>" \
  -H "Content-Type: application/json" \
  -d '{"code":"ORDER_READ","name":"订单查询","appCode":"ORDER"}'
```

如果不传 `appCode`，默认归属到内置应用 `AUTHZ`。

参数说明：

| 参数 | 示例 | 目的 |
| --- | --- | --- |
| `code` | `ORDER_READ` | 权限唯一编码，业务后端接口里的 `@PreAuthorize` 就检查这个值。 |
| `name` | `订单查询` | 权限展示名称，方便管理员知道这个权限控制什么功能。 |
| `appCode` | `ORDER` | 权限所属应用编码，用来区分这个权限属于哪个业务系统。 |

## 5. 角色授权

在后台“角色授权”区域选择角色，然后勾选权限并保存。

例如给 `ADMIN` 角色分配：

```text
ORDER_READ
ORDER_WRITE
PRODUCT_READ
PRODUCT_WRITE
```

接口方式：

```bash
curl -X PUT http://localhost:8080/api/roles/1/permissions \
  -H "Authorization: Bearer <adminAccessToken>" \
  -H "Content-Type: application/json" \
  -d '{"permissionIds":[1,2,3]}'
```

注意：该接口会用传入的权限集合覆盖角色原有权限。

参数说明：

| 参数 | 示例 | 目的 |
| --- | --- | --- |
| `roles/1` | `1` | 要授权的角色 ID。 |
| `permissionIds` | `[1,2,3]` | 要分配给该角色的权限 ID 集合。 |

关系说明：

```text
用户 -> 拥有角色 -> 角色拥有权限 -> 用户最终拥有这些权限
```

## 6. 用户授权

用户通过角色获得权限。

例如给用户分配 `ADMIN` 角色：

```bash
curl -X PUT http://localhost:8080/api/users/2/roles \
  -H "Authorization: Bearer <adminAccessToken>" \
  -H "Content-Type: application/json" \
  -d '{"roleIds":[1]}'
```

用户重新登录后，JWT 中会带上新的权限。

参数说明：

| 参数 | 示例 | 目的 |
| --- | --- | --- |
| `users/2` | `2` | 要授权的用户 ID。 |
| `roleIds` | `[1]` | 要分配给用户的角色 ID 集合。 |

注意：用户权限变更后，旧 JWT 不会自动变化，用户需要重新登录或刷新 Token 后才能拿到最新权限。

## 7. 用户登录

登录前先获取验证码：

```bash
curl http://localhost:8080/api/auth/captcha
```

返回示例：

```json
{
  "captchaId": "验证码ID",
  "image": "data:image/jpeg;base64,..."
}
```

验证码返回字段说明：

| 字段 | 目的 |
| --- | --- |
| `captchaId` | 验证码唯一 ID，登录时提交给后端，用来找到本次验证码。 |
| `image` | base64 图片，前端直接放到 `<img>` 上展示给用户。 |

登录：

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password","captchaId":"<captchaId>","captchaCode":"ABCD"}'
```

登录请求参数说明：

| 参数 | 示例 | 目的 |
| --- | --- | --- |
| `username` | `admin` | 登录账号。 |
| `password` | `password` | 登录密码。 |
| `captchaId` | `<captchaId>` | 获取验证码接口返回的 ID，用来匹配验证码。 |
| `captchaCode` | `ABCD` | 用户看到图片后输入的验证码文本。 |

返回示例：

```json
{
  "tokenType": "Bearer",
  "accessToken": "<accessToken>",
  "refreshToken": "<refreshToken>",
  "expiresIn": 7200,
  "issuer": "citadel",
  "username": "admin",
  "authorities": ["ROLE_ADMIN", "USER_MANAGE", "ORDER_READ"]
}
```

登录响应字段说明：

| 字段 | 目的 |
| --- | --- |
| `tokenType` | Token 类型，目前固定为 `Bearer`，请求时需要拼到 `Authorization` 请求头里。 |
| `accessToken` | 访问令牌，访问业务接口和管理接口时使用。 |
| `refreshToken` | 刷新令牌，access token 过期后用它换新的 token。 |
| `expiresIn` | access token 多少秒后过期。 |
| `issuer` | Token 签发方，业务后端校验 JWT 时要检查这个值。 |
| `username` | 当前登录用户名。 |
| `authorities` | 当前用户拥有的角色和权限，业务接口权限判断主要依赖这里。 |

### 7.1 一键登录 SSO

一键登录用于“其他业务应用接入授权中心”的场景。用户已经登录 Citadel 后，业务应用可以让用户点击“一键登录”，由授权中心生成一个短期一次性 `ticket`，业务应用再用这个 `ticket` 换取用户 Token。

推荐流程：

```text
1. 用户已经登录 Citadel，浏览器或前端持有 accessToken
2. 前端调用 POST /api/sso/tickets 创建一次性 ticket
3. 授权中心返回 redirectUrl
4. 前端跳转到 redirectUrl，例如 http://业务系统/sso/callback?ticket=xxx&state=yyy
5. 业务系统后端调用 POST /api/sso/token 兑换 ticket
6. 兑换成功后，业务系统拿到用户信息和 Token，完成本系统登录态
```

创建 SSO Ticket：

```bash
curl -X POST http://localhost:8080/api/sso/tickets \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"appCode":"ORDER","redirectUri":"http://order.example.com/sso/callback","state":"state-123"}'
```

请求参数说明：

| 参数 | 示例 | 目的 |
| --- | --- | --- |
| `Authorization` | `Bearer <accessToken>` | 证明当前用户已经在授权中心登录。 |
| `appCode` | `ORDER` | 要登录到哪个接入应用。 |
| `redirectUri` | `http://order.example.com/sso/callback` | 业务应用接收 SSO 回调的地址。 |
| `state` | `state-123` | 防止登录流程串单的随机值，业务应用生成并在回调时校验。 |

返回示例：

```json
{
  "ticket": "SSO-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "appCode": "ORDER",
  "redirectUri": "http://order.example.com/sso/callback",
  "state": "state-123",
  "expiresAt": "2026-07-11T12:00:00Z",
  "redirectUrl": "http://order.example.com/sso/callback?ticket=SSO-xxx&state=state-123"
}
```

响应字段说明：

| 字段 | 目的 |
| --- | --- |
| `ticket` | 一次性登录票据，有效期 2 分钟，只能兑换一次。 |
| `appCode` | 本次要登录的业务应用编码。 |
| `redirectUri` | 业务应用回调地址。 |
| `state` | 原样返回，业务应用用它确认回调是自己发起的。 |
| `expiresAt` | ticket 过期时间。 |
| `redirectUrl` | 前端可以直接跳转的完整地址，里面已经拼好 `ticket` 和 `state`。 |

业务应用后端兑换 Ticket：

```bash
curl -X POST http://localhost:8080/api/sso/token \
  -H "Content-Type: application/json" \
  -d '{"ticket":"SSO-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"}'
```

兑换参数说明：

| 参数 | 目的 |
| --- | --- |
| `ticket` | 授权中心签发的一次性登录票据。 |

兑换成功返回体和普通登录一致，包含 `accessToken`、`refreshToken`、`issuer`、`authorities`。

注意事项：

- `POST /api/sso/tickets` 必须登录后调用。
- `POST /api/sso/token` 不要求登录，因为业务应用拿到 ticket 后需要用它换 Token。
- ticket 只能用一次，重复兑换会返回 `401`。
- 当前版本先校验 `redirectUri` 必须是 `http` 或 `https` 地址；生产环境建议后续在接入应用里配置允许的回调地址白名单。

### 7.2 限流和防爆破登录

平台已经内置两类登录平台保护能力：

| 能力 | 默认规则 | 目的 |
| --- | --- | --- |
| 接口限流 | 按客户端 IP 限制 `/api/**`，默认每分钟 120 次请求。 | 避免同一个 IP 高频刷接口，保护登录平台稳定性。 |
| 防爆破登录 | 按 `用户名 + 客户端 IP` 记录失败次数，默认连续失败 5 次后锁定 15 分钟。 | 避免攻击者反复猜密码；验证码错误和密码错误都会计入失败次数。 |

命中限流时返回：

```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Too many requests, please try again later"
}
```

命中登录锁定时返回：

```json
{
  "status": 423,
  "error": "Locked",
  "message": "Login temporarily locked, please try again later"
}
```

生产环境可以通过 `.env` 调整：

```env
AUTHZ_RATE_LIMIT_ENABLED=true
AUTHZ_RATE_LIMIT_MAX_REQUESTS=120
AUTHZ_RATE_LIMIT_WINDOW=1m
AUTHZ_LOGIN_PROTECTION_ENABLED=true
AUTHZ_LOGIN_MAX_FAILURES=5
AUTHZ_LOGIN_LOCK_DURATION=15m
```

配置项说明：

| 配置项 | 示例 | 目的 |
| --- | --- | --- |
| `AUTHZ_RATE_LIMIT_ENABLED` | `true` | 是否启用接口限流。 |
| `AUTHZ_RATE_LIMIT_MAX_REQUESTS` | `120` | 单个 IP 在一个窗口内最多允许请求多少次 `/api/**`。 |
| `AUTHZ_RATE_LIMIT_WINDOW` | `1m` | 限流统计窗口，支持 `s`、`m`、`h` 这类 Spring Duration 写法。 |
| `AUTHZ_LOGIN_PROTECTION_ENABLED` | `true` | 是否启用防爆破登录。 |
| `AUTHZ_LOGIN_MAX_FAILURES` | `5` | 同一用户名和 IP 连续失败多少次后锁定。 |
| `AUTHZ_LOGIN_LOCK_DURATION` | `15m` | 锁定持续时间。 |

当前实现是本地内存版，适合单台 ECS 一键部署；如果后续做多台实例负载均衡，建议把限流和登录失败计数迁移到 Redis。

## 8. 业务后端如何接入

其他业务后端接入后，不需要自己做登录，只负责校验 Citadel 签发的 JWT，并根据权限控制自己的接口。

请求进入业务应用时，前端或调用方需要携带：

```http
Authorization: Bearer <accessToken>
```

业务应用后端需要做两件事：

- 校验 JWT 是否有效
- 根据 JWT 中的权限控制接口访问

当前版本使用 RS256 非对称签名：Citadel 使用私钥签发 JWT，业务应用只配置公钥或 JWKS 地址验签，不需要也不应该持有私钥。

### 8.1 本地安装 starter 包

当前已经在本项目下新增了本地接入包：

```text
authz-client-spring-boot-starter
```

如果后续改了 starter 代码，需要重新安装到本机 Maven 仓库：

```bash
cd authz-client-spring-boot-starter
mvn clean install
```

安装成功后，jar 会进入本机 Maven 仓库：

```text
~/.m2/repository/cn/com/smart/ai/claw/authz-client-spring-boot-starter/1.0.0/
```

### 8.2 业务应用引入依赖

业务应用如果也是 Spring Boot，可以加入：

```xml
<dependency>
    <groupId>cn.com.smart.ai.claw</groupId>
    <artifactId>authz-client-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

这个 starter 已经包含：

| 能力 | 目的 |
| --- | --- |
| JWT 解析过滤器 | 从 `Authorization` 请求头读取并校验 JWT。 |
| Spring Security 默认配置 | 默认所有接口都需要登录，除非配置了放行路径。 |
| `@PreAuthorize` 支持 | 业务接口可以直接使用权限注解。 |
| authorities 解析 | 自动读取 JWT 中的 `authorities` 字段并转换成 Spring Security 权限。 |

### 8.3 配置 JWT 参数

业务应用 `application.yml`：

```yaml
authz:
  client:
    issuer: citadel
    jwks-uri: https://auth.example.com/api/auth/jwks
    permit-paths:
      - /actuator/health
      - /api/public/**
```

如果业务应用不方便访问授权中心 JWKS，也可以直接配置授权中心公钥：

```yaml
authz:
  client:
    issuer: citadel
    public-key: ${AUTHZ_JWT_PUBLIC_KEY}
```

配置项说明：

| 配置项 | 示例 | 目的 |
| --- | --- | --- |
| `authz.client.enabled` | `true` | 是否启用 starter 自动配置，默认启用。 |
| `authz.client.issuer` | `citadel` | JWT 签发方标识，业务应用校验 token 时用它确认 token 确实来自本授权中心。 |
| `authz.client.jwks-uri` | `https://auth.example.com/api/auth/jwks` | 授权中心 JWKS 地址，业务应用自动拉取公钥验签。 |
| `authz.client.public-key` | `${AUTHZ_JWT_PUBLIC_KEY}` | 授权中心 RSA 公钥，适合不能访问 JWKS 的内网场景。 |
| `authz.client.permit-paths` | `/actuator/health` | 不需要登录即可访问的路径，比如健康检查、公开接口。 |
| `authz.client.authorities-claim` | `authorities` | JWT 中存放权限列表的字段名，默认就是本平台使用的 `authorities`。 |
| `authz.client.bearer-prefix` | `Bearer ` | `Authorization` 请求头中的 Token 前缀，通常不需要修改。 |
| `AUTHZ_JWT_PUBLIC_KEY` | 环境变量 | 业务应用用于验签的 RSA 公钥，只能验签，不能签发 token。 |

### 8.4 starter 内部做了什么

业务应用引入依赖并配置参数后，starter 会自动完成下面这些事情：

| 组件/参数 | 目的 |
| --- | --- |
| `AuthzClientAutoConfiguration` | Spring Boot 自动配置入口，负责注册 JWT 过滤器和默认安全配置。 |
| `AuthzClientProperties` | 读取 `authz.client.*` 配置。 |
| `AuthzJwtAuthenticationFilter` | 解析 `Authorization: Bearer <accessToken>`，校验签名、过期时间和 issuer。 |
| `SecurityContextHolder` | 保存当前登录用户和权限，后续 `@PreAuthorize` 从这里判断权限。 |
| `SimpleGrantedAuthority` | Spring Security 的权限对象，`ORDER_READ`、`USER_MANAGE` 会转换成它。 |

如果业务应用自己已经定义了 `SecurityFilterChain`，starter 不会覆盖你的安全配置。此时你可以自行注入 `AuthzJwtAuthenticationFilter`，再加到自己的安全链路里。

### 8.5 在业务接口上使用权限

订单系统接口示例：

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public List<String> listOrders() {
        return List.of("O-1001", "O-1002");
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORDER_WRITE')")
    public String createOrder() {
        return "created";
    }
}
```

如果用户没有对应权限，会返回 `403 Forbidden`。

权限注解说明：

| 写法 | 目的 |
| --- | --- |
| `@PreAuthorize("hasAuthority('ORDER_READ')")` | 要求用户拥有 `ORDER_READ` 权限。 |
| `@PreAuthorize("hasAuthority('ORDER_WRITE')")` | 要求用户拥有 `ORDER_WRITE` 权限。 |
| `@PreAuthorize("hasRole('ADMIN')")` | 要求用户拥有管理员角色；实际检查的是 `ROLE_ADMIN`。 |
| `@PreAuthorize("isAuthenticated()")` | 只要求登录，不要求具体业务权限。 |

### 8.6 后端接入验证流程

1. 在 Citadel 后台新增接入应用 `ORDER`。
2. 在 Citadel 后台新增权限 `ORDER_READ`、`ORDER_WRITE`。
3. 给角色分配这些权限。
4. 给用户分配角色。
5. 用户重新登录 Citadel 获取新的 `accessToken`。
6. 调用业务应用接口：

```bash
curl http://localhost:9001/api/orders \
  -H "Authorization: Bearer <accessToken>"
```

有权限返回成功，没有权限返回 `403`。

## 9. 获取当前用户信息

其他应用可以调用：

```bash
curl http://localhost:8080/api/auth/userinfo \
  -H "Authorization: Bearer <accessToken>"
```

返回示例：

```json
{
  "id": 1,
  "username": "admin",
  "displayName": "System Admin",
  "issuer": "citadel",
  "roles": ["ADMIN"],
  "authorities": ["ROLE_ADMIN", "USER_MANAGE", "ORDER_READ"]
}
```

字段说明：

| 字段 | 目的 |
| --- | --- |
| `id` | 用户 ID。 |
| `username` | 用户登录账号。 |
| `displayName` | 用户展示名称。 |
| `issuer` | 当前用户信息来自哪个授权中心。 |
| `roles` | 用户拥有的角色编码。 |
| `authorities` | 用户最终拥有的角色和权限集合。 |

## 10. 授权中心发现接口

其他应用可以调用：

```bash
curl http://localhost:8080/api/auth/discovery
```

返回授权中心基础信息：

```json
{
  "issuer": "citadel",
  "tokenType": "Bearer",
  "authorizationHeader": "Authorization: Bearer <accessToken>",
  "signingAlgorithm": "RS256",
  "jwksEndpoint": "/api/auth/jwks",
  "userinfoEndpoint": "/api/auth/userinfo"
}
```

字段说明：

| 字段 | 目的 |
| --- | --- |
| `issuer` | 授权中心标识，业务应用校验 JWT 时需要一致。 |
| `tokenType` | 当前 token 类型。 |
| `authorizationHeader` | 业务请求应携带的请求头格式。 |
| `signingAlgorithm` | JWT 签名算法，当前为 `RS256`。 |
| `jwksEndpoint` | 授权中心公钥集合地址，业务应用可以通过它获取验签公钥。 |
| `userinfoEndpoint` | 当前用户信息接口地址。 |

## 11. Token 刷新和退出

刷新 Token：

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
```

刷新参数说明：

| 参数 | 目的 |
| --- | --- |
| `refreshToken` | 用于换取新的 `accessToken` 和新的 `refreshToken`。 |

退出登录：

```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
```

退出后当前 access token 会进入黑名单，refresh token 也会失效。

退出参数说明：

| 参数 | 目的 |
| --- | --- |
| `Authorization` | 提交当前 access token，后端会把它加入黑名单。 |
| `refreshToken` | 提交刷新令牌，后端会将它置为失效。 |

## 12. 部署使用

阿里云 ECS 一键部署：

```bash
chmod +x deploy.sh
./deploy.sh
```

部署完成后访问：

```text
http://服务器IP/admin/index.html
```

详细部署、更新、备份和回滚说明见：

```text
DEPLOY.md
```

## 13. 生产注意事项

- 生产环境必须使用独立生成的 RSA 私钥/公钥，私钥只允许授权中心持有
- 生产环境使用 MySQL，不要使用 H2
- 不要执行 `docker compose down -v`，否则会删除数据库 volume
- 管理后台账号上线后应立即修改默认密码
- 权限编码需要统一规划，避免不同应用重复或混乱
- 根据业务访问量调整限流和登录锁定阈值，避免误伤正常用户
- 多应用接入时使用 RSA 私钥签发、公钥或 JWKS 验签，不要把私钥分发给业务应用

## 14. 常见字段和配置项速查

| 名称 | 属于哪里 | 目的 |
| --- | --- | --- |
| `accessToken` | 登录响应 | 短期访问令牌，请求业务接口时携带。 |
| `refreshToken` | 登录响应 | 长期刷新令牌，用来换新的 access token。 |
| `Authorization` | HTTP 请求头 | 携带 access token，格式是 `Bearer <accessToken>`。 |
| `Bearer` | Token 类型 | 表示请求头里携带的是 Bearer Token。 |
| `issuer` | JWT 字段/配置项 | 标识 token 由哪个授权中心签发。 |
| `JWT_PRIVATE_KEY` | 授权中心生产配置 | RSA 私钥，只能配置在 Citadel，用于签发 token。 |
| `JWT_PUBLIC_KEY` | 授权中心生产配置 | RSA 公钥，用于 Citadel 自身验签和对外暴露 JWKS。 |
| `authz.client.jwks-uri` | 业务应用配置 | 业务应用通过 JWKS 获取公钥验签。 |
| `authz.client.public-key` | 业务应用配置 | 业务应用直接配置 RSA 公钥验签。 |
| `authorities` | JWT 字段 | 用户拥有的角色和权限集合。 |
| `ROLE_ADMIN` | 角色权限 | Spring Security 的角色格式，`hasRole('ADMIN')` 会检查它。 |
| `USER_MANAGE` | 业务权限 | 管理后台接口权限，拥有它才能管理用户、角色、权限、接入应用。 |
| `ORDER_READ` | 业务权限 | 示例订单查询权限，业务应用用 `hasAuthority('ORDER_READ')` 检查。 |
| `appCode` | 权限参数 | 标识权限属于哪个接入应用。 |
| `roleIds` | 用户授权参数 | 给用户分配哪些角色。 |
| `permissionIds` | 角色授权参数 | 给角色分配哪些权限。 |
| `captchaId` | 登录参数 | 标识本次验证码。 |
| `captchaCode` | 登录参数 | 用户输入的验证码内容。 |
| `AUTHZ_RATE_LIMIT_MAX_REQUESTS` | 生产配置 | 单个 IP 在限流窗口内最多允许请求多少次 `/api/**`。 |
| `AUTHZ_RATE_LIMIT_WINDOW` | 生产配置 | 接口限流统计窗口。 |
| `AUTHZ_LOGIN_MAX_FAILURES` | 生产配置 | 同一用户名和 IP 连续登录失败多少次后锁定。 |
| `AUTHZ_LOGIN_LOCK_DURATION` | 生产配置 | 登录防爆破锁定时间。 |
| `ticket` | SSO 参数 | 一键登录使用的一次性票据，业务应用用它换 Token。 |
| `redirectUri` | SSO 参数 | 业务应用接收一键登录回调的地址。 |
| `state` | SSO 参数 | 业务应用生成的随机值，用来防止回调串单。 |

最重要的理解方式：

```text
用户登录后拿到 accessToken
accessToken 里有 username、issuer、authorities
业务后端校验 issuer 和签名
业务接口使用 authorities 判断是否允许访问
```
