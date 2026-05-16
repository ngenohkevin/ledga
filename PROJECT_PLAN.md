# Ledga — M-Pesa Spending Tracker

## Overview

A native Android app that automatically tracks M-Pesa spending by reading SMS confirmations. Zero manual entry — install, grant SMS permission, done. Built for non-technical users with a clean, Canva-like UI.

**Target users:** Family members (all Android), non-technical
**Distribution:** Direct APK install + in-app self-updates via GitHub Releases (no Play Store)
**Data model:** Fully offline, local-only per device. No accounts, no server.

---

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose + Material 3 |
| Local Database | Room (SQLite) |
| Background SMS | BroadcastReceiver + WorkManager |
| DI | Hilt (dependency injection) |
| Settings Storage | Jetpack DataStore (Preferences) |
| Charts | Vico (Compose-native charting library) |
| Notifications | Android NotificationManager + AlarmManager |
| Backup | Google Drive API (auto), manual file export |
| Self-Update | GitHub Releases API |
| Min SDK | API 26 (Android 8.0) |
| Build | Gradle (Kotlin DSL) |

---

## Architecture

```
┌─────────────────────────────────────────────┐
│                    UI Layer                  │
│         Jetpack Compose + Material 3        │
│  ┌──────┐ ┌──────────┐ ┌──────┐ ┌────────┐ │
│  │ Home │ │Transactions│ │Trends│ │Settings│ │
│  └──────┘ └──────────┘ └──────┘ └────────┘ │
├─────────────────────────────────────────────┤
│               ViewModel Layer               │
│            StateFlow + Coroutines           │
├─────────────────────────────────────────────┤
│              Repository Layer               │
│     TransactionRepo │ CategoryRepo │        │
│     SettingsRepo    │ BackupRepo   │        │
├─────────────────────────────────────────────┤
│               Data Layer                    │
│  ┌──────┐  ┌───────────┐  ┌─────────────┐  │
│  │ Room │  │SMS Parser │  │Google Drive │  │
│  │  DB  │  │           │  │  Backup     │  │
│  └──────┘  └───────────┘  └─────────────┘  │
├─────────────────────────────────────────────┤
│            System Services                  │
│  SMS BroadcastReceiver │ WorkManager        │
│  NotificationManager   │ AlarmManager       │
└─────────────────────────────────────────────┘
```

---

## SMS Parsing

### Sender Filter
Only process SMS from sender: `MPESA`

### Transaction Types & Patterns

**1. Send Money**
```
RK31B7X4ZQ Confirmed. Ksh500.00 sent to JOHN DOE 0712345678 on 21/3/26 at 1:30 PM. New M-PESA balance is Ksh1,200.00. Transaction cost, Ksh0.00.
```
→ Extract: code, amount, recipient name, recipient phone, date/time, balance, cost

**2. Buy Goods (Till)**
```
RK31B7X4ZQ Confirmed. Ksh1,200.00 paid to NAIVAS SUPERMARKET. on 21/3/26 at 2:15 PM.New M-PESA balance is Ksh3,500.00. Transaction cost, Ksh0.00.
```
→ Extract: code, amount, merchant name, date/time, balance, cost

**3. Pay Bill**
```
RK31B7X4ZQ Confirmed. Ksh2,500.00 paid to KPLC PREPAID. Account Number 12345678. on 21/3/26 at 3:00 PM. New M-PESA balance is Ksh1,000.00. Transaction cost, Ksh0.00.
```
→ Extract: code, amount, business name, account number, date/time, balance, cost

**4. Withdraw (Agent)**
```
RK31B7X4ZQ Confirmed.You have withdrawn Ksh1,000.00 from JAMES AGENT 543210 on 21/3/26 at 4:00 PM.New M-PESA balance is Ksh500.00. Transaction cost, Ksh28.00.
```
→ Extract: code, amount, agent name, agent number, date/time, balance, cost

