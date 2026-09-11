# 阿里云 ECS 部署文档

## 部署目标

把 Citadel 授权中心部署到海外阿里云 ECS，使用 Docker Compose 一键上线：

- MySQL：生产数据库
- Spring Boot：后端服务
- Nginx：公网入口和反向代理

部署完成后访问：

```text
http://服务器IP/admin/index.html
```

默认初始化账号：

| 用户名 | 密码 | 说明 |
| --- | --- | --- |
| `admin` | `password` | 管理员账号 |
| `user` | `password` | 普通用户账号 |

首次上线后建议立即修改默认密码。

## ECS 准备

建议配置：

| 项目 | 建议 |
| --- | --- |
| 操作系统 | Ubuntu 22.04 / Alibaba Cloud Linux 3 |
| CPU / 内存 | 2 核 2G 起步 |
| 磁盘 | 40G 起步 |
| 网络 | 分配公网 IP |

阿里云安全组需要放行：

| 端口 | 用途 |
| --- | --- |
| `22` | SSH 登录 |
| `80` | HTTP 访问后台 |
| `443` | HTTPS，后续配置证书时使用 |

当前一键上线默认只使用 `80` 端口。

## 项目文件

部署相关文件：

```text
Dockerfile
docker-compose.yml
deploy.sh
.env.example
deploy/online.env.example
deploy/nginx/default.conf
deploy/mysql/schema-mysql.sql
src/main/resources/application-prod.yml
```

说明：

| 文件 | 作用 |
| --- | --- |
| `deploy.sh` | 一键上线脚本，包含配置生成、生产校验、备份、构建启动、健康检查和接口验收 |
| `docker-compose.yml` | 编排 MySQL、后端、Nginx |
| `Dockerfile` | 构建 Spring Boot 镜像 |
| `application-prod.yml` | 生产环境配置 |
| `deploy/online.env.example` | 线上非敏感参数模板，复制为 `deploy/online.env` 后按实际环境修改 |
| `deploy/mysql/schema-mysql.sql` | MySQL 首次初始化表结构 |
| `deploy/nginx/default.conf` | Nginx 反向代理配置 |
| `.env` | 部署时自动生成，保存数据库密码和 JWT 密钥 |

`.env` 包含敏感信息，不要提交到 Git，也不要发给别人。

## 标准一键上线流程

推荐交付方式就是：只配置线上参数文件，复制工程到服务器，然后执行一键上线脚本。

### 1. 配置线上参数

在本地或服务器复制模板：

```bash
cp deploy/online.env.example deploy/online.env
```

按线上环境修改 `deploy/online.env`：

```env
HTTP_PORT=80
PUBLIC_BASE_URL=https://auth.example.com
TZ=UTC

JWT_ISSUER=citadel
JWT_KEY_ID=authz-prod-key-1
JWT_EXPIRATION=2h
JWT_REFRESH_EXPIRATION=7d

AUTHZ_RATE_LIMIT_ENABLED=true
AUTHZ_RATE_LIMIT_MAX_REQUESTS=120
AUTHZ_RATE_LIMIT_WINDOW=1m

AUTHZ_LOGIN_PROTECTION_ENABLED=true
AUTHZ_LOGIN_MAX_FAILURES=5
AUTHZ_LOGIN_LOCK_DURATION=15m
```

如果暂时没有域名，`PUBLIC_BASE_URL` 可以留空，脚本会使用 `http://服务器IP` 输出访问地址。

`deploy/online.env` 只允许配置非敏感上线参数。下面这些不需要你配置，脚本会自动生成到服务器 `.env`：

```env
MYSQL_PASSWORD=自动生成
MYSQL_ROOT_PASSWORD=自动生成
JWT_PRIVATE_KEY=自动生成
JWT_PUBLIC_KEY=自动生成
```

生产环境不要在任何参数文件里加入或开启：

```env
JWT_SECRET=...
AUTHZ_CAPTCHA_RETURN_CODE=true
```

### 2. 复制工程到服务器

```bash
scp -r ./citadel root@服务器IP:/opt/citadel
```

上传前确认本地没有 `.env` 被一起上传。真实线上参数文件 `deploy/online.env` 可以随工程上传到服务器，脚本会读取它并合并到服务器 `.env`。

