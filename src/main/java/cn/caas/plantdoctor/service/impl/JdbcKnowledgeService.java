package cn.caas.plantdoctor.service.impl;

import cn.caas.plantdoctor.service.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Knowledge lookup against the local database.
 *
 * <p>The table {@code caas_diseases_pests_agridata} keeps the column names used by
 * the production knowledge platform. Column labels are converted to camel case on
 * the way out, so the JSON contract seen by clients is unchanged.
 *
 * <p>Matching is exact on the canonical Chinese name first and falls back to the
 * alias column, because recognition providers do not all use the canonical name.
 * Fuzzy or embedding-based matching is deliberately out of scope: the paper states
 * that linkage is a name-based lookup, and over-claiming here would misrepresent
 * the system.
 */
@Service
public class JdbcKnowledgeService implements KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(JdbcKnowledgeService.class);

    private static final String LOOKUP_SQL = """
            SELECT * FROM caas_diseases_pests_agridata
             WHERE zh_name = ?
                OR zh_alias_name LIKE ?
             ORDER BY CASE WHEN zh_name = ? THEN 0 ELSE 1 END
             LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> findByInsectName(String insectName) {
        if (insectName == null || insectName.isBlank()) {
            return Map.of();
        }
        String needle = insectName.trim();
        List<Map<String, Object>> rows = jdbcTemplate.query(
                LOOKUP_SQL,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    var meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String label = meta.getColumnLabel(i);
                        Object value = rs.getObject(i);
                        if (value != null) {
                            row.put(toCamelCase(label), value);
                        }
                    }
                    return row;
                },
                needle, "%" + needle + "%", needle);

        if (rows.isEmpty()) {
            log.debug("No knowledge record matched label '{}'", needle);
            return Map.of();
        }
        return rows.get(0);
    }

    /**
     * Converts a database column label such as {@code hazard_symptoms} into the
     * {@code hazardSymptoms} form used by the JSON contract.
     */
    static String toCamelCase(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        boolean upperNext = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == '_') {
                upperNext = true;
                continue;
            }
            sb.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return sb.toString();
    }
}
