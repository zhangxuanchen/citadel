package cn.com.app.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "authz.captcha.return-code=true",
        "authz.security.rate-limit.max-requests=10000",
        "authz.security.login-protection.max-failures=3",
        "authz.security.login-protection.lock-duration=10m"
})
@AutoConfigureMockMvc
class CitadelApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginReturnsJwtToken() throws Exception {
        Captcha captcha = captcha();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"password","captchaId":"%s","captchaCode":"%s"}
                                """.formatted(captcha.captchaId(), captcha.captchaCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.authorities[?(@ == 'ROLE_ADMIN')]").exists())
                .andExpect(jsonPath("$.authorities[?(@ == 'ARTICLE_WRITE')]").exists());
    }

    @Test
    void loginRequiresValidCaptcha() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"password","captchaId":"missing","captchaCode":"0000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid captcha"));
    }

    @Test
    void userCanReadArticlesButCannotCreateArticles() throws Exception {
        String token = login("user", "password");

        mockMvc.perform(get("/api/articles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/articles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessAdminDashboard() throws Exception {
        String token = login("admin", "password");

        mockMvc.perform(get("/api/admin/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("admin dashboard"));
    }

    @Test
    void registerAndRefreshTokenWork() throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"new-user","password":"password","displayName":"New User"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("new-user"))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String refreshToken = objectMapper.readTree(response).get("refreshToken").asText();
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void logoutBlacklistsAccessToken() throws Exception {
        Tokens tokens = loginTokens("admin", "password");

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(tokens.refreshToken())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanManagePermissionsRolesAndUsers() throws Exception {
        String adminToken = login("admin", "password");

        long permissionId = createPermission(adminToken, "REPORT_VIEW", "View reports");
        String roleResponse = mockMvc.perform(post("/api/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"REPORTER","name":"Reporter"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REPORTER"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long roleId = objectMapper.readTree(roleResponse).get("id").asLong();

        mockMvc.perform(put("/api/roles/{id}/permissions", roleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionIds":[%d]}
                                """.formatted(permissionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[?(@.code == 'REPORT_VIEW')]").exists());

        String userResponse = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"managed-user","password":"password","displayName":"Managed User","enabled":true,"roleIds":[%d]}
                                """.formatted(roleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[?(@ == 'REPORTER')]").exists())
                .andExpect(jsonPath("$.permissions[?(@ == 'REPORT_VIEW')]").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long userId = objectMapper.readTree(userResponse).get("id").asLong();

        mockMvc.perform(put("/api/users/{id}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Managed User Updated","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Managed User Updated"));
    }

    @Test
    void authzServerExposesClientAppAndUserInfoForOtherApplications() throws Exception {
        mockMvc.perform(get("/api/auth/discovery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("citadel"))
                  .andExpect(jsonPath("$.signingAlgorithm").value("RS256"))
                  .andExpect(jsonPath("$.jwksEndpoint").value("/api/auth/jwks"))
                .andExpect(jsonPath("$.userinfoEndpoint").value("/api/auth/userinfo"));

          mockMvc.perform(get("/api/auth/jwks"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                  .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                  .andExpect(jsonPath("$.keys[0].kid").value("authz-demo-key-1"));

        String adminToken = login("admin", "password");
        String appResponse = mockMvc.perform(post("/api/client-apps")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ORDER","name":"订单系统","description":"订单业务应用","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ORDER"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long appId = objectMapper.readTree(appResponse).get("id").asLong();

        mockMvc.perform(post("/api/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ORDER_READ","name":"订单查询","appCode":"ORDER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ORDER_READ"))
                .andExpect(jsonPath("$.appCode").value("ORDER"))
                .andExpect(jsonPath("$.appName").value("订单系统"));

        mockMvc.perform(get("/api/client-apps/{id}", appId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("订单系统"));

        mockMvc.perform(get("/api/auth/userinfo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.authorities[?(@ == 'USER_MANAGE')]").exists());
    }

    @Test
    void managementListsSupportSearchAndPagination() throws Exception {
        String adminToken = login("admin", "password");
        String appResponse = mockMvc.perform(post("/api/client-apps")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PAGE_APP","name":"分页测试应用","description":"用于分页搜索测试","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long appId = objectMapper.readTree(appResponse).get("id").asLong();

        String permissionResponse = mockMvc.perform(post("/api/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PAGE_ACCESS","name":"分页测试权限","appCode":"PAGE_APP"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long permissionId = objectMapper.readTree(permissionResponse).get("id").asLong();

        String roleResponse = mockMvc.perform(post("/api/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"PAGE_ROLE","name":"分页测试角色"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long roleId = objectMapper.readTree(roleResponse).get("id").asLong();

        mockMvc.perform(put("/api/roles/{id}/permissions", roleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionIds":[%d]}
                                """.formatted(permissionId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"page-user","password":"password","displayName":"分页测试账号","enabled":true,"roleIds":[%d]}
                                """.formatted(roleId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/client-apps")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());

        mockMvc.perform(get("/api/client-apps")
                        .queryParam("keyword", "PAGE_APP")
                        .queryParam("page", "0")
                        .queryParam("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(appId))
                .andExpect(jsonPath("$.content[0].code").value("PAGE_APP"));

        mockMvc.perform(get("/api/permissions")
                        .queryParam("keyword", "PAGE_ACCESS")
                        .queryParam("page", "0")
                        .queryParam("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("PAGE_ACCESS"));

        mockMvc.perform(get("/api/roles")
                        .queryParam("keyword", "PAGE_ACCESS")
                        .queryParam("page", "0")
                        .queryParam("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("PAGE_ROLE"));

        mockMvc.perform(get("/api/users")
                        .queryParam("keyword", "page-user")
                        .queryParam("page", "0")
                        .queryParam("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("page-user"));
    }

    @Test
    void ssoTicketCanBeExchangedOnlyOnce() throws Exception {
        String adminToken = login("admin", "password");
        String response = mockMvc.perform(post("/api/sso/tickets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appCode":"ARTICLE","redirectUri":"http://article.example.com/sso/callback","state":"state-123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").isNotEmpty())
                .andExpect(jsonPath("$.appCode").value("ARTICLE"))
                .andExpect(jsonPath("$.redirectUrl").value(org.hamcrest.Matchers.containsString("ticket=")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String ticket = objectMapper.readTree(response).get("ticket").asText();
        mockMvc.perform(post("/api/sso/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticket":"%s"}
                                """.formatted(ticket)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/sso/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticket":"%s"}
                                """.formatted(ticket)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userWithoutUserManageCannotManageRoles() throws Exception {
        String userToken = login("user", "password");

        mockMvc.perform(get("/api/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void permissionUsageDemoEndpointsShowAuthorizationRules() throws Exception {
        mockMvc.perform(get("/api/demo/permissions/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permission").value("permitAll"));

        String userToken = login("user", "password");
        mockMvc.perform(get("/api/demo/permissions/login-required")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permission").value("isAuthenticated()"));

        mockMvc.perform(get("/api/demo/permissions/article-write")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());

        String adminToken = login("admin", "password");
        mockMvc.perform(get("/api/demo/permissions/user-manage")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permission").value("hasAuthority('USER_MANAGE')"));
    }

    @Test
    void passwordChangeAndAdminResetWork() throws Exception {
        String adminToken = login("admin", "password");
        long userId = objectMapper.readTree(mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"password-user","password":"password","displayName":"Password User","enabled":true,"roleIds":[]}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).get("id").asLong();

        String userToken = login("password-user", "password");
        mockMvc.perform(post("/api/auth/password/change")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"password","newPassword":"changed-password"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/password/reset/{userId}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newPassword":"reset-password"}
                                """))
                .andExpect(status().isOk());

        login("password-user", "reset-password");
    }

    @Test
    void adminCanDeleteUserWithRefreshTokenAndSsoTicket() throws Exception {
        String adminToken = login("admin", "password");
        String userResponse = mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"delete-user","password":"password","displayName":"Delete User","enabled":true,"roleIds":[]}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long userId = objectMapper.readTree(userResponse).get("id").asLong();

        String userToken = login("delete-user", "password");
        mockMvc.perform(post("/api/sso/tickets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"appCode":"ARTICLE","redirectUri":"http://article.example.com/sso/callback","state":"delete-user"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").isNotEmpty());

        mockMvc.perform(delete("/api/users/{id}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("user deleted"));

        mockMvc.perform(get("/api/users/{id}", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void repeatedLoginFailuresLockAccountForCurrentIp() throws Exception {
        String adminToken = login("admin", "password");
        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"lock-user","password":"password","displayName":"Lock User","enabled":true,"roleIds":[]}
                                """))
                .andExpect(status().isOk());

        for (int i = 0; i < 3; i++) {
            Captcha captcha = captcha();
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"lock-user","password":"wrong-password","captchaId":"%s","captchaCode":"%s"}
                                    """.formatted(captcha.captchaId(), captcha.captchaCode())))
                    .andExpect(status().isUnauthorized());
        }

        Captcha captcha = captcha();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"lock-user","password":"password","captchaId":"%s","captchaCode":"%s"}
                                """.formatted(captcha.captchaId(), captcha.captchaCode())))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.message").value("Login temporarily locked, please try again later"));
    }

    private long createPermission(String adminToken, String code, String name) throws Exception {
        String response = mockMvc.perform(post("/api/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"%s"}
                                """.formatted(code, name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private String login(String username, String password) throws Exception {
        return loginTokens(username, password).accessToken();
    }

    private Tokens loginTokens(String username, String password) throws Exception {
        Captcha captcha = captcha();
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","captchaId":"%s","captchaCode":"%s"}
                                """.formatted(username, password, captcha.captchaId(), captcha.captchaCode())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return new Tokens(root.get("accessToken").asText(), root.get("refreshToken").asText());
    }

    private Captcha captcha() throws Exception {
        String response = mockMvc.perform(get("/api/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captchaId").isNotEmpty())
                .andExpect(jsonPath("$.image").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return new Captcha(root.get("captchaId").asText(), root.get("captchaCode").asText());
    }

    private record Tokens(String accessToken, String refreshToken) {
    }

    private record Captcha(String captchaId, String captchaCode) {
    }
}
