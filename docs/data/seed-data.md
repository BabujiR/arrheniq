# Seed and Reference Data

Source: `V10__reference_catalog_seed.sql` (catalog) and `V11__tenant_provisioning.sql` (per-tenant copy).
**Status: VERIFY.** The stability SME advisor must review this catalog before the first release (mvp-scope.md §6, month-3 checkpoint). Changes after release go through change control.

## 1. What a new tenant receives (`platform.provision_tenant`)
| Item | Count | Notes |
|---|---|---|
| Site | 1 | From the provisioning parameters (code, name, time zone) |
| Preset roles | 8 | TENANT_ADMIN, STABILITY_MANAGER, ANALYST, REVIEWER_L1, APPROVER_QA, STATISTICIAN, READ_ONLY, CLIENT_VIEWER. **Tenant admin has no GxP approval permissions** (segregation of duties) |
| Dictionaries | 25 | Copied from the catalog (133 values); tenant-editable |
| Settings | 25 | Equivalents of the legacy `EA_APPL_PARAMETER` defaults (legacy-mapping.md §6) |
| Label templates | 3 | STD_2D, STD_1D, LARGE_2D (fixed templates for the MVP) |
| Sequences | 1+ | Document numbers; study codes are created per year on first use |
| Genesis audit event | 1 | `seq = 1`, `action = TENANT_PROVISIONED`; starts the tenant hash chain |

Genesis hash: `SHA-256( UTF8('ARRHENIQ-GENESIS|' + tenant_id + '|' + occurred_at as YYYY-MM-DDTHH:MM:SS.ffffffZ) ‖ 32 zero bytes )`. The audit kernel's verify tool must implement exactly this for `seq = 1`.

## 2. Storage conditions (dictionary `STORAGE_CONDITION`)
| Code | Condition | Typical role |
|---|---|---|
| 25C60RH | 25 °C ± 2 °C / 60 % RH ± 5 % RH | Long-term (Climatic Zones I/II) |
| 30C65RH | 30 °C ± 2 °C / 65 % RH ± 5 % RH | Intermediate; long-term Zone III/IVa |
| 30C75RH | 30 °C ± 2 °C / 75 % RH ± 5 % RH | Long-term Zone IVb |
| 40C75RH | 40 °C ± 2 °C / 75 % RH ± 5 % RH | Accelerated |
| 5C | 5 °C ± 3 °C | Long-term, refrigerated |
| 25C60RH_REF | 25 °C ± 2 °C / 60 % RH ± 5 % RH | Accelerated for refrigerated products |
| M20C | −20 °C ± 5 °C | Long-term, frozen |
| 5C_FRZ_ACC | 5 °C ± 3 °C | Accelerated for frozen products |
| 25C40RH | 25 °C ± 2 °C / 40 % RH ± 5 % RH | Long-term, semi-permeable containers |
| 30C35RH | 30 °C ± 2 °C / 35 % RH ± 5 % RH | Intermediate, semi-permeable containers |
| 40C25RH | 40 °C ± 2 °C / NMT 25 % RH | Accelerated, semi-permeable containers |
| PHOTO_Q1B | ICH Q1B (≥ 1.2 million lux·h; ≥ 200 W·h/m² near-UV) | Photostability |
| M70C | −70 °C or below | Ultra-low (biologics) |

## 3. Protocol templates (`platform.ref_protocol_template`)
| Code | Condition | Time points | Basis |
|---|---|---|---|
| LT_ZONE_II_36M | 25C60RH | 0, 3, 6, 9, 12, 18, 24, 36 M | ICH Q1A(R2): every 3 months in year 1, every 6 months in year 2, annually after |
| LT_ZONE_II_60M | 25C60RH | … 36, 48, 60 M | Same |
| LT_ZONE_IVB_36M | 30C75RH | 0, 3, 6, 9, 12, 18, 24, 36 M | ICH Q1A(R2) + WHO |
| INT_30_65_12M | 30C65RH | 0, 6, 9, 12 M | ICH Q1A(R2): minimum four points including initial and final |
| ACC_40_75_6M | 40C75RH | 0, 3, 6 M | ICH Q1A(R2): minimum three points including initial and final |
| ACC_40_75_6M_EXT | 40C75RH | 0, 1, 2, 3, 6 M | Industry practice (early trending) |
| REF_LT_36M | 5C | 0, 3, 6, 9, 12, 18, 24, 36 M | ICH Q1A(R2) / Q5C |
| REF_ACC_6M | 25C60RH_REF | 0, 1, 3, 6 M | ICH Q5C practice |
| FRZ_LT_36M | M20C | 0, 3, 6, 9, 12, 18, 24, 36 M | ICH Q1A(R2) / Q5C |
| IN_USE_28D | 25C60RH | 0, 7, 14, 21, 28 D | EMA in-use stability guidance |

## 4. Other dictionaries
- **Reason for study (9):** registration, annual, process validation, change control, development, clinical, shelf-life extension, investigation, other
- **Study length (7):** 6, 12, 18, 24, 36, 48, 60 months
- **Units of measure (20):** %, % LC, % w/w, mg, mg/g, mg/mL, ppm, pH, CFU/g, CFU/mL, kp, N, min, s, mOsm/kg, NTU, cP, particles/container, AU, none
- **Abbreviations (5):** ND, LOQ (<LOQ/BQL), LOD, NT, NA → structured qualifiers
- **Quick list (6):** Conforms, Does not conform, Complies, No significant change, Positive, Negative
- **Dosage forms (13)**
- **Packaging (11 dictionaries, 54 values):** container, resin/material, closure, liner, inner seal, desiccant, filler, blister, lidding foil, forming foil, other
- **Reason for change (6):** coded reasons for the audit trail (URS-AUD-004)
- **Empty at provisioning** (tenant-specific): storage location, analyst, lab number, supplier, production site

## 5. Permission catalog (initial)
`tenant.admin, site.manage, user.manage, role.manage, config.manage, apikey.manage, audit.view, audit.export, export.data, masterdata.view, test.manage, product.manage, product.approve, spec.manage, spec.approve, dictionary.manage, protocol.manage, protocol.approve, study.view, study.create, study.approve, study.terminate, study.backentry.attest, attachment.manage, schedule.view, schedule.change_date, pull.perform, pull.extra, pull.reverse, inventory.destroy, label.print, label.reprint, result.view, result.enter, result.import, result.approve.l1, result.approve.final, result.edit_approved, investigation.manage, evaluation.run, evaluation.sign, report.generate, report.sign`
