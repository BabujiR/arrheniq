package com.arrheniq.masterdata;

import com.arrheniq.kernel.api.ApiException;
import com.arrheniq.kernel.audit.AuditEventDraft;
import com.arrheniq.kernel.audit.AuditService;
import com.arrheniq.kernel.audit.CanonicalJson;
import com.arrheniq.kernel.audit.FieldChange;
import com.arrheniq.kernel.audit.FieldDiff;
import com.arrheniq.kernel.audit.ReasonRequiredException;
import com.arrheniq.kernel.security.Access;
import com.arrheniq.kernel.security.CurrentUserHolder;
import com.arrheniq.kernel.security.RequirePermission;
import com.arrheniq.kernel.security.SiteScope;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Controlled lists (URS-MD-006) and the ICH starter set they include (URS-MD-007). Read: {@code masterdata.view};
 * write: {@code dictionary.manage} (site-specific values: at that site). Values are retired, not deleted, once in
 * use or when they come from the catalog; a value nobody uses can be deleted (logically, with a reason). Codes and
 * sites never change after creation, because records refer to values by code.
 */
@Service
public class DictionaryService {

    static final String RECORD_TYPE = "DICTIONARY_VALUE";

    private static final String VALUE_COLUMNS = """
            v.id, v.version, d.code AS dictionary_code, v.code, v.label, v.description, v.sort_order, v.site_id,
            v.attributes::text AS attributes, v.status, v.created_at, v.updated_at,
            EXISTS (SELECT 1 FROM platform.ref_dictionary_value r
                     WHERE r.dictionary_code = d.code AND r.code = v.code) AS from_catalog""";

    private static final RowMapper<DictionaryValue> VALUE = (rs, _) -> new DictionaryValue(
            rs.getObject("id", UUID.class), rs.getLong("version"), rs.getString("dictionary_code"),
            rs.getString("code"), rs.getString("label"), rs.getString("description"), rs.getInt("sort_order"),
            rs.getObject("site_id", UUID.class), rs.getString("attributes"), rs.getString("status"),
            rs.getBoolean("from_catalog"), false, rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            MasterDataSupport.instant(rs.getObject("updated_at", OffsetDateTime.class)));

    private record ListRef(UUID id, String code, boolean systemManaged) { }

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final Access access;

