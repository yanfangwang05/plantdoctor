package cn.caas.plantdoctor.domain;

/**
 * Outcome of a single call to a recognition provider.
 *
 * <p>Mirrors the production value object: a provider may fail (no credentials,
 * timeout, unknown label) and the caller must be able to surface that failure to
 * the user instead of returning a half-filled diagnosis.
 */
public class RecognitionResult {

    private boolean success;
    private String message;
    private String insectName;
    private String part;
    private Double trustLevel;
    private String requestParam;
    private String originalText;
    private String provider;

    public static RecognitionResult failure(String message, String provider) {
        RecognitionResult result = new RecognitionResult();
        result.success = false;
        result.message = message;
        result.provider = provider;
        return result;
    }

    public static RecognitionResult success(String insectName, Double trustLevel, String provider) {
        RecognitionResult result = new RecognitionResult();
        result.success = true;
        result.insectName = insectName;
        result.trustLevel = trustLevel;
        result.provider = provider;
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getInsectName() {
        return insectName;
    }

    public void setInsectName(String insectName) {
        this.insectName = insectName;
    }

    public String getPart() {
        return part;
    }

    public void setPart(String part) {
        this.part = part;
    }

    public Double getTrustLevel() {
        return trustLevel;
    }

    public void setTrustLevel(Double trustLevel) {
        this.trustLevel = trustLevel;
    }

    /** Raw request parameters, kept for audit and for the diagnosis log. */
    public String getRequestParam() {
        return requestParam;
    }

    public void setRequestParam(String requestParam) {
        this.requestParam = requestParam;
    }

    /** Raw provider payload, kept for audit and for the diagnosis log. */
    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