**5. Withdraw (ATM)**
```
RK31B7X4ZQ Confirmed. You have withdrawn Ksh5,000.00 from an ATM on 21/3/26 at 4:30 PM. New M-PESA balance is Ksh2,000.00. Transaction cost, Ksh34.00.
```
→ Extract: code, amount, date/time, balance, cost
→ Note: No agent name/number — just "from an ATM"

**6. Deposit**
```
RK31B7X4ZQ Confirmed.You have deposited Ksh5,000.00 to your M-PESA account on 21/3/26 at 10:00 AM.New M-PESA balance is Ksh5,500.00.
```
→ Extract: code, amount, date/time, balance

**7. Received Money**
```
RK31B7X4ZQ Confirmed.You have received Ksh2,000.00 from JANE DOE 0798765432 on 21/3/26 at 11:00 AM.New M-PESA balance is Ksh7,500.00.
```
→ Extract: code, amount, sender name, sender phone, date/time, balance

**8. Airtime Purchase (Self)**
```
RK31B7X4ZQ Confirmed. Ksh100.00 of airtime purchased on 21/3/26 at 12:00 PM.New M-PESA balance is Ksh7,400.00. Transaction cost, Ksh0.00.
```
→ Extract: code, amount, date/time, balance, cost

**9. Airtime Purchase (For Others)**
```
RK31B7X4ZQ Confirmed. You bought Ksh100.00 of airtime for 0712345678 on 21/3/26 at 12:30 PM.New M-PESA balance is Ksh7,300.00. Transaction cost, Ksh0.00.
```
→ Extract: code, amount, recipient phone, date/time, balance, cost

**10. M-Pesa Global (International Transfer)**
```
RK31B7X4ZQ Confirmed. Ksh5,000.00 sent to JOHN DOE +44712345678 (United Kingdom) via M-PESA Global on 21/3/26 at 1:30 PM. New M-PESA balance is Ksh10,000.00. Transaction cost, Ksh150.00.
```
→ Extract: code, amount, recipient name, recipient phone (international), destination country, date/time, balance, cost

**11. Fuliza (Borrow)**
```
RK31B7X4ZQ Confirmed. Ksh500.00 sent to JOHN DOE 0712345678 on 21/3/26 at 1:30 PM. New M-PESA balance is Ksh0.00. Fuliza M-PESA amount is Ksh500.00. Fuliza M-PESA outstanding amount is Ksh500.00.
```
→ Extract: all standard fields + fuliza amount, fuliza outstanding

**12. Fuliza Repayment**
```
RK31B7X4ZQ Confirmed. You have paid Ksh200.00 to Fuliza M-PESA on 21/3/26 at 2:00 PM. Fuliza M-PESA outstanding amount is Ksh300.00.
```
→ Extract: code, amount, date/time, fuliza outstanding

**13. Fuliza Reversal**
```
RK31B7X4ZQ Confirmed. Fuliza M-PESA of Ksh500.00 has been reversed on 21/3/26 at 3:00 PM. Fuliza M-PESA outstanding amount is Ksh0.00.
```
→ Extract: code, amount, date/time, fuliza outstanding

**14. Reversal**
```
RK31B7X4ZQ Confirmed. Transaction RJ12345678 has been reversed. Your account balance is Ksh2,000.00.
```
→ Extract: code, reversed transaction code, balance

### M-Shwari & KCB M-Pesa (Acknowledged, Not Parsed)

These services send SMS from the `MPESA` sender but are savings/loan products, not spending transactions:
- **M-Shwari:** `Confirmed. Ksh1,000.00 transferred to M-Shwari account...` / `Ksh1,000.00 transferred from M-Shwari...`
- **KCB M-Pesa:** `Confirmed. Ksh2,000.00 transferred to KCB M-Pesa account...` / loan disbursements

