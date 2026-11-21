package cn.caas.plantdoctor.service;

import java.util.Map;

/**
 * Knowledge linkage: given a label returned by a recognition provider, retrieve
 * the structured record (symptoms, causes, control measures, ...) that explains
 * it.
 *
 * <p>In the production deployment this step is a remote call to the AIKS
 * knowledge platform. Here it is a local lookup so the workflow can run in
 * isolation; the returned field names are identical, which is what keeps the rest
 * of the application unchanged.
 */
public interface KnowledgeService {

    /**
     * @param insectName label returned by the recognition provider
     * @return camel-case knowledge fields, or an empty map when no record matches.
     *         An empty map is a normal outcome and is reported as "no knowledge
     *         record" rather than as an error.
     */
    Map<String, Object> findByInsectName(String insectName);
}
