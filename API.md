# 统一认证授权中心 Citadel 接口文档

## 基础信息

- 服务地址：`http://localhost:8080`
- 默认 Content-Type：`application/json`
- 鉴权方式：除注册、获取验证码、登录、刷新 Token、SSO Ticket 兑换、H2 控制台外，接口都需要携带访问令牌。
- 平台定位：统一维护用户、角色、权限和接入应用；其他业务应用复用本平台登录签发的 JWT，并按 JWT 中的权限做接口控制。

```http
Authorization: Bearer <accessToken>
```

启动项目：

```bash
mvn spring-boot:run
```

阿里云 ECS 一键部署：

```bash
chmod +x deploy.sh
./deploy.sh
```

部署脚本会自动生成 `.env`，并通过 Docker Compose 启动 MySQL、Spring Boot 和 Nginx。海外 ECS 安全组需要放行 `80` 端口，部署完成后访问：

```text
http://服务器IP/admin/index.html
```

完整部署、更新、备份和排查步骤见根目录 `DEPLOY.md`。

如果服务器 `80` 端口已被占用，可以修改 `.env` 中的 `HTTP_PORT` 后重新执行：

```bash
docker compose up -d
```

内置初始化账号：

| 用户名 | 密码 | 说明 |
| --- | --- | --- |
| `admin` | `password` | 管理员，拥有 `USER_MANAGE`、`ARTICLE_READ`、`ARTICLE_WRITE` |
| `user` | `password` | 普通用户，拥有 `ARTICLE_READ` |

## 通用响应

登录、注册、刷新 Token 成功响应：

```json
{
  "tokenType": "Bearer",
  "accessToken": "<accessToken>",
  "refreshToken": "<refreshToken>",
  "expiresIn": 7200,
  "issuer": "citadel",
  "username": "admin",
  "authorities": ["ROLE_ADMIN", "ARTICLE_READ", "ARTICLE_WRITE", "USER_MANAGE"]
}
```

错误响应示例：

```json
{
  "timestamp": "2026-07-11T03:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid username or password"
}
```

常见状态码：

| 状态码 | 说明 |
| --- | --- |
| `200` | 请求成功 |
| `400` | 请求参数错误 |
| `401` | 未登录、Token 无效或刷新令牌失效 |
| `403` | 已登录但没有权限 |
| `423` | 登录失败次数过多，当前账号和 IP 组合被临时锁定 |
| `429` | 请求过于频繁，被接口限流拦截；响应头会返回 `Retry-After` |
| `404` | 资源不存在 |
| `409` | 用户名、角色编码或权限编码重复 |

## 安全保护

平台内置两类保护能力：

| 能力 | 规则 | 命中后的响应 |
| --- | --- | --- |
| 接口限流 | 默认按客户端 IP 对 `/api/**` 做固定窗口限流，开发和生产默认每分钟 120 次。 | `429 Too Many Requests`，响应头包含 `Retry-After`。 |
| 防爆破登录 | 默认按 `username + clientIp` 记录登录失败次数，连续失败 5 次后锁定 15 分钟。验证码错误和密码错误都会计入失败次数。 | `423 Locked`，提示稍后再试。 |

生产环境可通过 `.env` 调整：

```env
AUTHZ_RATE_LIMIT_MAX_REQUESTS=120
AUTHZ_RATE_LIMIT_WINDOW=1m
AUTHZ_LOGIN_MAX_FAILURES=5
AUTHZ_LOGIN_LOCK_DURATION=15m
```

## 认证接口

### 授权中心发现信息

`GET /api/auth/discovery`

无需登录。其他应用可通过该接口获取当前授权中心 issuer、Token 使用方式和用户信息接口地址。

响应体：

