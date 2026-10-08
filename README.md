# Ledga

An M-Pesa spending tracker for Android. Ledga reads your M-Pesa messages and shows where your money goes, with nothing
to type in.

## Install

1. On your phone, download `ledga-<version>.apk` from [Releases](https://github.com/ngenohkevin/ledga/releases/latest).
2. Open it. Android asks once to let your browser or Files app install apps: allow it.
3. Google Play Protect may say **"App scan recommended"**, because Ledga doesn't come from the Play Store. Choose
   **Scan app**: Play Protect checks it, then the install goes ahead.

After that Ledga updates itself. You → Updates shows new versions; Android may ask once more before the first update
Ledga installs.

**Coming from Ledga 1.x?** Install over it. Your history comes with you: the categories you chose, your rules, notes and
lines. Ledga then reads your inbox again to fill in payments 1.x missed.

**Betas:** turn on You → Updates → Beta updates to get new versions before everyone else.

## What it does

- **Home:** your M-Pesa balance on each line, what you owe on Fuliza, and what you've spent this week, month or year.
- **Activity:** every payment, with search and filters; where your money went, who you pay and who pays you.
- **Categories:** any icon and colour, your own rules, and month-by-month totals for the ones you track.
- **Notifications:** large payments, Fuliza and its due dates, and a daily or weekly summary.
- **Export & restore:** a full backup file, a spreadsheet, and Android's own backup of your history.
- **History check:** shows whether each line's balances add up, so a missing message stands out.
- Light and dark, at the text size you choose.

## Privacy

Everything stays on your phone: no server, no account, no analytics. Ledga reads only the messages M-Pesa and Fuliza
send, and the only thing it fetches from the internet is its own updates, from this repository's releases. An export
holds your M-Pesa messages, so keep it somewhere safe.

## Requirements

- Android 8.0 or newer
- Permission to read SMS, for the M-Pesa messages
- Notifications (Android 13 and newer), for alerts and summaries

## Building

```bash
./gradlew :app:assembleDebug                                                  # "Ledga dev" (com.ledga.app.dev), beside the real app
./gradlew :core:test :app:testDebugUnitTest -Proborazzi.test.verify=true      # every test, with the screenshots compared
./gradlew :app:assembleRelease                                                # needs the release key (below)
```

A release build signs with `keystore/ledga-release.jks`. Its password comes from the `KEYSTORE_PASSWORD` and
`KEY_PASSWORD` environment variables, or from `keystore/keystore.properties` (`storePassword=…`, `keyPassword=…`,
`keyAlias=…`). Neither is in the repository; without them the release build stops.

## Release

Ledga's version lives in `version.properties`. To release:

1. Set `VERSION_NAME` (`2.0.1`, or `2.0.1-beta.2` for a beta) and write `release-notes/<VERSION_NAME>.md`, with
   `## What's new` and `## Fixes` sections. Commit both.
2. Tag the commit `v<VERSION_NAME>` and push the tag:

```bash
git tag v2.0.1-beta.2
git push origin v2.0.1-beta.2
```

The Release workflow runs every test on macOS, refuses a tag that doesn't match `version.properties` or has no notes,
builds the signed APK, writes `ledga-release.json` (its SHA-256 and versionCode) and publishes both, with the notes as
the release's text. Betas are pre-releases, so only phones on the beta channel see them; a full release becomes Latest,
which v1 phones also update to.

To try the in-app update on a phone before releasing, see `scripts/update-test-server.sh` (Ledga dev only).

## Tech stack

| Part | Technology |
|------|------------|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Data | Room (SQLite), DataStore |
| DI | Hilt |
| Background | WorkManager, a BroadcastReceiver for incoming SMS |
| Tests | JUnit, Robolectric, Roborazzi screenshots |
| Build | Gradle (Kotlin DSL), GitHub Actions |

## License

Private — for personal/family use.
