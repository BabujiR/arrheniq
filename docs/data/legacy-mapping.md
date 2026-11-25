# Legacy eSTAR → ArrheniQ Data Mapping

Mapping version: **1.0.0** (stored in `app.migration_run.mapping_version`).
Source: eSTAR Oracle schema (`EAPharmics/sql/install/eapharmics_objects.sql`, 43 tables). Target: ArrheniQ PostgreSQL 16 schema (`backend/app/src/main/resources/db/migration`).

Conventions:
- **→** direct copy · **T:** transform (rule given) · **D:** derived · **∅:** not migrated (reason given)
- Every migrated row gets `origin = 'ESTAR_MIGRATION'` and a `app.legacy_id_map` row (legacy table + key → new UUID).
- `INSERT_DATE/INSERT_USER/UPDATE_DATE/UPDATE_USER` → `created_at/created_by/updated_at/updated_by`. Legacy user numbers are resolved through the migrated `app_user`. Unresolvable users are recorded as `created_by = NULL`, and the original value is kept in the migrated audit event.
- Oracle `DATE` values (no time zone) are interpreted in the **site time zone** given in the migration config (ADR 0012). Calendar fields become `date`; instants become `timestamptz`.
- `AUDIT_ID` columns: ∅ (legacy session id, no meaning after migration).
- `COMMENTS` columns: kept, either in the target `comment`/`description` or as an `ANNOTATION` audit event when the target has no comment field.

## 1. Organisation, users, security
| Legacy | Target | Rules |
|---|---|---|
| `EA_LOCATIONS` | `app.site` | `LOCATION_NAME`→`name`; `code` = T: upper(substr(name,1,10)) unless mapping config gives codes; address fields → `address` JSON; `DEFAULT_LOCALE`→`locale`; `ACTIVE` Y/N → `active`; time zone from migration config |
| `EA_APP_USER` | `app.app_user` | `USER_NAME`→`username`; `FIRST_NAME`+`LAST_NAME`→`display_name`; `EMAIL`→`email`; `ACTIVE`/`TERMINATED` → `status` (TERMINATED or not active → DEACTIVATED); `DEFAULT_LOCATION_ID`→`default_site_id`. **∅ `PASSWORD`, `PREV_PASSWORD1..3`, `INVALID_LOGIN_COUNT`, `PASSWORD_EXPIRED`** (never migrated; users are linked to the IdP on first login by email; `idp_subject` = `pending:<username>` until then) |
| `EA_APP_ACCESS_TEMPLATE` | `app.role` | `TEMPLATE_NAME`→`name`, `code` = T: slug; `preset=false` |
| `EA_APP_TEMPLATE_MODULE`, `EA_APP_USER_MODULE_ACCESS` | `app.role_permission`, `app.user_site_role` | T: the 62 legacy modules are mapped to ArrheniQ permissions with the **module→permission table in §9**. Per-user module grants that don't match any template are converted into a generated role `LEGACY_<username>` (flagged for admin review in the reconciliation report) |
| `EA_APP_USER_LOCATIONS` | `app.user_site_role.site_id` | One assignment per (user, role, site) |
| `EA_APP_AREA`, `EA_APP_MODULE` | ∅ | Static legacy menu metadata; replaced by the permission catalog |
| `EA_APP_USER_AUDIT` | `audit.audit_event` | `action` = LOGIN_SUCCESS / LOGIN_FAILURE; `legacy = true`; `actor_type = MIGRATION`; original `LOGIN_TIME`, `REASON`, `LOGIN_SUCCESS` in `changes` |
| `EA_MODULE_ACCESS_AUDIT` | `audit.audit_event` | `action` = `LEGACY_MODULE_ACCESS`; module description + activity in `changes`; `legacy = true` |
| `EA_PRODUCT_REGISTRATION` | ∅ | Legacy license; replaced by `platform.entitlement` |

