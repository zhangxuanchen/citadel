#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

SKIP_TESTS=false
SKIP_BACKUP=false
SKIP_BUILD=false
STRICT_TESTS="${STRICT_TESTS:-false}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-120}"

log() {
  printf '\n[citadel-online] %s\n' "$1"
}

fail() {
  log "上线失败：$1"
  printf '排查命令：\n'
  printf '  docker compose ps\n'
  printf '  docker compose logs -f app\n'
  printf '  docker compose logs -f nginx\n'
  exit 1
}

usage() {
  cat <<'EOF'
Citadel 一键上线脚本

用法：
  ./deploy.sh                 执行完整上线流程
  ./deploy.sh --skip-tests    跳过上线前测试
  ./deploy.sh --skip-backup   跳过上线前数据库备份
  ./deploy.sh --skip-build    不重新构建镜像，只重启容器
  ./deploy.sh --check         仅检查当前线上服务状态

可选环境变量：
  STRICT_TESTS=true           服务器未安装 Maven 或测试失败时直接终止
  HEALTH_TIMEOUT=180          等待服务可用的最长秒数

上线参数：
  可选复制 deploy/online.env.example 为 deploy/online.env，只填写域名、端口、token 过期时间、
  限流和登录保护等线上参数。数据库密码和 RSA 密钥由脚本自动生成。
EOF
}

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

run_as_root() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  elif command_exists sudo; then
    sudo "$@"
  else
    fail "需要 root 权限执行：$*"
  fi
}

compose_cmd() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  elif command_exists docker-compose; then
    docker-compose "$@"
  else
    fail "未找到 Docker Compose。请安装 Docker Compose 插件后重试。"
  fi
}

env_value() {
  local key="$1"
  local file="${2:-.env}"
  if [ ! -f "$file" ]; then
    return 0
  fi
  grep -E "^${key}=" "$file" | tail -n 1 | cut -d '=' -f 2- || true
}

random_password() {
  if command_exists openssl; then
    openssl rand -hex 16
  elif command_exists sha256sum; then
    date +%s%N | sha256sum | cut -c 1-32
  else
    date +%s%N | shasum -a 256 | cut -c 1-32
  fi
}

pem_to_env() {
  awk 'NF {printf "%s\\n", $0}' "$1"
}

generate_rsa_env_values() {
  if ! command_exists openssl; then
    fail "生成 RSA JWT 密钥需要 openssl，请先安装 openssl 后重试。"
  fi
  local key_dir
  key_dir="$(mktemp -d)"
  openssl genrsa 2048 > "$key_dir/jwt-private.pem"
  openssl rsa -in "$key_dir/jwt-private.pem" -pubout > "$key_dir/jwt-public.pem" 2>/dev/null
  JWT_PRIVATE_KEY_VALUE="$(pem_to_env "$key_dir/jwt-private.pem")"
  JWT_PUBLIC_KEY_VALUE="$(pem_to_env "$key_dir/jwt-public.pem")"
  rm -rf "$key_dir"
}

remove_env_key() {
  local key="$1"
  local file="$2"
  sed -i.bak "/^${key}=/d" "$file"
  rm -f "${file}.bak"
}

append_env_key_if_missing() {
  local key="$1"
  local value="$2"
  local file="$3"
  if ! grep -q "^${key}=" "$file"; then
    printf '%s=%s\n' "$key" "$value" >> "$file"
  fi
}

set_env_key() {
  local key="$1"
  local value="$2"
  local file="$3"
  remove_env_key "$key" "$file"
  printf '%s=%s\n' "$key" "$value" >> "$file"
}

is_online_env_key_allowed() {
  case "$1" in
    HTTP_PORT|PUBLIC_BASE_URL|TZ|JWT_ISSUER|JWT_KEY_ID|JWT_EXPIRATION|JWT_REFRESH_EXPIRATION|AUTHZ_RATE_LIMIT_ENABLED|AUTHZ_RATE_LIMIT_MAX_REQUESTS|AUTHZ_RATE_LIMIT_WINDOW|AUTHZ_LOGIN_PROTECTION_ENABLED|AUTHZ_LOGIN_MAX_FAILURES|AUTHZ_LOGIN_LOCK_DURATION)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

apply_online_env_overrides() {
  local online_env="deploy/online.env"
  [ -f "$online_env" ] || return

  log "加载线上参数文件：${online_env}"
  local line key value
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      ''|\#*)
        continue
        ;;
    esac

    if [[ "$line" != *=* ]]; then
      fail "${online_env} 存在非法行：${line}"
    fi

    key="${line%%=*}"
    value="${line#*=}"
    key="$(printf '%s' "$key" | tr -d '[:space:]')"

    if ! is_online_env_key_allowed "$key"; then
      fail "${online_env} 不允许配置 ${key}。数据库密码和 RSA 密钥由脚本自动生成，请不要写入线上参数文件。"
    fi

    set_env_key "$key" "$value" ".env"
  done < "$online_env"
}

