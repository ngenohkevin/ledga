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
2. `Startup.run()`, called from `AppViewModel`, is the first opener of `LedgaDatabase`; opening runs `MIGRATION_5_6` on a v1 file.
   - A failure becomes `StartupState.Failed`. The recovery screen shares `files/pre-v6/ledga.db` (FileProvider path `pre-v6/`) and offers Retry.
   - Otherwise:
     - after the migration: `BackgroundWork.afterMigration()` queues `ledga-startup` (`LegacyImportWorker` → `InboxScanWorker(FULL)` → `RebuildWorker`) and cancels v1's periodic jobs (R31);
     - after a version bump: `rebuild()`;
     - once nothing is owed: it deletes the pre-v6 copy;
     - when onboarded with SMS access: `LinesRepository.syncActive()` + `catchUp()`.
3. "Updating your history…" reads `BackgroundWork.history`, which covers `ledga-startup`, `ledga-rebuild` and `ledga-import`.

- **Capture (from Phase 5, R26).** `SmsReceiver` and `InboxScanner` both resolve the line before `SmsIngestor`, so only existing line ids reach `sms.lineId`.
  - Inbox reads probe `sub_id`, then `sim_id`, then neither, and return nothing without READ_SMS.
  - A catch-up reads from `Settings.smsWatermarkMillis` minus 6 hours; the watermark never moves back.
- **WorkManager names.**
  - `WorkManagerBackgroundWork` owns `ledga-startup`, `ledga-rebuild`, `ledga-catch-up` and `ledga-import`.
  - **Never reuse v1's names** (`daily_summary`, `weekly_summary`, `insight_generation`, `update_check`): `V1Leftovers` cancels them after the migration.
  - Workers are `@HiltWorker`, with their dependencies bound in `di/DataModule`.
- **Settings.** `SettingsStore` reads v1's own DataStore file `ledga_settings` through v2 keys. `V1SettingsMigration` maps v1's values once and removes every v1 key (R15/R30). There is one DataStore per file, from `di/AppModule`.
- **Debug builds** are `com.ledga.app.dev` ("Ledga dev", R25). On a phone they import the inbox through onboarding and never touch the real install.

## Phase 5 acceptance step (from the 2026-10-05 Phase 1 review)

The v1 export can't prove inbox-wide coverage: v1 never stored the messages its parser rejected. So after the first full inbox rescan **on the owner's phone**, record:
- the `UNREADABLE` count, with a target of 0 confirmations;
- the History-check (`BalanceChain`) breaks, compared against the export audit (`LegacyMigrationAuditTest`).

Investigate any rise before shipping a beta.
