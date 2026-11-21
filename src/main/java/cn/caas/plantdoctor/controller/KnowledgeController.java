package cn.caas.plantdoctor.controller;

import cn.caas.plantdoctor.domain.ApiResult;
import cn.caas.plantdoctor.service.KnowledgeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Direct access to the knowledge layer, independent of recognition.
 *
 * <p>Exposed separately on purpose: it lets a user look up a pest or disease by
 * name when the automatic identification is wrong or unavailable, and it lets the
 * linkage layer be evaluated on its own (knowledge retrieval hit rate).
 */
@RestController
@RequestMapping("/api")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/knowledge")
    public ApiResult<Map<String, Object>> lookup(@RequestParam("name") String name) {
        Map<String, Object> data = knowledgeService.findByInsectName(name);
        if (data.isEmpty()) {
            return ApiResult.fail("No knowledge record found for '" + name + "'");
        }
        return ApiResult.ok(data);
    }
}
