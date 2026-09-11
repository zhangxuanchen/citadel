package cn.com.app.security.config;

import cn.com.app.security.domain.ClientApp;
import cn.com.app.security.domain.Permission;
import cn.com.app.security.domain.Role;
import cn.com.app.security.domain.UserAccount;
import cn.com.app.security.repository.ClientAppRepository;
import cn.com.app.security.repository.PermissionRepository;
import cn.com.app.security.repository.RoleRepository;
import cn.com.app.security.repository.UserAccountRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedUsers(
            ClientAppRepository clientAppRepository,
            PermissionRepository permissionRepository,
            RoleRepository roleRepository,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (userAccountRepository.count() > 0) {
                return;
            }

            ClientApp authzApp = new ClientApp("AUTHZ", "统一认证授权中心", "Citadel 自身管理后台和认证接口");
            ClientApp articleApp = new ClientApp("ARTICLE", "文章示例应用", "用于演示其他业务系统如何接入权限控制");
            clientAppRepository.save(authzApp);
            clientAppRepository.save(articleApp);

            Permission articleRead = new Permission("ARTICLE_READ", "Read articles", articleApp);
            Permission articleWrite = new Permission("ARTICLE_WRITE", "Write articles", articleApp);
            Permission userManage = new Permission("USER_MANAGE", "Manage users", authzApp);

            Role adminRole = new Role("ADMIN", "Administrator")
                    .addPermission(articleRead)
                    .addPermission(articleWrite)
                    .addPermission(userManage);
            Role userRole = new Role("USER", "Regular user")
                    .addPermission(articleRead);

            permissionRepository.saveAll(List.of(articleRead, articleWrite, userManage));
            roleRepository.saveAll(List.of(adminRole, userRole));

            UserAccount admin = new UserAccount("admin", passwordEncoder.encode("password"), "System Admin")
                    .addRole(adminRole);
            UserAccount user = new UserAccount("user", passwordEncoder.encode("password"), "Demo User")
                    .addRole(userRole);

            userAccountRepository.saveAll(List.of(admin, user));
        };
    }
}
