package cn.com.app.security.repository;

import cn.com.app.security.domain.Permission;
import cn.com.app.security.domain.Role;
import cn.com.app.security.domain.UserAccount;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import tk.mybatis.mapper.common.Mapper;

public interface UserAccountRepository extends Mapper<UserAccount> {

    default Optional<UserAccount> findByUsername(String username) {
        UserAccount probe = new UserAccount();
        probe.setUsername(username);
        return Optional.ofNullable(selectOne(probe)).map(this::hydrate);
    }

    default Optional<UserAccount> findById(Long id) {
        return Optional.ofNullable(selectByPrimaryKey(id)).map(this::hydrate);
    }

    default boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    default List<UserAccount> findAll() {
        return selectAll().stream()
                .map(this::hydrate)
                .toList();
    }

    default long count() {
        return selectCount(new UserAccount());
    }

    @Override
    default int save(UserAccount user) {
        int rows;
        if (user.getId() == null) {
            rows = insertSelective(user);
        } else {
            rows = updateByPrimaryKeySelective(user);
        }
        replaceRoles(user.getId(), user.getRoles().stream().map(Role::getId).toList());
        hydrate(user);
        return rows;
    }

    default void saveAll(Collection<UserAccount> users) {
        users.forEach(this::save);
    }

    @Override
    default int delete(UserAccount user) {
        deleteRolesByUserId(user.getId());
        return deleteByPrimaryKey(user.getId());
    }

    default UserAccount hydrate(UserAccount user) {
        List<Role> roles = selectRolesByUserId(user.getId());
        roles.forEach(role -> role.setPermissions(new LinkedHashSet<>(selectPermissionsByRoleId(role.getId()))));
        user.setRoles(new LinkedHashSet<>(roles));
        return user;
    }

    default void replaceRoles(Long userId, Collection<Long> roleIds) {
        deleteRolesByUserId(userId);
        if (roleIds != null) {
            roleIds.forEach(roleId -> insertUserRole(userId, roleId));
        }
    }

    @Select("""
            select r.id, r.code, r.name
            from sys_role r
            inner join sys_user_role ur on ur.role_id = r.id
            where ur.user_id = #{userId}
            order by r.id
            """)
    List<Role> selectRolesByUserId(@Param("userId") Long userId);

    @Select("""
            select p.id, p.code, p.name
            from sys_permission p
            inner join sys_role_permission rp on rp.permission_id = p.id
            where rp.role_id = #{roleId}
            order by p.id
            """)
    List<Permission> selectPermissionsByRoleId(@Param("roleId") Long roleId);

    @Delete("delete from sys_user_role where user_id = #{userId}")
    void deleteRolesByUserId(@Param("userId") Long userId);

    @Insert("insert into sys_user_role(user_id, role_id) values(#{userId}, #{roleId})")
    void insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
