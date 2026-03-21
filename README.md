# Ledga

M-Pesa spending tracker for Android. Automatically reads M-Pesa SMS confirmations to track your spending — zero manual entry.

## Download

Download the latest APK from [Releases](https://github.com/ngenohkevin/ledga/releases/latest).

Install directly on your Android phone (no Play Store needed). The app checks for updates automatically.

## Features

### Automatic SMS Tracking
- Reads M-Pesa SMS in real-time via BroadcastReceiver
- Supports **17 transaction types**: Send Money, Buy Goods, Pay Bill, Withdraw (Agent/ATM), Deposit, Received, Airtime (Self/Other), M-Pesa Global, Fuliza (Borrow/Repayment/Reversal), M-Shwari, KCB M-Pesa, Reversal
- Import existing SMS history on first launch
- Stores raw SMS for future parser improvements

### Smart Categorization
- 13 default categories: Groceries, Transport, Bills, Airtime, Food, Send Money, Received, Withdrawal, Deposit, Shopping, International, Savings & Loans, Other
- Auto-categorizes by merchant name and paybill/till number
- Pre-loaded rules for common merchants (Naivas, KPLC, Uber, etc.)
- Tap any transaction to re-categorize

### Home Dashboard
- Current M-Pesa balance (from last transaction)
- Monthly spending total with fees breakdown
- Animated donut chart by category
- Period selector: Today / This Week / Month
- Recent transactions feed

### Transactions
- Grouped by day with search and filters
- Filter by type: All, Sent, Received, Bills, Goods, Withdraw
- Transaction detail bottom sheet with full info
- One-tap re-categorization

### Trends & Analytics
- Daily spending bar chart (Vico)
- Category breakdown with color indicators
- Top 5 merchants by spending
- Transaction fees summary
- Period selector: 7D / 30D / 90D / 1Y

### Budget Tracking
- Set overall monthly budget
- Progress bars with color coding (green/warning/red)
- Per-category budget support

### Notifications
- Daily spending summary (default 8 PM)
- Weekly summary (Sunday evening)
- Budget warning at 80% and exceeded at 100%
- Large transaction alerts (configurable threshold)
- All toggleable in settings

### Backup & Export
- **Google Drive**: Auto-backup to app data folder, restore on fresh install
- **Manual Export**: ZIP file with `transactions.csv` (Excel-friendly) + `data.json` (full restore)
- **Manual Import**: Restore from exported ZIP
- Share exports via WhatsApp, email, Bluetooth

### Self-Update
- Checks GitHub Releases on launch (max once per day)
- Download and install updates directly from the app

### Customization
- Theme: System / Light / Dark
- Font size: System Default / Small / Medium / Large / Extra Large
- M-Pesa green primary color with Material 3

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Database | Room (SQLite) |
| DI | Hilt |
| Settings | Jetpack DataStore |
| Charts | Vico |
| Background | WorkManager + BroadcastReceiver |
| Backup | Google Drive API |
| Navigation | Navigation Compose (type-safe routes) |
| Build | Gradle (Kotlin DSL) |

## Requirements

- Android 8.0+ (API 26)
- SMS permission (to read M-Pesa messages)
- Notification permission (Android 13+, for summaries and alerts)

## Privacy

All data stays on your device. No server, no accounts, no analytics, no tracking. Google Drive backup uses the app-specific folder — invisible to other apps and only accessible by Ledga.

## Building

```bash
# Debug
./gradlew assembleDebug

# Release (requires keystore)
./gradlew assembleRelease

# Tests
./gradlew testDebugUnitTest
```

## Release

Tag a version to trigger the CI/CD pipeline:

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions builds the signed APK and creates a release automatically.

## License

Private — for personal/family use.
