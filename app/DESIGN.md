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
- Paged day cards (R40): `PagingData<TxRow>.toActivityItems()` makes the items. The header item is `cardSegment(Segment.Top)`; rows are `Middle`, with `dividerAbove = true` after the first; the end cap is its own `Bottom` item, as tall as the corner (`Radii.card`, 24 dp: a shorter cap starts mid-arc and the edge steps in, `DayCardTest`) (`ActivityItem.End`, or `Day.closesPrevious`). Paging can't know a day's last row until the next page arrives, so no row is ever `Bottom`. `cardSegment` clips what an item draws after it (a ripple, a pressed colour) to the card's corners.
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

## Screens (Phase 4)

- Edge-to-edge (targetSdk 35): `MainActivity` draws behind the system bars, and `LedgaRoot` sets their icons from `LedgaTheme.colors.isDark`. Every screen pads its own insets:
  - tab screens pad `WindowInsets.statusBars` at the top (`ShellFrame`'s `LedgaBottomBar` pads the navigation bar);
  - full-screen flows (onboarding, recovery) pad `WindowInsets.safeDrawing`, which includes the keyboard.
- Text size: `LedgaRoot` applies You → Appearance → Text size by overriding `LocalDensity.fontScale`. `TextSize.SYSTEM` leaves Android's own non-linear scaling alone.
- `ui.app` building blocks:
  - `ShellFrame` (content + bottom bar; the content keeps its place, so a NavHost survives the bar hiding);
  - `ScreenTitle` (24/800, a heading) and `HeroIcon` (96 dp raised 3D tile);
  - `Tab` + `NavController.openTab`;
  - `@Serializable` routes in `ui/app/Routes.kt`.
- A one-question screen (onboarding) scrolls its content and pins its actions to the bottom, so they're reachable at 1.3× and with the keyboard open. Keep an outlined field's label short: a label that wraps at 1.3× runs into the border (`OnboardingLayoutTest`).
- Pixel assertions on Robolectric: draw the window's root view into a `Bitmap` (`ChartContrastTest`); Compose's `captureToImage` uses PixelCopy and times out under software rendering.
- Until 4d lands, `ComingNext` holds the You tab (R32). Development builds only.
- **Activity (4b).** `ActivityTab` hosts three segments, each with its own ViewModel: Transactions in `ActivityViewModel`, `SpendingViewModel`, `PeopleViewModel`.
  - Spending's "tap a category" calls `ActivityViewModel.showTransactions(filter)`.
  - Flow chips are a single choice (`ChoiceChip(role = Role.RadioButton)` in a `selectableGroup`); line chips toggle (`Role.Checkbox`).
- **Shared sheets (4b, for 4c and 4d).**
  - `TransactionSheetHost(code, onDismiss, onHidden, onChangeCategory)` and `CategoryPickerHost(code, onDismiss)` own their ViewModels (`hiltViewModel(key = …)`): a screen holds only the open code, in `rememberSaveable`.
  - After Hide, the screen offers Undo with its own `SnackbarHost`, as `ActivityTab` does. Every M3 sheet is its own dialog window above the screen, so Hide also closes any sheet under the transaction sheet (the person sheet) before the snackbar shows (`OpenSheets.afterHide`).
  - The picker opens on top of the transaction sheet; the transaction sheet opens on top of the person sheet.
- **What a transaction says** comes only from `TxText`: title, row subtitle (or the Fuliza note), leading icon or initials, chips, facts, TalkBack phrase and share text.
  - Display names go through `NameFormat.display` (R44); stored names stay as M-Pesa wrote them.
  - List rows never show the balance; the sheet's "Balance after" does.
  - Share sends the payment itself (title, amount and category, date, code, a paybill's account, fees): never the balance, Fuliza borrowing, the line or a note.
- **Today** is `LiveClock.today` (R34). It emits at Nairobi midnight and on `MainActivity.onResume`. Never compute "the current month" once and keep it.
- **Landscape (owner ruling M5).** `ShellFrame` pads `WindowInsets.safeDrawing` horizontally, so tab screens never sit under a landscape cutout or a side navigation bar. Each screen family has one `snapScreenLandscape` golden (800×360 dp). In a pane shorter than 400 dp (a phone in landscape), Transactions' search and chips are the list's first item and scroll away with it (`ActivityLandscapeTest`); People's controls always scroll with its list.
- **Sheet screenshots.** Robolectric doesn't capture `ModalBottomSheet`'s dialog window. Snap a sheet's stateless content inside `SheetScaffold` instead (`TransactionSheetScreensTest`).
- **Category picker.** "Apply to all" defaults on (N > 1) only when the person picks a different category; Save with the payment's own category and "apply to all" off changes nothing. The grid shows four columns while a cell holds the longest seeded word at the current text size, else three (`CategoryPickerBehaviourTest`).
- **One line choice (R47).** `SelectedLine.choice` (a `LineChoice`) narrows Home, Activity › Spending and People (and the
  person sheet), Trackers and Tracker detail. Every one of them shows `LinePicker`, the chip plus switcher sheet, which
  draws nothing on a phone with fewer than two lines. Read `LineChoice.lineId`, never `selectedId`: the choice counts only
  with two or more lines and while that line exists. Transactions keeps its own line chips; a link into it carries the line.
- **Links into Activity (R61).** A screen that opens Activity calls `ActivityLinks.open(…)` and then switches tabs.
  `ActivityViewModel` applies each request once: `ActivityLink.Transactions(filter, focusSearch)` or `ActivityLink.Spending`.
  A search request focuses the field a frame later and is then consumed (`TransactionsActions.onSearchFocused`), so
  coming back to the pane doesn't take the focus again. Each Spending request also counts in `ActivityLinks.spendingHops`,
  and `SpendingViewModel` returns to the current month on each one (R57), however far back the person stepped before.
  A hop from another tab's screen (Home, or Tracker detail's "See all") remembers that tab: Back in Activity returns to
  it, to the screen it left; tapping a tab ends that. Trackers open single-top, so a double tap opens one.
  `LedgaNavHost` takes its screens as `LedgaScreens` (`AppScreens` in the app), so `LedgaNavHostTest` drives the real
  routes with stand-ins.
- **Home (4c).** `HomeRoute` owns the permission requests (4a M3: the first tap asks Android; once Android won't ask
  again, the next tap opens Settings), the sheets (`HomeSheets`: payment, picker, Fuliza; Hide closes the Fuliza sheet
  too) and Undo. `HomeContent` is stateless.
  - Recent rows take `HomeText.rowTime` as their tail: the time today, then "Yesterday" or the date. No balance (R56).
  - The spending card's labels sit under `MiniBars` in its slots and fall back to short labels when the full ones don't
    fit. Its fees line and badge, and its two footer figures, are `FlowRow`s: side by side while they fit, else wrapped
    (`HomeBehaviourTest`). `ChangeBadge` is the shared "▲ 9% vs Aug" badge.
  - `Banner` takes an optional second action ("Not now", R59). With two actions they sit in a row under the text:
    beside it they squeezed it until words broke.
  - A tracker tile is 13 caption font sizes wide (`TILE_EMS`), not a fixed dp, so "usually by the 12th" stays whole at
    1.3× (Android 14 grows dp lengths less than small text).
- **Trackers and Tracker detail (4c).**
  - Every tracker number comes from `data/trackers/Trackers`: Home's tile, the Trackers row and the detail always agree.
  - Text comes only from `TrackerText`: tile caption, row context and detail, rule chips, tooltips, payment lines and
    the add-rule preview.
  - Tracker detail is a pushed route (`TrackerRoute(categoryKey)`) with no bottom bar, so it pads `safeDrawing`
    vertically. Its "All" range uses `Bucketing.allTime` (years after 12 months, R54).
  - "Matched by" chips sit on a `LedgaCard`: a chip's plate barely shows on the canvas. `RuleChip`'s label ellipsizes
    before its ×, so a long rule stays removable at large text.
  - Stop tracking closes the detail (R51); the screen below, Trackers or Home, then says "Stopped tracking <name>" with
    Undo (`StoppedTrackers`, shown once).
  - The add-rule sheet shows a count only for exactly the text typed (`ShownPreview`, recounted with `mapLatest`). Until
    then it says "Counting…" and Save rule is disabled (`PrimaryPill(enabled = false)`), and
    `TrackerDetailViewModel.addRule` refuses other text: the rule clears hand-filed choices that removing it won't
    bring back (R48).
- **Fuliza sheet (4c).** Its rows show the date as the subtitle and the draw ("Fuliza Ksh 463") under the amount, where
  it never gives way (`FulizaSheetBehaviourTest`). A row is read as one phrase, so the draw is in that phrase too.
- **Shared sheets keep their state (R62).** Hosts pass a saveable session id (`rememberSaveable(code) { Random.nextLong() }`);
  `open(code, session)` reloads only for a new session. `PickerState.code` stops a stale picker frame. `OpenSheets` lives
  in `ui.tx`.

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
- Robolectric fakes `System.nanoTime`, so coroutine delays and timeouts (`withTimeoutOrNull`, also under `runBlocking`) never fire. For real time, use `Thread.sleep`.
- A failing assertion inside `runBlocking`/`runTest` still waits for running children: a latch-parked child turns a RED into a hang. Release latches in `finally`, and give a deliberately parked coroutine its own thread (`TransactionEditsTest`).
- A ViewModel callback that runs after a suspend edit (e.g. `addRule(…) { onDone }`) arrives after the UI state has already changed. Wait on a `CompletableDeferred` rather than reading a flag at once.
- A route test with its real ViewModel (`TrackersRouteTest`) runs `viewModelScope` on Robolectric's main looper: clean up with `TestViewModels.stopAllOnMainLooper()` (plain `stopAll()` blocks that thread and times out), and step `mainClock` by hand around a snackbar, or auto-advance runs straight through its timeout.
- Text that must fit at large font scales: check `TextLayoutResult` (`maxIntrinsicWidth` ≤ width for a line that mustn't be cut, `minIntrinsicWidth` ≤ width for no word broken, `lineCount` for no wrap). Size containers that hold text in the text's own font size, not dp.