`deploy/online.env` 已加入 `.gitignore`，不会被提交到代码仓库；但你用 `scp -r` 复制整个工程目录时，它会一起复制到服务器。

### 3. 一键执行

```bash
ssh root@服务器IP
cd /opt/citadel
chmod +x deploy.sh
./deploy.sh
```

脚本会自动完成 Docker 检查、`.env` 生成、RSA 密钥生成、数据库初始化/迁移、容器启动和上线验收。

### 4. 阿里云安全组

必须放行：

- `22`：SSH 登录
- `80`：HTTP 访问
- `443`：HTTPS 访问，配置证书后使用

如果后台只给公司内部使用，建议安全组或 Nginx 增加办公 IP 白名单。

### 5. 域名和 HTTPS

如果有正式域名，需要配置：

- 域名 DNS A 记录指向 ECS 公网 IP
- SSL 证书
- `deploy/nginx/default.conf` 增加 HTTPS server 配置
- `docker-compose.yml` 挂载证书目录并开放 `443`
- `.env` 中设置 `PUBLIC_BASE_URL=https://你的域名`

当前仓库默认是 HTTP 上线，HTTPS 需要按你的证书位置再补 Nginx 配置。

### 6. 数据库

数据库也是一键处理，不需要手动执行 SQL。

全新部署：

- MySQL 首次启动自动执行 `deploy/mysql/schema-mysql.sql`

旧库升级：

- 脚本先备份运行中的 MySQL
- 再自动执行 `deploy/mysql/migrations/*.sql`
- 迁移完成后才启动 Spring Boot

### 7. 默认账号

首次上线后必须处理默认账号：

- 用 `admin/password` 首次登录
- 立即修改 `admin` 密码
- 按实际需要禁用、删除或修改 `user/password` 演示账号
- 确认至少保留一个拥有 `USER_MANAGE` 权限的管理员账号

### 8. 业务应用接入配置

其他业务应用使用二方包接入时，需要把 JWKS 地址改成生产地址：

```yaml
authz:
  client:
    issuer: citadel
    jwks-uri: https://你的域名/api/auth/jwks
```

同时在授权中心后台维护：

- 接入应用
- 应用权限
- 角色授权
- 用户角色

## 首次部署

1. 准备线上参数：

```bash
cp deploy/online.env.example deploy/online.env
vim deploy/online.env
```

2. 上传项目到服务器：

```bash
scp -r ./citadel root@服务器IP:/opt/citadel
```

3. 登录服务器并执行一键上线：

```bash
ssh root@服务器IP
cd /opt/citadel
chmod +x deploy.sh
./deploy.sh
```

脚本会自动完成：

- 检查 Docker
- 没有 Docker 时尝试安装
- 生成 `.env`
- 读取 `deploy/online.env` 并合并线上参数
- 自动迁移旧版 `JWT_SECRET` 到 RSA 私钥/公钥
- 校验生产环境变量，阻止占位密码、旧 JWT 密钥、验证码明文返回配置上线
- 如果服务器安装了 Maven，自动执行 `mvn test`
- 如果已有 MySQL 容器运行，先备份数据库到 `deploy/backups/`
- 启动 MySQL 并等待数据库就绪
- 自动执行 `deploy/mysql/migrations/*.sql` 数据库迁移
- 构建 Spring Boot 镜像
- 启动 Spring Boot
- 启动 Nginx
- 等待后台页面、认证发现接口、JWKS 接口可访问
- 验证生产验证码接口不会返回明文 `captchaCode`
- 输出后台访问地址

常用参数：

```bash
./deploy.sh --skip-tests     # 跳过上线前测试
./deploy.sh --skip-backup    # 跳过上线前数据库备份
./deploy.sh --skip-build     # 不重新构建镜像，只重启容器
./deploy.sh --check          # 仅检查当前线上服务状态
```

如果希望服务器没有 Maven 或测试失败时直接终止上线，可以使用：

```bash
STRICT_TESTS=true ./deploy.sh
```

5. 访问后台：

```text
http://服务器IP/admin/index.html
```

## 后续更新部署

后续改代码后，推荐流程：

1. 本地确认测试通过：

```bash
mvn test
```

2. 上传新代码到服务器，覆盖 `/opt/citadel`。

注意：不要覆盖服务器上的 `.env` 文件。如果本地目录里有 `.env`，上传前先排除。

3. 在服务器执行一键上线：

