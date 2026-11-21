package cn.caas.plantdoctor.service.impl;

import cn.caas.plantdoctor.domain.InsectCropType;
import cn.caas.plantdoctor.domain.RecognitionResult;
import cn.caas.plantdoctor.service.RecognitionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Offline recognition provider backed by bundled fixtures.
 *
 * <p><b>Purpose.</b> The commercial recognition services cannot be redistributed
 * and require credentials issued under a commercial agreement. This provider lets
 * an independent researcher reproduce the complete diagnostic workflow - upload,
 * routing, label resolution, knowledge linkage, log persistence - without them.
 *
 * <p><b>It is not a model.</b> The labels returned here are fixed fixtures, not
 * predictions. Accuracy figures reported in the paper were measured with the real
 * providers in {@code remote} mode, and must never be derived from this class.
 *
 * <p>Lookup order: image SHA-256, then file name, then the per-category default.
 * Fixtures live in {@code src/main/resources/mock/recognition.json}.
 */
@Service
@ConditionalOnProperty(prefix = "plantdoctor.recognition", name = "mode",
        havingValue = "mock", matchIfMissing = true)
public class MockRecognitionService implements RecognitionService {

    public static final String PROVIDER_ID = "mock";

    private static final Logger log = LoggerFactory.getLogger(MockRecognitionService.class);

    private final ObjectMapper objectMapper;
    private JsonNode bySha256;
    private JsonNode byFileName;
    private JsonNode defaultByCropType;

    public MockRecognitionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadFixtures() {
        ClassPathResource resource = new ClassPathResource("mock/recognition.json");
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            bySha256 = root.path("bySha256");
            byFileName = root.path("byFileName");
            defaultByCropType = root.path("defaultByCropType");
            log.info("Mock recognition fixtures loaded: {} by hash, {} by file name, {} category defaults",
                    bySha256.size(), byFileName.size(), defaultByCropType.size());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to load mock/recognition.json; run with -Dplantdoctor.recognition.mode=remote "
                            + "to use the real provider instead", e);
        }
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public RecognitionResult recognize(Path imagePath, String originalFilename, InsectCropType type) {
        if (type == null || !type.isSelectable()) {
            return RecognitionResult.failure("Unsupported diagnostic category", PROVIDER_ID);
        }

        JsonNode fixture = lookupByHash(imagePath);
        if (fixture == null) {
            String fixtureName = fixtureName(originalFilename, imagePath);
            fixture = byFileName.path(fixtureName);
            if (!fixture.isMissingNode() && !fixture.isNull()) {
                log.debug("Mock fixture matched by file name: {}", fixtureName);
            } else {
                fixture = null;
            }
        }
        if (fixture == null) {
            fixture = defaultByCropType.path(type.getKey());
            if (!fixture.isMissingNode() && !fixture.isNull()) {
                log.debug("Mock fixture falls back to category default for {}", type.getKey());
            } else {
                fixture = null;
            }
        }
        if (fixture == null) {
            return RecognitionResult.failure(
                    "No mock fixture for this image or category; add an entry to mock/recognition.json",
                    PROVIDER_ID);
        }

        RecognitionResult result = RecognitionResult.success(
                fixture.path("insectName").asText(null),
                fixture.hasNonNull("trustLevel") ? fixture.get("trustLevel").asDouble() : null,
                PROVIDER_ID);
        result.setPart(fixture.path("part").asText(null));
        result.setRequestParam("{\"mode\":\"mock\",\"cropType\":\"" + type.getKey() + "\"}");
        result.setOriginalText(fixture.toString());
        return result;
    }

    /**
     * The stored image is renamed to a random identifier, so the name the user
     * uploaded is the only stable key for a fixture. Browsers may send a full
     * path, hence the reduction to the last segment.
     */
    private static String fixtureName(String originalFilename, Path imagePath) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return imagePath.getFileName().toString();
        }
        String name = originalFilename;
        int cut = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (cut >= 0) {
            name = name.substring(cut + 1);
        }
        return name;
    }

    private JsonNode lookupByHash(Path imagePath) {
        String hash = sha256(imagePath);
        if (hash == null) {
            return null;
        }
        JsonNode node = bySha256.path(hash);
        return node.isMissingNode() || node.isNull() ? null : node;
    }

    private String sha256(Path imagePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(Files.readAllBytes(imagePath));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            log.warn("Unable to hash {}: {}", imagePath, e.getMessage());
            return null;
        }
    }
}
