package cn.caas.plantdoctor.controller;

import cn.caas.plantdoctor.domain.ApiResult;
import cn.caas.plantdoctor.domain.InsectCropType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Metadata endpoints.
 *
 * <p>{@code /api/crop-types} returns the routing table of Table 1 in machine
 * readable form, so the table in the paper can be checked against the code
 * without reading Java sources.
 */
@RestController
@RequestMapping("/api")
public class MetaController {

    @Value("${plantdoctor.recognition.mode:mock}")
    private String recognitionMode;

    @GetMapping("/crop-types")
    public ApiResult<List<CategoryView>> cropTypes() {
        List<CategoryView> views = InsectCropType.selectable().stream()
                .map(t -> new CategoryView(
                        t.getCode(),
                        t.getKey(),
                        t.getDisplayName(),
                        t.getDisplayNameZh(),
                        t.isDedicatedPath() ? "dedicated" : "general",
                        t.isDedicatedPath() ? t.getDedicatedImageType() : t.getVendorCropId()))
                .toList();
        return ApiResult.ok(views);
    }

    @GetMapping("/meta")
    public ApiResult<MetaView> meta() {
        return ApiResult.ok(new MetaView("1.0.0", recognitionMode,
                InsectCropType.selectable().size()));
    }

    /**
     * @param code           internal identifier used by the web client
     * @param key            stable upper-case key
     * @param displayName    English label
     * @param displayNameZh  Chinese label
     * @param path           {@code general} or {@code dedicated}
     * @param providerCode   identifier sent to the provider ({@code crop_id} or
     *                       {@code imageType})
     */
    public record CategoryView(int code, String key, String displayName, String displayNameZh,
                               String path, String providerCode) {
    }

    public record MetaView(String version, String recognitionMode, int selectableCategories) {
    }
}