ensure_docker() {
  if command_exists docker; then
    log "Docker 已安装：$(docker --version)"
  else
    log "未检测到 Docker，开始安装 Docker。"
    if ! command_exists curl; then
      if command_exists apt-get; then
        run_as_root apt-get update
        run_as_root apt-get install -y curl
      elif command_exists yum; then
        run_as_root yum install -y curl
      else
        fail "未找到 curl，也无法自动识别包管理器，请先安装 curl 和 Docker。"
      fi
    fi
    curl -fsSL https://get.docker.com | run_as_root sh
  fi

  run_as_root systemctl enable docker >/dev/null 2>&1 || true
  run_as_root systemctl start docker >/dev/null 2>&1 || true

  if ! docker info >/dev/null 2>&1; then
    fail "Docker 服务不可用，请检查 Docker 是否启动。"
  fi
}

ensure_env() {
  if [ -f .env ]; then
    if grep -q '^JWT_PRIVATE_KEY=' .env && grep -q '^JWT_PUBLIC_KEY=' .env; then
      log ".env 已存在且包含 RSA JWT 配置，沿用现有生产配置。"
    else
      log ".env 已存在但缺少 RSA JWT 配置，开始自动迁移。"
      local backup
      backup=".env.backup.$(date +%Y%m%d%H%M%S)"
      cp .env "$backup"
      generate_rsa_env_values
      remove_env_key "JWT_SECRET" ".env"
      remove_env_key "JWT_PRIVATE_KEY" ".env"
      remove_env_key "JWT_PUBLIC_KEY" ".env"
      append_env_key_if_missing "JWT_ISSUER" "citadel" ".env"
      append_env_key_if_missing "JWT_KEY_ID" "authz-prod-key-1" ".env"
      append_env_key_if_missing "JWT_PRIVATE_KEY" "$JWT_PRIVATE_KEY_VALUE" ".env"
      append_env_key_if_missing "JWT_PUBLIC_KEY" "$JWT_PUBLIC_KEY_VALUE" ".env"
      log ".env 已自动迁移到 RSA JWT 配置，原文件备份为 ${backup}。"
    fi
  else
    log "生成 .env 生产环境变量。"
    generate_rsa_env_values
    cat > .env <<EOF
MYSQL_DATABASE=authz
MYSQL_USER=authz
MYSQL_PASSWORD=$(random_password)
MYSQL_ROOT_PASSWORD=$(random_password)
JWT_ISSUER=citadel
JWT_KEY_ID=authz-prod-key-1
JWT_PRIVATE_KEY=$JWT_PRIVATE_KEY_VALUE
JWT_PUBLIC_KEY=$JWT_PUBLIC_KEY_VALUE
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
EOF
  fi

  append_env_key_if_missing "HTTP_PORT" "80" ".env"
  append_env_key_if_missing "PUBLIC_BASE_URL" "" ".env"
  apply_online_env_overrides
  chmod 600 .env
}

validate_production_env() {
  log "校验生产环境变量。"

  [ -f .env ] || fail ".env 不存在。"
  grep -q '^MYSQL_PASSWORD=' .env || fail ".env 缺少 MYSQL_PASSWORD。"
  grep -q '^MYSQL_ROOT_PASSWORD=' .env || fail ".env 缺少 MYSQL_ROOT_PASSWORD。"
  grep -q '^JWT_PRIVATE_KEY=' .env || fail ".env 缺少 JWT_PRIVATE_KEY。"
  grep -q '^JWT_PUBLIC_KEY=' .env || fail ".env 缺少 JWT_PUBLIC_KEY。"

  if grep -q '^JWT_SECRET=' .env; then
    fail ".env 仍包含旧版 JWT_SECRET，请先迁移到 RSA 私钥/公钥。"
  fi

  if grep -Eq 'change-me|replace-with|default-secret' .env; then
    fail ".env 仍包含占位值，请替换后再上线。"
  fi

  if grep -Eq 'auth\.example\.com|你的域名' .env; then
    fail ".env 仍包含示例域名，请在 deploy/online.env 中改成真实域名；没有域名时请把 PUBLIC_BASE_URL 留空。"
  fi

  if grep -q '^AUTHZ_CAPTCHA_RETURN_CODE=true' .env; then
    fail "生产环境不能开启 AUTHZ_CAPTCHA_RETURN_CODE=true。"
  fi
}

run_preflight_tests() {
  if [ "$SKIP_TESTS" = "true" ]; then
    log "已按参数跳过上线前测试。"
    return
  fi

  if command_exists mvn; then
    log "执行上线前测试：mvn test。"
    mvn test || fail "mvn test 未通过。"
    return
  fi

  if [ "$STRICT_TESTS" = "true" ]; then
    fail "当前服务器未安装 Maven，且 STRICT_TESTS=true，无法完成上线前测试。"
  fi

  log "当前服务器未安装 Maven，跳过 mvn test。建议先在 CI 或本地确认测试通过。"
}

