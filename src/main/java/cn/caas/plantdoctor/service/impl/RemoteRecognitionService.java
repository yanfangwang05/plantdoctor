package cn.caas.plantdoctor.service.impl;

import cn.caas.plantdoctor.config.PlantDoctorProperties;
import cn.caas.plantdoctor.domain.InsectCropType;
import cn.caas.plantdoctor.domain.RecognitionResult;
import cn.caas.plantdoctor.service.RecognitionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Recognition provider backed by an external commercial recognition API.
 *
 * <p>Two contracts are involved, mirroring the production implementation:
 * <ol>
 *   <li><b>general contract</b> - an OAuth-style token is obtained from
 *       {@code tokenPath}, cached for 23 hours, and then used to submit
 *       {@code crop_id} + {@code image_url} to {@code predictPath}; the label is
 *       returned in {@code content.result} together with {@code content.score};</li>
 *   <li><b>dedicated contract</b> - corn and soybean are uploaded as a
 *       {@code multipart/form-data} request carrying {@code imageType},
 *       {@code imageFile} and an institutional {@code license}; the label is
 *       returned in {@code returnResult.returnString}.</li>
 * </ol>
 *
 * <p>All endpoints and credentials come from {@link PlantDoctorProperties}. When
 * they are absent the provider answers with an explicit configuration error, so a
 * misconfigured deployment never looks like a low-confidence diagnosis.
 */
@Service
@ConditionalOnProperty(prefix = "plantdoctor.recognition", name = "mode", havingValue = "remote")
public class RemoteRecognitionService implements RecognitionService {

    public static final String PROVIDER_ID = "remote";

    private static final Logger log = LoggerFactory.getLogger(RemoteRecognitionService.class);

    /** Tokens are valid for up to 24 hours; refresh slightly earlier. */
    private static final Duration TOKEN_TTL = Duration.ofHours(23);

    private final PlantDoctorProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    private volatile CachedToken cachedToken;

