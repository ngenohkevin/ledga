# :app data layer (Ledga v2, schema 6)

    raw SMS ──SmsIngestor──▶ sms (bodyHash UNIQUE; status PARSED/IGNORED/UNREADABLE)
      ──Deriver.rederive(codes)──▶ transactions (one row per code, = :core DerivedTx)
      ──ledger view──▶ LedgerQueries (spent, money in, by category, balance) · TransactionsDao.page
    MIGRATION_5_6 + LegacyImporter + Deriver.rebuildAll: v1.x → v6

## Contracts for Phases 3–5

- **`LedgaDatabase` is the Hilt singleton from `di/DataModule`.** Never build a second instance on `ledga.db` (two Room instances corrupt it), and never open it before `PreV6Snapshot` (`LedgaApp.onCreate` does that first).
- Every total reads the `ledger` view (`LedgerDao` / `LedgerQueries`). Never re-implement "spent" in another query. The view must equal `:core` `Ledger` (`LedgerViewTest`).
- Changing a transaction: `Deriver.saveOverride(override)`.
- Changing rules or categories: write the row, then `Deriver.reclassifyAll()`.
- A parser/derivation version bump, a restore, or You → Data "Rebuild": `RebuildScheduler.enqueue(workManager)` (when `Deriver.needsRebuild()`).
- New SMS: `SmsIngestor.ingest/ingestAll`. The caller resolves `lineId` with `LinesRepository.resolve` + `lineFor` (§9.2). `IngestResult.newCodes` drives alerts.
- Ingest is atomic per chunk of `Deriver.CHUNK` messages: the SMS rows and their derive commit together, and a failure rolls back the whole chunk and throws (re-deliver it). A `lineId` that doesn't exist in `lines` is a foreign-key violation that `INSERT OR IGNORE` does not cover, so it fails its whole chunk. Phase 5 must pass only existing line ids.
- SQL must run on SQLite 3.18 (API 26): no window functions or UPSERT clauses. Chunk `IN` lists to `Deriver.CHUNK`.
- Hidden rows are absent from the view and the list. `isReversed` is maintained by `Deriver` (a reversal may arrive first).
- KSP2 is required (`ksp.useKSP2=true` in `gradle.properties`): under KSP1, Room 2.8 crashes reading an exported schema.

## Startup and capture (Phase 4a)

1. `LedgaApp.onCreate` runs `PreV6Snapshot.takeIfNeeded()` before anything in the process can open the database. A file whose header already reads 6 is recognised in 100 bytes and never copied (R29).
2. `Startup.run()`, called from `AppViewModel`, opens `LedgaDatabase` (a cold start from the receiver or a worker may open it first; that is safe because the snapshot already ran in `Application.onCreate`). Opening runs `MIGRATION_5_6` on a v1 file.
   - `LedgaDatabase.builder` uses `KeepCorruptOpenHelperFactory`: Room's default deletes a corrupt file; Ledga never does.
   - A failure becomes `StartupState.Failed`. The recovery screen shares `files/pre-v6/ledga.db` with its `-wal`/`-shm` (FileProvider path `pre-v6/`), says the file holds M-Pesa messages, and offers Retry.
   - Otherwise:
     - after the migration: `BackgroundWork.afterMigration()` queues `ledga-startup` (`LegacyImportWorker` → `InboxScanWorker(FULL)` → `RebuildWorker`) and cancels v1's periodic jobs (R31). A `LegacyImportWorker` that gives up still succeeds (flagged), so the rescan and rebuild run; the staging tables stay, the next start re-queues it, and Home shows `BackgroundWork.legacyImportFailed`;
     - while that chain still runs: nothing more (its own rescan and rebuild are coming);
     - after a version bump: `rebuild()`;
     - once the migration's work is done: it deletes the pre-v6 copy — **only on proof** that this database received
       the migration (meta `migratedFromV1`, written when Startup first sees the staging tables). A copy beside a
       database without that marker routes to recovery instead: it may be the user's only copy;
     - when onboarded with SMS access: `LinesRepository.syncActive()`, then the migration's full rescan if it never
       completed (`Settings.fullRescanOwed`), else `catchUp()`.
3. "Updating your history…" reads `BackgroundWork.history`, which covers `ledga-startup`, `ledga-rebuild` and `ledga-import`.

- **Capture (from Phase 5, R26).** `SmsReceiver` and `InboxScanner` both resolve the line before `SmsIngestor`, so only existing line ids reach `sms.lineId`.
  - Inbox reads probe `sub_id`, then `sim_id`, then neither, and return nothing without READ_SMS.
  - A catch-up reads from `Settings.smsWatermarkMillis` minus 6 hours; the watermark never moves back.
  - A SIM whose subscription id changed keeps its line: an old id resolves to the line its past messages are filed under (`LinesDao.lineOfPastMessages`) before any new line is made.
  - Access granted later (dialog or Android Settings) imports the whole inbox when the user comes back.