```json
{
  "issuer": "citadel",
  "tokenType": "Bearer",
  "authorizationHeader": "Authorization: Bearer <accessToken>",
  "signingAlgorithm": "RS256",
  "jwksEndpoint": "/api/auth/jwks",
  "userinfoEndpoint": "/api/auth/userinfo",
  "managementEndpoints": ["/api/client-apps", "/api/permissions", "/api/roles", "/api/users", "/api/audit-logs"]
}
```

### 授权中心 JWKS 公钥

`GET /api/auth/jwks`

无需登录。业务应用可通过该接口获取 RSA 公钥并验证 Citadel 签发的 JWT。

响应体：

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "kid": "authz-prod-key-1",
      "alg": "RS256",
      "n": "<modulus>",
      "e": "AQAB"
    }
  ]
}
```

### 获取图形验证码

`GET /api/auth/captcha`

无需登录。登录前先调用该接口获取 `captchaId` 和验证码图片，随后在登录请求中提交用户输入的验证码。

响应体：

```json
{
  "captchaId": "e1b6c8f6-0e3f-4f31-b51b-d73f2c7c6f4a",
  "image": "data:image/jpeg;base64,/9j/4AAQSk..."
}
```

示例：

```bash
curl http://localhost:8080/api/auth/captcha
```

### 注册

`POST /api/auth/register`

无需登录。注册后默认分配 `USER` 角色，并直接返回访问令牌和刷新令牌。

请求体：

```json
{
  "username": "tom",
  "password": "password",
  "displayName": "Tom"
}
```

示例：

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"tom","password":"password","displayName":"Tom"}'
```

### 登录

`POST /api/auth/login`

无需登录。

登录接口会同时校验图形验证码和账号密码。验证码错误、密码错误都会计入防爆破失败次数；同一账号在同一 IP 下连续失败达到阈值后，会临时锁定并返回 `423 Locked`。

请求体：

```json
{
  "username": "admin",
  "password": "password",
  "captchaId": "<captchaId>",
  "captchaCode": "ABCD"
}
```

示例：

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password","captchaId":"<captchaId>","captchaCode":"ABCD"}'
```

### 刷新 Token

`POST /api/auth/refresh`

无需 access token。刷新成功后旧 refresh token 会失效，并返回新的 access token 和 refresh token。

请求体：

```json
{
  "refreshToken": "<refreshToken>"
}
```

示例：

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
```

### 退出登录

`POST /api/auth/logout`

需要登录。退出后当前 access token 会进入黑名单，传入的 refresh token 也会失效。

请求体：

```json
{
  "refreshToken": "<refreshToken>"
}
```

示例：

```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
```

响应：

```json
{
  "message": "logout success"
}
```

## SSO 一键登录接口

SSO 一键登录用于其他业务应用接入授权中心。推荐使用短期一次性 `ticket` 完成跳转登录，不建议把长期 `accessToken` 放到 URL 中。

### 创建 SSO Ticket

`POST /api/sso/tickets`

需要登录。当前用户已经登录授权中心后，调用该接口创建一个 2 分钟有效、只能使用一次的 SSO Ticket。

请求体：

```json
{
  "appCode": "ORDER",
  "redirectUri": "http://order.example.com/sso/callback",
  "state": "state-123"
}
```

参数说明：

| 参数 | 说明 |
| --- | --- |
| `appCode` | 接入应用编码，例如 `ORDER`。 |
| `redirectUri` | 业务应用接收回调的地址。 |
| `state` | 业务应用生成的随机值，授权中心原样返回，业务应用用于校验回调。 |

响应体：

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

示例：

```bash
curl -X POST http://localhost:8080/api/sso/tickets \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"appCode":"ORDER","redirectUri":"http://order.example.com/sso/callback","state":"state-123"}'
```

### 兑换 SSO Ticket

`POST /api/sso/token`

无需登录。业务应用后端收到回调中的 `ticket` 后，调用该接口兑换登录结果。兑换成功后 ticket 会立即失效，不能重复使用。

请求体：

```json
{
  "ticket": "SSO-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
}
```

响应体同普通登录成功响应。

示例：

