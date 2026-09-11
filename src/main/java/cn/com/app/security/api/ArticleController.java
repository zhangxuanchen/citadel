package cn.com.app.security.api;

import cn.com.app.security.logging.OperationLog;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    @GetMapping
    @PreAuthorize("hasAuthority('ARTICLE_READ')")
    @OperationLog(module = "article", operation = "list_articles")
    public List<Map<String, String>> listArticles() {
        return List.of(
                Map.of("id", "A-1001", "title", "Spring Security authorization guide"),
                Map.of("id", "A-1002", "title", "RBAC permission design")
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ARTICLE_WRITE')")
    @OperationLog(module = "article", operation = "create_article")
    public Map<String, String> createArticle() {
        return Map.of("status", "created");
    }
}
