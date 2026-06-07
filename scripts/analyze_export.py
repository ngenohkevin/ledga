#!/usr/bin/env python3
"""Analyze a Ledga export zip: real vs shown spending, month by month.

Usage: python3 analyze_export.py <ledga-export.zip | data.json>

"Shown" replicates the app's OLD Total Spending (all OUTFLOW rows).
The breakdown columns show how much of that figure is:
  - reversed:  outflows later reversed
  - fuliza:    Fuliza repayments/auto-pay (double-counts the original spend)
  - savings:   M-Shwari / KCB M-Pesa deposits (your own money moving)
  - unknown:   unparsed SMS defaulted to spending
  - withdraw:  agent/ATM cash-out (cash in hand, not yet spent)
"real" = shown - reversed - fuliza - savings - unknown  (withdrawals kept)
The app now excludes reversed + fuliza + savings, so the in-app figure
should equal REAL + unknown for any month.
"""

import json
import sys
import zipfile
from collections import defaultdict
from datetime import datetime

FULIZA_REPAY = {"FULIZA_REPAYMENT", "FULIZA_AUTO_PAY"}
SAVINGS = {"MSHWARI", "KCB_MPESA"}
WITHDRAW = {"WITHDRAW_AGENT", "WITHDRAW_ATM"}


def load(path):
    if path.endswith(".zip"):
        with zipfile.ZipFile(path) as z:
            name = next(n for n in z.namelist() if n.endswith("data.json"))
            with z.open(name) as f:
                return json.load(f)
    with open(path) as f:
        return json.load(f)


def main(path):
    data = load(path)
    txns = data["transactions"]
    print(f"Transactions: {len(txns)}  (exported {datetime.fromtimestamp(data.get('exportedAt', 0) / 1000):%Y-%m-%d %H:%M})\n")

    reversed_codes = {
        t["reversedTransactionCode"]
        for t in txns
        if t.get("reversedTransactionCode")
    }

    months = defaultdict(lambda: defaultdict(float))
    counts = defaultdict(int)
    for t in txns:
        if t["direction"] != "OUTFLOW":
            continue
        key = datetime.fromtimestamp(t["timestamp"] / 1000).strftime("%Y-%m")
        amt = t["amount"]
        m = months[key]
        m["shown"] += amt
        m["fees"] += t.get("transactionCost", 0.0)
        counts[key] += 1
        if t["transactionCode"] in reversed_codes:
            m["reversed"] += amt
        elif t["type"] in FULIZA_REPAY:
            m["fuliza"] += amt
        elif t["type"] in SAVINGS:
            m["savings"] += amt
        elif t["type"] == "UNKNOWN":
            m["unknown"] += amt
        elif t["type"] in WITHDRAW:
            m["withdraw"] += amt

    cols = ["shown", "reversed", "fuliza", "savings", "unknown", "withdraw"]
    header = f"{'Month':<9}{'Txns':>6}" + "".join(f"{c:>12}" for c in cols) + f"{'REAL':>12}{'fees':>9}"
    print(header)
    print("-" * len(header))
    totals = defaultdict(float)
    for key in sorted(months):
        m = months[key]
        real = m["shown"] - m["reversed"] - m["fuliza"] - m["savings"] - m["unknown"]
        row = f"{key:<9}{counts[key]:>6}" + "".join(f"{m[c]:>12,.0f}" for c in cols)
        print(row + f"{real:>12,.0f}{m['fees']:>9,.0f}")
        for c in cols + ["fees"]:
            totals[c] += m[c]
        totals["real"] += real
    print("-" * len(header))
    print(
        f"{'TOTAL':<9}{sum(counts.values()):>6}"
        + "".join(f"{totals[c]:>12,.0f}" for c in cols)
        + f"{totals['real']:>12,.0f}{totals['fees']:>9,.0f}"
    )

    if totals["shown"] > 0:
        inflation = totals["shown"] - totals["real"]
        print(
            f"\nShown spending is inflated by Ksh {inflation:,.0f} "
            f"({inflation / totals['shown'] * 100:.1f}% of the shown figure)."
        )
        print("REAL keeps cash withdrawals; subtract the withdraw column too if you treat cash-out as a transfer.")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