```bash
cd /opt/citadel
./deploy.sh
```

脚本会自动备份已运行的 MySQL 数据库，重新构建镜像，启动容器，并做上线验收。

4. 查看容器状态：

```bash
docker compose ps
```

5. 查看后端日志：

```bash
docker compose logs -f app
```

如果只是修改 Nginx 配置，可以执行：

```bash
docker compose restart nginx
```

如果只是修改 `.env`，通常需要重启应用：

```bash
./deploy.sh --skip-build
```

## 常用运维命令

查看所有容器：

```bash
docker compose ps
```

查看后端日志：

```bash
docker compose logs -f app
```

查看 MySQL 日志：

```bash
docker compose logs -f mysql
```

查看 Nginx 日志：

```bash
docker compose logs -f nginx
```

重启所有服务：

```bash
docker compose restart
```

停止服务：

```bash
docker compose down
```

停止并删除 MySQL 数据卷：

```bash
docker compose down -v
```

注意：`docker compose down -v` 会删除数据库数据，生产环境不要随便执行。

## 数据库说明

生产环境使用 MySQL 容器，数据保存在 Docker volume：

```text
citadel_mysql_data
```

`deploy/mysql/schema-mysql.sql` 只会在 MySQL 数据卷首次创建时执行。

如果数据库已经初始化过，后续重启不会重新执行初始化 SQL，也不会清空数据。

如果是从旧版本升级到“统一认证授权中心”版本，`./deploy.sh` 会在启动应用前自动执行：

```text
deploy/mysql/migrations/*.sql
```

其中 `20260711_permission_client_app_not_null.sql` 会：

- 确保存在默认接入应用 `AUTHZ`
- 旧库缺少 `sys_client_app` 表时自动创建
- 旧库缺少 `sys_permission.client_app_id` 字段时自动添加
- 把历史权限归属到 `AUTHZ`
- 将 `sys_permission.client_app_id` 改为 `not null`
- 缺少权限到应用的外键时自动补齐

正常上线不需要手动执行数据库 SQL。只有排查或补救时，才需要手动执行单个迁移文件：

```bash
docker exec -i citadel-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < deploy/mysql/migrations/20260711_permission_client_app_not_null.sql
```

## 数据备份

建议定期备份 MySQL。

手动备份：

```bash
docker exec citadel-mysql mysqldump -u root -p authz > citadel_backup.sql
```

执行后会提示输入 `.env` 中的 `MYSQL_ROOT_PASSWORD`。

恢复备份：

```bash
docker exec -i citadel-mysql mysql -u root -p authz < citadel_backup.sql
```

生产环境建议把备份文件同步到对象存储 OSS 或其他独立存储，不要只放在 ECS 本机。

## 配置说明

日常上线只需要维护：

```text
deploy/online.env
```

`deploy.sh` 会读取 `deploy/online.env`，再自动生成最终运行用的 `.env`。`.env` 包含数据库密码和 RSA 私钥，不建议手动维护，也不要提交到 Git。

`deploy/online.env` 示例：

```env
HTTP_PORT=80
PUBLIC_BASE_URL=https://auth.example.com
TZ=UTC
JWT_ISSUER=citadel
JWT_KEY_ID=authz-prod-key-1
JWT_EXPIRATION=2h
JWT_REFRESH_EXPIRATION=7d
AUTHZ_RATE_LIMIT_ENABLED=true
AUTHZ_RATE_LIMIT_MAX_REQUESTS=120
AUTHZ_RATE_LIMIT_WINDOW=1m
AUTHZ_LOGIN_PROTECTION_ENABLED=true
AUTHZ_LOGIN_MAX_FAILURES=5
AUTHZ_LOGIN_LOCK_DURATION=15m
```

脚本生成的 `.env` 示例：

```env
MYSQL_DATABASE=authz
MYSQL_USER=authz
MYSQL_PASSWORD=自动生成
MYSQL_ROOT_PASSWORD=自动生成
JWT_ISSUER=citadel
JWT_KEY_ID=authz-prod-key-1
JWT_PRIVATE_KEY=自动生成
JWT_PUBLIC_KEY=自动生成
JWT_EXPIRATION=2h
JWT_REFRESH_EXPIRATION=7d
AUTHZ_RATE_LIMIT_ENABLED=true
AUTHZ_RATE_LIMIT_MAX_REQUESTS=120
AUTHZ_RATE_LIMIT_WINDOW=1m
AUTHZ_LOGIN_PROTECTION_ENABLED=true
AUTHZ_LOGIN_MAX_FAILURES=5
AUTHZ_LOGIN_LOCK_DURATION=15m
HTTP_PORT=80
PUBLIC_BASE_URL=
TZ=UTC
```

