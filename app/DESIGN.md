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
  - `today` is `DateLabels.nairobiDate(now)`, with `now` from the injected clock. Never `LocalDate.now()`: that uses the device zone and breaks the Nairobi guarantee around midnight.
  - Day headers: `DateLabels.dayHeader(day, today)`.
  - TalkBack: `DateLabels.txSpeech(cents, row.flow, name, at, today)`, passed to `TxRow(speech = …)`. The verb follows the `FlowKind`: only SPEND is "spent".
- Transaction rows: category in `subtitle`, time in `subtitleTail`. The tail never truncates and the subtitle ellipsizes before it, so keep the tail short (a time). For a Fuliza row the note replaces the category (spec §10.4): subtitle "Fuliza Ksh 300", tail "9:15 AM".
- Paged day cards:
  - the header item is `Modifier.cardSegment(Segment.Top)`;
  - rows are `Middle`, with `dividerAbove = true` after the first;
  - the last row is `Bottom`.
- Charts (R17) only draw; `:core/chart` supplies buckets and averages.
  - `MiniBars` / `SparkBars`: Home and tracker tiles.
  - `ColumnChart`: Spending, the stacked Trackers chart, Tracker detail.
  - `ShareBar`: Where it went.
  - Bar colours meet WCAG 1.4.11 on the card in both themes (R27, owner decision 2026-10-06): pass a category's or `chartPrimary`'s colour as it is; the charts draw `ChartTones.soft` (≥ 3:1) for unselected bars and `ChartTones.strong` (≥ 4.5:1) for the selected/current bar, legends and share fills. `ColumnChart(dimUnselected = true)` dims every bar but the selected one; without it every bar is strong. Never draw a data bar in `barTrack` or a hand-mixed tint.
  - 12-month charts keep single-letter month labels on phones (owner ruling 2026-10-06); the tooltip and TalkBack carry the full month.
  - Every `ColumnChart` gets a `summary`, and every `Bar` a `speech`. `SparkBars` has no summary of its own: the tracker tile carries the description.
  - `ColumnChart` fits about 13 bars on a phone, because the gap between bars is a fixed 6 dp. Tracker detail's "All" range must be bucketed by quarter or year in `:core` before it is charted.
  - Period labels fall back to first letters when full labels don't fit, which only reads well for month names. Pass short labels for anything else.
- Motion: respect `LedgaTheme.reducedMotion`. `AnimatedAmount`, `Skeleton` and progress `Banner` already do.
- Touch targets are at least 48 dp. The one exception is `RuleChip`'s ×, a 22 dp visual button whose hit area Compose widens to 48 dp. An icon is decorative (`null`) only when text beside it names it.
- Sheets use `LedgaModalSheet`. Its content doesn't scroll by itself: wrap long content in `verticalScroll` so actions stay reachable at large font scales. A lazy list inside a sheet scrolls itself; never wrap it in `verticalScroll`. Bottom navigation is `LedgaBottomBar`, which pads for the navigation bar; edge-to-edge for the rest of the screen is Phase 4's job.

## Screenshot tests

- **Component groups:** `compose.snap("name") { … }` in a Robolectric class annotated `@GraphicsMode(NATIVE)` and `@Config(qualifiers = SPECIMEN_QUALIFIERS)`. It writes `src/test/screenshots/design/<name>.png`, a 2×2 grid of light/dark × 1.0/1.3.
- **Screens (Phase 4):** `snapScreen("home") { … }` writes four 360×800 dp files under `src/test/screenshots/screens/`. Annotate the class `@Config(qualifiers = "xhdpi")`, or the shots are 1× pixels (Robolectric defaults to mdpi).
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
