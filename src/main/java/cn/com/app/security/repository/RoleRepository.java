package cn.com.app.security.repository;

import cn.com.app.security.domain.Permission;
import cn.com.app.security.domain.Role;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import tk.mybatis.mapper.common.Mapper;
import tk.mybatis.mapper.entity.Example;

public interface RoleRepository extends Mapper<Role> {

    default Optional<Role> findByCode(String code) {
        Role probe = new Role();
        probe.setCode(code);
        return Optional.ofNullable(selectOne(probe)).map(this::hydrate);
    }

    default Optional<Role> findById(Long id) {
        return Optional.ofNullable(selectByPrimaryKey(id)).map(this::hydrate);
    }

    default boolean existsByCode(String code) {
        return findByCode(code).isPresent();
    }

    default List<Role> findAll() {
        return selectAll().stream()
                .map(this::hydrate)
                .toList();
    }

    default List<Role> findAllById(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Example example = new Example(Role.class);
        example.createCriteria().andIn("id", ids);
        return selectByExample(example).stream()
                .map(this::hydrate)
                .toList();
    }

    @Override
    default int save(Role role) {
        int rows;
        if (role.getId() == null) {
            rows = insertSelective(role);
        } else {
            rows = updateByPrimaryKeySelective(role);
        }
        replacePermissions(role.getId(), role.getPermissions().stream().map(Permission::getId).toList());
        hydrate(role);
        return rows;
    }

    default void saveAll(Collection<Role> roles) {
        roles.forEach(this::save);
    }

    @Override
    default int delete(Role role) {
        return deleteByPrimaryKey(role.getId());
    }

    default Role hydrate(Role role) {
        role.setPermissions(new LinkedHashSet<>(selectPermissionsByRoleId(role.getId())));
        return role;
    }

    default void replacePermissions(Long roleId, Collection<Long> permissionIds) {
        deletePermissionsByRoleId(roleId);
        if (permissionIds != null) {
            permissionIds.forEach(permissionId -> insertRolePermission(roleId, permissionId));
        }
    }

    @Select("""
            select p.id, p.code, p.name, p.client_app_id, ca.code as app_code, ca.name as app_name
            from sys_permission p
            inner join sys_role_permission rp on rp.permission_id = p.id
            left join sys_client_app ca on ca.id = p.client_app_id
            where rp.role_id = #{roleId}
            order by p.id
            """)
    List<Permission> selectPermissionsByRoleId(@Param("roleId") Long roleId);

    @Delete("delete from sys_role_permission where role_id = #{roleId}")
    void deletePermissionsByRoleId(@Param("roleId") Long roleId);

    @Delete("delete from sys_user_role where role_id = #{roleId}")
    void deleteUserRolesByRoleId(@Param("roleId") Long roleId);

    @Insert("insert into sys_role_permission(role_id, permission_id) values(#{roleId}, #{permissionId})")
    void insertRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
