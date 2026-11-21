package cn.caas.plantdoctor.domain;

/**
 * Envelope used by every HTTP endpoint, so that clients can distinguish a
 * rejected request from a successful one without inspecting status codes only.
 *
 * @param success {@code true} when the request produced a usable answer
 * @param message operator-readable reason, {@code null} on success
 * @param data    payload, {@code null} on failure
 */
public record ApiResult<T>(boolean success, String message, T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(true, null, data);
    }

    public static <T> ApiResult<T> fail(String message) {
        return new ApiResult<>(false, message, null);
    }
}