- **WorkManager names.**
  - `WorkManagerBackgroundWork` owns `ledga-startup`, `ledga-rebuild`, `ledga-catch-up` and `ledga-import`.
  - **Never reuse v1's names** (`daily_summary`, `weekly_summary`, `insight_generation`, `update_check`): `V1Leftovers` cancels them after the migration.
  - Workers are `@HiltWorker`, with their dependencies bound in `di/DataModule`.
- **Settings.** `SettingsStore` reads v1's own DataStore file `ledga_settings` through v2 keys. `V1SettingsMigration` maps v1's values once and removes every v1 key (R15/R30). There is one DataStore per file, from `di/AppModule`.
- **Debug builds** are `com.ledga.app.dev` ("Ledga dev", R25). On a phone they import the inbox through onboarding and never touch the real install.

## Activity (Phase 4b)

- **Reads.** `LedgerQueries.transactions(TransactionFilter)` and `dayTotals(filter)` share one SQL filter (`TX_FILTER` in `dao/TxFilterSql.kt`), so a day header always sums exactly the rows under it.
  - The chips follow the spending definition (R38): Money out = SPEND, Money in = INCOME, Fuliza = any payment Fuliza touched. Transfers show under All.
  - A header's Out is spent including fees; its In is money in.
  - SQL day and month buckets add Nairobi's fixed +3 h (`NAIROBI_OFFSET_MS`, R45).
  - People (R42) are sends (`SEND`, `GLOBAL_SEND`) and receipts (`RECEIVE`, `GLOBAL_RECEIVE`), reversed ones excluded, grouped by `counterpartyKey`.
- **Writes.** `TransactionEdits` is the only writer of `overrides` and `rules` outside migration and import.
  - It merges with the existing override. After a rule change it calls `Deriver.reclassifyAll()`; after a note, hidden flag or line change, `Deriver.saveOverride`. It never touches `sms`.
  - **"Apply to all N from <name>"** (R36) writes a USER `NAME_CONTAINS` rule, replacing a USER rule with the same field and pattern, and clears the category of every override the rule will label. N is simulated with `Derivation.reclassify` under the would-be rules, so it is exactly what changes.
  - **"Only this account number"** (R35) is a `NAME_AND_ACCOUNT` rule (pattern: name, U+001F, account). `ACCOUNT_EQUALS` remains for v1's imported paybill rules.
  - **"My own account"** for all from a name (R37) is a USER `MARK_OWN_ACCOUNT` `NAME_CONTAINS` rule on the name. Its count is the payments that change (those that can be own-account and aren't already where the switch puts them); with one, the sheet changes it at once with an override. Turning it off for all deletes only that rule, clears the name's `ownAccount` overrides, and writes an `ownAccount = false` exception on any of its payments a broader rule of the person's (v1's imports bring some) still marks own: payments from other names never change.
  - **New categories** (R43): key `user_<slug>[_n]`, origin USER, icon `fluent_label`, untracked; a duplicate name in the same group reuses that category.
- **Live periods.** `LiveClock` (R34) is a Hilt singleton; `MainActivity.onResume` pokes it.

## Home and Trackers (Phase 4c)

- **Reads.**
  - `LedgerQueries.balances()` is each line's latest stated balance with its time; hidden payments count, because the
    balance is the wallet's.
  - `HomeBalance.of(readings, lineId)` adds up each line's latest under all lines, or takes the latest overall when no
    reading has a line. Under all lines it also keeps each line's part (`HomeBalance.lines`): on a two-line phone Home
    lists them under the total ("Personal ··11 · Ksh … · 7:42 PM") instead of spec §10.4's "from <line>" (owner,
    2026-10-06: the total read as that line's balance). A quiet line's money still counts; its row shows how old it is.
  - `fulizaReadings()` with `FulizaStatus.forLine(readings, lineId)` gives a line's status or the lines added up.
    Ceiling and available are known only when every line's are; the earliest due date leads.
  - `recent(lineId, categoryKey?)` is the newest five, in Activity's order.
  - `spentByPeriod(WEEK | MONTH | YEAR, range, lineId)` is keyed like `Period.key`. Weeks start on Nairobi's Monday
    (`'weekday 0', '-6 days'`).
  - `spentByCategoryMonth` and `latestSpends` feed `Trackers`. That is one reader: 13 months per tracker, the average of
    completed months from the first payment, "usually by" only for a bill paid in each of the last three months
    (`Bucketing.isMonthly`), and all-time months for the detail.
- **Line choice (R47).** `Settings.selectedLineId` through `SelectedLine` → `LineChoice.lineId`. It is null (all lines)
  on a phone with fewer than two lines or for a line that no longer exists, so a v1 choice never hides unattributed
  payments.