## 2. Configuration and dictionaries
| Legacy | Target | Rules |
|---|---|---|
| `EA_APPL_PARAMETER` (per location) | `app.tenant_setting` (site-level when it differs from the tenant default) | Parameter → setting key per **§6**. Secrets (`EMAIL ADMIN PASSWORD`, `DATA_LOCK_PASSWORD`) **∅, never migrated** (security-findings S-07) |
| `EA_DEFAULT_APPL_PARAMETER` | ∅ | Defaults replaced by provisioning defaults |
| `EA_DICTIONARY_MASTER` | `app.dictionary` | Matched to catalog dictionaries by `legacy_code` (V10); unknown legacy dictionaries are created as tenant dictionaries with `category = 'STABILITY'` |
| `EA_DICTIONARY_DETAIL` | `app.dictionary_value` | `DICTIONARY_VALUE`→`code` (trimmed), `DICTIONARY_VALUE_DESCRIPTION`→`label` (fallback: code), `DISPLAY_ORDER`→`sort_order`, `LOCATION_ID`→`site_id`, `ACTIVE`='N' → `status = 'RETIRED'`; `CUSTOM_FIELD_1/2`, `GRAPH_TO`, `VERSION` → `attributes` JSON (kept for traceability; `GRAPH_TO` is **not** used as poolability α, see BR-053) |
| `EA_DEFAULT_DICTIONARY_DETAIL` | ∅ | Replaced by the catalog |
| `EA_REPORT_COLUMN`, `EA_RPT_USER_PREFERENCE` | `app.report_preference` (best effort) | Only for reports that exist in ArrheniQ; others ∅ |
| `EA_STUDY_ID_MASTER` | `app.tenant_sequence` (`name='STUDY_CODE'`, `scope_key=<year>`) | `next_value` = max(`STUDY_ID`)+1 per year, so new studies never collide with migrated codes |
| `EA_REGR_TINV` | ∅ | Replaced by the statistics engine (ADR 0002) |

## 3. Master data
| Legacy | Target | Rules |
|---|---|---|
| `EA_PRD_TESTS` | `app.test_method` | `TEST_NAME`→`name`, `code` = T: slug(name) + suffix on duplicates; `METHOD`→`method_reference`; `LAB_NUMBER`→`lab_number`; `RESULTS_FORMAT` V/T → NUMERIC/TEXT; `NUM_OF_ENTRIES`→`replicates`; `DECIMAL_POINTS`→`decimals`; `MEASUREMENT`→`unit_code` (dictionary lookup); `LOWER/UPPER_LIMIT` VARCHAR → `default_lower/upper_limit` numeric (T: numeric parse; non-numeric → exception + kept in `default_text_limit`); `TEXT_LIMIT`→`default_text_limit`; `ALERT_*`→`default_alert_*`; `ALERT_*_VARIANCE`→`default_variance_*_pct`; `GRAPH` Y/N → `trendable` |
| `EA_PRD_PRODUCTS` | `app.product` + `app.specification` + `app.spec_version` | `PRODUCT_CODE`→`code`, `PRODUCT_NAME`→`name`, `PRODUCT_DESCRIPTION`→`description`, `PRODUCT_TYPE` → T: `product_type` (mapping config; default DRUG_PRODUCT); `EXPIRATION_PERIOD` free text → T: regex `^(\d+)\s*(month|months|m|year|years|y)` → value/unit (years × 12 → MONTH); "To be determined/Not established/Not applicable" → `expiration_status`; unparseable → exception. `ANNUAL_RPT_ANNIVERSARY_DATE`→`annual_report_anniversary`; `LOCATION_ID`→`site_id`. One `specification` per product (`spec_number` = product code); **one `spec_version` per product** with `version_no = VERSION`, status = EFFECTIVE if `APPROVAL_LEVEL2_YN='Y'` (or level 1 when single approval), otherwise DRAFT. Legacy approvals → `audit.e_signature` with `legacy = true` (see §8) |
| `EA_PRD_PRODUCT_TESTS` | `app.spec_test` | As test_method mapping, plus `TEST_NAME`→`display_name`, `SORT_ORDER_CURRENT`→`sort_order`; link to `test_method` by (name, method) match or a generated library entry. Limits VARCHAR → numeric (T as above) |
| `EA_PRD_PRODUCT_SPECS` | `app.spec_version.spec_text` | Lines ordered by `LINE_NUMBER`, joined with newline |
| `EA_PRD_PRODUCT_FORMULATION` | `app.product_formulation_line` | `LINE_NUMBER`→`line_no`, `PRODUCT_FORMULATION`→`ingredient` |
| `EA_PRD_PRODUCT_BATCHES` | `app.product_batch_size` | `BATCH_LINE_NUMBER`→`line_no`, `BATCH_SIZE`→`batch_size_text` |