    public DictionaryService(JdbcClient jdbc, AuditService audit, Access access) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.access = access;
    }

    @RequirePermission("masterdata.view")
    @Transactional(readOnly = true)
    public List<Dictionary> lists() {
        return jdbc.sql("""
                SELECT d.id, d.code, d.name, d.category, d.system_managed,
                       count(v.id) FILTER (WHERE v.status = 'ACTIVE')  AS active_values,
                       count(v.id) FILTER (WHERE v.status = 'RETIRED') AS retired_values
                  FROM app.dictionary d
                  LEFT JOIN app.dictionary_value v ON v.tenant_id = d.tenant_id AND v.dictionary_id = d.id
                                                   AND v.deleted_at IS NULL
                 WHERE d.deleted_at IS NULL
                 GROUP BY d.id, d.code, d.name, d.category, d.system_managed
                 ORDER BY d.category, d.name
                """)
            .query((rs, _) -> new Dictionary(rs.getObject("id", UUID.class), rs.getString("code"),
                    rs.getString("name"), rs.getString("category"), rs.getBoolean("system_managed"),
                    rs.getInt("active_values"), rs.getInt("retired_values")))
            .list();
    }

    /**
     * Values of one list that the user may see: tenant-wide values, plus site values of sites where the user has
     * {@code masterdata.view}. Each value says whether it is in use.
     */
    @RequirePermission("masterdata.view")
    @Transactional(readOnly = true)
    public List<DictionaryValue> values(String dictionaryCode, @Nullable String status) {
        ListRef list = list(dictionaryCode);
        SiteScope scope = CurrentUserHolder.require().grants().scopeOf("masterdata.view");
        StringBuilder sql = new StringBuilder("SELECT " + VALUE_COLUMNS + """
                  FROM app.dictionary_value v
                  JOIN app.dictionary d ON d.tenant_id = v.tenant_id AND d.id = v.dictionary_id
                 WHERE v.dictionary_id = ? AND v.deleted_at IS NULL""");
        List<Object> params = new ArrayList<>();
        params.add(list.id());
        if (!scope.allSites()) {
            sql.append(" AND (v.site_id IS NULL OR v.site_id = ANY (CAST(? AS uuid[])))");
            params.add(scope.siteIds().stream().map(UUID::toString).toArray(String[]::new));
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND v.status = ?");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY v.sort_order, v.label");
        Set<String> used = usedCodes(list.code());
        return jdbc.sql(sql.toString()).params(params).query(VALUE).list().stream()
                .map(value -> value.withUsage(used.contains(value.code())))
                .toList();
    }

    @RequirePermission("dictionary.manage")
    @Transactional
    public DictionaryValue create(String dictionaryCode, DictionaryValueInput input) {
        ListRef list = editableList(dictionaryCode);
        DictionaryValueInput.Clean c = input.validate();
        requireManageAt(c.siteId());
        if (c.siteId() != null) {
            requireActiveSite(c.siteId());
        }
        boolean taken = jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM app.dictionary_value
                                WHERE dictionary_id = ? AND code = ? AND deleted_at IS NULL)
                """)
            .params(list.id(), c.code()).query(Boolean.class).single();
        if (taken) {
            throw ApiException.duplicate("code", c.code());
        }
        UUID id = UUID.randomUUID();
        audit.open("dictionary_value.create");
        jdbc.sql("""
                INSERT INTO app.dictionary_value (tenant_id, id, created_by, dictionary_id, site_id, code, label,
                                                  description, sort_order, attributes)
                VALUES (app.current_tenant(), ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """)
            // as a list: site and description may be NULL (JdbcClient's varargs are declared non-null)
            .params(Arrays.asList(id, CurrentUserHolder.require().userId(), list.id(), c.siteId(), c.code(),
                    c.label(), c.description(), c.sortOrder(), CanonicalJson.write(c.attributes())))
            .update();
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("CREATE", RECORD_TYPE, id, 0L,
                businessKey(list, c.code()), c.siteId(), FieldDiff.between(null, c.auditMap("ACTIVE")), null, null,
                null));
        return find(id, false);
    }

    /**
     * Changes label, description, sort order or attributes. A value in use needs a reason: the change shows on
     * every record that refers to it.
     */
    @RequirePermission("dictionary.manage")
    @Transactional
    public DictionaryValue update(String dictionaryCode, UUID id, long expectedVersion, DictionaryValueInput input,
                                  @Nullable String reason) {
        ListRef list = editableList(dictionaryCode);
        DictionaryValue current = lockedCurrent(list, id, expectedVersion);
        requireManageAt(current.siteId());
        if (!"ACTIVE".equals(current.status())) {
            throw ApiException.invalidState(current.status(), "EDIT");
        }
        // code and site are fixed: take them from the stored value, whatever the request says
        DictionaryValueInput.Clean before = cleanOf(current);
        // attributes not sent: keep the stored ones (e.g. temperature and humidity of an ICH condition)
        Map<String, @Nullable Object> attributes = input.attributes() != null
                ? input.attributes() : new LinkedHashMap<String, @Nullable Object>(before.attributes());
        DictionaryValueInput.Clean c = new DictionaryValueInput(current.code(), input.label(), input.description(),
                input.sortOrder(), current.siteId(), attributes).validate();
        List<FieldChange> changes = FieldDiff.between(before.auditMap(current.status()),
                c.auditMap(current.status()));
        if (changes.isEmpty()) {
            return withUsage(current);
        }
        boolean inUse = usedCodes(list.code()).contains(current.code());
        if (inUse && reason == null) {
            throw new ReasonRequiredException(RECORD_TYPE, "UPDATE", "IN_USE");
        }
        audit.open("dictionary_value.update");
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("UPDATE", RECORD_TYPE, id,
                current.version() + 1, businessKey(list, current.code()), current.siteId(), changes,
                current.status(), reason, null));
        int n = jdbc.sql("""
                UPDATE app.dictionary_value SET label = ?, description = ?, sort_order = ?,
                       attributes = CAST(? AS jsonb), version = version + 1, updated_at = clock_timestamp(),
                       updated_by = ?
                 WHERE id = ? AND version = ?
                """)
            // as a list: description may be NULL (JdbcClient's varargs are declared non-null)
            .params(Arrays.asList(c.label(), c.description(), c.sortOrder(), CanonicalJson.write(c.attributes()),
                    CurrentUserHolder.require().userId(), id, expectedVersion))
            .update();
        if (n != 1) {
            throw ApiException.versionConflict();
        }
        return find(id, false);
    }

    /** Retired values can't be chosen for new records; existing records keep them. Reason required. */
    @RequirePermission("dictionary.manage")
    @Transactional
    public DictionaryValue retire(String dictionaryCode, UUID id, long expectedVersion, @Nullable String reason) {
        return setStatus(dictionaryCode, id, expectedVersion, "ACTIVE", "RETIRED", "RETIRE", reason);
    }

    @RequirePermission("dictionary.manage")
    @Transactional
    public DictionaryValue reactivate(String dictionaryCode, UUID id, long expectedVersion, @Nullable String reason) {
        return setStatus(dictionaryCode, id, expectedVersion, "RETIRED", "ACTIVE", "REACTIVATE", reason);
    }

    /**
     * Deletes (logically, with a reason) a value added by mistake. Not allowed for values from the catalog or in
     * use: those are retired instead (URS-MD-006).
     */
    @RequirePermission("dictionary.manage")
    @Transactional
    public void delete(String dictionaryCode, UUID id, long expectedVersion, @Nullable String reason) {
        ListRef list = editableList(dictionaryCode);
        DictionaryValue current = lockedCurrent(list, id, expectedVersion);
        requireManageAt(current.siteId());
        if (current.fromCatalog()) {
            throw new ApiException(409, "ARQ-MD-010",
                    "Values shipped with ArrheniQ can't be deleted; retire it instead", List.of());
        }
        if (usedCodes(list.code()).contains(current.code())) {
            throw new ApiException(409, "ARQ-MD-011",
                    "This value is used by existing records and can't be deleted; retire it instead", List.of());
        }
        audit.open("dictionary_value.delete");
        audit.record(MasterDataSupport.actor(), new AuditEventDraft("LOGICAL_DELETE", RECORD_TYPE, id,
                current.version() + 1, businessKey(list, current.code()), current.siteId(),
                List.of(new FieldChange("deleted", false, true)), current.status(), reason, null));
        jdbc.sql("""
                UPDATE app.dictionary_value SET deleted_at = clock_timestamp(), deleted_by = ?, delete_reason = ?,
                       version = version + 1
                 WHERE id = ? AND version = ?
                """)
            .params(CurrentUserHolder.require().userId(), reason == null ? "" : reason, id, expectedVersion)
            .update();
    }

    // ------------------------------------------------------------------ helpers

    private DictionaryValue setStatus(String dictionaryCode, UUID id, long expectedVersion, String from, String to,
                                      String action, @Nullable String reason) {
        ListRef list = editableList(dictionaryCode);
        DictionaryValue current = lockedCurrent(list, id, expectedVersion);
        requireManageAt(current.siteId());
        if (!from.equals(current.status())) {
            throw ApiException.invalidState(current.status(), action);
        }
        audit.open("dictionary_value." + action.toLowerCase(Locale.ROOT));
        audit.record(MasterDataSupport.actor(), new AuditEventDraft(action, RECORD_TYPE, id, current.version() + 1,
                businessKey(list, current.code()), current.siteId(), List.of(new FieldChange("status", from, to)),
                current.status(), reason, null));
        jdbc.sql("""
                UPDATE app.dictionary_value SET status = ?, version = version + 1, updated_at = clock_timestamp(),
                       updated_by = ?
                 WHERE id = ? AND version = ?
                """)
            .params(to, CurrentUserHolder.require().userId(), id, expectedVersion)
            .update();
        return find(id, false);
    }

    private ListRef list(String dictionaryCode) {
        return jdbc.sql("SELECT id, code, system_managed FROM app.dictionary WHERE code = ? AND deleted_at IS NULL")
                .param(Validation.normalizeCode(dictionaryCode))
                .query((rs, _) -> new ListRef(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getBoolean("system_managed")))
                .optional()
                .orElseThrow(() -> ApiException.notFound("List"));
    }

    private ListRef editableList(String dictionaryCode) {
        ListRef list = list(dictionaryCode);
        if (list.systemManaged()) {
            throw new ApiException(409, "ARQ-MD-012", "The values of this list are managed by ArrheniQ", List.of());
        }
        return list;
    }

    /** Tenant-wide values: dictionary.manage anywhere (like the test library); site values: at that site. */
    private void requireManageAt(@Nullable UUID siteId) {
        if (siteId != null) {
            access.requireAt("dictionary.manage", siteId);
        }
    }

    private Set<String> usedCodes(String dictionaryCode) {
        String sql = DictionaryUsage.usedCodesQuery(dictionaryCode);
        return sql.isEmpty() ? Set.of() : new HashSet<>(jdbc.sql(sql).query(String.class).list());
    }

    private DictionaryValue withUsage(DictionaryValue v) {
        return v.withUsage(usedCodes(v.dictionaryCode()).contains(v.code()));
    }

    private DictionaryValue lockedCurrent(ListRef list, UUID id, long expectedVersion) {
        DictionaryValue current = find(id, true);
        if (!current.dictionaryCode().equals(list.code())) {
            throw ApiException.notFound("List value");
        }
        if (current.version() != expectedVersion) {
            throw ApiException.versionConflict();
        }
        return current;
    }

    /** The value if it exists and the user may see it (tenant-wide or a visible site); otherwise 404. */
    private DictionaryValue find(UUID id, boolean forUpdate) {
        DictionaryValue v = jdbc.sql("SELECT " + VALUE_COLUMNS + """
                  FROM app.dictionary_value v
                  JOIN app.dictionary d ON d.tenant_id = v.tenant_id AND d.id = v.dictionary_id
                 WHERE v.id = ? AND v.deleted_at IS NULL""" + (forUpdate ? " FOR UPDATE OF v" : ""))
                .param(id)
                .query(VALUE)
                .optional()
                .orElseThrow(() -> ApiException.notFound("List value"));
        if (v.siteId() != null && !access.hasAt("masterdata.view", v.siteId())) {
            throw ApiException.notFound("List value");
        }
        return forUpdate ? v : withUsage(v);
    }

    private DictionaryValueInput.Clean cleanOf(DictionaryValue v) {
        return new DictionaryValueInput.Clean(v.code(), v.label(), v.description(), v.sortOrder(), v.siteId(),
                attributesOf(v));
    }

    /** Stored attributes as a map, for the field diff; the stored JSON was written by CanonicalJson. */
    private Map<String, Object> attributesOf(DictionaryValue v) {
        return MasterDataSupport.jsonObject(v.attributes());
    }

    private void requireActiveSite(UUID siteId) {
        boolean ok = jdbc.sql("SELECT EXISTS (SELECT 1 FROM app.site WHERE id = ? AND active AND deleted_at IS NULL)")
                .param(siteId).query(Boolean.class).single();
        if (!ok) {
            Validation v = new Validation();
            v.add("siteId", "UNKNOWN", "no active site with this id");
            v.throwIfAny();
        }
    }

    private static String businessKey(ListRef list, String code) {
        return list.code() + ":" + code;
    }
}