```bash
curl -X POST http://localhost:8080/api/sso/token \
  -H "Content-Type: application/json" \
  -d '{"ticket":"SSO-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"}'
```

### 获取当前登录用户信息

`GET /api/auth/userinfo`

需要登录。其他业务应用拿到 JWT 后，可以调用该接口确认当前用户、角色和权限。

响应体：

```json
{
  "id": 1,
  "username": "admin",
  "displayName": "System Admin",
  "issuer": "citadel",
  "roles": ["ADMIN"],
  "authorities": ["ROLE_ADMIN", "ARTICLE_READ", "ARTICLE_WRITE", "USER_MANAGE"]
}
```

示例：

```bash
curl http://localhost:8080/api/auth/userinfo \
  -H "Authorization: Bearer <accessToken>"
```

### 修改当前用户密码

`POST /api/auth/password/change`

需要登录。

请求体：

```json
{
  "oldPassword": "password",
  "newPassword": "new-password"
}
```

示例：

```bash
curl -X POST http://localhost:8080/api/auth/password/change \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"oldPassword":"password","newPassword":"new-password"}'
```

响应：

```json
{
  "message": "password changed"
}
```

### 管理员重置用户密码

`POST /api/auth/password/reset/{userId}`

需要 `USER_MANAGE` 权限。

请求体：

```json
{
  "newPassword": "reset-password"
}
```

示例：

```bash
curl -X POST http://localhost:8080/api/auth/password/reset/2 \
  -H "Authorization: Bearer <adminAccessToken>" \
  -H "Content-Type: application/json" \
  -d '{"newPassword":"reset-password"}'
```

响应：

```json
{
  "message": "password reset"
}
```

## 用户接口

用户响应结构：

```json
{
  "id": 1,
  "username": "admin",
  "displayName": "System Admin",
  "enabled": true,
  "roles": ["ADMIN"],
  "permissions": ["ARTICLE_READ", "ARTICLE_WRITE", "USER_MANAGE"]
}
```

### 获取当前登录用户

`GET /api/users/me`

需要登录。

```bash
curl http://localhost:8080/api/users/me \
  -H "Authorization: Bearer <accessToken>"
```

### 管理列表分页与搜索

以下管理列表接口都支持可选分页搜索参数：

| 接口 | 搜索字段 |
| --- | --- |
| `GET /api/users` | 账号、显示名称、状态、角色编码、权限编码 |
| `GET /api/roles` | 角色编码、角色名称、已分配权限、应用编码、应用名称 |
| `GET /api/client-apps` | 应用编码、应用名称、描述、状态 |
| `GET /api/permissions` | 权限编码、权限名称、应用编码、应用名称 |

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `keyword` | 否 | 关键词，模糊匹配。 |
| `page` | 否 | 页码，从 `0` 开始，默认 `0`。 |
| `size` | 否 | 每页数量，默认 `10`，最大 `100`。 |

兼容说明：不传 `keyword/page/size` 时仍返回原来的数组结构；只要传入任一分页搜索参数，就返回分页结构。

分页响应示例：

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

### 用户列表

`GET /api/users`

需要 `USER_MANAGE` 权限。

```bash
curl http://localhost:8080/api/users \
  -H "Authorization: Bearer <adminAccessToken>"
```

分页搜索：

```bash
curl "http://localhost:8080/api/users?keyword=admin&page=0&size=10" \
  -H "Authorization: Bearer <adminAccessToken>"
```

### 用户详情

`GET /api/users/{id}`

需要 `USER_MANAGE` 权限。

```bash
curl http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer <adminAccessToken>"
```

### 创建用户

`POST /api/users`

需要 `USER_MANAGE` 权限。

请求体：

```json
{
  "username": "managed-user",
  "password": "password",
  "displayName": "Managed User",
  "enabled": true,
  "roleIds": [1]
}
```