backup_mysql_if_running() {
  if [ "$SKIP_BACKUP" = "true" ]; then
    log "已按参数跳过上线前数据库备份。"
    return
  fi

  if ! docker ps --format '{{.Names}}' | grep -qx 'citadel-mysql'; then
    log "未检测到运行中的 citadel-mysql，首次上线或数据库未启动，跳过备份。"
    return
  fi

  mkdir -p deploy/backups
  local backup_file
  backup_file="deploy/backups/citadel_$(date +%Y%m%d%H%M%S).sql"
  log "检测到已有 MySQL 容器，开始备份数据库到 ${backup_file}。"
  docker exec citadel-mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' > "$backup_file" \
    || fail "数据库备份失败，已终止上线。"
  gzip -f "$backup_file"
  log "数据库备份完成：${backup_file}.gz"
}

start_mysql() {
  log "启动 MySQL 并等待数据库就绪。"
  compose_cmd up -d mysql

  local deadline
  deadline=$((SECONDS + HEALTH_TIMEOUT))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if docker exec citadel-mysql sh -c 'mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' >/dev/null 2>&1; then
      log "MySQL 已就绪。"
      return
    fi
    sleep 3
  done

  fail "MySQL 在 ${HEALTH_TIMEOUT} 秒内未就绪。"
}

apply_database_migrations() {
  if [ ! -d deploy/mysql/migrations ]; then
    log "未发现数据库迁移目录，跳过迁移。"
    return
  fi

  local migration
  local found=false
  for migration in deploy/mysql/migrations/*.sql; do
    [ -e "$migration" ] || continue
    found=true
    log "执行数据库迁移：${migration}"
    docker exec -i citadel-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < "$migration" \
      || fail "数据库迁移失败：${migration}"
  done

  if [ "$found" = "false" ]; then
    log "未发现数据库迁移文件，跳过迁移。"
  else
    log "数据库迁移执行完成。"
  fi
}

deploy_containers() {
  local args=(up -d)
  if [ "$SKIP_BUILD" != "true" ]; then
    args+=(--build)
  fi

  log "构建并启动 MySQL、Spring Boot、Nginx。"
  compose_cmd "${args[@]}"
}

base_url() {
  local public_base
  public_base="$(env_value PUBLIC_BASE_URL)"
  if [ -n "$public_base" ]; then
    printf '%s' "${public_base%/}"
    return
  fi

  local port
  port="$(env_value HTTP_PORT)"
  port="${port:-80}"
  printf 'http://127.0.0.1:%s' "$port"
}

wait_for_url() {
  local url="$1"
  local name="$2"
  local deadline
  deadline=$((SECONDS + HEALTH_TIMEOUT))

  log "等待 ${name} 可用：${url}"
  while [ "$SECONDS" -lt "$deadline" ]; do
    if curl -fsS --max-time 5 "$url" >/dev/null 2>&1; then
      log "${name} 已可用。"
      return
    fi
    sleep 3
  done

  fail "${name} 在 ${HEALTH_TIMEOUT} 秒内未变为可用。"
}

verify_online() {
  local url
  url="$(base_url)"

  wait_for_url "${url}/admin/index.html" "管理后台页面"
  wait_for_url "${url}/api/auth/discovery" "认证发现接口"
  wait_for_url "${url}/api/auth/jwks" "JWKS 公钥接口"

  if curl -fsS --max-time 5 "${url}/api/auth/captcha" | grep -q 'captchaCode'; then
    fail "验证码接口返回了 captchaCode，生产环境疑似开启了验证码明文返回。"
  fi

  log "上线验收通过。"
}

print_access_info() {
  local port public_ip url
  port="$(env_value HTTP_PORT)"
  port="${port:-80}"
  url="$(base_url)"

  public_ip="$(curl -fsS --max-time 3 https://ifconfig.me 2>/dev/null || true)"
  if [ -z "$public_ip" ]; then
    public_ip="服务器IP"
  fi

  log "一键上线完成。"
  if [ -n "$(env_value PUBLIC_BASE_URL)" ]; then
    printf '后台地址：%s/admin/index.html\n' "$url"
  elif [ "$port" = "80" ]; then
    printf '后台地址：http://%s/admin/index.html\n' "$public_ip"
  else
    printf '后台地址：http://%s:%s/admin/index.html\n' "$public_ip" "$port"
  fi
  printf '默认账号：admin / password（上线后请立即修改）\n'
  printf '查看状态：docker compose ps\n'
  printf '查看日志：docker compose logs -f app\n'
  printf '停止服务：docker compose down\n'
}

check_online() {
  validate_production_env
  log "当前容器状态："
  compose_cmd ps
  verify_online
}

parse_args() {
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --skip-tests)
        SKIP_TESTS=true
        ;;
      --skip-backup)
        SKIP_BACKUP=true
        ;;
      --skip-build)
        SKIP_BUILD=true
        ;;
      --check)
        CHECK_ONLY=true
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        usage
        fail "未知参数：$1"
        ;;
    esac
    shift
  done
}

main() {
  CHECK_ONLY=false
  parse_args "$@"

  log "开始一键上线 citadel。"
  ensure_docker
  ensure_env

  if [ "$CHECK_ONLY" = "true" ]; then
    check_online
    return
  fi

  validate_production_env
  run_preflight_tests
  backup_mysql_if_running
  start_mysql
  apply_database_migrations
  deploy_containers

  log "当前容器状态："
  compose_cmd ps

  verify_online
  print_access_info
}

main "$@"
