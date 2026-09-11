package cn.com.app.security.repository;

import cn.com.app.security.domain.Permission;
import cn.com.app.security.domain.ClientApp;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import tk.mybatis.mapper.common.Mapper;
import tk.mybatis.mapper.entity.Example;

public interface PermissionRepository extends Mapper<Permission> {

    default Optional<Permission> findByCode(String code) {
        Permission probe = new Permission();
        probe.setCode(code);
        return Optional.ofNullable(selectOne(probe)).map(this::hydrate);
    }

    default Optional<Permission> findById(Long id) {
        return Optional.ofNullable(selectByPrimaryKey(id)).map(this::hydrate);
    }

    default boolean existsByCode(String code) {
        return findByCode(code).isPresent();
    }

    default boolean existsByClientAppId(Long clientAppId) {
        Permission probe = new Permission();
        probe.setClientAppId(clientAppId);
        return selectCount(probe) > 0;
    }

    default List<Permission> findAll() {
        return selectAll().stream()
                .map(this::hydrate)
                .toList();
    }

    default List<Permission> findAllById(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Example example = new Example(Permission.class);
        example.createCriteria().andIn("id", ids);
        return selectByExample(example).stream()
                .map(this::hydrate)
                .toList();
    }

    @Override
    default int save(Permission permission) {
        if (permission.getId() == null) {
            return insertSelective(permission);
        }
        return updateByPrimaryKeySelective(permission);
    }

    default void saveAll(Collection<Permission> permissions) {
        permissions.forEach(this::save);
    }

    @Override
    default int delete(Permission permission) {
        return deleteByPrimaryKey(permission.getId());
    }

    @Delete("delete from sys_role_permission where permission_id = #{permissionId}")
    void deleteRolePermissionsByPermissionId(@Param("permissionId") Long permissionId);

    default Permission hydrate(Permission permission) {
        if (permission.getClientAppId() != null) {
            permission.setClientApp(selectClientAppById(permission.getClientAppId()));
        }
        return permission;
    }

    @Select("select id, code, name, description, enabled from sys_client_app where id = #{clientAppId}")
    ClientApp selectClientAppById(@Param("clientAppId") Long clientAppId);
}
