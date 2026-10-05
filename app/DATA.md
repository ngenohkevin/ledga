# :app data layer (Ledga v2, schema 6)

    raw SMS ──SmsIngestor──▶ sms (bodyHash UNIQUE; status PARSED/IGNORED/UNREADABLE)
      ──Deriver.rederive(codes)──▶ transactions (one row per code, = :core DerivedTx)
      ──ledger view──▶ LedgerQueries (spent, money in, by category, balance) · TransactionsDao.page
    MIGRATION_5_6 + LegacyImporter + Deriver.rebuildAll: v1.x → v6

## Contracts for Phases 3–5

- **`LedgaDatabase` is not opened at runtime until the Phase 4 switch-over.** Two Room instances on `ledga.db` corrupt it.
- Every total reads the `ledger` view (`LedgerDao` / `LedgerQueries`). Never re-implement "spent" in another query. The view must equal `:core` `Ledger` (`LedgerViewTest`).
- Changing a transaction: `Deriver.saveOverride(override)`.
- Changing rules or categories: write the row, then `Deriver.reclassifyAll()`.
- A parser/derivation version bump, a restore, or You → Data "Rebuild": `RebuildScheduler.enqueue(workManager)` (when `Deriver.needsRebuild()`).
- New SMS: `SmsIngestor.ingest/ingestAll`. The caller resolves `lineId` (Phase 5, §9.2). `IngestResult.newCodes` drives alerts.
- SQL must run on SQLite 3.18 (API 26): no window functions or UPSERT clauses. Chunk `IN` lists to `Deriver.CHUNK`.
- Hidden rows are absent from the view and the list. `isReversed` is maintained by `Deriver` (a reversal may arrive first).
- KSP2 is required (`ksp.useKSP2=true` in `gradle.properties`): under KSP1, Room 2.8 crashes reading an exported schema.

## Phase 4 switch-over checklist

1. Delete v1's `data/db`, `di/DatabaseModule`, the v1 repositories/UI that use them, and `app/schemas/com.ledga.app.data.db.AppDatabase/`.
2. Add a Hilt module providing `LedgaDatabase.builder(context).build()`, `Deriver`, `SmsIngestor`, `LedgerQueries`, and `@HiltWorker` on `RebuildWorker`.
3. On startup, before Room opens the file: `PreV6Snapshot(context).takeIfNeeded()`.
4. After the open:
   - if `LegacyImporter.isPending()`, run it as a worker;
   - then run the full inbox rescan (Phase 5) and `rebuildAll`;
   - show "Updating your history…" from the worker progress;
   - delete the snapshot after the first successful launch **and** a completed rebuild;
   - on a failed migration show the recovery screen (share `files/pre-v6/ledga.db`, Retry).
5. Map the v1 DataStore settings to the v2 keys (spec §8 step 4, refinement R15).

## Phase 5 acceptance step (from the 2026-10-05 Phase 1 review)

The v1 export can't prove inbox-wide coverage: v1 never stored the messages its parser rejected. So after the first full inbox rescan **on the owner's phone**, record:
- the `UNREADABLE` count, with a target of 0 confirmations;
- the History-check (`BalanceChain`) breaks, compared against the export audit (`LegacyMigrationAuditTest`).

Investigate any rise before shipping a beta.
