# Ledga — Major Redesign Spec (v2)

> Target visual direction: **Modern Fintech Bento**. Soft neutral surfaces, M-Pesa green as a single accent, big confident numbers, generous whitespace, rounded everything. Feels at home next to Revolut / Cash App / Monzo while staying friendly for a Kenyan family audience.
>
> Scope: full UI overhaul + four new feature areas:
> 1. **Insights** — smart, derived suggestions (recurring detection, fee tips, anomaly callouts)
> 2. **Goals** — savings targets with progress + ETA
> 3. **Multi-SIM** — switch between two M-Pesa lines on a dual-SIM device
> 4. **In-app update flow** — surface release notes + one-tap install when a new APK is published

---

## 1. Design Tokens

These are the canonical names. The Compose `Color.kt` / `Theme.kt` files should mirror them 1:1. (They're also already saved into the pencil document via `set_variables`, so when Pencil starts cooperating we can wire them up as variables there too.)

### 1.1 Color — Light

| Token                | Hex       | Use                                                    |
|----------------------|-----------|--------------------------------------------------------|
| `--bg`               | `#FAFAF7` | Page background (warm off-white, not stark)            |
| `--surface`          | `#FFFFFF` | Card / sheet background                                |
| `--surface-2`        | `#F2F2EE` | Subtle elevation, chip backgrounds                     |
| `--ink`              | `#0E1311` | Primary text, big numbers                              |
| `--ink-2`            | `#3A4240` | Body text                                              |
| `--muted`            | `#6B7270` | Secondary text, captions                               |
| `--muted-2`          | `#A4A8A5` | Disabled, hints                                        |
| `--line`             | `#E8E8E2` | Dividers, hairlines                                    |
| `--accent`           | `#10A37F` | M-Pesa-ish green, but de-saturated for premium feel    |
| `--accent-soft`      | `#E2F3EC` | Accent tints (chip backgrounds, progress tracks)       |
| `--accent-deep`      | `#0E7C5F` | Accent text on light, hover states                     |
| `--ink-on-accent`    | `#FFFFFF` | Text on accent surfaces                                |
| `--danger`           | `#D14B4B` | Destructive, over-budget, errors                       |
| `--danger-soft`      | `#FBE8E8` | Danger backgrounds                                     |
| `--warning`          | `#E7A82C` | "Budget at 80%", fuliza outstanding                    |
| `--warning-soft`     | `#FBEFD2` | Warning backgrounds                                    |
| `--inflow`           | `#1F8B5A` | Received / deposit / refund amounts                    |
| `--outflow`          | `#0E1311` | Outflow uses `--ink` (not red) — red is for alerts only|

### 1.2 Color — Dark

Same token names, different values. Designed for OLED-friendly true-blacks while keeping the accent vivid.

| Token                | Hex       |
|----------------------|-----------|
| `--bg`               | `#0A0C0B` |
| `--surface`          | `#15191B` |
| `--surface-2`        | `#1E2326` |
| `--ink`              | `#F4F6F5` |
| `--ink-2`            | `#C9CFCC` |
| `--muted`            | `#8E948F` |
| `--muted-2`          | `#5A615D` |
| `--line`             | `#262B2D` |
| `--accent`           | `#3CD9A8` | brighter on dark for pop                |
| `--accent-soft`      | `#0F2A23` |
| `--accent-deep`      | `#85F2C8` |
| `--ink-on-accent`    | `#06120E` |
| `--danger`           | `#FF7A7A` |
| `--danger-soft`      | `#2A1414` |
| `--warning`          | `#FFC25E` |
| `--warning-soft`     | `#2A2009` |
| `--inflow`           | `#3CD9A8` |
| `--outflow`          | `#F4F6F5` |

### 1.3 Category palette (same on both themes — saturated enough to stay distinct)

```
Groceries     #4CAF50   Transport     #3478F6   Bills         #FF9F2D
Airtime/Data  #A155F0   Food          #EC4072   Send Money    #6E7B8B
Received      #06B6D4   Withdrawal    #8B5A3C   Deposit       #7CC242
Shopping      #FF6A3D   International #4F58D9   Savings/Loans #089B91
Other         #9E9E9E
```

Always paired with a 12% tint as the icon-tile background, full color for the icon glyph itself.

### 1.4 Typography

Font: **Inter** (variable) everywhere. Numbers use `font-feature-settings: "tnum"` (tabular figures) so balances align in columns.

| Style          | Size | Weight | Line | Letter-spacing | Use                                              |
|----------------|------|--------|------|-----------------|--------------------------------------------------|
| Display XL     | 56   | 700    | 1.05 | -1.5            | Onboarding hero amounts (rare)                   |
| Display L      | 44   | 700    | 1.05 | -1.0            | Home "Spent this month"                          |
| Display M      | 32   | 700    | 1.1  | -0.5            | Goal current amount, trend hero                  |
| Title L        | 24   | 700    | 1.2  | -0.2            | Screen titles (Home, Transactions, …)           |
| Title M        | 20   | 600    | 1.25 | -0.1            | Section headers, sheet titles                    |
| Title S        | 17   | 600    | 1.3  | 0               | Card titles                                      |
| Body L         | 16   | 500    | 1.4  | 0               | Transaction recipient, primary body              |
| Body M         | 14   | 500    | 1.4  | 0               | Secondary body, descriptions                     |
| Caption        | 12   | 500    | 1.3  | 0.1             | Day labels, meta info                            |
| Overline       | 11   | 600    | 1.3  | 0.5 (uppercase) | Tab labels, section eyebrows                     |
| Mono S         | 13   | 500    | 1.4  | 0               | Transaction codes (use system mono)              |

### 1.5 Spacing scale

`4 · 8 · 12 · 16 · 20 · 24 · 32 · 48 · 64`

- Screen horizontal padding: **20**
- Card inner padding: **20** (compact cards 16)
- Section vertical gap: **24**
- Tight group vertical gap: **12**

### 1.6 Radius

`xs:8 · sm:12 · md:16 · lg:24 · xl:32 · pill:999`

- Cards: **24** (lg)
- Sheets: **32 top-only**
- Inputs / chips: **pill**
- Tab bar pill: **36**
- Icon tiles: **14**

### 1.7 Elevation

No drop shadows on light theme — depth comes from **1px `--line` borders + subtle surface-2 wells**. On dark theme, no shadows either — depth via surface stepping (`--surface` over `--bg`).

The only exception: the floating tab bar uses a soft drop shadow on light (y:8, blur:24, color: `#0E1311 @ 6%`).

### 1.8 Motion

| Token            | Duration | Easing                            | Use                                |
|------------------|----------|-----------------------------------|------------------------------------|
| `motion-fast`    | 120ms    | `cubic-bezier(0.2, 0, 0, 1)`     | Press states, ripples              |
| `motion-base`    | 240ms    | `cubic-bezier(0.2, 0, 0, 1)`     | Sheet open, chip select            |
| `motion-emph`    | 360ms    | `cubic-bezier(0.2, 0, 0, 1)`     | Screen enter, hero number flip     |
| `motion-spring`  | spring(stiffness=300, damping=30) | Tab switch, FAB scale         |

Numbers (balances, totals) **animate from old → new value** when changed (counting up/down). Spec: 600ms `easeOutCubic`. Use Compose `animateFloatAsState` + `String.format`.

---

## 2. Component Inventory

### 2.1 Atoms

- **Avatar** 40 / 32 / 24 — circular, initial inside on `--surface-2`. Used in account switcher, transaction rows for person-to-person.
- **CategoryIcon tile** 40×40 — 14-radius rounded square, category color @ 12% bg, glyph in full color.
- **Chip / Filter** — pill, 32 height, 12px horizontal padding, `--surface-2` bg, `--ink` text. Selected: `--accent-soft` bg, `--accent-deep` text, 1px `--accent` border.
- **Switch** — Material 3 with `--accent` selected track.
- **TextField** — 56 height, pill radius, `--surface-2` bg, no outline. Floating label collapses on focus.
- **Button** — Primary (filled accent, pill, 52h), Secondary (1px `--line`, `--surface`), Ghost (text only), Danger (filled `--danger`).
- **Badge** — 20h pill, 8px horizontal, caption text. Variants: neutral, success, warning, danger.
- **ProgressBar** — 8h pill, `--surface-2` track, `--accent` fill. Over-budget: `--danger` fill.
- **ProgressRing** — 56 / 80 / 120 sizes, 6 stroke, same color rules as ProgressBar.

### 2.2 Molecules

- **TransactionRow**

  ```
  ┌────────────────────────────────────────────────┐
  │ [🛒]  Naivas Supermarket            -Ksh 1,200 │
  │       Groceries · 2:15 PM            Bal 3,500 │
  └────────────────────────────────────────────────┘
  ```
  Icon (40), middle stack (recipient L, meta M muted), right stack (amount L, balance Caption muted). 16px vertical padding, no borders between rows — separated only by 12px gap inside a card.

- **StatCard** — title (Overline muted), value (Display M ink), delta chip (Caption with ▲/▼).
- **BentoTile** — generic 24-radius card with optional header row (icon + title + trailing chevron) and content slot.
- **DayHeader** — sticky inside the transactions list. "Today · Ksh 3,420" left, weekday caption right.
- **AccountChip** — used in Home header for multi-SIM. Avatar + carrier + chevron, opens AccountSwitcherSheet.
- **InsightCard** — illustrated, 24-radius, accent-soft bg, icon top-left, title + body, optional CTA.
- **GoalCard** — ProgressRing left, name + amount stack right, ETA badge bottom.

### 2.3 Organisms

- **TopBar** — sticky on every screen. Three slots: leading (Avatar + greeting OR back button), title (only on detail screens), trailing (icons: notifications, settings).
- **PillTabBar** — floating, bottom-anchored, 4 tabs: Home / Activity / Insights / You.
  - Spec from mobile-app guideline: container padding 12 top / 21 sides+bottom, pill 62h with 36 radius, items vertical (18 icon + 10 uppercase label), active = `--accent` fill + `--ink-on-accent`.
  - Slots: Home, Activity (combines Transactions + Trends in one screen with segmented top), Insights, You (profile/settings/goals).
  - FAB-like + center action removed to keep the bar honest — actions belong inside screens.

- **BottomSheet** — radius 32 top-only, 4-wide 40-long grab handle, 24px padding, max 90% screen height.

- **EmptyState** — 240 illustrated SVG centered, Title M, Body M muted, single primary CTA.

---

## 3. Information Architecture

Old IA: Home / Transactions / Trends / Settings (4 tabs).
New IA: **Home / Activity / Insights / You** (4 tabs) — but with more inside.

```
┌─ Home                — overview, hero spend, quick stats, recent
│
├─ Activity            — segmented [ Transactions · Trends ]
│   ├─ Transactions    — day-grouped list, search, filter chips
│   └─ Trends          — charts, top merchants, fees, heatmap
│
├─ Insights            — smart cards (auto-generated)
│   • recurring detection
│   • fee tips
│   • spending anomalies
│   • category trends
│
└─ You                 — profile + grouped settings + goals
    ├─ Accounts        — multi-SIM management
    ├─ Goals           — list + add
    ├─ Budgets
    ├─ Appearance
    ├─ Notifications
    ├─ Data
    ├─ Backup
    └─ About / Updates
```

**Why this IA**:
- "Activity" merges Transactions + Trends because users always toggle between "what happened" and "what's the pattern" — they shouldn't be two tabs apart.
- "You" pulls in Goals because goals are personal, not screen-scoped. It also hosts Accounts (multi-SIM).
- "Insights" earns top-level because the feature only works if users see it without going looking.

---

## 4. Screen Specs

For each screen below: **status bar 62**, then **wrapper** with horizontal padding `--pad-screen` (20) and gap `--gap-7` (24) between sections, then **pill tab bar** floating at the bottom (where shown).

Bottom padding on the scroll content: **120** (tab bar height 62 + 21 padding + 32 breathing room) so the last item is never hidden behind the bar.

### 4.1 Onboarding (4 steps, swipeable + dotted progress)

**Step 1 — Welcome**
```
┌──────────────────────────────────────────────┐
│                                              │
│                                              │
│                  ●  ○  ○  ○                  │
│                                              │
│                  ┌──────┐                    │
│                  │  L   │  (logo, 88px)     │
│                  └──────┘                    │
│                                              │
│           Track every M-Pesa shilling.       │
│            Automatically.                    │
│                                              │
│   Ledga reads your M-Pesa SMS and turns     │
│   it into a calm, private spending          │
│   picture. Nothing leaves your phone.       │
│                                              │
│                                              │
│   ┌────────────────────────────────────┐    │
│   │            Get started        →    │    │
│   └────────────────────────────────────┘    │
│                                              │
│              I'll set this up later          │
└──────────────────────────────────────────────┘
```
- Hero text: Title L
- Body: Body L muted, max-width ~320
- Primary CTA: 52h pill filled accent
- "Set up later" = Ghost button, only proceeds with read-only mode

**Step 2 — SMS permission (the trust moment)**
```
┌──────────────────────────────────────────────┐
│              ●  ●  ○  ○                      │
│                                              │
│   ┌──────┐                                   │
│   │ 📩   │  (icon tile, 56)                 │
│   └──────┘                                   │
│                                              │
│   Let Ledga read M-Pesa SMS                  │
│                                              │
│   • Only messages from MPESA are read        │
│   • Nothing is sent to a server              │
│   • You can revoke this in system settings   │
│                                              │
│   ┌──────────────────────────────────────┐  │
│   │ 🔒  Your data is on this device only │  │
│   │     and is backed up to your Google  │  │
│   │     Drive if you turn it on.         │  │
│   └──────────────────────────────────────┘  │
│                                              │
│   ┌────────────────────────────────────┐    │
│   │           Allow SMS access     →   │    │
│   └────────────────────────────────────┘    │
│                                              │
│              Not now                         │
└──────────────────────────────────────────────┘
```
- The accent-soft callout card under the bullets is the "trust signal" — keep it.
- "Not now" routes to a minimal manual-entry mode (out of scope this release, just a stub screen).

**Step 3 — Import history**
```
┌──────────────────────────────────────────────┐
│              ●  ●  ●  ○                      │
│                                              │
│   Bring in your history?                     │
│                                              │
│   We can scan the M-Pesa messages already    │
│   on your phone to build a full picture.     │
│                                              │
│   ┌──────────────────────────────────────┐  │
│   │ 📊  342 M-Pesa messages found        │  │
│   │     Going back to Aug 2024           │  │
│   └──────────────────────────────────────┘  │
│                                              │
│   ┌────────────────────────────────────┐    │
│   │       Import all 342 messages  →   │    │
│   └────────────────────────────────────┘    │
│                                              │
│              Start fresh                     │
│                                              │
│   ─────  while importing  ─────              │
│                                              │
│   ┌──────────────────────────────────────┐  │
│   │  Importing…                          │  │
│   │  ▓▓▓▓▓▓▓▓▓░░░░░░  68%               │  │
│   │  231 of 342 messages                 │  │
│   └──────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```
- Show found-count BEFORE asking, so the user can decide informedly.
- Progress card replaces the CTA when import is running, then auto-advances.

**Step 4 — Multi-SIM detection (NEW, optional)**

Only shown if two active M-Pesa lines are detected via subscription IDs in the SMS data.

```
┌──────────────────────────────────────────────┐
│              ●  ●  ●  ●                      │
│                                              │
│   We spotted two M-Pesa lines                │
│                                              │
│   You can name them so it's clear which is   │
│   which. Both are tracked separately and you │
│   can switch from the home screen anytime.   │
│                                              │
│   ┌──────────────────────────────────────┐  │
│   │ ⓅⓛⓁ Line 1    Safaricom · 07XX…23 │  │
│   │ Name  ┌──────────────────────────┐   │  │
│   │       │ Personal               × │   │  │
│   │       └──────────────────────────┘   │  │
│   └──────────────────────────────────────┘  │
│                                              │
│   ┌──────────────────────────────────────┐  │
│   │ ⓆⓀ  Line 2    Safaricom · 07XX…87 │  │
│   │ Name  ┌──────────────────────────┐   │  │
│   │       │ Business               × │   │  │
│   │       └──────────────────────────┘   │  │
│   └──────────────────────────────────────┘  │
│                                              │
│   ┌────────────────────────────────────┐    │
│   │              You're set       →    │    │
│   └────────────────────────────────────┘    │
└──────────────────────────────────────────────┘
```

### 4.2 Home

```
┌──────────────────────────────────────────────┐
│ Status bar                                   │
├──────────────────────────────────────────────┤
│                                              │
│ ⒶPersonal ▾           🔔  ⚙                │  ← AccountChip + 2 icon buttons
│                                              │
│ Hi, Kevin                                    │  Title L
│ Here's how March is going.                   │  Body M muted
│                                              │
│ ┌──────────────────────────────────────────┐ │  ← HERO bento card
│ │ SPENT THIS MONTH                       › │ │  overline muted
│ │                                          │ │
│ │ Ksh 45,200                               │ │  Display L
│ │ ▼ 12% vs February   ·   Ksh 320 in fees  │ │  Body M w/ inline chip
│ │                                          │ │
│ │ ▓▓▓▓▓▓▓▓▓▓▓▓░░░  78% of 60,000 budget    │ │  progress
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────┐ ┌──────────────────┐   │  ← 2-up small bentos
│ │ BALANCE          │ │ TOP CATEGORY     │   │
│ │ Ksh 12,500       │ │ 🛒  Groceries    │   │
│ │ from last tx     │ │ Ksh 18,200       │   │
│ └──────────────────┘ └──────────────────┘   │
│                                              │
│ ┌──────────────────────────────────────────┐ │  ← Donut bento
│ │ Where it went                          ›│ │
│ │                                          │ │
│ │      ○─◐─●                  Groceries 40%│ │
│ │     ╱ donut ╲                Transport 18│ │
│ │    │  45,200 │               Bills    14│ │
│ │     ╲       ╱                Food     11│ │
│ │      ─────                   Other     17│ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │  ← Insights teaser
│ │ ✨ INSIGHTS                             ›│ │
│ │                                          │ │
│ │ You're spending 40% more on transport    │ │
│ │ this week. 3 Uber trips, all weekends.   │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ Recent activity                       See all│  ← section header
│ ┌──────────────────────────────────────────┐ │
│ │ [🛒] Naivas               −Ksh 1,200    │ │
│ │      2:15 PM                Bal 3,500    │ │
│ │ ─────────────────────────────────────    │ │  hairline
│ │ [📱] Airtime                −Ksh 100     │ │
│ │      12:00 PM                            │ │
│ │ ─────────────────────────────────────    │ │
│ │ [💰] Jane Doe              +Ksh 5,000    │ │  inflow color
│ │      11:00 AM                            │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ (UPDATE BANNER, conditional, see §4.10)      │
│                                              │
│   ⌒  ⌒  ⌒  ⌒    pill tab bar floating       │
└──────────────────────────────────────────────┘
```

Behaviors:
- The hero `Spent` number animates from last value to new on receipt of a new SMS.
- Tapping the hero card → Trends (Activity tab → Trends segment).
- Tapping "Balance" small card → reveals "balance history" sparkline in-place (300ms expand, doesn't navigate).
- Tapping "Top category" → filter Transactions list by that category.
- Donut card uses Vico segments; legend is right-justified.
- Insights teaser shows the highest-priority unresolved insight; rotates daily.

### 4.3 Activity — Transactions segment

```
┌──────────────────────────────────────────────┐
│ ‹                                            │
│ Activity                                     │
│                                              │
│ ┌──────────────────────────────────────────┐ │
│ │ [ Transactions ]   Trends                │ │  ← segmented (pill)
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │
│ │ 🔍  Search merchants, codes…             │ │  ← search input
│ └──────────────────────────────────────────┘ │
│                                              │
│ All · Sent · Received · Bills · Goods …  ‹ ›│  ← horizontal chip row
│                                              │
│ ┌──────────────────────────────────────────┐ │
│ │ TODAY · Out 3,420 · In 5,000             │ │  ← sticky day header
│ │                                          │ │
│ │ [🛒] Naivas               −Ksh 1,200     │ │
│ │ [📱] Airtime               −Ksh 100      │ │
│ │ [💰] Jane Doe             +Ksh 5,000     │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │
│ │ YESTERDAY · Out 2,150                    │ │
│ │ [🍔] KFC Westgate         −Ksh 850       │ │
│ │ [🚗] Uber                  −Ksh 1,300    │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │
│ │ WED 18 MAR                               │ │
│ │ [⚡] KPLC                  −Ksh 2,500    │ │
│ │      Account 12345678                    │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│   pill tab bar                               │
└──────────────────────────────────────────────┘
```

Interactions:
- Tap row → TransactionDetailSheet (§4.9).
- Long-press row → quick category picker (mini sheet).
- Filter chips multi-select; the row scrolls horizontally; "Customize…" at end opens the full filter sheet.
- Search ranks by transaction code prefix > merchant name fuzzy > recipient phone.

### 4.4 Activity — Trends segment

```
┌──────────────────────────────────────────────┐
│ Activity                                     │
│                                              │
│ [ Transactions ]   [ Trends ]                │
│                                              │
│ ┌──────────────────────────────────────────┐ │
│ │  7D    [ 30D ]    90D    1Y    Custom   │ │  ← period selector pill
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │  ← Hero compare bento
│ │ NET CASHFLOW                             │ │
│ │                                          │ │
│ │ −Ksh 39,880   ▼ 8% vs prev 30D           │ │
│ │ Out 45,200    In 5,320                   │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │  ← Daily bars
│ │ Daily spending                           │ │
│ │                                          │ │
│ │  ▁▂█▃▅▂▁▄▆▂▁▃▅▇▂▁▃▆▄▂▃▅▇▃▁▂▄▆▂▁         │ │
│ │  1   5   10   15   20   25   30          │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │  ← Heatmap calendar (NEW)
│ │ Spending heatmap                       › │ │
│ │                                          │ │
│ │ M T W T F S S                            │ │
│ │ □ □ ▤ ▤ ▥ ▦ ■                            │ │  
│ │ ▤ ■ ▦ ▤ ■ ▦ ▥                            │ │  4 intensity steps
│ │ ▦ ▤ ▥ ■ ▤ □ ▦                            │ │
│ │ ▤ ▥ ▦ ▤ ▤ ■ ▥                            │ │
│ │ ▦ ▤ □ ▥ □ ▤ ▦                            │ │
│ │ low ▢▢▢▢ high                            │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │  ← Top merchants
│ │ Top merchants                          › │ │
│ │ 🛒 Naivas         5 visits  Ksh 8,400    │ │
│ │ 🚗 Uber           4 trips   Ksh 5,200    │ │
│ │ 🍔 KFC            3 visits  Ksh 2,550    │ │
│ │ ⚡ KPLC           1 bill    Ksh 2,500    │ │
│ │ 📱 Airtime        9 buys    Ksh 900      │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │  ← Fees summary (NEW)
│ │ 💸 FEES PAID                             │ │
│ │ Ksh 320   over 30 days                   │ │
│ │ ▲ 12% vs previous 30D                    │ │
│ │ Most fees came from Withdraw (Ksh 215)   │ │
│ └──────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘
```

The heatmap is the high-value new visual — turns "you spent more last Friday" into something instantly readable. 4 intensity steps using `--accent` opacity 8/24/56/88%.

### 4.5 Insights (NEW)

Algorithmically generated. Each card has a "Got it" button (dismiss for 30 days) and optional secondary action (e.g., "See transactions").

```
┌──────────────────────────────────────────────┐
│ Insights                                     │
│ 4 new this week                              │
│                                              │
│ ┌──────────────────────────────────────────┐ │  ← Anomaly insight
│ │ 🚨  WATCH OUT                            │ │
│ │ You spent 40% more on transport this    │ │
│ │ week than your typical week.             │ │
│ │ 3 Uber trips on weekends · Ksh 4,800     │ │
│ │ [ See transactions ]   [ Got it ]        │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │  ← Recurring detection
│ │ 🔁  RECURRING DETECTED                   │ │
│ │ KPLC seems to be a monthly bill.         │ │
│ │ Avg Ksh 2,420 · last 3 months            │ │
│ │ [ Add to budget ]    [ Got it ]          │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │  ← Fee tip
│ │ 💡  FEE TIP                              │ │
│ │ You paid Ksh 215 in agent withdrawal     │ │
│ │ fees this month. Withdrawing fewer,      │ │
│ │ larger amounts could save ~Ksh 90.       │ │
│ │ [ Show me how ]      [ Got it ]          │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │  ← Positive nudge
│ │ ✨  NICE                                 │ │
│ │ Groceries spending is down 14% this      │ │
│ │ month. Keep it up!                       │ │
│ │                                          │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │  ← Fuliza warning
│ │ ⚠  FULIZA                                │ │
│ │ Outstanding Fuliza: Ksh 500              │ │
│ │ Last borrowed 3 days ago.                │ │
│ │ [ How Fuliza fees work ]                 │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ Want fewer of these?  Settings → Insights    │
│                                              │
└──────────────────────────────────────────────┘
```

Card variants (background colors):
- **Watch out / anomaly**: warning-soft bg, warning icon
- **Recurring**: surface bg, accent icon
- **Fee tip**: accent-soft bg
- **Positive nudge**: accent-soft bg, sparkles icon
- **Fuliza / danger**: danger-soft bg, danger icon

Generation cadence: re-evaluate on every new transaction (cheap rules) + weekly batch (heavier comparisons). All logic local, deterministic — no ML.

### 4.6 Goals (NEW, lives under "You")

```
┌──────────────────────────────────────────────┐
│ ‹ You                                        │
│ Goals                                    +   │
│                                              │
│ ┌──────────────────────────────────────────┐ │  ← Active goal card
│ │  ◖◗  School fees                         │ │
│ │ 67%  Term 2                              │ │
│ │      Ksh 40,000 / 60,000                 │ │
│ │      ▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░                 │ │
│ │      ETA 14 Apr · on track ✓             │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │
│ │  ◖◗  Pi server budget                    │ │
│ │ 32%  Hardware fund                       │ │
│ │      Ksh 8,000 / 25,000                  │ │
│ │      ▓▓▓▓▓▓░░░░░░░░░░░░░░                 │ │
│ │      ETA 12 Jul · slightly behind ⚠      │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │
│ │  ◖◗  Eldoret trip                        │ │
│ │ 100% Completed Mar 4 🎉                  │ │
│ │      Ksh 15,000 / 15,000                 │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ Suggested by your activity:                  │
│                                              │
│ ┌──────────────────────────────────────────┐ │
│ │ 💡  You've moved Ksh 5,000 to M-Shwari   │
│ │     each month for 3 months. Turn that   │ │
│ │     into a goal?                         │ │
│ │     [ Create goal ]                      │ │
│ └──────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘
```

**Goal model**:

```kotlin
data class Goal(
  val id: Long,
  val name: String,
  val targetAmount: Double,
  val targetDate: LocalDate?,        // nullable → no ETA, only progress
  val contributionRule: ContributionRule,
  val createdAt: Long,
  val completedAt: Long?,
)

sealed class ContributionRule {
  // count every M-Shwari/KCB deposit as contribution
  object AllSavingsDeposits : ContributionRule()
  // count outflows to a specific paybill (e.g. school fees paybill)
  data class ToPaybill(val paybill: String) : ContributionRule()
  // user manually marks a transaction as contributing
  object Manual : ContributionRule()
}
```

Progress is derived. ETA = `(targetAmount - currentAmount) / avgMonthlyContribution * 30 days from today`.

**Add Goal sheet** (full screen sheet):
- Goal name
- Target amount
- Target date (optional)
- Contribution rule (radio: All savings · Specific paybill · Manual mark)
- Color picker (one of the category colors)

### 4.7 You

```
┌──────────────────────────────────────────────┐
│ You                                          │
│                                              │
│ ┌──────────────────────────────────────────┐ │
│ │  ⒶⒶ  Kevin                              │ │  ← profile card
│ │       2 M-Pesa lines · 1,432 tx tracked  │ │
│ │ [ Manage accounts ]                      │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │
│ │ 🎯  Goals                          3   ›│ │
│ │ 💰  Budgets                        2   ›│ │
│ │ 🏷  Categories                          ›│ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │
│ │ 🌓  Appearance                          │ │
│ │     Theme              System ›          │ │
│ │     Font size          Medium ›          │ │
│ │     Accent             ●●●●● ›           │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │
│ │ 🔔  Notifications                       │ │
│ │     Daily summary           8:00 PM      │ │
│ │     Weekly summary          Sun, 6 PM    │ │
│ │     Budget alerts                ●       │ │
│ │     Large transaction alert     OFF ›    │ │
│ │     Insights                     ●       │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │
│ │ 🗄  Data                                │ │
│ │     Import SMS history             ›    │ │
│ │     Re-parse 14 unknown messages   ›    │ │
│ │     Export (CSV / JSON)            ›    │ │
│ │     Clear all data                 ›    │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │
│ │ ☁  Backup                               │ │
│ │     Google Drive            ngenoh@…    │ │
│ │     Last backup             Today 3 AM  │ │
│ │     Back up now                    ›    │ │
│ │     Restore                        ›    │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ ┌──────────────────────────────────────────┐ │
│ │ ℹ  About                                │ │
│ │     Version              1.0.0          │ │
│ │     [ NEW ] v1.1.0 available  Update ›  │ │
│ │     What's new                     ›    │ │
│ │     Privacy                        ›    │ │
│ └──────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘
```

Settings rows are **grouped into bento cards** instead of being a flat list — same `--surface` cards, hairlines between rows inside a card, no hairline at edges. Each "group" = one bento.

### 4.8 Account Switcher Sheet (Multi-SIM)

Triggered from the AccountChip on Home.

```
┌──────────────────────────────────────────────┐
│                                              │
│                    ━━━━                      │  ← grab handle
│                                              │
│   Accounts                                   │
│                                              │
│   ┌──────────────────────────────────────┐  │
│   │ ⓅⓛⓁ Personal           ● selected   │  │  ← accent border + check
│   │       Safaricom · 07XX…23            │  │
│   │       Bal 12,500 · 1,200 tx          │  │
│   └──────────────────────────────────────┘  │
│                                              │
│   ┌──────────────────────────────────────┐  │
│   │ ⓆⓀ  Business                         │  │
│   │       Safaricom · 07XX…87            │  │
│   │       Bal 4,300 · 232 tx             │  │
│   └──────────────────────────────────────┘  │
│                                              │
│   View                                       │
│   ( ) Selected account only                  │
│   (●) Combined view                          │
│                                              │
│   ┌──────────────────────────────────────┐  │
│   │ + Add another line                   │  │
│   └──────────────────────────────────────┘  │
│                                              │
│   Rename · Recolor accounts in You → Accounts│
│                                              │
└──────────────────────────────────────────────┘
```

**Data model addition**:

```kotlin
@Entity(tableName = "mpesa_accounts")
data class MpesaAccount(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val subscriptionId: Int,        // Android SubscriptionInfo id
  val phoneNumber: String?,       // E.164 if available
  val displayName: String,        // "Personal" / "Business"
  val colorHex: String,           // accent for tiles
  val isPrimary: Boolean,
)
```

`Transaction.accountId: Long?` is added. Migration:
1. ALTER TABLE add column nullable.
2. Existing rows stay null = "default" account.
3. On next SMS receive, populate the `accounts` table from SubscriptionManager and stamp incoming transactions.

### 4.9 Transaction Detail Sheet

```
┌──────────────────────────────────────────────┐
│                  ━━━━                        │
│                                              │
│   [🛒]   Naivas Supermarket                  │
│          BUY GOODS                           │
│                                              │
│              −Ksh 1,200.00                   │  Display L outflow ink
│              cost Ksh 0.00                   │  Caption muted
│                                              │
│   ┌──────────────────────────────────────┐  │
│   │  Code            RK31B7X4ZQ          │  │
│   │  Date            21 Mar 2026, 2:15PM │  │
│   │  Balance after   Ksh 3,500.00        │  │
│   │  Account         Personal ⓅⓛⓁ      │  │  ← only if multi-SIM
│   └──────────────────────────────────────┘  │
│                                              │
│   Category                                   │
│   ┌──────────────────────────────────────┐  │
│   │  🛒 Groceries                     ›  │  │  ← tap to change
│   └──────────────────────────────────────┘  │
│                                              │
│   ┌──────────────────────────────────────┐  │
│   │  Mark as contribution to goal     ›  │  │
│   └──────────────────────────────────────┘  │
│                                              │
│   Notes                                      │
│   ┌──────────────────────────────────────┐  │
│   │  Tap to add note                     │  │
│   └──────────────────────────────────────┘  │
│                                              │
│   ⌄ Original SMS                             │  ← collapsible
│   ┌──────────────────────────────────────┐  │
│   │  RK31B7X4ZQ Confirmed. Ksh1,200.00   │  │  mono
│   │  paid to NAIVAS SUPERMARKET. on …    │  │
│   └──────────────────────────────────────┘  │
│                                              │
│   [ Share ]              [ Delete ]          │
│                                              │
└──────────────────────────────────────────────┘
```

Notes:
- The "Mark as contribution" row appears only if user has at least one Goal with `Manual` rule.
- Delete only removes from app (the SMS remains in Android's SMS DB), and creates an UNDO snackbar.

### 4.10 In-app Update Flow

**Discovered (Home banner)** — slot just above Recent Activity:
```
┌──────────────────────────────────────────────┐
│  ✨  Ledga v1.1.0 is ready                   │
│      • Insights · Goals · Multi-SIM          │
│      [ See what's new ] [ Update ›]          │
└──────────────────────────────────────────────┘
```
- Background: `--accent-soft`, border: `--accent`.
- Dismissable. If dismissed it reappears in You → About.

**Full screen** — tap "See what's new":
```
┌──────────────────────────────────────────────┐
│ ‹                                            │
│                                              │
│ Ledga v1.1.0                                 │  Title L
│ March 2026 · 4.8 MB                          │  Body M muted
│                                              │
│ ┌──────────────────────────────────────────┐ │  hero illustration
│ │      [ release artwork ]                 │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ What's new                                   │
│                                              │
│ ✨  Insights tab — smart suggestions         │
│     based on your spending patterns.         │
│                                              │
│ 🎯  Goals — track savings towards rent,      │
│     school fees, trips.                      │
│                                              │
│ Ⓢ  Dual-SIM support — switch between        │
│     two M-Pesa lines from the home screen.   │
│                                              │
│ 🎨  Brand new design — bento layout, big     │
│     numbers, dark theme polish.              │
│                                              │
│ Fixes                                        │
│ • Faster startup on Android 8 devices        │
│ • Better Fuliza parsing                      │
│ • 14 small fixes from your feedback          │
│                                              │
│ ┌────────────────────────────────────┐      │
│ │     Download and install     →     │      │
│ └────────────────────────────────────┘      │
│                                              │
│             Remind me later                  │
└──────────────────────────────────────────────┘
```

**Installing** — replaces the CTA:
```
┌────────────────────────────────────┐
│  Downloading  ▓▓▓▓▓▓▓▓░░░  72%     │
│  3.4 MB of 4.8 MB                  │
└────────────────────────────────────┘
```

Then Android's standard installer intent fires.

---

## 5. Implementation Order

The redesign maps cleanly to phased PRs. Each phase is shippable.

### Phase A — Foundation (PR 1)
- Replace `Color.kt` with new token names + dark theme.
- Add `MpesaAccount` entity + DAO + migration.
- Add `Goal` entity + DAO + migration.
- Extend `Transaction` with `accountId: Long?`, `note: String?`.
- New `BentoCard`, `StatCard`, `Chip`, `ProgressRing` composables.
- New `TopBar` and `PillTabBar` composables.

### Phase B — Visual Overhaul (PR 2)
- Rewrite Home, Transactions, Trends with the new components, tokens, IA.
- New segmented "Activity" parent screen.
- New You screen consolidating Settings + new entries.

### Phase C — Insights (PR 3)
- Rule engine: anomaly (z-score against same-category rolling 4-week mean), recurring detection (3+ same merchant ≥21-day apart), fee tips, Fuliza warnings.
- `InsightsRepository` + `InsightWorker` (daily WorkManager).
- Insights screen + dismiss/snooze table.

### Phase D — Goals (PR 4)
- Goals screen + Add sheet + detail flow.
- Contribution attribution on transaction insert.
- "Mark as contribution" entry point in TransactionDetailSheet.

### Phase E — Multi-SIM (PR 5)
- Subscription detection on SMS receive (`SmsMessage.getSubscriptionId` / Android `SubscriptionManager`).
- Account switcher sheet.
- Filter on all queries via selected account or "combined".
- Onboarding step 4.

### Phase F — Update flow (PR 6)
- GitHub Releases polling.
- Banner + full-screen changelog.
- DownloadManager + install intent.

---

## 6. Open questions (decide before Phase A)

1. **Tab bar count**: keep 4 (Home / Activity / Insights / You) or 5 (split Trends out from Activity)? Recommendation: **4** — keeps thumb reach generous, Insights doesn't fight Trends.
2. **Multi-SIM detection**: Android only exposes `subscriptionId` on incoming SMS — not on already-imported ones. For historical imports we'll have to leave `accountId = null` and let the user assign in bulk. OK?
3. **Insight cadence**: notify on new insight (push) or only show in-app? Recommendation: **in-app only**, with a daily-summary mention if any new insights exist.

---

## 7. What's intentionally NOT in this redesign

- Web / desktop companion — out of scope, app stays mobile-only.
- Bill-pay shortcuts — Ledga is read-only; it tracks, not transacts.
- Bank-account integration — M-Pesa SMS only.
- Cloud sync between devices — backup-only via Google Drive; no per-device sync.
- AI summaries — Insights are deterministic rule output, not LLM output. Keeps the app offline and predictable.