**Strategy:** Detect these by keywords ("M-Shwari", "KCB M-Pesa") and store with type `MSHWARI` or `KCB_MPESA`. Show them in the transaction list but don't count them as spending. They're internal transfers between M-Pesa wallet and savings/loan products.

### Parser Strategy
- Regex-based pattern matching per transaction type
- Keyword detection: "sent to", "paid to", "withdrawn.*from.*AGENT" (agent), "withdrawn.*from an ATM" (ATM), "deposited", "received", "airtime purchased", "bought.*airtime for", "M-PESA Global", "reversed", "Fuliza M-PESA amount", "paid to Fuliza", "Fuliza M-PESA of", "M-Shwari", "KCB M-Pesa"
- Amount extraction: `Ksh[\d,]+\.\d{2}` pattern
- Date extraction: `\d{1,2}/\d{1,2}/\d{2,4} at \d{1,2}:\d{2} [AP]M`
- International phone detection: `\+\d{7,15}` pattern (for M-Pesa Global)
- Country extraction: text in parentheses after international phone number
- Fallback: store unparsed M-Pesa SMS with type `UNKNOWN` for later parsing improvements

---

## Database Schema (Room)

### transactions
| Column | Type | Description |
|--------|------|-------------|
| id | Long (PK, auto) | Primary key |
| transactionCode | String (unique) | M-Pesa transaction code |
| type | Enum | SEND, BUY_GOODS, PAY_BILL, WITHDRAW_AGENT, WITHDRAW_ATM, DEPOSIT, RECEIVED, AIRTIME_SELF, AIRTIME_OTHER, MPESA_GLOBAL, FULIZA, FULIZA_REPAYMENT, FULIZA_REVERSAL, MSHWARI, KCB_MPESA, REVERSAL, UNKNOWN |
| amount | Double | Transaction amount in Ksh |
| transactionCost | Double | M-Pesa fee |
| recipientName | String? | Name of recipient/merchant |
| recipientPhone | String? | Phone number (if person) |
| accountNumber | String? | Account number (paybill) |
| destinationCountry | String? | Country name for M-Pesa Global transfers |
| balance | Double | M-Pesa balance after transaction |
| direction | Enum | INFLOW, OUTFLOW (derived at parse time — simplifies totals/chart queries) |
| categoryId | Long? (FK) | Assigned category |
| fulizaAmount | Double? | Fuliza amount (if applicable) |
| fulizaOutstanding | Double? | Fuliza outstanding (if applicable) |
| rawSms | String | Original SMS text (for re-parsing) |
| timestamp | Long | Transaction timestamp (epoch ms) |
| createdAt | Long | Record creation time |

### categories
| Column | Type | Description |
|--------|------|-------------|
| id | Long (PK, auto) | Primary key |
| name | String | Category name |
| icon | String | Material icon name |
| color | String | Hex color code |
| isDefault | Boolean | System default vs user-created |

### budgets
| Column | Type | Description |
|--------|------|-------------|
| id | Long (PK, auto) | Primary key |
| categoryId | Long? (FK) | Null = overall budget |
| monthlyLimit | Double | Budget limit in Ksh |
| isActive | Boolean | Whether alerts are enabled |

### category_rules
| Column | Type | Description |
|--------|------|-------------|
| id | Long (PK, auto) | Primary key |
| categoryId | Long (FK) | Target category |
| matchType | Enum | RECIPIENT_NAME, PHONE, TILL, PAYBILL |
| matchValue | String | Value to match |