## 4. Protocols
| Legacy | Target | Rules |
|---|---|---|
| `EA_PRD_PROTOCOLS` | `app.protocol` + `app.protocol_version` (v1) + `app.protocol_timepoint` | `PROTOCOL_NUMBER`→`protocol_number`, `PROTOCOL_NAME`→`name`; `INTERVAL_UNITS`→`unit` (M/W/D → MONTH/WEEK/DAY); `TEST_INTERVAL_VALUE1..20` → one `protocol_timepoint` per non-null value (`seq` = n−1, `value`); status APPROVED (legacy protocols in use); `study_type` = T from the condition role of studies using it (default LONG_TERM) |
| `EA_PRD_PROTOCOL_DETAILS` | `app.protocol_test` + `app.protocol_matrix_cell` | `TEST_ID`→`protocol_test.test_method_id` (via spec_test → test_method); `TEST_INTERVAL_USEDn`='Y' → `matrix_cell(required=true)` for time point n |
| `EA_PRD_INVENTORY` | `app.protocol_timepoint.units_planned`, `app.protocol_version.reserve_units`, `fill_unit_code` | `TEST_INTERVAL_INVENTORYn` → time point n; `EXTRA` → reserve; `FILL_UNITS`/`UNIT_OF_MEASUREMENT` → fill unit |

## 5. Studies, schedule, inventory, results
| Legacy | Target | Rules |
|---|---|---|
| `EA_PRD_PRODUCT_STUDY` + `EA_PRD_STUDY_CONDITIONS` (1:1 by `PRD_STUDY_BATCH_ID`) | `app.study` | `STUDY_ID`→`study_code` (unchanged); `PROPREITARY_STUDY_ID`→`proprietary_study_id`; `LOT_NUMBER`, `FORMULA_NUMBER`, `BATCH_SIZE`, `MFG_DATE`→`manufacture_date`, `PACKAGE_DATE`, `EXPIRY`→`expiry_date`; `ON_STABILITY_DATE` (fallback `INTIAL_TEST_DATE`) → `on_stability_date`; `STUDY_CONDITION`/`NORMAL_ENV_CONDITIONS_CODE`→`condition_code`; `CHALLENGE_CONDITIONS_CODE`→`challenge_condition_code`; `STUDY_REASON_CODE`→`reason_code`; `STUDY_LENGTH_CODE`→`study_length_code`; `STORAGE_LOCATION_CODE`→`storage_location_code`; `FILL_UNITS`/`UNIT_OF_MEASUREMENT`→`fill_*`; `TOTAL_VALUE`→`units_placed`; `EXTRA_VALUE`→`reserve_units`; `LABEL_ID`→`label_template_id` (mapped to the closest fixed template; original label kept in `custom_fields.legacy_label_id`); `LABEL_COLOR`→`label_color`; `STUDY_TEXT`→`study_text`; `USER_DEFINED_COLUMN_1..10` → `custom_fields` JSON with labels from the `USER DEFINED FIELDS` dictionary; `STUDY_STATUS` → status (COMPLETED → COMPLETED; approved and not completed → APPROVED; else DRAFT/IN_REVIEW); `spec_version_id` = the product's migrated spec version; `protocol_version_id` = migrated protocol v1 |
| Packaging columns of `EA_PRD_PRODUCT_STUDY` | `app.study_component` | 20 columns → one row per non-null component: `PACKAGE_SIZE_CODE`, `CONTAINER_CODE`(+`CONTAINER_MANUFACTURER_CODE`), `CONTAINER_RESIN_CODE`, `CLOSURE_CODE`(+mfr), `LINER_CODE`(+mfr), `INNERSEAL_CODE`, `DESICCANT_CODE`(+mfr), `FILLER_CODE`(+mfr), `BLISTER_CODE`, `LIDDING_FOIL_CODE`, `FORMING_FOIL_CODE`, `OTHER_CODE_1/2`(+mfr), `MANUFACTURING_SITE_CODE`, `PACKAGING_SITE_CODE` |
| `EA_PRD_STUDY_RAW_MATERIALS` | `app.study_raw_material` | `PRODUCT_FORMULATION_ID`→`formulation_line_id`; `PRODUCT_FORMULATION`→`material_name`; `LOT_NUMBER`; `MFG_SUPPLIER_CODE`→`supplier_code` |
| `EA_PRD_STUDY_ATTACHMENTS` | `app.study_attachment` + object storage | BLOB `ATTACHMENT` → uploaded to S3 with a server-generated key; `sha256` computed; `FILE_NAME`, `FILE_DESCRIPTION`; the legacy upload-directory files (UploadServlet) are also collected if available |
| `EA_PRD_STUDY_INTERVALS` + `EA_PRD_STUDY_SCHEDULES` (by `PRD_STUDY_INTERVAL_ID`) | `app.study_timepoint` | `INTERVAL_NUMBER`→`seq`; `INTERVAL_VALUE`→`value`; `UNITS` → unit; `INTERVAL_DATE`→`calculated_date` **and** `scheduled_date` (legacy stored only the adjusted date; `date_rule_applied = 'LEGACY'`); `SCHEDULE_STATUS` SCHEDULED/PENDING/COMPLETED/TERMINATED → SCHEDULED/IN_TESTING (if `DATE_PULLED` set)/COMPLETED/TERMINATED; `DATE_PULLED`→`pulled_on`; `PULLED_BY`→`pulled_by` (user lookup); `TEST_START_DATE`→`testing_started_on`; `TEST_END_DATE`→`testing_completed_on`; `PAST_INTERVAL`='Y' → `back_entered`; `LABEL_COUNT`→ label print history (∅ if no print job) |
| Pull/extra/destroy columns | `app.inventory_entry` (ledger reconstructed) | One `PLACE` entry per study (`units_placed + reserve`); one `PULL` entry per time point with `UNITS_PULLED` (date = `DATE_PULLED`); one `EXTRA_PULL` per `EXTRA_PULLED` (> 0) with comment "Migrated extra pull"; one `DESTROY` per `EA_STUDY_INV_DESTROYED` row. **Reconciliation check:** reconstructed balance ≥ 0, else exception |
| `EA_STUDY_INV_DESTROYED` | `app.inventory_entry` (`DESTROY`) | `UNITS_DESTROYED` → −quantity; `DESTROYED_DATE`→`performed_on`; `DESTROYED_BY`→`performed_by` |
| `EA_PRD_STUDY_TEST_RESULTS` | `app.test_result` + `app.result_replicate` | Key: (schedule → time point, `PRODUCT_TEST_ID` → spec_test). `TEST_INTERVAL_USED` Y/N → `required`; `ANALYST`→`analyst_code`; `REFERENCE`; `RESULT_DATE`→`test_date`; `RESULT_STATUS` (PENDING/APPROVED) + schedule approval flags → status (APPROVED / L1_APPROVED / SUBMITTED / PLANNED if no values); `RESULT_1..30` → `result_replicate` rows (`replicate_no` = n) with T: **value parsing** (§7); `RESULT_AVERAGE` → `legacy_average_text`; `reportable_*` **recomputed** by the ArrheniQ algorithm in parity mode, and any difference from `RESULT_AVERAGE` is listed in the reconciliation report; `FILL_UNITS_USED`/`USED_BY`/`USED_DATE` → `ANNOTATION` audit event |

