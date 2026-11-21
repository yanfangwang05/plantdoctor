package cn.caas.plantdoctor.repository;

import cn.caas.plantdoctor.domain.InsectCropType;
import cn.caas.plantdoctor.domain.RecognitionResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence of diagnosis requests.
 *
 * <p>Every request is logged, including the ones that failed and the ones whose
 * label found no knowledge record. Keeping failures is what allows the system to
 * answer questions such as "which labels are not covered by the knowledge base",
 * a question reviewers of software papers routinely ask.
 */
@Repository
public class AccessLogRepository {

    private static final String INSERT_SQL = """
            INSERT INTO plantdoctor_access_log
                (log_id, created_at, crop_type, image_name, provider,
                 insect_name, trust_level, knowledge_matched, request_param, response_text)
            VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String RECENT_SQL = """
            SELECT log_id, created_at, crop_type, image_name, provider,
                   insect_name, trust_level, knowledge_matched
              FROM plantdoctor_access_log
             ORDER BY created_at DESC, log_id DESC
             LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public AccessLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @return identifier of the stored entry
     */
    public String save(InsectCropType type, String imageName, RecognitionResult result,
                       boolean knowledgeMatched) {
        String logId = UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(INSERT_SQL,
                logId,
                type == null ? null : type.getKey(),
                imageName,
                result.getProvider(),
                result.getInsectName(),
                result.getTrustLevel(),
                knowledgeMatched,
                truncate(result.getRequestParam(), 1900),
                truncate(result.getOriginalText(), 3900));
        return logId;
    }

    /**
     * Recent requests in the same camel-case shape as the knowledge payload, so
     * that clients do not have to know the column names. Columns are read by name
     * rather than by position, since H2 and MySQL do not agree on identifier case.
     */
    public List<Map<String, Object>> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.query(RECENT_SQL, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("logId", rs.getString("log_id"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            row.put("createdAt", createdAt == null ? null : createdAt.toLocalDateTime().toString());
            row.put("cropType", rs.getString("crop_type"));
            row.put("imageName", rs.getString("image_name"));
            row.put("provider", rs.getString("provider"));
            row.put("insectName", rs.getString("insect_name"));
            double trustLevel = rs.getDouble("trust_level");
            row.put("trustLevel", rs.wasNull() ? null : trustLevel);
            row.put("knowledgeMatched", rs.getBoolean("knowledge_matched"));
            return row;
        }, safeLimit);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
