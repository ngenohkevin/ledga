# :app design system (Ledga v2, spec §10)

    tokens (LedgaColors, Spacing, Radii, Sizes, Motion, CategoryPalette) ──┐
    type (Inter, LedgaType) ───────────────────────────────────────────────┼─▶ LedgaTheme ─▶ components / charts
    icons (Ph vectors, Fluent 3D WebPs) · format (AmountFormat, DateLabels) ┘

Everything lives in `com.ledga.app.ui.design`. Until Phase 4 uses it, R8 strips it from release builds; that is expected.

## Contracts for Phases 4–6

- Wrap every screen in `LedgaTheme(appearance)` from `ui.design.theme`, not v1's `ui.theme.LedgaTheme`.
  - Read colours as `LedgaTheme.colors`; never write hex literals in UI code.
- Text colours come only from pairs listed in `LedgaColors.textPairs()`. `ContrastTest` holds every pair to at least 4.5:1, so add a new pair there first.
  - `faint` is decoration only.
  - Secondary text on `dangerSoft`/`warningSoft` is `ink2`.
- Amounts:
  - outflows use `AmountFormat.signed(cents, inflow = false)` in `ink`, with the U+2212 minus;
  - inflows get a `+` in `inflow`;
  - red is only for alerts and Fuliza owed.
- Category colour: `CategoryPalette.resolve(row.key, row.color, row.colorDark).pick(LedgaTheme.colors.isDark)` (R9).
- Category icon: `CategoryIcon(row.icon3d, row.name)`. An unknown key shows Other's icon.
- Dates and times: `DateLabels`, which is always Nairobi time.
  - Day headers: `DateLabels.dayHeader(day, today)`.
  - TalkBack: `DateLabels.txSpeech(...)`, passed to `TxRow(speech = …)`.
- Paged day cards:
  - the header item is `Modifier.cardSegment(Segment.Top)`;
  - rows are `Middle`, with `dividerAbove = true` after the first;
  - the last row is `Bottom`.
- Charts (R17) only draw; `:core/chart` supplies buckets and averages.
  - `MiniBars` / `SparkBars`: Home and tracker tiles.
  - `ColumnChart`: Spending, the stacked Trackers chart, Tracker detail.
  - `ShareBar`: Where it went.
  - Every chart gets a `summary`, and every `Bar` a `speech`.
- Motion: respect `LedgaTheme.reducedMotion`. `AnimatedAmount`, `Skeleton` and progress `Banner` already do.
- Touch targets are at least 48 dp. An icon is decorative (`null`) only when text beside it names it.
- Sheets use `LedgaModalSheet`. Bottom navigation is `LedgaBottomBar`, which pads for the navigation bar; edge-to-edge for the rest of the screen is Phase 4's job.

## Screenshot tests

- **Component groups:** `compose.snap("name") { … }` in a Robolectric class annotated `@GraphicsMode(NATIVE)` and `@Config(qualifiers = SPECIMEN_QUALIFIERS)`. It writes `src/test/screenshots/design/<name>.png`, a 2×2 grid of light/dark × 1.0/1.3.
- **Screens (Phase 4):** `snapScreen("home") { … }` writes four 360×800 dp files under `src/test/screenshots/screens/`.
- **Record:** `./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true --tests '<class>'`. Review every PNG, then check with `-Proborazzi.test.verify=true`.
- **Pin:** Roborazzi stays at 1.60.0, because Kotlin 2.1 can't read 1.61+ metadata. Upgrade it together with Kotlin.
- **CI (Phase 6):** goldens are recorded on macOS. Linux CI may render slightly differently, so re-record on CI or set a small compare threshold.

## Assets

- `tools/design/fetch_{inter,phosphor,fluent}.py` regenerate the fonts, `Ph.kt`, the 44 WebPs with `FluentIcons.kt`, and the licence texts. They are deterministic and pinned to a checksum, version or commit; see `tools/design/README.md`. Never hand-edit generated files.
- The licences for You → About → Open-source licences are in `app/src/main/assets/licenses/`: Inter (OFL), Phosphor (MIT) and Fluent Emoji (MIT).

## Testing notes (learned in Phase 3)

- Any test that asserts on text or layout sizes needs `@GraphicsMode(GraphicsMode.Mode.NATIVE)`. Robolectric's legacy graphics measure text at 1 px per character.
- A clickable's merged semantics bounds are its visible shape, not its 48 dp minimum, and Compose widens every small hit area anyway. To check a touch target, assert the reserved layout height: `fetchSemanticsNode().layoutInfo.height`.
- `TextLayoutResult.layoutInput.style.fontSize` doesn't reflect `TextAutoSize`; compare `size`, intrinsics or baselines instead. With `softWrap = false`, `hasVisualOverflow` is true even when nothing is cut.
- Write state from a test inside `Snapshot.withMutableSnapshot { … }` when the clock is paused (`mainClock.autoAdvance = false`).
- `LedgaModalSheet` exposes M3's experimental `SheetState`, so call sites `@OptIn(ExperimentalMaterial3Api::class)`.
- Chart period labels share one size: full labels while they fit (down to 8 sp), else first letters. Pass full names ("SEP"); the chart decides.
