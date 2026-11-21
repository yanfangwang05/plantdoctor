package cn.caas.plantdoctor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration of PlantDoctor.
 *
 * <p>No credential has a usable default: when a value is missing the affected
 * provider reports a configuration error at call time instead of silently
 * degrading. This is deliberate - it makes a misconfigured deployment obvious
 * instead of producing plausible-looking but wrong diagnoses.
 */
@ConfigurationProperties(prefix = "plantdoctor")
public class PlantDoctorProperties {

    /** Directory that receives uploaded images. */
    private String uploadDir = "./uploads";

    /**
     * Public base URL under which uploaded images are reachable. Required when the
     * recognition provider fetches images by URL instead of by upload.
     */
    private String publicBaseUrl = "";

    private final Recognition recognition = new Recognition();

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public Recognition getRecognition() {
        return recognition;
    }

    public static class Recognition {

        /** {@code mock} (bundled fixtures) or {@code remote} (external API). */
        private String mode = "mock";

        /** HTTP timeout for provider calls, in seconds. */
        private int timeoutSeconds = 30;

        private final Remote remote = new Remote();
        private final CornSoy cornSoy = new CornSoy();

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public Remote getRemote() {
            return remote;
        }

        public CornSoy getCornSoy() {
            return cornSoy;
        }
    }

    /**
     * Credentials for the general prediction endpoint. Issued by the vendor under
     * a commercial agreement; they are never distributed with this repository.
     */
    public static class Remote {

        private String baseUrl = "";
        private String tokenPath = "";
        private String predictPath = "";
        private String clientId = "";
        private String clientSecret = "";

        public boolean isConfigured() {
            return !baseUrl.isBlank() && !tokenPath.isBlank() && !predictPath.isBlank()
                    && !clientId.isBlank() && !clientSecret.isBlank();
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getTokenPath() {
            return tokenPath;
        }

        public void setTokenPath(String tokenPath) {
            this.tokenPath = tokenPath;
        }

        public String getPredictPath() {
            return predictPath;
        }

        public void setPredictPath(String predictPath) {
            this.predictPath = predictPath;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }
    }

    /**
     * Dedicated endpoint used for corn and soybean. The {@code license} string is
     * bound to the institution that obtained access to the service.
     */
    public static class CornSoy {

        private String url = "";
        private String license = "";

        public boolean isConfigured() {
            return !url.isBlank() && !license.isBlank();
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getLicense() {
            return license;
        }

        public void setLicense(String license) {
            this.license = license;
        }
    }
}