## 6. Parameters (`EA_APPL_PARAMETER`) → settings
| Legacy parameter | Setting key | Transform |
|---|---|---|
| INVALID LOGIN COUNT | ∅ (IdP policy) | Reported to the admin for IdP configuration |
| PASSWORD VALID PERIOD | ∅ (IdP policy) | Same |
| SYSTEM LOCK OUT | `session.idle_lock_minutes` | number |
| SYSTEM LOG OFF | `session.logoff_minutes` | number |
| DATA LOCK OUT | `session.idle_lock_minutes` (if lower) | number |
| PASSWORD REQUIRED FOR EDIT | ∅ | Superseded by e-signature points (always on for approvals) |
| DAYS TO SAMPLE PULL | `notify.days_before_pull` | number |
| DAYS TO SAMPLE TEST CYCLE | `notify.days_after_pull_not_started` | number |
| DAYS TO COMPLETE SAMPLE TEST | `notify.days_after_pull_not_completed` | number |
| SPECIFICATION APPROVAL LEVEL | `approval.product`, `approval.specification` | N/S/D → NONE/SINGLE/DOUBLE |
| NEW STUDY APPROVAL LEVEL | `approval.study` | N/S → NONE/SINGLE |
| DATA APPROVAL LEVEL | `approval.results` | S/D → SINGLE/DOUBLE |
| SCHEDULE APPROVAL LEVEL | ∅ | Not used in the reviewed code |
| NOTIFY_SPEC/STUDY/DATA_APPROVAL_REQUEST | `approval.notify_on_request` | any Y → true |
| MOVE SATURDAY PULL DATE | `scheduling.saturday_rule` | L/F/M → LEAVE/MOVE_BEFORE/MOVE_AFTER |
| MOVE SUNDAY PULL DATE | `scheduling.sunday_rule` | L/F/M → LEAVE/MOVE_BEFORE/MOVE_AFTER |
| INVENTORY PULL DAYS BUFFER | `scheduling.pull_window_days` | number |
| INVENTORY EXTRAS ALLOWED | `inventory.extras_allowed` | Y/N |
| PARTIAL INVENTORY PULL ALLOWED | `inventory.partial_pull_allowed` | Y/N |
| LABEL_REQUIRED_FOR_STUDY | `labels.required_for_study_approval` | Y/N |
| DATA_ENTRY_REF_INFO_REQD | `results.reference_required` | Y/N |
| DEFAULT LATE DAYS OK LEVEL | `reports.late_days_ok` | number |
| COMPANY NAME | `company.name` | text |
| EMAIL HOST / PORT / ADMIN USER ID | ∅ | Platform email service replaces customer SMTP (Private: deployment config) |
| EMAIL ADMIN PASSWORD, DATA_LOCK_PASSWORD | **∅ never migrated** | Secrets (S-07) |
| DATA_LOCK | ∅ | Replaced by result locking + re-approval (URS-REV-003) |

