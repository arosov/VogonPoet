# Plan: Rework `TranscriptTelemetryCard` — "Current utterance"

**Scope:** single file — `composeApp/src/jvmMain/kotlin/ovh/devcraft/vogonpoet/ui/components/TranscriptTelemetryCard.kt`

## 1. Rename panel title

- Header text `"Ghost pass — current utterance"` → `"Current utterance"` (line 80).
- Update the KDoc at the top of the file to match.

## 2. Fixed heights + scrolling

All three content areas become scrollable with bounded height.

| Section | Behavior | Height |
|---|---|---|
| 1 · Ghost increments | Scrollable, **fixed** — always reserves the area | 6 rows ≈ 112dp incl. padding; the "waiting for voice…" placeholder renders *inside* the reserved area so the panel never jumps |
| 2 · On-progress composition | Scrollable, **grows up to limit** | `maxHeight` = 3 × 22sp line height + inner padding (~74dp) |
| 3 · Final | Scrollable, **grows up to limit** | same 3-line cap as section 2 |

- The ⏱ timing strip stays **outside** section 3's scroll area so it is always visible.
- Section 1: replace the current `heightIn(max = 140.dp)` with a true fixed height computed from 6 rows.

## 3. Discreet desktop scrollbars

Each area becomes a shared private `ScrollArea` composable:

```kotlin
Box {
    Box(scrollable, padding end ≈ scrollbar width) { content }
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        style = ScrollbarStyle(...),
    )
}
```

API (verified available in Compose Multiplatform 1.10, `foundation-desktop` artifact, package `androidx.compose.foundation`):

- `VerticalScrollbar(adapter, modifier, reverseLayout, style, interactionSource)`
- `rememberScrollbarAdapter(scrollState: ScrollState): v2.ScrollbarAdapter`
- `ScrollbarStyle(minimalHeight, thickness, shape, hoverDurationMillis, unhoverColor, hoverColor)` + `LocalScrollbarStyle`

Discreet styling:

- thin: `thickness = 4.dp`, `minimalHeight = 16.dp`
- rounded shape (`RoundedCornerShape`)
- `unhoverColor` = `GruvboxGray` at low alpha (~0.25) — nearly invisible at rest
- `hoverColor` = `GruvboxFg2` at moderate alpha

The scrollbar overlays the inset area's right edge; content gets end padding so text doesn't hide beneath it. No layout jump.

Note: these APIs are desktop-only (fine — the composable lives in `jvmMain`).

## 4. Inset visuals for scroll areas

Unified treatment for all three sections, matching the Gruvbox dark theme:

- **Inset background**: `GruvboxBg0` (darker than the card's `GruvboxBg1`) — reads as a scroll well.
- **Subtle border**: 1dp `GruvboxGray` at reduced alpha (~40%), rounded corners (~6dp) via `RoundedCornerShape` + `clip`.

## 5. Line wrapping

Texts already soft-wrap by default, and the increment rows wrap via `weight(1f)`. Make it explicit (`softWrap = true`) in all three sections so the constraint is guaranteed, and verify manually with long utterances.

## 6. Verification

- `./gradlew :composeApp:compileKotlinJvm`
- Manual run (`./gradlew :composeApp:run`) to eyeball: scrollbar discreetness, wrap behavior at long utterances, no panel jumping when sections fill/empty.
