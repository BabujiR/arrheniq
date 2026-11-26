package com.arrheniq.masterdata;

import com.arrheniq.kernel.security.RequirePermission;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Array;
import java.util.Arrays;
import java.util.List;

/** The ICH time-point templates every tenant has (URS-MD-007). Read: {@code masterdata.view}. */
@Service
public class ProtocolTemplateService {

    private final JdbcClient jdbc;

    public ProtocolTemplateService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @RequirePermission("masterdata.view")
    @Transactional(readOnly = true)
    public List<ProtocolTemplate> templates() {
        return jdbc.sql("""
                SELECT t.code, t.name, t.study_type, t.condition_code, t.timepoint_unit, t.timepoints, t.source,
                       COALESCE(t.note, '') AS note, COALESCE(v.label, t.condition_code) AS condition_label
                  FROM platform.ref_protocol_template t
                  LEFT JOIN app.dictionary d ON d.code = 'STORAGE_CONDITION' AND d.deleted_at IS NULL
                  LEFT JOIN app.dictionary_value v ON v.tenant_id = d.tenant_id AND v.dictionary_id = d.id
                                                   AND v.code = t.condition_code AND v.site_id IS NULL
                                                   AND v.deleted_at IS NULL
                 ORDER BY t.study_type, t.code
                """)
            .query((rs, _) -> new ProtocolTemplate(rs.getString("code"), rs.getString("name"),
                    rs.getString("study_type"), rs.getString("condition_code"), rs.getString("condition_label"),
                    rs.getString("timepoint_unit"), timepoints(rs.getArray("timepoints")), rs.getString("source"),
                    rs.getString("note")))
            .list();
    }

    private static List<BigDecimal> timepoints(Array array) throws java.sql.SQLException {
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).map(v -> (BigDecimal) v).toList();
    }
}
