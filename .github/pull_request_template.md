## What and why
<!-- One or two sentences. Link the slice/epic (mvp-scope.md) if relevant. -->

## Requirements and design
- URS IDs covered: <!-- e.g. URS-STU-005, URS-AUD-001 -->
- ADR(s) affected: <!-- e.g. ADR 0004, or "none" -->

## GxP and security checklist
- [ ] Tests added/updated and tagged with the URS IDs (ADR 0015)
- [ ] Every GxP write goes through the audit kernel; no hard deletes (audit-esignature.md)
- [ ] New tables registered in `platform.table_classification` and the migration ends with `SELECT platform.apply_security();`
- [ ] No string-built SQL; no secrets in code, config or logs
- [ ] New endpoints require explicit permissions (deny by default)
- [ ] Risk to validated functions assessed (none / low / needs QA review)

## Reviewer notes
<!-- Anything the founder / QA reviewer should look at closely -->
