package cn.caas.plantdoctor.domain;

import java.util.Map;

/**
 * Response returned by {@code POST /api/diagnosis}.
 *
 * <p>The structure matches the production endpoint: the recognition outcome
 * ({@code diagnosis}) and the linked knowledge record ({@code diseaseData}) are
 * kept separate, so a client can display the identified name even when the
 * knowledge lookup finds nothing.
 *
 * @param diagnosis recognition outcome; {@code insectName} is {@code null} when
 *                  the provider failed
 * @param diseaseData camel-case knowledge fields of the matched record, or an
 *                    empty map when no record matched
 * @param logId identifier of the persisted diagnosis log entry
 */
public record DiagnosisResponse(Diagnosis diagnosis,
                                Map<String, Object> diseaseData,
                                String logId) {

    /**
     * @param insectName identified pest or disease name
     * @param trustLevel provider confidence in the range 0..1, or {@code null}
     *                   when the provider does not report one
     * @param provider   identifier of the recognition provider that answered
     */
    public record Diagnosis(String insectName, Double trustLevel, String provider) {
    }
}
