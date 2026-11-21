package cn.caas.plantdoctor.service;

import cn.caas.plantdoctor.domain.InsectCropType;
import cn.caas.plantdoctor.domain.RecognitionResult;

import java.nio.file.Path;

/**
 * A recognition provider.
 *
 * <p>The interface is intentionally narrow: PlantDoctor owns the routing decision
 * ({@link InsectCropType}) and the knowledge linkage, while a provider only maps
 * one image to one label. Implementations are selected by configuration, not by
 * code change, which is what makes the recognition layer pluggable.
 */
public interface RecognitionService {

    /** Stable identifier recorded in the diagnosis log. */
    String providerId();

    /**
     * @param imagePath        image on local disk
     * @param originalFilename name the image was uploaded under, or {@code null}.
     *                         Only a provider that answers from fixtures uses it;
     *                         it is never forwarded to a third party.
     * @param type             diagnostic category chosen by the user
     * @return a success result carrying the label and confidence, or a failure
     *         result carrying an operator-readable reason. Never {@code null}.
     */
    RecognitionResult recognize(Path imagePath, String originalFilename, InsectCropType type);
}
