# ArrheniQ brand

Design canvas (concepts, brand sheet, colour tweaks): https://claude.ai/artifact/J3rq6MovhMsL48WZMYTZGJ

## The mark: "Q-Curve"
A **Q** whose tail is a **stability curve**: it falls steeply, levels off and ends in a **measurement point**,
the degradation curve ArrheniQ predicts shelf life from. The Q also stands for quality and the ICH Q1 stability
guidelines. Tagline: *Intelligent stability. Predictable shelf life.*

| File | Use |
|---|---|
| `arrheniq-mark.svg` | the mark on light backgrounds (documents, website) |
| `arrheniq-mark-on-dark.svg` | the mark on the dark brand background |
| `arrheniq-app-icon.svg`, `arrheniq-app-icon-{16…512}.png` | app/tile icon (indigo tile, white Q, teal curve) |
| `arrheniq-favicon.svg` | browser favicon (heavier strokes for 16 px) |
| `arrheniq-mark-512.png` | raster mark for tools that can't take SVG |

The app uses the same mark in `frontend/src/brand/Logo.tsx` (it adapts to light/dark mode) and the favicons in
`frontend/public/`.

## Colour
| Name | Hex | Use |
|---|---|---|
| Arrhenius Indigo | `#2E3192` | primary: the Q, buttons, links, app tile |
| Shelf-life Teal | `#0CA678` | accent: the curve, the "Q" of the wordmark, positive states (light backgrounds) |
| Teal on dark | `#38D9A9` | the accent on dark backgrounds |
| Ink | `#1A1B2E` | text |
| Mist | `#F6F7FB` | page background |
| Night | `#14152B` | dark background |

## Type
- **Wordmark and headings:** Space Grotesk SemiBold (600), letter-spacing −1.5 %. Wordmark: "Arrheni" in Ink,
  "Q" in teal. Self-hosted via `@fontsource/space-grotesk` (no external font requests).
- **Body:** Inter / the system UI font.

## Rules
- Keep clear space around the mark of at least the Q's stroke width × 2.
- Don't recolour, rotate, outline or add effects to the mark; don't separate the tail from the Q.
- Minimum size: 16 px (use the favicon variant below 32 px), 20 px for the mark with wordmark.
- Motion: the curve may draw itself once (sign-in, splash) or loop while loading; never on data screens.
  Respect "reduce motion".

## Before registering
Run a trademark search for the mark and the name (USPTO and EUIPO, classes 9 and 42; pharma context) before
filing the trademark and registering domains.