常用调整：

| 配置 | 说明 |
| --- | --- |
| `HTTP_PORT` | Nginx 对外端口，默认 `80` |
| `JWT_KEY_ID` | JWT 密钥 ID，会写入 token header，方便后续密钥轮换 |
| `JWT_PRIVATE_KEY` | RSA 私钥，只配置在授权中心，用于签发 JWT |
| `JWT_PUBLIC_KEY` | RSA 公钥，用于授权中心验签和对外暴露 JWKS |
| `JWT_EXPIRATION` | Access Token 过期时间 |
| `JWT_REFRESH_EXPIRATION` | Refresh Token 过期时间 |
| `AUTHZ_RATE_LIMIT_ENABLED` | 是否启用接口限流，默认 `true` |
| `AUTHZ_RATE_LIMIT_MAX_REQUESTS` | 单个客户端 IP 在限流窗口内最多允许请求 `/api/**` 的次数，默认 `120` |
| `AUTHZ_RATE_LIMIT_WINDOW` | 接口限流统计窗口，默认 `1m` |
| `AUTHZ_LOGIN_PROTECTION_ENABLED` | 是否启用防爆破登录，默认 `true` |
| `AUTHZ_LOGIN_MAX_FAILURES` | 同一用户名和 IP 连续登录失败多少次后锁定，默认 `5` |
| `AUTHZ_LOGIN_LOCK_DURATION` | 登录失败锁定时长，默认 `15m` |
| `PUBLIC_BASE_URL` | 可选，公网访问地址，例如 `https://auth.example.com`；设置后脚本用该地址做上线验收和输出 |

