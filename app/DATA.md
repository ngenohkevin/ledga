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

- **Capture (from Phase 5, R26).** `IncomingSms` (the receiver's pipeline) and `InboxScanner` both resolve the line before `SmsIngestor`, so only existing line ids reach `sms.lineId`.
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

- **Alerts.** `AlertsDao` reads the `alerts` table (`Notifier` writes it, 5a): `observeWithTx` (newest first, with the payment's code
  while it exists and isn't hidden), `observeUnread`, `unreadKeys`/`markRead` (chunk to `Deriver.CHUNK`), and
  `insertIgnore` for the dedupe (spec §11: nothing posts twice). `alerts.type` is an `AlertType` name (`LARGE`,
  `FULIZA_DRAW`, `FULIZA_DUE`, `DAILY`, `WEEKLY`); anything else reads as `OTHER`. `Notifier.prune` drops alerts after 60 days.
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
  Ksh 100 to Ksh 1,000,000 (cents); anything else is ignored. 5a's workers read them.
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

## Notifications (Phase 5a)

- **Alerts.** `Notifier` is the only writer of `alerts` (spec §7.1, §11). `send(alert)` inserts the row under its key
  first (`AlertsDao.insertIgnore`), then shows it when Android allows (`PhoneNotifications.allowed`: Android's switch,
  Android 13's permission and the channel's switch). A key already logged posts nothing, ever. An alert Android blocks is
  still logged (R101). `prune()` drops alerts older than 60 days (`SyncWorker`).
  - Keys: `large:<code>`, `fuliza-draw:<code>`, `fuliza-due:<lineId|none>:<due date>:<3d|0d>`, `daily:<date>`,
    `weekly:<Monday>`. What each alert says, its key and its tap come only from `AlertWords` (R111).
- **Channels (R102).** `NotifyChannels.ensure` at every start: `spending_summaries` (Summaries) and `large_transactions`
  (Large payments) keep v1's ids; `fuliza` is new; v1's `budget_alerts` is deleted. Updates join in Phase 6.
- **Live payments (spec §7.2 step 4).** `SmsReceiver` → `IncomingSms.store`: the line, `SmsIngestor`, then
  `BackgroundWork.alertsFor(newCodes, receivedAt)`, a `PaymentAlertWorker` job 30 s later (R103) that runs
  `PaymentAlerts.check(codes, receivedAt)`. Large = a non-reversed, non-hidden `SPEND` whose amount (no fees) reaches the
  threshold; a Fuliza companion whose payment SMS hasn't come is not a large payment (the payment's own alert follows,
  final review I3); a Fuliza draw = any payment Fuliza covered (R104). Only for a payment at most 2 hours old **when its
  SMS arrived**, so a job Android holds while the phone is idle alerts late rather than never (owner 2026-10-07, final
  review I4). **Inbox scans, imports, rebuilds and the 6-hourly check never alert** (spec §11).
- **Scheduled alerts (R105–R107).** `Scheduled` names the kinds and their unique work: `ledga-daily` (the person's time),
  `ledga-weekly` (Sunday 7 PM), `ledga-fuliza` (9 AM), all Nairobi time, strictly after now (`AlertTimes`). Each is
  one-time work that re-reads its switch, writes, and queues its own next run with REPLACE, last.
  - `BackgroundWork.schedule(kind, settings, replace)` cancels a kind switched off. App start and the end of onboarding
    pass `replace = false` (KEEP: a start never pushes a queued alert back); You → Notifications replaces only the kind
    it changed.
  - Summaries read the `ledger` view over all lines (`LedgerQueries.spentIn`, `biggest`): Spent includes fees, and the
    count is the payments that added to it. Nothing spent, or a run more than 12 hours late, writes nothing (R106). A
    daily summary set before noon sums up the day before, "yesterday" (`AlertTimes.summaryDay`, owner 2026-10-07).
  - Fuliza reminders: `FulizaReminders.due`, each line's status (the lines' own readings when any reading has a line),
    "3d" once while 1–3 days are left, "0d" on the day, only while something is owed (R105).
- **The 6-hourly check (R108).** `ledga-sync` (periodic, KEEP), queued once onboarded: `LinesRepository.syncActive`,
  an inbox catch-up (with SMS access), and `Notifier.prune`.
- **WorkManager names (5a).** `ledga-daily`, `ledga-weekly`, `ledga-fuliza`, `ledga-sync`, and untagged
  `PaymentAlertWorker` jobs. Never reuse v1's names (`V1Leftovers`).

## Backup, export and restore (Phase 5b)

- **One payload.** `BackupData` (format 1) is the snapshot's whole content and an export's `data.json` (R118): every SMS,
  overrides, the person's rules and the built-in ones they switched off, their categories and the built-in ones they
  changed, lines, the portable settings, a device fingerprint (a SHA-256 of Android's per-app id, never the id) and counts.
  Per-phone state (onboarded, SMS watermark, owed rescan, chosen line, banner dismissed) never travels (R114).
- **The snapshot.** `files/backup/ledga-snapshot.json.gz`, written atomically by `SnapshotStore`. `Snapshots` decides
  when: never before onboarding is done or while `sms` is empty (R119), at most hourly when Ledga leaves the screen
  (`MainActivity.onStop` → `ledga-snapshot`, R130) and in the 6-hourly check (R131), and at once after a rebuild or a
  restore. A snapshot an install didn't restore becomes `ledga-snapshot-earlier.json.gz` when onboarding ends (R120);
  every restore first writes `ledga-before-restore.json.gz` (R121), unless a retry finds its own request already written
  (meta `restoreRequest`, final review I1). A copy on the phone is restored from a copy of it in no-backup storage: a
  restore rewrites "Before your last restore" first (C1). Auto Backup and device transfer include only
  `files/backup/` (`backup_rules.xml`, `data_extraction_rules.xml`).
- **Export** (`Exporter`): a zip of `manifest.json`, `data.json` and `transactions.csv` (R124). The share copy lives in the
  cache's `exports/`; FileProvider path `exports/`.
- **Restore** (`BackupFiles` → `LineMatching` → `Restorer`, run by `RestoreWorker` as `ledga-restore`, R122). Reads a
  `.ledga` file, a snapshot or a v1 export zip by its first bytes; a backup with no messages is refused (I2). Lines: same phone by SIM id, else by number, else one
  question per line (R115). Merge fills gaps and never overrides this phone's choices; Replace clears this phone first and
  resets the built-ins (`RestoreDao`). The write is one transaction that clears the stored parser version, so a restore
  cut short is rebuilt at the next start; then `rebuildAll` and a fresh snapshot. `Restorer` is the only writer of `sms`
  besides `SmsIngestor`; a restored message from another phone keeps no SIM id. **A restore never alerts.**

## Lines (Phase 5b)

- **Placement** (R116, R128): `LinePlacements.propose()` runs `:core` `LinePlacer` over every payment (hidden ones too)
  and this phone's lines. It keeps a line's balance only while it is certain: a gap (a Fuliza companion with no
  balance), a payment it couldn't place, or one that fits no line makes the lines it could belong to unknown until
  their next anchor (final review C2), so it never guesses. `TransactionEdits.placeOnLines` writes `overrides.lineId` only for payments still not on a line
  and returns them for Undo (`unplace`, which drops override rows left empty). A date range is whole Nairobi days, both
  ends. Lines reconciliation itself is 4a's (`LinesRepository.syncActive`, at start and in the 6-hourly check).

## Phase 5 acceptance step (from the 2026-10-05 Phase 1 review)

The v1 export can't prove inbox-wide coverage: v1 never stored the messages its parser rejected. So after the first full inbox rescan **on the owner's phone**, record:
- the `UNREADABLE` count, with a target of 0 confirmations;
- the History-check (`BalanceChain`) breaks, compared against the export audit (`LegacyMigrationAuditTest`).

Investigate any rise before shipping a beta.

R99: it runs in 5b's S26 check, after bulk line assignment, so the History check covers every payment.