```bash
curl -X POST http://localhost:8080/api/users \
  -H "Authorization: Bearer <adminAccessToken>" \
  -H "Content-Type: application/json" \
  -d '{"username":"managed-user","password":"password","displayName":"Managed User","enabled":true,"roleIds":[1]}'
```

### 更新用户

`PUT /api/users/{id}`

需要 `USER_MANAGE` 权限。

请求体：

```json
{
  "displayName": "Managed User Updated",
  "enabled": true
}
```

```bash
curl -X PUT http://localhost:8080/api/users/3 \
  -H "Authorization: Bearer <adminAccessToken>" \
  -H "Content-Type: application/json" \
  -d '{"displayName":"Managed User Updated","enabled":true}'
```

### 删除用户

`DELETE /api/users/{id}`

需要 `USER_MANAGE` 权限。

```bash
curl -X DELETE http://localhost:8080/api/users/3 \
  -H "Authorization: Bearer <adminAccessToken>"
```

响应：

```json
{
  "message": "user deleted"
}
```

### 给用户分配角色

`PUT /api/users/{id}/roles`

需要 `USER_MANAGE` 权限。该接口会用传入角色集合覆盖用户原有角色。

请求体：

```json
{
  "roleIds": [1, 2]
}
```

```bash
curl -X PUT http://localhost:8080/api/users/3/roles \
  -H "Authorization: Bearer <adminAccessToken>" \
  -H "Content-Type: application/json" \
  -d '{"roleIds":[1,2]}'
```

## 角色接口

所有角色接口都需要 `USER_MANAGE` 权限。

角色响应结构：

```json
{
  "id": 1,
  "code": "ADMIN",
  "name": "Administrator",
  "permissions": [
    {
      "id": 1,
      "code": "ARTICLE_READ",
      "name": "Read articles"
    }
  ]
}
```

### 角色列表

`GET /api/roles`

```bash
curl http://localhost:8080/api/roles \
  -H "Authorization: Bearer <adminAccessToken>"
```

分页搜索：

```bash
curl "http://localhost:8080/api/roles?keyword=ORDER_READ&page=0&size=10" \
  -H "Authorization: Bearer <adminAccessToken>"
```

### 角色详情

`GET /api/roles/{id}`

```bash
curl http://localhost:8080/api/roles/1 \
  -H "Authorization: Bearer <adminAccessToken>"
```

### 创建角色

`POST /api/roles`

请求体：

```json
{
  "code": "REPORTER",
  "name": "Reporter"
}
```

```bash
curl -X POST http://localhost:8080/api/roles \
  -H "Authorization: Bearer <adminAccessToken>" \
  -H "Content-Type: application/json" \
  -d '{"code":"REPORTER","name":"Reporter"}'
```

### 更新角色

`PUT /api/roles/{id}`

请求体：

```json
{
  "code": "REPORTER",
  "name": "Report Manager"
}
```

```bash
curl -X PUT http://localhost:8080/api/roles/3 \
  -H "Authorization: Bearer <adminAccessToken>" \
  -H "Content-Type: application/json" \
  -d '{"code":"REPORTER","name":"Report Manager"}'
```

### 删除角色

`DELETE /api/roles/{id}`

```bash
curl -X DELETE http://localhost:8080/api/roles/3 \
  -H "Authorization: Bearer <adminAccessToken>"
```

响应：

```json
{
  "message": "role deleted"
}
```

### 给角色分配权限

`PUT /api/roles/{id}/permissions`

该接口会用传入权限集合覆盖角色原有权限。

请求体：

```json
{
  "permissionIds": [1, 2, 3]
}
```

```bash
curl -X PUT http://localhost:8080/api/roles/3/permissions \
  -H "Authorization: Bearer <adminAccessToken>" \
  -H "Content-Type: application/json" \
  -d '{"permissionIds":[1,2,3]}'
```

## 接入应用接口

所有接入应用接口都需要 `USER_MANAGE` 权限。

