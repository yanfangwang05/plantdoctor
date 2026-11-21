package cn.caas.plantdoctor.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Diagnostic categories supported by PlantDoctor.
 *
 * <p>The enumeration is the single source of truth for the routing table reported
 * in Table 1 of the paper. Ten categories are selectable by the user
 * ({@link #selectable()}); {@link #UNSUPPORTED} is a sentinel used for input
 * validation only.
 *
 * <p>Two recognition paths exist:
 * <ul>
 *   <li><b>general path</b> - apple, citrus, grape, rice, wheat, insect pest,
 *       fall armyworm and grass weed use the vendor's general prediction
 *       endpoint, addressed by {@code cropId};</li>
 *   <li><b>dedicated path</b> - corn and soybean use a separate endpoint that
 *       expects an {@code imageType} parameter instead, because the general
 *       endpoint has no coverage for these two crops.</li>
 * </ul>
 * Keep this class in sync with the third-party contract described in
 * {@code docs/AI_SERVICE.md}.
 */
public enum InsectCropType {

    APPLE(1, "APPLE", "Apple", "苹果", "123", null),
    CITRUS(2, "CITRUS", "Citrus", "柑橘", "1", null),
    GRAPE(3, "GRAPE", "Grape", "葡萄", "127", null),
    RICE(4, "RICE", "Rice", "水稻", "132", null),
    WHEAT(5, "WHEAT", "Wheat", "小麦", "131", null),
    INSECT(6, "INSECT", "Insect pest", "虫体", "143", null),
    YEE(7, "YEE", "Fall armyworm", "草地贪夜蛾", "142", null),
    GRASS(8, "GRASS", "Grass weed", "杂草", "2", null),
    SOYBEAN(9, "SOYBEAN", "Soybean", "大豆", null, "15"),
    CORN(10, "CORN", "Corn", "玉米", null, "14"),
    UNSUPPORTED(0, "NO", "Unsupported", "不支持类型", null, null);

    private final int code;
    private final String key;
    private final String displayName;
    private final String displayNameZh;
    private final String vendorCropId;
    private final String dedicatedImageType;

    InsectCropType(int code, String key, String displayName, String displayNameZh,
                   String vendorCropId, String dedicatedImageType) {
        this.code = code;
        this.key = key;
        this.displayName = displayName;
        this.displayNameZh = displayNameZh;
        this.vendorCropId = vendorCropId;
        this.dedicatedImageType = dedicatedImageType;
    }

    public int getCode() {
        return code;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDisplayNameZh() {
        return displayNameZh;
    }

    /**
     * Identifier expected by the general prediction endpoint, or {@code null}
     * when the category is served by the dedicated endpoint instead.
     */
    public String getVendorCropId() {
        return vendorCropId;
    }

    /**
     * Identifier expected by the dedicated endpoint (corn and soybean only).
     */
    public String getDedicatedImageType() {
        return dedicatedImageType;
    }

    /**
     * Corn and soybean are routed to the dedicated endpoint.
     */
    public boolean isDedicatedPath() {
        return this == CORN || this == SOYBEAN;
    }

    public boolean isSelectable() {
        return this != UNSUPPORTED;
    }

    public static List<InsectCropType> selectable() {
        return Arrays.stream(values()).filter(InsectCropType::isSelectable).toList();
    }

    /**
     * Resolves a category from its numeric code, its upper-case key
     * (e.g. {@code "WHEAT"}) or its display name (e.g. {@code "Wheat"}).
     * Returns {@link #UNSUPPORTED} for anything that cannot be resolved, so that
     * callers can answer with a validation error instead of throwing.
     */
    public static InsectCropType resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNSUPPORTED;
        }
        String value = raw.trim();
        try {
            return codeOf(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            // fall through to name lookup
        }
        Optional<InsectCropType> byKey = Arrays.stream(values())
                .filter(t -> t.key.equalsIgnoreCase(value))
                .findFirst();
        if (byKey.isPresent()) {
            return byKey.get();
        }
        return Arrays.stream(values())
                .filter(t -> t.displayName.equalsIgnoreCase(value))
                .findFirst()
                .orElse(UNSUPPORTED);
    }

    public static InsectCropType codeOf(Integer code) {
        if (code == null) {
            return UNSUPPORTED;
        }
        for (InsectCropType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return UNSUPPORTED;
    }
}