    public RemoteRecognitionService(PlantDoctorProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        Duration timeout = Duration.ofSeconds(
                Math.max(1, properties.getRecognition().getTimeoutSeconds()));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public RecognitionResult recognize(Path imagePath, String originalFilename, InsectCropType type) {
        // originalFilename is unused: the general contract sends a public URL and the
        // dedicated contract sends the bytes under a regenerated multipart name.
        if (type == null || !type.isSelectable()) {
            return RecognitionResult.failure("Unsupported diagnostic category", PROVIDER_ID);
        }
        if (type.isDedicatedPath()) {
            return recognizeViaDedicatedEndpoint(imagePath, type);
        }
        return recognizeViaGeneralEndpoint(imagePath, type);
    }

    // ------------------------------------------------------------------
    // General contract (8 of the 10 categories)
    // ------------------------------------------------------------------

    private RecognitionResult recognizeViaGeneralEndpoint(Path imagePath, InsectCropType type) {
        PlantDoctorProperties.Remote cfg = properties.getRecognition().getRemote();
        if (!cfg.isConfigured()) {
            return RecognitionResult.failure(
                    "Recognition service is not configured: set RECOGNITION_BASE_URL, RECOGNITION_TOKEN_PATH, "
                            + "RECOGNITION_PREDICT_PATH, RECOGNITION_CLIENT_ID and RECOGNITION_CLIENT_SECRET, "
                            + "or run in mock mode", PROVIDER_ID);
        }
        if (properties.getPublicBaseUrl().isBlank()) {
            return RecognitionResult.failure(
                    "PLANTDOCTOR_PUBLIC_BASE_URL is required: the general contract fetches the image by URL",
                    PROVIDER_ID);
        }

        String imageUrl = stripTrailingSlash(properties.getPublicBaseUrl())
                + "/uploads/" + imagePath.getFileName();
        String vendorCropId = type.getVendorCropId();

        try {
            String authorization = obtainAuthorization(cfg);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("crop_id", vendorCropId);
            form.add("image_url", imageUrl);

            String body = restClient.post()
                    .uri(stripTrailingSlash(cfg.getBaseUrl()) + cfg.getPredictPath())
                    .header("Authorization", authorization)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            return parseGeneralResponse(body, type, vendorCropId, imageUrl);
        } catch (RuntimeException e) {
            log.error("General recognition call failed (category={}, image={})", type.getKey(), imageUrl, e);
            return RecognitionResult.failure(
                    "Recognition service call failed: " + e.getMessage(), PROVIDER_ID);
        }
    }

    private RecognitionResult parseGeneralResponse(String body, InsectCropType type,
                                                   String vendorCropId, String imageUrl) {
        JsonNode root = readTree(body);
        if (root == null) {
            return RecognitionResult.failure("Recognition service returned a non-JSON payload", PROVIDER_ID);
        }
        if (!"1".equals(root.path("status").asText())) {
            return RecognitionResult.failure(
                    "Recognition service rejected the request: " + truncate(body), PROVIDER_ID);
        }
        JsonNode content = root.path("content");
        if (!content.hasNonNull("result")) {
            return RecognitionResult.failure(
                    "Recognition service returned no label: " + truncate(body), PROVIDER_ID);
        }
        RecognitionResult result = RecognitionResult.success(
                content.get("result").asText(),
                content.hasNonNull("score") ? content.get("score").asDouble() : null,
                PROVIDER_ID);
        result.setRequestParam("{\"crop_id\":\"" + vendorCropId + "\",\"image_url\":\"" + imageUrl + "\"}");
        result.setOriginalText(body);
        return result;
    }

    private synchronized String obtainAuthorization(PlantDoctorProperties.Remote cfg) {
        CachedToken current = cachedToken;
        if (current != null && current.expiresAt.isAfter(Instant.now())) {
            return current.headerValue;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", cfg.getClientId());
        form.add("client_secret", cfg.getClientSecret());

        String body = restClient.post()
                .uri(stripTrailingSlash(cfg.getBaseUrl()) + cfg.getTokenPath())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

        JsonNode root = readTree(body);
        if (root == null || root.path("code").asInt(-1) != 0) {
            throw new IllegalStateException("Unable to obtain an access token: " + truncate(body));
        }
        JsonNode data = root.path("data");
        String tokenType = data.path("token_type").asText("");
        String accessToken = data.path("access_token").asText("");
        if (accessToken.isBlank()) {
            throw new IllegalStateException("Access token missing from response: " + truncate(body));
        }
        String headerValue = (tokenType.isBlank() ? "Bearer" : tokenType) + " " + accessToken;
        cachedToken = new CachedToken(headerValue, Instant.now().plus(TOKEN_TTL));
        log.debug("Access token refreshed, valid until {}", cachedToken.expiresAt);
        return headerValue;
    }

    // ------------------------------------------------------------------
    // Dedicated contract (corn and soybean)
    // ------------------------------------------------------------------

    private RecognitionResult recognizeViaDedicatedEndpoint(Path imagePath, InsectCropType type) {
        PlantDoctorProperties.CornSoy cfg = properties.getRecognition().getCornSoy();
        if (!cfg.isConfigured()) {
            return RecognitionResult.failure(
                    "Dedicated recognition endpoint is not configured: set CORN_SOY_URL and "
                            + "CORN_SOY_LICENSE, or run in mock mode", PROVIDER_ID);
        }
        if (!Files.isReadable(imagePath)) {
            return RecognitionResult.failure("Uploaded image is not readable", PROVIDER_ID);
        }

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("imageType", type.getDedicatedImageType());
        form.add("imageFile", new FileSystemResource(imagePath));
        form.add("license", cfg.getLicense());

        try {
            String body = restClient.post()
                    .uri(cfg.getUrl())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            JsonNode root = readTree(body);
            if (root == null) {
                return RecognitionResult.failure("Recognition service returned a non-JSON payload", PROVIDER_ID);
            }
            JsonNode resultNode = root.path("returnResult");
            String status = resultNode.path("returnStatus").asText();
            String returnString = resultNode.path("returnString").asText("");
            if (!"1".equals(status) || returnString.isBlank()) {
                String reason = resultNode.path("returnMessage").asText("Unable to recognise this image");
                return RecognitionResult.failure(reason, PROVIDER_ID);
            }
            // The endpoint returns "label,extra"; only the first field is the label.
            String label = returnString.split(",")[0].trim();
            RecognitionResult result = RecognitionResult.success(label, null, PROVIDER_ID);
            result.setRequestParam("{\"imageType\":\"" + type.getDedicatedImageType() + "\"}");
            result.setOriginalText(body);
            return result;
        } catch (RuntimeException e) {
            log.error("Dedicated recognition call failed (category={}, image={})",
                    type.getKey(), imagePath, e);
            return RecognitionResult.failure(
                    "Recognition service call failed: " + e.getMessage(), PROVIDER_ID);
        }
    }

    // ------------------------------------------------------------------

    private JsonNode readTree(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("Unable to parse recognition response: {}", truncate(body));
            return null;
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 300 ? value : value.substring(0, 300) + "...";
    }

    private static String stripTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record CachedToken(String headerValue, Instant expiresAt) {
    }
}
