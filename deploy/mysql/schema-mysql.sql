create table if not exists sys_client_app (
    id bigint not null auto_increment primary key,
    code varchar(60) not null unique,
    name varchar(120) not null,
    description varchar(255),
    enabled boolean not null default true
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_user (
    id bigint not null auto_increment primary key,
    username varchar(60) not null unique,
    password varchar(255) not null,
    display_name varchar(80) not null,
    account_non_expired boolean not null default true,
    account_non_locked boolean not null default true,
    credentials_non_expired boolean not null default true,
    enabled boolean not null default true
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_role (
    id bigint not null auto_increment primary key,
    code varchar(60) not null unique,
    name varchar(120) not null
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_permission (
    id bigint not null auto_increment primary key,
    code varchar(80) not null unique,
    name varchar(120) not null,
    client_app_id bigint not null,
    constraint fk_permission_client_app foreign key (client_app_id) references sys_client_app(id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_user_role (
    user_id bigint not null,
    role_id bigint not null,
    primary key (user_id, role_id),
    constraint fk_user_role_user foreign key (user_id) references sys_user(id),
    constraint fk_user_role_role foreign key (role_id) references sys_role(id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_role_permission (
    role_id bigint not null,
    permission_id bigint not null,
    primary key (role_id, permission_id),
    constraint fk_role_permission_role foreign key (role_id) references sys_role(id),
    constraint fk_role_permission_permission foreign key (permission_id) references sys_permission(id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_refresh_token (
    id bigint not null auto_increment primary key,
    token varchar(80) not null unique,
    user_id bigint not null,
    expires_at datetime(6) not null,
    revoked boolean not null default false,
    constraint fk_refresh_token_user foreign key (user_id) references sys_user(id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_sso_ticket (
    id bigint not null auto_increment primary key,
    ticket varchar(80) not null unique,
    user_id bigint not null,
    client_app_id bigint not null,
    redirect_uri varchar(500) not null,
    state varchar(120),
    expires_at datetime(6) not null,
    used boolean not null default false,
    constraint fk_sso_ticket_user foreign key (user_id) references sys_user(id),
    constraint fk_sso_ticket_client_app foreign key (client_app_id) references sys_client_app(id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_blacklisted_token (
    id bigint not null auto_increment primary key,
    token_id varchar(80) not null unique,
    expires_at datetime(6) not null
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_audit_log (
    id bigint not null auto_increment primary key,
    operator varchar(80) not null,
    module varchar(60) not null,
    operation varchar(80) not null,
    target_type varchar(60),
    target_id varchar(80),
    detail varchar(1000),
    client_ip varchar(80),
    occurred_at datetime(6) not null
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create index idx_audit_log_occurred_at on sys_audit_log(occurred_at);
create index idx_audit_log_operator on sys_audit_log(operator);
create index idx_sso_ticket_expires_at on sys_sso_ticket(expires_at);