### Default Categories
| Category | Icon | Color | Auto-match examples |
|----------|------|-------|---------------------|
| Groceries | shopping_cart | #4CAF50 | NAIVAS, QUICKMART, CARREFOUR |
| Transport | directions_car | #2196F3 | Uber, Bolt, matatu paybills |
| Bills & Utilities | receipt | #FF9800 | KPLC, NAIROBI WATER, DSTV |
| Airtime & Data | phone_android | #9C27B0 | Airtime purchases |
| Food & Dining | restaurant | #E91E63 | Restaurants, fast food |
| Send Money | person | #607D8B | Person-to-person transfers |
| Received | account_balance_wallet | #00BCD4 | Money received |
| Withdrawal | atm | #795548 | Agent withdrawals |
| Deposit | savings | #8BC34A | Cash deposits |
| Shopping | shopping_bag | #FF5722 | General merchants |
| International | public | #3F51B5 | M-Pesa Global transfers |
| Savings & Loans | account_balance | #009688 | M-Shwari, KCB M-Pesa |
| Other | category | #9E9E9E | Uncategorized |

---

## Screens & UI

### Design System
- **Color palette:** M-Pesa green (#4CAF50) as primary, warm neutrals, category-specific accent colors
- **Typography:** System font, scalable (Small/Medium/Large/Extra Large + System Default)
- **Cards:** Rounded corners (16dp), subtle elevation, clean spacing
- **Animations:** Smooth transitions between screens, animated chart loading
- **Dark mode:** Full dark theme, follows system by default with manual override

### 1. Home Dashboard
```
┌─────────────────────────────────┐
│  Good morning 👋                │
│                                 │
│  M-PESA Balance                 │
│  Ksh 12,500.00                  │
│  (from last transaction)        │
│                                 │
│  ┌─────────────────────────┐   │
│  │   March 2026            │   │
│  │   Spent: Ksh 45,200     │   │
│  │   ▼ 12% vs last month   │   │
│  │   Fees: Ksh 320         │   │
│  └─────────────────────────┘   │
│                                 │
│  [Today] [This Week] [Month]   │
│                                 │
│  ┌─────────────────────────┐   │
│  │    🍩 Donut Chart       │   │
│  │    Category Breakdown    │   │
│  └─────────────────────────┘   │
│                                 │
│  Recent Transactions            │
│  ┌─────────────────────────┐   │
│  │ 🛒 Naivas    -Ksh 1,200 │   │
│  │ 📱 Airtime     -Ksh 100 │   │
│  │ 💰 Received  +Ksh 5,000 │   │
│  └─────────────────────────┘   │
│                                 │
│  ┌────┐┌────┐┌────┐┌────┐     │
│  │Home││List││Stats││ ⚙️ │     │
│  └────┘└────┘└────┘└────┘     │
└─────────────────────────────────┘
```

> **Balance display:** Shows the `balance` field from the most recent transaction. This is the last known M-Pesa balance — not a live query. Labeled "from last transaction" so users don't confuse it with a real-time check.

### 2. Transactions List
- Grouped by day with day totals
- Each item: category icon + recipient/merchant + amount (red spent, green received)
- Search bar at top
- Filter chips: All, Sent, Received, Bills, Goods, Withdraw
- Tap → transaction detail bottom sheet
- Long press → change category

### 3. Trends / Analytics
- Bar chart: daily spending for current month
- Line chart: monthly spending over time
- Top 5 recipients/merchants list
- Category comparison vs previous period
- **Transaction fees summary** — total fees paid this period (most people don't realize how much they spend on M-Pesa fees)
- Period selector: 7D / 30D / 90D / 1Y

### 4. Settings
```
Appearance
  ├── Theme: System / Light / Dark
  └── Font Size: System Default / Small / Medium / Large / Extra Large

Notifications
  ├── Daily Summary: ON/OFF + time picker
  ├── Weekly Summary: ON/OFF
  ├── Budget Alerts: ON/OFF
  └── Large Transaction Alert: ON/OFF + threshold (Ksh)

Budgets
  ├── Overall Monthly Budget: Ksh ___
  └── Per Category: [list of categories with limits]

Data
  ├── Import SMS History
  ├── Export Data (CSV/JSON)
  ├── Unparsed Messages (X) — view SMS that failed to parse
  └── Clear All Data (with confirmation)

Backup
  ├── Auto Backup: ON/OFF
  ├── Backup Frequency: Daily / Weekly
  ├── Google Account: shown
  ├── Last Backup: date
  ├── Back Up Now
  └── Restore from Backup

About
  ├── Version: 1.0.0
  ├── Check for Updates
  └── What's New
```

---

## Notification System

| Notification | Trigger | Default |
|-------------|---------|---------|
| Daily Summary | Scheduled (user picks time, default 8 PM) | ON |
| Weekly Summary | Every Sunday evening | ON |
| Budget Warning | 80% of budget reached | ON |
| Budget Exceeded | 100% of budget reached | ON |
| Large Transaction | Single transaction > threshold | OFF (default Ksh 5,000) |

Implementation:
- WorkManager for periodic summary jobs
- Immediate check on each new transaction for budget/large amount alerts
- NotificationChannel: "Spending Summaries", "Budget Alerts"

---

## Backup System

### Google Drive Auto-Backup
- Uses Google Sign-In (already on every Android phone)
- Backs up Room database + settings to app-specific Google Drive folder
- No extra permissions needed (app data folder is automatic)
- Frequency: daily or weekly (user choice)
- On fresh install: detect existing backup → prompt restore

### Manual Export/Import
- Export: generates a ZIP file containing:
  - `transactions.csv` — human-readable, can open in Excel
  - `data.json` — full database export for app restore
- Import: select ZIP file → restore all data
- Share via WhatsApp, Bluetooth, email, etc.

### SMS Re-import (Fallback)
- Scan all existing SMS from "MPESA" sender
- Parse and import, skipping duplicates (by transaction code)
- Progress indicator during import

---

## Self-Update System

### Flow
1. App checks GitHub Releases API on launch (max once per day)
2. Compares current version with latest release tag
3. If newer version exists:
   - Shows non-intrusive banner: "Update available (v1.2.0)"
   - Tap → shows changelog (from release notes)
   - "Download & Install" button
4. Downloads APK to app cache
5. Triggers Android package installer intent
6. User confirms install (standard Android prompt)

### GitHub Release Format
```
Tag: v1.0.0
Title: Version 1.0.0
Body: Changelog in markdown
Asset: ledga-v1.0.0.apk
```

### Endpoint
```
GET https://api.github.com/repos/{owner}/{repo}/releases/latest
```

---

## Auto-Categorization

### Rule Engine (Priority Order)
1. **User rules** — if user manually categorized a merchant before, remember it
2. **Category rules table** — pattern matching on recipient name, till number, paybill
3. **Transaction type fallback** — airtime → Airtime, withdrawal → Withdrawal, etc.
4. **Default** — "Other" category

### Pre-loaded Rules
| Pattern | Match Type | Category |
|---------|------------|----------|
| NAIVAS, QUICKMART, CARREFOUR, CLEANSHELF | Recipient | Groceries |
| KPLC, KENYA POWER | Recipient | Bills |
| 888880 (KPLC Prepaid), 888888 (KPLC Postpaid) | Paybill | Bills |
| NAIROBI WATER, ELDORET WATER | Recipient | Bills |
| 444400 (Nairobi Water) | Paybill | Bills |
| DSTV, GOTV, SHOWMAX | Recipient | Bills |
| UBER, BOLT, LITTLE | Recipient | Transport |
| JAVA, KFC, CHICKEN INN, PIZZA INN | Recipient | Food & Dining |
| (any self airtime) | Type | Airtime |
| (any airtime for others) | Type | Airtime |
| (any withdrawal) | Type | Withdrawal |
| (any deposit) | Type | Deposit |
| (any received) | Type | Received |
| (any send money) | Type | Send Money |
| (any M-Pesa Global) | Type | International |
| (any M-Shwari) | Type | Savings & Loans |
| (any KCB M-Pesa) | Type | Savings & Loans |

Users can add/edit rules from the category settings.

---

## Permissions Required

| Permission | Purpose | When Requested |
|-----------|---------|----------------|
| RECEIVE_SMS | Listen for new M-Pesa SMS in real-time | On first launch |
| READ_SMS | Import existing M-Pesa SMS history | When user taps "Import History" |
| POST_NOTIFICATIONS | Daily summaries, budget alerts | On first launch (Android 13+) |
| INTERNET | Google Drive backup, update checks | Automatic |
| REQUEST_INSTALL_PACKAGES | Self-update APK installation | When user taps "Install Update" |

---

## Project Structure

```
ledga/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/ledga/app/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── LedgaApp.kt
│   │   │   │   ├── di/
│   │   │   │   │   ├── AppModule.kt
│   │   │   │   │   └── DatabaseModule.kt
│   │   │   │   ├── data/
│   │   │   │   │   ├── db/
│   │   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   │   ├── Migrations.kt
│   │   │   │   │   │   ├── dao/
│   │   │   │   │   │   │   ├── TransactionDao.kt
│   │   │   │   │   │   │   ├── CategoryDao.kt
│   │   │   │   │   │   │   └── BudgetDao.kt
│   │   │   │   │   │   └── entity/
│   │   │   │   │   │       ├── Transaction.kt
│   │   │   │   │   │       ├── Category.kt
│   │   │   │   │   │       ├── Budget.kt
│   │   │   │   │   │       └── CategoryRule.kt
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── TransactionRepository.kt
│   │   │   │   │   │   ├── CategoryRepository.kt
│   │   │   │   │   │   └── SettingsRepository.kt
│   │   │   │   │   └── parser/
│   │   │   │   │       └── MpesaSmsParser.kt
│   │   │   │   ├── receiver/
│   │   │   │   │   └── SmsReceiver.kt
│   │   │   │   ├── worker/
│   │   │   │   │   ├── NotificationWorker.kt
│   │   │   │   │   ├── BackupWorker.kt
│   │   │   │   │   └── UpdateCheckWorker.kt
│   │   │   │   ├── ui/
│   │   │   │   │   ├── theme/
│   │   │   │   │   │   ├── Theme.kt
│   │   │   │   │   │   ├── Color.kt
│   │   │   │   │   │   └── Type.kt
│   │   │   │   │   ├── navigation/
│   │   │   │   │   │   └── AppNavigation.kt
│   │   │   │   │   ├── onboarding/
│   │   │   │   │   │   ├── OnboardingScreen.kt
│   │   │   │   │   │   └── OnboardingViewModel.kt
│   │   │   │   │   ├── home/
│   │   │   │   │   │   ├── HomeScreen.kt
│   │   │   │   │   │   └── HomeViewModel.kt
│   │   │   │   │   ├── transactions/
│   │   │   │   │   │   ├── TransactionsScreen.kt
│   │   │   │   │   │   ├── TransactionDetailSheet.kt
│   │   │   │   │   │   └── TransactionsViewModel.kt
│   │   │   │   │   ├── trends/
│   │   │   │   │   │   ├── TrendsScreen.kt
│   │   │   │   │   │   └── TrendsViewModel.kt
│   │   │   │   │   ├── settings/
│   │   │   │   │   │   ├── SettingsScreen.kt
│   │   │   │   │   │   ├── SettingsViewModel.kt
│   │   │   │   │   │   └── UnparsedSmsScreen.kt
│   │   │   │   │   └── components/
│   │   │   │   │       ├── TransactionCard.kt
│   │   │   │   │       ├── CategoryChip.kt
│   │   │   │   │       ├── SpendingChart.kt
│   │   │   │   │       └── BudgetProgressBar.kt
│   │   │   │   └── util/
│   │   │   │       ├── CurrencyFormatter.kt
│   │   │   │       └── DateUtils.kt
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   │       └── java/com/ledga/app/
│   │           └── parser/
│   │               └── MpesaSmsParserTest.kt
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## Build Phases

### Phase 1 — Core (MVP)
- [ ] Project setup (Kotlin, Compose, Room, Hilt, Gradle)
- [ ] SMS parser — handle all M-Pesa transaction types (including M-Pesa Global, M-Shwari, KCB M-Pesa)
- [ ] SMS parser unit tests — real SMS samples for every transaction type
- [ ] Room database — transactions, categories, category rules, migrations setup
- [ ] SMS BroadcastReceiver — real-time capture
- [ ] SMS history import
- [ ] Onboarding flow — welcome screen, SMS permission with plain-language explanation, optional history import
- [ ] Home dashboard — current balance, spending total, fees total, donut chart, recent transactions
- [ ] Transactions list — grouped by day, search, filter
- [ ] Basic auto-categorization with default rules (name + paybill/till matching)
- [ ] Bottom navigation (Home, Transactions, Trends, Settings)
- [ ] Theme system (System/Light/Dark)

### Phase 2 — Polish
- [ ] Trends screen — bar charts, line charts, top merchants, fees summary, period comparison
- [ ] Font size settings (System/Small/Medium/Large/Extra Large)
- [ ] Budget system — set limits, track progress bars
- [ ] Category management — add/edit/delete custom categories
- [ ] Transaction detail bottom sheet
- [ ] Long press to re-categorize
- [ ] Category rules editor
- [ ] Unparsed SMS viewer (Settings → Data)

### Phase 3 — Notifications & Backup
- [ ] Daily spending summary notification
- [ ] Weekly summary notification
- [ ] Budget alert notifications
- [ ] Large transaction alerts
- [ ] Notification settings (toggles, time pickers)
- [ ] Google Drive auto-backup
- [ ] Google Drive restore on fresh install
- [ ] Manual export (CSV + JSON zip)
- [ ] Manual import

### Phase 4 — Distribution
- [ ] APK signing setup — generate keystore, document secure storage
- [ ] GitHub Actions CI/CD — build release APK on push to main
- [ ] GitHub Releases self-update system
- [ ] Update check on launch
- [ ] Changelog display
- [ ] APK download + install flow
- [ ] App icon and splash screen
- [ ] Final UI polish and animations

---

## App Name

**Ledga** — a play on "ledger". Short, unique, easy to say.
- Package name: `com.ledga.app`

---

## Onboarding Flow

First launch experience for non-technical users. Only shown once — a `hasCompletedOnboarding` flag in DataStore skips it on subsequent launches.

```
┌─────────────────────────────────┐
│                                 │
│        Welcome to Ledga         │
│                                 │
│   Track your M-Pesa spending    │
│   automatically. No typing,    │
│   no manual entry.              │
│                                 │
│        [Get Started →]          │
│                                 │
└─────────────────────────────────┘
          ↓
┌─────────────────────────────────┐
│                                 │
│   Ledga reads your M-Pesa SMS  │
│   to track your spending.       │
│                                 │
│   Your messages never leave     │
│   your phone. Everything stays  │
│   private and offline.          │
│                                 │
│     [Allow SMS Access →]        │
│                                 │
└─────────────────────────────────┘
          ↓
┌─────────────────────────────────┐
│                                 │
│   Import your M-Pesa history?  │
│                                 │
│   We can scan your existing     │
│   M-Pesa messages to build     │
│   your spending history.        │
│                                 │
│   [Import History]              │
│   [Skip — start fresh]         │
│                                 │
│   ┌─────────────────────┐      │
│   │ ████████░░ 67%      │      │
│   │ Found 342 transactions│      │
│   └─────────────────────┘      │
│                                 │
└─────────────────────────────────┘
          ↓
┌─────────────────────────────────┐
│                                 │
│         You're all set!         │
│                                 │
│   Ledga will now track your     │
│   M-Pesa spending in the       │
│   background.                   │
│                                 │
│     [Go to Dashboard →]        │
│                                 │
└─────────────────────────────────┘
```

---

## Testing Strategy

### Unit Tests (Critical)
- **SMS Parser** — test every transaction type with real M-Pesa SMS samples
  - At least 3 variations per type (different amounts, names, edge cases)
  - Malformed messages (truncated SMS, missing fields)
  - Messages from non-M-Pesa senders (should be ignored)
  - Fuliza messages embedded in send/buy goods SMS
  - M-Pesa Global with various country formats
  - M-Shwari/KCB M-Pesa detection
  - Amount parsing edge cases: `Ksh1.00`, `Ksh100,000.00`
- **Auto-categorization** — rule matching priority, paybill matching, fallback behavior
- **CurrencyFormatter** — Ksh formatting, large numbers, zero amounts

### Integration Tests
- Room database — DAO queries, migrations, duplicate transaction code handling
- SMS import — bulk parsing performance, duplicate skipping

### Manual Testing
- Collect real M-Pesa SMS from family members (anonymized) as test fixtures
- Test on low-end Android devices (target audience likely has budget phones)

---

## APK Signing & CI/CD

### Keystore
- Path: `mobile/ledga/keystore/ledga-release.jks` (gitignored)
- Key alias: `ledga`
- Generate a release keystore and store it securely
- **CRITICAL:** Back up the keystore — if lost, existing installs can never receive updates (Android requires consistent signing). The VPS backup job does not cover this file; back it up manually to a password manager or offline storage.
- Store keystore password and key alias in a secure location (not in the repo)

### Build Commands
```bash
cd ~/dev/mobile/ledga

# Debug APK
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk

# Release APK (signed, requires keystore + secrets)
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk

# Tests
./gradlew testDebugUnitTest
```

### Release a Version
```bash
git tag v1.0.0 && git push origin v1.0.0
# GitHub Action builds signed APK and creates release automatically
```

### GitHub Actions
```yaml
# .github/workflows/release.yml
# Triggered on push to main or manual dispatch
# Steps:
#   1. Checkout code
#   2. Set up JDK 17
#   3. Decode keystore from GitHub secret
#   4. Build release APK (assembleRelease)
#   5. Sign APK
#   6. Create GitHub Release with APK asset
```

### GitHub Secrets Required
| Secret | Purpose |
|--------|---------|
| `KEYSTORE_BASE64` | Base64-encoded release keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias name |
| `KEY_PASSWORD` | Key password |

---

## Database Migrations

Schema changes are inevitable as the app evolves. Room requires explicit migrations to preserve user data.

### Strategy
- **Never use `fallbackToDestructiveMigration()`** — this destroys all user data on schema change
- Define all migrations in `Migrations.kt` as `Migration(oldVersion, newVersion)` objects
- Test every migration path with Room's `MigrationTestHelper`
- Keep a version history:

| Version | Changes |
|---------|---------|
| 1 | Initial schema (transactions, categories, budgets, category_rules) |

### Example Migration Pattern
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN newField TEXT")
    }
}
```

---

## Notes
- All data stays on device — no external server, no analytics, no tracking
- SMS parsing must be thoroughly tested with real M-Pesa SMS variations
- Fuliza messages have extra fields — parser must handle gracefully
- Fuliza has 3 distinct message types: borrow (Fuliza M-PESA amount), repayment (paid to Fuliza), and reversal — each has a different format
- Some M-Pesa SMS formats may vary slightly — keep raw SMS stored for re-parsing
- Transaction code uniqueness prevents duplicate imports
- Multi-SIM: some users have two M-Pesa lines — consider adding a `simSlot` or `phoneNumber` field on transactions (extracted from SMS subscription ID) if multi-line tracking is needed later
- Google Drive backup uses `appDataFolder` scope — backups are invisible to the user in Drive. This is by design (no extra permissions), but users should know they won't find the file browsing Drive manually
- The `direction` column (INFLOW/OUTFLOW) is set at parse time based on transaction type, avoiding repeated derivation in every query for totals and charts
