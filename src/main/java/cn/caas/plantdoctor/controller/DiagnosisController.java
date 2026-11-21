package cn.caas.plantdoctor.controller;

import cn.caas.plantdoctor.config.PlantDoctorProperties;
import cn.caas.plantdoctor.domain.ApiResult;
import cn.caas.plantdoctor.domain.DiagnosisResponse;
import cn.caas.plantdoctor.domain.InsectCropType;
import cn.caas.plantdoctor.domain.RecognitionResult;
import cn.caas.plantdoctor.repository.AccessLogRepository;
import cn.caas.plantdoctor.service.KnowledgeService;
import cn.caas.plantdoctor.service.RecognitionService;
import cn.caas.plantdoctor.util.FileStorageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The end-to-end diagnostic workflow:
 * upload - route by category - recognise - link knowledge - log.
 *
 * <p>This is the workflow described in Section 2 of the paper and illustrated in
 * Figure 1. Failures are answered as failures and still logged, so that the
 * proportion of successful, failed and unmatched diagnoses can be reported.
 */
@RestController
@RequestMapping("/api")
public class DiagnosisController {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisController.class);

    private final RecognitionService recognitionService;
    private final KnowledgeService knowledgeService;
    private final AccessLogRepository accessLogRepository;
    private final PlantDoctorProperties properties;

    public DiagnosisController(RecognitionService recognitionService,
                               KnowledgeService knowledgeService,
                               AccessLogRepository accessLogRepository,
                               PlantDoctorProperties properties) {
        this.recognitionService = recognitionService;
        this.knowledgeService = knowledgeService;
        this.accessLogRepository = accessLogRepository;
        this.properties = properties;
    }

    @PostMapping("/diagnosis")
    public ApiResult<DiagnosisResponse> diagnose(
            @RequestParam("file") MultipartFile file,
            @RequestParam("cropType") String cropType) {

        InsectCropType type = InsectCropType.resolve(cropType);
        if (!type.isSelectable()) {
            return ApiResult.fail("Unsupported diagnostic category: " + cropType);
        }

        String storedName;
        try {
            storedName = FileStorageUtil.store(file, properties.getUploadDir());
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(e.getMessage());
        }

        Path imagePath = Path.of(properties.getUploadDir()).resolve(storedName);
        RecognitionResult result = recognitionService.recognize(
                imagePath, file.getOriginalFilename(), type);

        if (!result.isSuccess()) {
            accessLogRepository.save(type, storedName, result, false);
            log.info("Diagnosis failed (category={}, provider={}): {}",
                    type.getKey(), result.getProvider(), result.getMessage());
            return ApiResult.fail(result.getMessage());
        }

        Map<String, Object> diseaseData = knowledgeService.findByInsectName(result.getInsectName());
        boolean matched = !diseaseData.isEmpty();
        String logId = accessLogRepository.save(type, storedName, result, matched);

        log.info("Diagnosis completed (category={}, label={}, knowledgeMatched={}, logId={})",
                type.getKey(), result.getInsectName(), matched, logId);

        return ApiResult.ok(new DiagnosisResponse(
                new DiagnosisResponse.Diagnosis(
                        result.getInsectName(), result.getTrustLevel(), result.getProvider()),
                diseaseData,
                logId));
    }

    /**
     * Recent diagnosis requests, including failures and unmatched labels.
     * Used by the demo page and by the evaluation scripts.
     */
    @GetMapping("/diagnosis/logs")
    public ApiResult<List<Map<String, Object>>> recentLogs(
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ApiResult.ok(accessLogRepository.recent(limit));
    }
}
