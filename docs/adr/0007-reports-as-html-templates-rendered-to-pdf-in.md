# ADR 0007: Reports as HTML templates rendered to PDF in-process; Apache POI for Office formats

- **Status:** Accepted (proposed by AI agent; pending founder review)
- **Date:** 2026-11-25
- **Deciders:** Founder (approver), AI engineering agent (author)

## Context
About 26 legacy reports must be rebuilt (10 in the MVP). GxP reports need consistent headers and footers, signatures, watermarks, PDF/A for archiving, and values identical to the screens (URS-RPT-003). AI-assisted development works best with plain, testable templates.

## Decision
Use Thymeleaf HTML/CSS templates rendered to PDF by **OpenHTMLtoPDF** (pure Java, PDF/A-capable). Charts are SVG produced by the stats service. Report data comes from the same query services the UI uses. XLSX and DOCX (CTD tables) are generated with Apache POI. Generated GxP reports are stored immutably with a SHA-256 hash (ADR 0010).

## Consequences
+ No external rendering service; works the same in Private.
+ Templates are diffable and text-extractable for automated verification.
− CSS support is limited to CSS 2.1 plus some extensions (no flexbox/grid in PDFs); layouts use tables.

## Alternatives considered
JasperReports 7 (rejected: heavy designer tooling, legacy layouts would still need rework). Headless Chromium/Gotenberg (rejected: extra container, larger attack surface).