接入应用代表一个会使用本授权中心的业务系统，例如订单系统、商品系统、支付系统。权限可以归属到具体接入应用，方便统一维护和隔离。

应用响应结构：

```json
{
  "id": 1,
  "code": "ORDER",
  "name": "订单系统",
  "description": "订单业务应用",
  "enabled": true
}
```

### 应用列表

`GET /api/client-apps`

```bash
curl http://localhost:8080/api/client-apps \
  -H "Authorization: Bearer <adminAccessToken>"
```

分页搜索：

```bash
curl "http://localhost:8080/api/client-apps?keyword=ORDER&page=0&size=10" \
  -H "Authorization: Bearer <adminAccessToken>"
```

### 应用详情

`GET /api/client-apps/{id}`

```bash
curl http://localhost:8080/api/client-apps/1 \
  -H "Authorization: Bearer <adminAccessToken>"
```

### 创建应用

`POST /api/client-apps`

请求体：

```json
{
  "code": "ORDER",
  "name": "订单系统",
  "description": "订单业务应用",
  "enabled": true
}
```

```bash
curl -X POST http://localhost:8080/api/client-apps \
  -H "Authorization: Bearer <adminAccessToken>" \
  -H "Content-Type: application/json" \
  -d '{"code":"ORDER","name":"订单系统","description":"订单业务应用","enabled":true}'
```

### 更新应用

`PUT /api/client-apps/{id}`

请求体同创建应用。

### 删除应用

`DELETE /api/client-apps/{id}`

应用下仍有权限时不能删除，需要先删除或迁移权限。

```bash
curl -X DELETE http://localhost:8080/api/client-apps/1 \
  -H "Authorization: Bearer <adminAccessToken>"
```

## 权限接口

所有权限接口都需要 `USER_MANAGE` 权限。

权限可以通过 `appCode` 归属到某个接入应用。不传 `appCode` 时，默认归属到内置应用 `AUTHZ`。

权限响应结构：

```json
{
  "id": 1,
  "code": "ARTICLE_READ",
  "name": "Read articles",
  "appCode": "ARTICLE",
  "appName": "文章示例应用"
}
```

### 权限列表

`GET /api/permissions`

```bash
curl http://localhost:8080/api/permissions \
  -H "Authorization: Bearer <adminAccessToken>"
```

分页搜索：

```bash
curl "http://localhost:8080/api/permissions?keyword=订单&page=0&size=10" \
  -H "Authorization: Bearer <adminAccessToken>"
```

### 权限详情

`GET /api/permissions/{id}`

```bash
curl http://localhost:8080/api/permissions/1 \
  -H "Authorization: Bearer <adminAccessToken>"
```

### 创建权限

`POST /api/permissions`

请求体：

```json
{
  "code": "REPORT_VIEW",
  "name": "View reports",
  "appCode": "AUTHZ"
}
```

```bash
curl -X POST http://localhost:8080/api/permissions \
  -H "Authorization: Bearer <adminAccessToken>" \
  -H "Content-Type: application/json" \
  -d '{"code":"REPORT_VIEW","name":"View reports","appCode":"AUTHZ"}'
```

### 更新权限

`PUT /api/permissions/{id}`

请求体：

```json
{
  "code": "REPORT_VIEW",
  "name": "View report dashboard",
  "appCode": "AUTHZ"
}
```

```bash
curl -X PUT http://localhost:8080/api/permissions/4 \
  -H "Authorization: Bearer <adminAccessToken>" \
  -H "Content-Type: application/json" \
  -d '{"code":"REPORT_VIEW","name":"View report dashboard","appCode":"AUTHZ"}'
```

### 删除权限

`DELETE /api/permissions/{id}`

```bash
curl -X DELETE http://localhost:8080/api/permissions/4 \
  -H "Authorization: Bearer <adminAccessToken>"
```

响应：

```json
{
  "message": "permission deleted"
}
```

## 示例业务接口

### 查询文章

`GET /api/articles`