- **Writes.** `TransactionEdits` runs its writes one at a time (a `Mutex`, R63; `TransactionEditsTest` pauses one edit
  mid-way and proves a second waits); its counts don't lock. A write is never cancelled part way (`NonCancellable`):
  leaving the screen that asked, which cancels its ViewModel, can't strand a rule without its re-classify.
  - `setTracked` and `renameCategory` change no transaction, so nothing reclassifies. A rename refuses a blank name or one
    another category in the same group has (R51).
  - `addRule(categoryKey, name, account?)` behaves like "Apply to all" (R36, R48): a USER `NAME_CONTAINS` rule, or
    `NAME_AND_ACCOUNT` with an account, replacing a USER rule like it, with the person's own category choices cleared on
    every payment it will label. The name is stored upper-cased and needs at least two letters or digits.
    `rulePreview` counts matches, movers, and the movers the person had filed elsewhere, and names the category of the
    person's own rule for the same words that saving replaces (`RulesDao.userLike`).
  - `removeRule(id)` deletes a USER rule or switches a SYSTEM rule off (`enabled = 0`); `restoreRule` undoes either
    (R49). Restoring does not bring back category choices an earlier "apply to all" cleared.
- **Settings.** `notificationNudgeDismissed` (R59): Home's notifications banner is gone for good after "Not now".

## You, Categories & rules, Alerts (Phase 4d)

- **Alerts.** `AlertsDao` reads the `alerts` table Phase 5 writes: `observeWithTx` (newest first, with the payment's code
  while it exists and isn't hidden), `observeUnread`, `unreadKeys`/`markRead` (chunk to `Deriver.CHUNK`), and
  `insertIgnore` for Phase 5's dedupe (spec §11: nothing posts twice). `alerts.type` is an `AlertType` name (`LARGE`,
  `FULIZA_DRAW`, `FULIZA_DUE`, `DAILY`, `WEEKLY`); anything else reads as `OTHER`. Pruning after 60 days is Phase 5's.
- **Date filters.** `TransactionFilter.dates` is a `DateFilter` choice; `LedgerQueries.transactions/dayTotals(filter,
  today)` turn it into a range (`today` is required when there are dates). Presets for the current period stay
  open-ended; a `Month` is open while it is the current month; a `Custom` range covers whole Nairobi days, both ends.
- **Writes (`TransactionEdits`).**
  - `setRuleEnabled(id, enabled)` flips a rule, built-in or yours, and reclassifies; the same state changes nothing.
  - `setCategoryIcon`, `setCategoryColor`, `setArchived` act only on USER categories, and only with an icon or swatch
    from `CategoryLooks`. They change no transaction, so nothing reclassifies.
  - Archiving (R72) hides a category from the picker, the filter sheet and "Track a category" and stops tracking it;
    its payments keep it and its rules keep working. An archived name stays taken in its group (`renameCategory`), and
    `createCategory` with that name brings the archived one back.
  - `CategoryRules.forCategory(rules, key)` is what a category's screen lists: every rule filing into it, on or off;
    Own accounts lists the `MARK_OWN_ACCOUNT` rules.
- **Settings.** The notification setters clamp: `setDailySummaryMinute` keeps 0–1439, `setLargeThreshold` keeps
  Ksh 100 to Ksh 1,000,000 (cents); anything else is ignored. Phase 5 reads them.
- **Lines.** `LinesRepository.rename` refuses blank and cuts to 24. Granting phone access later runs `syncActive()`.
- **History check.** `BalanceChain.check` over `TransactionsDao.all()` (hidden rows included: the wallet moved). With two
  or more lines the group not on a line is left out of the verdict (`LineCheckUi.mixed`): its payments could be either
  line's, so its balances jump between SIMs and read as breaks (owner, 2026-10-07).

## Categories (Phase 4e)

- **Measures.** `CategoryMeasure.of(group)`: spent (spend + fees) for the four spending groups, received for Money in,
  moved for Not spending. `LedgerDao.categoryMonthTotals` returns all three per category per Nairobi month; moved is
  `ledger.amountCents` of rows whose `transactions.isReversed = 0` (the view's spend/in are 0 for those flows). Hidden
  rows never count (the `ledger` view). `spentByCategoryMonth` is gone; the trackers pick `SPENT` from the same query.
- **Readers.** `Trackers.category(key, lineId, now)` (was `detail`): the summary in the category's measure, all months,
  this year so far, `topPlaces` (`LedgerDao.topPlaces`, measure-aware, the last 12 months and this one, rows without a
  counterparty left out) and the newest payments. `Trackers.monthTotals(lineId, now)`: this month's amount for every
  category; a category with nothing has no entry.
- **Looks.** `TransactionEdits.setCategoryIcon` takes any `fluent_[a-z0-9_]+` key and `setCategoryColor` any swatch, for
  any category (D6, R91); `resetCategoryLooks` (built-in only) writes the seed's icon and clears both colours
  (`CategoriesDao.resetLooks`). Archive stays for your own categories. No schema change.

## Phase 5 acceptance step (from the 2026-10-05 Phase 1 review)

The v1 export can't prove inbox-wide coverage: v1 never stored the messages its parser rejected. So after the first full inbox rescan **on the owner's phone**, record:
- the `UNREADABLE` count, with a target of 0 confirmations;
- the History-check (`BalanceChain`) breaks, compared against the export audit (`LegacyMigrationAuditTest`).

Investigate any rise before shipping a beta.