`deploy/online.env` 不允许配置 `MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`JWT_PRIVATE_KEY`、`JWT_PUBLIC_KEY` 这类敏感项，这些由脚本生成并写入 `.env`。

如果服务器上已经存在旧版本 `.env`，`deploy.sh` 会自动检查 JWT 配置：

- 已存在 `JWT_PRIVATE_KEY` 和 `JWT_PUBLIC_KEY`：直接沿用现有 RSA 配置。
- 仍是旧的 `JWT_SECRET` 或缺少 RSA 配置：自动备份为 `.env.backup.<时间戳>`，移除 `JWT_SECRET`，并生成新的 `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY`。

限流和防爆破登录默认适合单台 ECS 部署：

- 接口限流：对 `/api/**` 按客户端 IP 统计，请求过多返回 `429 Too Many Requests`，并带 `Retry-After` 响应头。
- 防爆破登录：验证码错误和密码错误都会计入失败次数，同一 `用户名 + IP` 达到阈值后返回 `423 Locked`。
- 当前计数存储在应用内存中；如果后续部署多台应用实例，建议迁移到 Redis 统一计数。

修改 `.env` 后执行：

```bash
./deploy.sh --skip-build
```

## HTTPS 建议

当前方案默认提供 HTTP。

正式上线建议后续增加：

- 域名解析到 ECS 公网 IP
- SSL 证书
- Nginx HTTPS 配置
- HTTP 自动跳转 HTTPS

如果后台只给内部人员使用，也可以考虑：

- 安全组限制访问 IP
- Nginx 增加 IP 白名单
- 使用 VPN 后再访问后台

## 排查问题

### 访问不了后台

检查安全组是否放行 `80`：

```bash
docker compose ps
docker compose logs -f nginx
```

### 后端启动失败

查看后端日志：

```bash
docker compose logs -f app
```

常见原因：

- `.env` 缺少 `JWT_PRIVATE_KEY` 或 `JWT_PUBLIC_KEY`
- MySQL 密码不正确
- MySQL 还没有启动完成

### 登录被锁定或接口被限流

如果登录接口返回 `423 Locked`，说明同一账号和 IP 连续登录失败次数过多。可以等待锁定时间自动解除，或临时调整 `.env`：

```env
AUTHZ_LOGIN_MAX_FAILURES=10
AUTHZ_LOGIN_LOCK_DURATION=5m
```

如果接口返回 `429 Too Many Requests`，说明单个 IP 请求过于频繁。可以根据实际访问量调整：

```env
AUTHZ_RATE_LIMIT_MAX_REQUESTS=300
AUTHZ_RATE_LIMIT_WINDOW=1m
```

修改后重启应用：

```bash
./deploy.sh --skip-build
```

### MySQL 启动失败

查看 MySQL 日志：

```bash
docker compose logs -f mysql
```

如果是首次部署配置错了，并且确认可以删除数据，可以清理数据卷后重来：

```bash
docker compose down -v
./deploy.sh
```

生产环境执行前必须确认数据已经备份。

## 回滚

如果新版本部署后异常，可以：

1. 上传上一个可用版本代码。
2. 保留服务器 `.env` 不变。
3. 重新构建启动：

```bash
docker compose up -d --build
```

数据库结构变更需要额外谨慎。后续如果加入数据库迁移工具，建议使用 Flyway 或 Liquibase 管理版本化 SQL。

## 上线检查清单

### 服务器和网络

- ECS 配置满足最低要求，建议至少 `2 核 2G / 40G`。
- ECS 安全组已放行 `22`、`80`，需要 HTTPS 时已放行 `443`。
- 域名 DNS 已解析到 ECS 公网 IP。
- 如果后台只给内部使用，已配置安全组或 Nginx IP 白名单。

### 生产配置

- `.env` 已在服务器生成，且未提交到 Git。
- `.env` 中已生成 RSA `JWT_PRIVATE_KEY` 和 `JWT_PUBLIC_KEY`。
- `.env` 中不存在旧版 `JWT_SECRET`。
- `.env` 中不存在 `change-me`、`replace-with` 等占位值。
- 生产环境未开启 `AUTHZ_CAPTCHA_RETURN_CODE=true`。
- `PUBLIC_BASE_URL` 已按实际域名设置；没有域名时已确认使用公网 IP 访问。
- `JWT_EXPIRATION`、`JWT_REFRESH_EXPIRATION` 已按实际安全要求确认。
- 限流和登录防爆破参数已按实际访问量确认。
- `src/main/resources/application-prod.yml` 保持 `spring.sql.init.mode=never`。

### 数据库

- 全新部署已确认 MySQL 会使用 `deploy/mysql/schema-mysql.sql` 自动初始化。
- 旧库升级前脚本已自动备份数据库，或你已手动备份。
- `./deploy.sh` 已自动执行 `deploy/mysql/migrations/*.sql`。
- 已确认 `sys_permission.client_app_id` 为 `not null`。
- 已配置定期数据库备份，并计划同步到 OSS 或独立存储。
- 生产环境不会执行 `docker compose down -v`，除非已确认要删除全部数据库数据。

### 上线脚本和容器

- 已执行 `./deploy.sh`。
- 脚本输出“上线验收通过”。
- `docker compose ps` 中 `mysql`、`app`、`nginx` 状态正常。
- `docker compose logs -f app` 没有持续异常日志。
- `docker compose logs -f nginx` 没有持续代理错误。

### 后台功能

- 后台可以访问 `/admin/index.html`。
- 可以获取验证码，且生产响应不包含明文 `captchaCode`。
- `admin/password` 可以完成首次登录。
- 已立即修改默认管理员密码。
- 已禁用、删除或修改 `user/password` 演示账号。
- 至少保留一个拥有 `USER_MANAGE` 权限的管理员账号。
- 已完成接入应用、权限维护、角色授权、账号维护的基本操作验证。

### 业务应用接入

- 业务应用已配置生产 JWKS 地址：`https://你的域名/api/auth/jwks`。
- 业务应用已配置正确 `issuer`，默认是 `citadel`。
- 授权中心已创建业务系统对应的接入应用。
- 授权中心已维护该应用的权限，并完成角色授权。
- 业务应用已验证 `@PreAuthorize` 权限控制生效。

### HTTPS 和安全

- 正式公网生产建议已配置 HTTPS。
- HTTPS 场景下，`PUBLIC_BASE_URL` 使用 `https://`。
- JWT 私钥只存在授权中心服务器，不下发给业务应用。
- 业务应用只配置 JWKS 地址或公钥。
- 已确认 `.env` 文件权限为 `600`。
