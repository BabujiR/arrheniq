# Validation evidence inputs

| File | What | Maintained by |
|---|---|---|
| `urs-index.csv` | Requirement IDs with GxP risk, planned release and status, for the CI traceability gate. No requirement text (the URS itself is a local working document). | Generated: `python3 scripts/ci/urs_index.py` after changing the URS |

The traceability matrix (URS → test → result) is produced on every backend CI run by
[`scripts/ci/traceability.py`](../../scripts/ci/traceability.py) and attached to the run as `traceability-<run>`
(see [docs/ci/README.md](../ci/README.md), stage C7). Tests link to requirements with
`@Requirement("URS-…")` (`backend/app/src/test/java/com/arrheniq/testsupport/Requirement.java`).

Run it locally after `mvnw verify` (from the repository root):

```
python scripts/ci/traceability.py
```

Output: `backend/target/traceability/traceability.html` (with requirement text, because the URS is present locally).
