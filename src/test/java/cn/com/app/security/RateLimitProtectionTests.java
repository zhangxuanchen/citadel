package cn.com.app.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "authz.security.rate-limit.max-requests=2",
        "authz.security.rate-limit.window=1m"
})
@AutoConfigureMockMvc
class RateLimitProtectionTests {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @Test
    void apiRequestsAreRateLimitedByClientIp() throws Exception {
        String clientIp = "203.0.113.77";

        mockMvc.perform(get("/api/auth/discovery")
                        .header("X-Forwarded-For", clientIp))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/discovery")
                        .header("X-Forwarded-For", clientIp))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/discovery")
                        .header("X-Forwarded-For", clientIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.message").value("Too many requests, please try again later"));
    }
}