需要 `ARTICLE_READ` 权限。

```bash
curl http://localhost:8080/api/articles \
  -H "Authorization: Bearer <accessToken>"
```

### 创建文章

`POST /api/articles`

需要 `ARTICLE_WRITE` 权限。

```bash
curl -X POST http://localhost:8080/api/articles \
  -H "Authorization: Bearer <accessToken>"
```

### 管理员看板

`GET /api/admin/dashboard`

需要 `ROLE_ADMIN`。

```bash
curl http://localhost:8080/api/admin/dashboard \
  -H "Authorization: Bearer <adminAccessToken>"
```

### 接口权限使用 Demo

Demo 类：`PermissionUsageDemoController`

路径前缀：`/api/demo/permissions`

| 接口 | 权限写法 | 说明 |
| --- | --- | --- |
| `GET /api/demo/permissions/public` | `permitAll` | 公开访问示例 |
| `GET /api/demo/permissions/login-required` | `@PreAuthorize("isAuthenticated()")` | 任意登录用户可访问 |
| `GET /api/demo/permissions/article-read` | `@PreAuthorize("hasAuthority('ARTICLE_READ')")` | 需要文章读取权限 |
| `GET /api/demo/permissions/article-write` | `@PreAuthorize("hasAuthority('ARTICLE_WRITE')")` | 需要文章写入权限 |
| `GET /api/demo/permissions/admin-role` | `@PreAuthorize("hasRole('ADMIN')")` | 需要管理员角色，实际检查 `ROLE_ADMIN` |
| `GET /api/demo/permissions/user-manage` | `@PreAuthorize("hasAuthority('USER_MANAGE')")` | 需要用户管理权限 |
| `GET /api/demo/permissions/read-and-manage` | `@PreAuthorize("hasAuthority('ARTICLE_READ') and hasAuthority('USER_MANAGE')")` | 组合权限示例 |

示例：

```bash
curl http://localhost:8080/api/demo/permissions/user-manage \
  -H "Authorization: Bearer <adminAccessToken>"
```

## 审计日志接口

系统会将以下操作写入 `sys_audit_log`：

- 所有带 `@OperationLog` 的接口调用，包括登录、注册、用户管理、角色管理、权限管理、文章示例接口等。
- 重要变更操作的详细记录，比如给角色分配权限时会记录变更前后的权限集合。
- 未进入 Controller 的 `/api/**` 请求兜底记录，比如无权限访问产生的 `403`。

### 查询审计日志

`GET /api/audit-logs?limit=100`

需要 `USER_MANAGE` 权限。默认返回最近 100 条，最大返回 200 条。

响应结构：

```json
[
  {
    "id": 1,
    "operator": "admin",
    "module": "role",
    "operation": "assign_permissions",
    "targetType": "role",
    "targetId": "3",
    "detail": "roleCode=REPORTER,beforePermissions=[],afterPermissions=[REPORT_VIEW]",
    "clientIp": "127.0.0.1",
    "occurredAt": "2026-07-11T03:58:46.925Z"
  }
]
```

示例：

```bash
curl "http://localhost:8080/api/audit-logs?limit=50" \
  -H "Authorization: Bearer <adminAccessToken>"
```

## 推荐联调流程

1. 调用 `/api/auth/captcha` 获取验证码，再使用 `admin/password` 携带验证码登录，保存返回的 `accessToken` 和 `refreshToken`。
2. 调用 `/api/permissions` 创建业务权限。
3. 调用 `/api/roles` 创建角色。
4. 调用 `/api/roles/{id}/permissions` 给角色分配权限。
5. 调用 `/api/users` 创建用户，或调用 `/api/users/{id}/roles` 给已有用户分配角色。
6. 再次获取验证码，使用普通用户登录，验证其能访问被授权接口，不能访问未授权接口。
7. 使用 `/api/auth/refresh` 刷新令牌，使用 `/api/auth/logout` 退出登录。