## 7. Result value parsing (`RESULT_1..30`, VARCHAR)
| Raw value (trimmed) | numeric_value | qualifier | text_value | Notes |
|---|---|---|---|---|
| `98.7`, `-1.2`, `1.5E-3` | parsed | — | — | Negative values are possible in migrated data only if typed via other paths |
| `<0.05`, `< 0.05` | 0.05 | LT | — | |
| `>100` | 100 | GT | — | |
| `ND`, `N.D.`, `Not detected` | — | ND | — | Abbreviation dictionary lookup (case-insensitive) |
| `BQL`, `<LOQ`, `BLQ` | — | LOQ | — | |
| `NT` | — | NT | — | |
| Text test result (`RESULTS_FORMAT='T'`) | — | — | raw | QuickList codes resolved to labels |
| Anything else | — | — | raw | **Exception listed** in reconciliation; reviewed manually |
`raw_input` always keeps the exact legacy string.

## 8. Legacy approvals → signatures
Legacy approvals weren't Part 11 e-signatures (no re-authentication, no meaning). They are migrated as **historical approvals**:
- `audit.e_signature` with `legacy = true`, `meaning = 'LEGACY_APPROVAL_L1'` or `'LEGACY_APPROVAL_FINAL'`, `signer_name` from the user, `signed_at` = `APPROVED_LEVEL*_DATE`, `auth_time` = `signed_at`, `idp_issuer = 'estar-legacy'`, and `content_hash` = hash of the migrated record version.
- Reports show them as "Approved in eSTAR by … on …", distinct from ArrheniQ e-signatures.

## 9. Legacy module → permission mapping
| Legacy area / module (EA_APP_MODULE) | ArrheniQ permissions |
|---|---|
| Login New Studies: Study Login, Edit Study Login | `study.create` |
| Authorize | `study.approve` |
| Log Manipulation: Sample Scheduler, Pending Stability Requests/Inventory/Cycle Start/End, Inventory Status Report | `schedule.view`, `pull.perform` |
| Take Extra Inventory For Sample | `pull.extra` |
| Restore Inventory By Study Selection | `pull.reverse` |
| Data entry | `result.enter`, `result.import` |
| Level 1 Approval Data | `result.approve.l1` |
| Final Approval Data | `result.approve.final` |
| Approved Data Entry Edit | `result.edit_approved` |
| CDS Form, Destruction Form | `inventory.destroy` |
| Data Access Evaluation (Graphics Generator, Single Product By Year, Sample To Sample, Parameter Overlay, Expiration Single/Multiple) | `evaluation.run` |
| Study Results – OOS / OOT Report | `result.view`, `report.generate` |
| Print modules: reports, Summary/Analyst report generator, Database logs, C of A, Protocol report | `report.generate` |
| Label Manager / Label Request / Label Printing | `label.print` (+ `label.reprint`) |
| Stability Maintenance: View Product | `masterdata.view` |
| Edit Product, Test Library | `product.manage`, `spec.manage`, `test.manage` |
| Level1 Approval, Final Approval (product) | `spec.approve`, `product.approve` |
| Packaging Dictionary, Reason for Study, Study Condition, Site, Abbreviation, QuickList, User Defined Fields, Analyst, Storage Location, Lab Number, Measurement | `dictionary.manage` |
| Annual Report Log | `product.manage` |
| System Maintenance: Admin, Location | `config.manage`, `site.manage` |
| User Maintenance | `user.manage` |
| User Group | `role.manage` |
| Access Log Report, Edit Log Report, System Audit Trail | `audit.view` |
| Access Alert, Sample Pull Alert | `config.manage` |
| License Management | ∅ (platform) |

## 10. Not migrated (summary)
`EA_APP_AREA`, `EA_APP_MODULE`, `EA_DEFAULT_APPL_PARAMETER`, `EA_DEFAULT_DICTIONARY_DETAIL`, `EA_PRODUCT_REGISTRATION`, `EA_REGR_TINV`, all password and secret columns, `AUDIT_ID` columns, `EA_PHARMA_NEW_SAMPLE` (VERIFY with the customer; if used, it's exported to CSV and attached to the migration record).
