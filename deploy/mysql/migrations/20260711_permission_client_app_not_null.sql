create table if not exists sys_client_app (
    id bigint not null auto_increment primary key,
    code varchar(60) not null unique,
    name varchar(120) not null,
    description varchar(255),
    enabled boolean not null default true
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

set @column_exists = (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 'sys_permission'
      and column_name = 'client_app_id'
);

set @add_column_sql = if(
    @column_exists = 0,
    'alter table sys_permission add column client_app_id bigint null',
    'select 1'
);
prepare add_column_stmt from @add_column_sql;
execute add_column_stmt;
deallocate prepare add_column_stmt;

insert into sys_client_app (code, name, description, enabled)
select 'AUTHZ', 'Authorization Center', 'Default authorization platform application', true
where not exists (
    select 1 from sys_client_app where code = 'AUTHZ'
);

update sys_permission p
join sys_client_app ca on ca.code = 'AUTHZ'
set p.client_app_id = ca.id
where p.client_app_id is null;

set @column_nullable = (
    select is_nullable
    from information_schema.columns
    where table_schema = database()
      and table_name = 'sys_permission'
      and column_name = 'client_app_id'
);

set @modify_column_sql = if(
    @column_nullable = 'YES',
    'alter table sys_permission modify client_app_id bigint not null',
    'select 1'
);
prepare modify_column_stmt from @modify_column_sql;
execute modify_column_stmt;
deallocate prepare modify_column_stmt;

set @fk_exists = (
    select count(*)
    from information_schema.table_constraints
    where constraint_schema = database()
      and table_name = 'sys_permission'
      and constraint_name = 'fk_permission_client_app'
);

set @add_fk_sql = if(
    @fk_exists = 0,
    'alter table sys_permission add constraint fk_permission_client_app foreign key (client_app_id) references sys_client_app(id)',
    'select 1'
);
prepare add_fk_stmt from @add_fk_sql;
execute add_fk_stmt;
deallocate prepare add_fk_stmt;
