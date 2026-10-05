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

## Tests

    ./gradlew :core:test

Fixtures are synthetic: the repo is public. The real-corpus audit reads the owner's local v1 export and is skipped when it is absent:

    ./gradlew :core:test --tests 'com.ledga.core.audit.CorpusAuditTest' --rerun
    # LEDGA_CORPUS=/path/to/data.json overrides ~/Documents/ledga-export/data.json

The audit prints masked skeletons and counts only. Never commit corpus content.
