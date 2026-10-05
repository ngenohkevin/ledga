# :core — Ledga's pure-Kotlin domain

Everything Ledga computes that doesn't need Android: no Android or Room imports, JVM tests only.

## Pipeline

    raw SMS ──MpesaParser.parse──▶ ParsedSms           (Phase 2 stores it as an `sms` row)
    sms rows sharing a code ──▶ List<SourceSms>
      ──Derivation.derive(sources, override, RuleEngine)──▶ DerivedTx  (= a `transactions` row)
    Reversals.reversedCodes(...) ──▶ isReversed
    Ledger      reference spending definition (Phase 2's `ledger` view must match it)
    BalanceChain per-line wallet reconciliation ("History check")
    Periods / Bucketing   Nairobi periods, live ranges, chart buckets

- Money is `Long` cents (`Money`); time is `Africa/Nairobi` (`Nairobi.ZONE`).
- Bump `MpesaParser.VERSION` or `Derivation.VERSION` on any behaviour change: Phase 2 rebuilds history when they change.

## Contracts Phase 2 must honour

- `isReversed` is recomputed by the caller (via `Reversals.reversedCodes`); `Derivation.derive` always emits `false`.
- `SmsText.normalize` and `SmsText.hash` are persisted keys (`sms.bodyHash`, UNIQUE). Changing either needs a migration that re-hashes every stored SMS.
- `MpesaParser.VERSION` and `Derivation.VERSION` trigger full rebuilds when bumped.
- R3: a rule applies only where its category's group accepts the flow; own-account flows (`OWN_OUT`/`OWN_IN`) always go to `own_accounts`; overrides are never filtered.
- `Derivation.reclassify` recomputes flow and categoryKey only. When an override's note, hidden flag or lineId changes, re-derive the code with `derive`.
- `Ledger` is the oracle: the Phase 2 `ledger` SQL view must produce the same per-row values (`PipelineGoldenTest` pins an end-to-end month).

## Tests

    ./gradlew :core:test

Fixtures are synthetic: the repo is public. The real-corpus audit reads the owner's local v1 export and is skipped when it is absent:

    ./gradlew :core:test --tests 'com.ledga.core.audit.CorpusAuditTest' --rerun
    # LEDGA_CORPUS=/path/to/data.json overrides ~/Documents/ledga-export/data.json

The audit prints masked skeletons and counts only. Never commit corpus content.
