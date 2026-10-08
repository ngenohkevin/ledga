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
- Paged payment lists (Transactions, the person sheet, the Fuliza sheet) page with `ActivityViewModel.PAGING`, which keeps placeholders on, and show `SkeletonRow` for a row that hasn't loaded. An edit reloads the list, and the reload must leave what's on screen where it was (owner report 2026-10-08). Without placeholders, Room reloads deep lists from the wrong place. Transactions also needs `KeepPlace`: a reload drops the day headers above it, and Compose's own key search reaches only about 100 items. Its saved state (`ListPlace`) keeps the top item too, so the place survives leaving Activity while the list reloads. A new filter, chip or search starts at the top of its results instead, whether it narrows the list or widens it (owner call 2026-10-08; each settled load is numbered in `ActivityItem.Tx.load`). `ActivityScrollTest` covers all three lists.
- Charts (R17) only draw; `:core/chart` supplies buckets and averages.
  - `MiniBars` / `SparkBars`: Home and tracker tiles.
  - `ColumnChart`: Spending, the stacked Trackers chart, Tracker detail.
  - `ShareBar`: Where it went.
  - Bar colours meet WCAG 1.4.11 on the card in both themes (R27, owner decision 2026-10-06): pass a category's or `chartPrimary`'s colour as it is; the charts draw `ChartTones.soft` (≥ 3:1) for unselected bars and `ChartTones.strong` (≥ 4.5:1) for the selected/current bar, legends and share fills. `ColumnChart(dimUnselected = true)` dims every bar but the selected one; without it every bar is strong. Never draw a data bar in `barTrack` or a hand-mixed tint.
  - 12-month charts keep single-letter month labels on phones (owner ruling 2026-10-06); the tooltip and TalkBack carry the full month.
  - A tooltip sits above the tallest bar it spans (`ChartMath.tooltipPosition`), so a short running month never hides last month's bar, and a gridline value it would cover is left out rather than half hidden. The AVG label and gridline values keep their card-coloured plates where they cross a bar.
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
- **Today** is `LiveClock.today` (R34). It emits at Nairobi midnight and on `MainActivity.onResume`. Never compute "the current month" once and keep it. Text that changes during a day reads `LiveClock.hours` (just past every Nairobi hour, and on resume): Home takes its date and its greeting from that one flow, so the greeting moves on while Home stays open.
- **Landscape (owner ruling M5).** `ShellFrame` pads `WindowInsets.safeDrawing` horizontally, so tab screens never sit under a landscape cutout or a side navigation bar. Each screen family has one `snapScreenLandscape` golden (800×360 dp). In a pane shorter than 400 dp (a phone in landscape), Transactions' search and chips are the list's first item and scroll away with it (`ActivityLandscapeTest`); People's controls always scroll with its list.
- **Sheet screenshots.** Robolectric doesn't capture `ModalBottomSheet`'s dialog window. Snap a sheet's stateless content inside `SheetScaffold` instead (`TransactionSheetScreensTest`).
- **Category picker.** A Fuliza repayment names the service, not a payee, so it offers no "Apply to all" (owner, 2026-10-06). "Apply to all" defaults on (N > 1) only when the person picks a different category; Save with the payment's own category and "apply to all" off changes nothing. The grid shows four columns while a cell holds the longest seeded word at the current text size, else three (`CategoryPickerBehaviourTest`).
- **One line choice (R47).** `SelectedLine.choice` (a `LineChoice`) narrows Home, Activity › Spending and People (and the
  person sheet, which says "On <line>" because People's chip sits under it), Trackers and Tracker detail. Every one of them shows `LinePicker`, the chip plus switcher sheet, which
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
    vertically. Its "All" range uses `Bucketing.allTime` (years after 12 months, R54). A per-year average (that
    dashed line, and Home's Year "Avg/year") is `Bucketing.averagePerYear` over months: a first year counts only the
    months since the first payment.
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
  `open(code, session)` reloads only for a new session; the session has no default (a clock default can repeat under Robolectric). `PickerState.code` stops a stale picker frame. `OpenSheets` lives
  in `ui.tx`.

- **Pushed screens (4d, R83).** You's subscreens, Categories & rules, a category, a licence and Alerts push full screen
  without the bottom bar, in `DetailFrame` (round Back, a title that wraps to two lines, `safeDrawing` padding). A hop
  into Activity from any of them returns on Back to the screen it left: `LedgaNavHost.owningTab()` is the last tab root
  on the back stack. `LedgaNavHostTest` covers every route with stand-ins. `DetailFrame` paints no background: the
  app's `ShellFrame` does, so a pushed screen's golden wraps its content in `ShellFrame(null, onSelect = {})`.
- **You (4d).** `YouContent` is a card per group (`GroupLabel` + `LedgaCard` of `ListRow`s): Money, App, Data, About.
  5b added Export & restore and Android backup under Data (R125); Phase 6 added Updates and Version history under About (R149)
  (R66): add a row, not a section. Rescan's row shows its progress, and a result only for a rescan started there (R77).
- **Alerts (4d, R71).** Home's bell sits beside search; its badge is `onPrimary` on `primary`, "9+" above nine, and
  TalkBack hears "Alerts, N unread" (the badge's own text is cleared from semantics). M3's `IconButton` clips what it
  draws, so the badge is a sibling over the button's corner, not its child (a child was cut off). Opening Alerts marks every alert
  read; what was unread stays "New" for that visit. A row opens its payment only while the payment exists and isn't
  hidden; Undo after Hide lives in Alerts' own snackbar host.
- **Categories & rules (4d, R67, R72–R74; its list and screen were replaced in 4e by the Categories tab and the category page, below).** A rule's whole row is its switch (`toggleable`, role Switch); your own rule
  also has a delete button (Undo). Built-in categories keep their icon and colour; your own choose from
  `CategoryLooks.ICONS` and `SWATCHES` (`IconChoiceContent`, `ColourChoiceContent`, radio groups). Archived categories
  sit under "Archived" with "Bring back". Each group's "+ New category" chip tells TalkBack its group. The add-rule and rename sheets are shared with Tracker detail (`ui/rules`:
  `RuleDraft`, `AddRuleSheet`, `RenameSheet`).
- **Dates (4d, R69, R70).** A `DateFilter` is what the person chose (`Preset`, `Month`, `Custom`), turned into a range
  against `LiveClock.today` on each load. M3's date picker speaks UTC midnights: convert through `PickerDates`, never the
  device zone. M3's pickers take Inter from `LedgaType.material`.
- **Settings screens (4d).** Radio groups use `ChoiceRow` in a `selectableGroup`. Permission rows follow 4a M3 (ask
  first; once Android won't ask again, open Settings) through the shared `Context.showsRationale`.
- **History check (4d, R79).** A break row's subtitle is its day; "Expected Ksh … · M-Pesa said Ksh …" sits under the row
  in full (it is the point of the row; as a one-line subtitle it was cut to "Expected K…" at 1.3×), hidden from
  TalkBack because the row's phrase already says it. With two or more lines, "Not on a line" reads "Left out · could be
  either line", sits last, and a caption under the lines card says why.
- **The category page (4e D1, R89, R94, R96).** The only screen for a category: the chart (a month from Spending starts
  selected; older than a year opens on All), the tiles, Top places (the five biggest counterparties over the last
  twelve months and this one: payees, payers or accounts by measure), the payments, then Settings (icon, colour, Reset
  to default for a built-in category whose looks changed, Show on Trackers), 4d's rules list and Archive. Its words
  follow what it counts: "Spent, fees included", "Money received", "Money moved, either way" (§3.3). Stopping tracking
  never closes it; the page offers Undo itself.
- **The Categories tab (4e D2, R92, R95, R97, R98).** Search folds case, accents and spaces (`TextFold`) and lists
  matches in picker order, archived ones marked; no match offers "Create '<query>'". Then the trackers' chart and rows,
  "All categories" by group with this month's amount in each category's measure, and "Archived (N)", folded for the
  visit. "+" and Create open New category with a name and a group (Everyday first).
- **Icons (4e D5, R85, R86, R91).** The 60 drawables plus the catalog in `assets/icons3d` (lossy q90, 1,535 WebPs,
  `index.tsv`). `CategoryIcon` draws a drawable, else the asset (decoded off the main thread into `CatalogBitmaps`),
  else `Fluent.FALLBACK`. The icon sheet (`IconPickerSheet`) is search, Suggested (`CategoryLooks.ICONS`), then the set
  by group; every copy of the current icon is ringed. Goldens that draw catalog icons call `CatalogBitmaps.preload`.
- **Home's strip (4e D8, R88).** `StripLayout.tileWidth` shows whole tiles and half of the next, never under 13 caption
  sizes; the strip runs to the screen's right edge, ends in a See all tile and returns to its first tile when the set of
  tracked keys changes.
- **Ways in (4e D3, R90, R93).** `CategoryRoute(categoryKey, month)`, pushed on the tab that opened it: Categories rows,
  Home tiles, Spending's rows by category (with the month), a payment's View (Home, Activity, Alerts, History check; not
  on the page itself). See all pops back to Activity's root when the page was opened inside Activity and hops from any
  other tab. Activity takes a hand-off only while its screen is started (`TakeLinks`).

## Notifications (Phase 5a)

- **"Allowed" (R109).** `NotificationAccess.enabled()` is Android's own switch on every version
  (`areNotificationsEnabled`, which also covers Android 13's permission); `shouldAsk()` only means Android's dialog can
  still ask (13+). Home's banner, You's row and You → Notifications read `enabled()`. "Turn on" opens the dialog only
  while it can ask, else Android's notification settings (Android 8–12, a refusal for good, switched off there).
- **Taps (R100).** A notification carries its alert key and a `NotificationTap` (`NotificationIntents`). `MainActivity`
  reads it in `onCreate` (first launch only) and `onNewIntent`, clears it (a rotation never reopens it), and hands it to
  `NotificationOpens`, which marks the alert read and names an `OpenDestination`:
  - a payment → `AlertsRoute(openCode)` with the payment's sheet (Alerts alone when it is hidden or gone); a second
    payment's tap replaces an Alerts already on top;
  - a Fuliza reminder → Home, whose `HomeRoute` takes `HomeLinks`' `FulizaRequest` with `TakeRequest` and opens its
    Fuliza sheet; when Home shows a different single line it first moves to the reminder's line (owner 2026-10-07), and
    All lines stays;
  - a summary → Activity › Transactions on its days with every flow (`ActivityLinks`), so the day header's Out equals it.
  `LedgaNavHost` goes there once (`onOpened`): Home and Activity open their tab and pop to its own screen, whatever was
  pushed on it (final review I1); during onboarding the tap is dropped. `NotificationOpens` never throws: a database
  that can't be read leaves Alerts without a sheet. `LedgaNavHostTest` drives each.
- **Words and times** come only from `AlertWords` and `AlertTimes`; no screen formats an alert's text. You →
  Notifications says "the day before" for a summary time before noon (`NotificationText.dailyDetail`).
- **Ledga dev only (R113).** `adb shell am broadcast -a com.ledga.app.DEBUG_ALERT -n
  com.ledga.app.dev/com.ledga.app.debug.DebugAlertReceiver --es kind <large|draw|due|daily|weekly|clear>` posts a sample
  of each alert from the phone's own payments (keys `debug-…`); `clear` removes them. `src/debug` only.
- **Testing notes.** Grant POST_NOTIFICATIONS on Robolectric (`shadowOf(app).grantPermissions`) before posting.
  `ShadowNotificationManager.allNotifications` lists what was posted; `shadowOf(contentIntent).savedIntent` reads a tap.
  A delayed WorkManager request stays `ENQUEUED` under `SynchronousExecutor`; read its `initialDelayMillis`.

## Export & restore, Not on a line, onboarding (Phase 5b)

- **Export & restore** (`BackupRoute`, from You's "Export & restore" and "Android backup" rows, R125): Export (Save to a
  file through Android's picker, or Share), Android backup (when the snapshot was saved; Ledga never claims Android's
  switch is on), Restore (Before your last restore, Earlier backup, Choose a file). A picked file is copied into
  no-backup storage before it is read. The restore sheet (`RestoreDraftContent`) holds what the backup is, Merge or
  Replace (`ChoiceRow`s), the line questions (`LineQuestionsContent`, radio chips) and Restore, enabled once each question
  is answered; Replace asks once more in a dialog ("Replace" in `danger`). Results show only for a restore started on
  the screen, as Rescan's (R77).
- **Not on a line** (`UnassignedRoute`, from M-Pesa lines' row and History check's link, R129): By balance (counts per line,
  "still unclear", one tap) and By date (line chips, From/To with `RangeDatePicker`, now `internal` in `ui/activity`).
  Each placement offers Undo in the screen's snackbar.
- **Onboarding** (R126, R127): the steps are fixed at the start (Welcome, SMS, Import, Notifications when Android can
  ask). A snapshot found on a fresh install is offered inside the import step, with its line questions, before the
  inbox import; naming two or more lines sits under "Your history is ready". Start fresh keeps the backup as the earlier
  one.
- **Shared:** `LineQuestionsContent` is the one "Which SIM was <line>?" UI (Export & restore and onboarding).

## Updates, Version history, What's new (Phase 6)

- **You → About** (R149): Updates (`fluent_inbox_tray`, `BETA` badge on the beta channel, subtitle from
  `UpdateText.youLine`), Version history (`fluent_bar_chart`, "What changed in every release"), Open-source licences,
  Version. Both new screens are pushed screens (`DetailFrame`, no bottom bar).
- **Updates**: a status card (Up to date · Available with size, Download and Skip this version · Downloading with a
  bar · Ready with Install · a person's failed download with Try again), then "Release notes" (`NotesList`, whose sections carry their own "What's new" / "Fixes"), "Open its
  release page", and Channel's Beta updates switch (R145's wording). Banners above: Ledga dev (Info), an install
  failure (Danger), "Allow" when installing apps is off (Warning). The check's time or failure and Check now close the
  card.
- **Version history** (R142): one card per release ("2.0.0-beta.2", "4 Oct 2026 · Beta", an `Installed` chip, a caret);
  a tap opens its notes (`stateDescription` says shown/hidden). Empty: `EmptyState` with Try again.
- **Home** (R148): the update banner comes first: Available (Info, Later + Update), Downloading (Progress), Ready (Info,
  Later + Install), Failed (Danger: why Android refused the install or the person's download stopped, Later + Open
  Updates; final review I4). A tap on the banner opens Updates (`Banner(onClick = …)`); its actions stay their own.
- **Home, one number on two lines** (Phase 7b-1, R177): right under the update banner, Info, "Line 2 and Line 3 look
  like the same number" with Not the same + Merge (goldens `home_line_merge_*`). Nothing moves without a tap.
- **What's new** (R141): a modal sheet titled "What's new in Ledga <version>", `NotesList` + "Got it"; dismissing it
  counts as seen. It never opens over another Home sheet.
- `NotesList` renders sections as a bold title and `•` items in `ink2`; no notes read "No notes were written for this
  version."

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
- `SegmentedControl` labels share one size too: the largest at which every option fits its segment (down to 8 sp), then ellipsis. Shrunk one by one, a longer word was drawn smaller than its neighbours.
- Robolectric fakes `System.nanoTime`, so coroutine delays and timeouts (`withTimeoutOrNull`, also under `runBlocking`) never fire. For real time, use `Thread.sleep`.
- A failing assertion inside `runBlocking`/`runTest` still waits for running children: a latch-parked child turns a RED into a hang. Release latches in `finally`, and give a deliberately parked coroutine its own thread (`TransactionEditsTest`).
- A ViewModel callback that runs after a suspend edit (e.g. `addRule(…) { onDone }`) arrives after the UI state has already changed. Wait on a `CompletableDeferred` rather than reading a flag at once.
- A route test with its real ViewModel (`TrackersRouteTest`) runs `viewModelScope` on Robolectric's main looper: clean up with `TestViewModels.stopAllOnMainLooper()` (plain `stopAll()` blocks that thread and times out), and step `mainClock` by hand around a snackbar, or auto-advance runs straight through its timeout.
- Text that must fit at large font scales: check `TextLayoutResult` (`maxIntrinsicWidth` ≤ width for a line that mustn't be cut, `minIntrinsicWidth` ≤ width for no word broken, `lineCount` for no wrap). Size containers that hold text in the text's own font size, not dp.
- (4e) Goldens are only compared with `-Proborazzi.test.verify=true`. A change to the bottom bar re-records every
  `ShellFrame(Tab.*)` golden: diff each against `HEAD` (bounding box of the change) and expect it inside the bar.
