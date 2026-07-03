/** Ports android SummaryHelper.kt / ios Summary.swift — keep in sync (SPEC §5). */

export interface Summary {
  totalIncome: number;
  totalExpense: number;
  balance: number;
  monthIncome: number;
  monthExpense: number;
  monthBalance: number;
}

interface SummaryInput {
  amount: number;
  type: string;
  date: number; // epoch ms
}

/**
 * Reduce a list of entries to the hero totals. Month membership is decided by
 * the **UTC** calendar month+year of `now`, so all platforms agree regardless
 * of device timezone (SPEC §5).
 */
export function summarize(entries: SummaryInput[], now: number): Summary {
  const ref = new Date(now);
  const y = ref.getUTCFullYear();
  const m = ref.getUTCMonth();

  let totalIncome = 0;
  let totalExpense = 0;
  let monthIncome = 0;
  let monthExpense = 0;

  for (const e of entries) {
    const d = new Date(e.date);
    const inMonth = d.getUTCFullYear() === y && d.getUTCMonth() === m;
    if (e.type === "income") {
      totalIncome += e.amount;
      if (inMonth) monthIncome += e.amount;
    } else if (e.type === "expense") {
      totalExpense += e.amount;
      if (inMonth) monthExpense += e.amount;
    }
  }

  return {
    totalIncome,
    totalExpense,
    balance: totalIncome - totalExpense,
    monthIncome,
    monthExpense,
    monthBalance: monthIncome - monthExpense,
  };
}

export interface PeriodSummary {
  openingBalance: number;
  periodIncome: number;
  periodExpense: number;
  closingBalance: number;
  entryCount: number;
}

/**
 * Opening/closing balance + in-range totals for [start, endInclusive] (SPEC §7).
 * Entries strictly before the window feed only the opening balance; entries
 * strictly after the window are excluded entirely.
 */
export function periodSummarize(
  entries: SummaryInput[],
  start: number,
  endInclusive: number,
): PeriodSummary {
  let openingBalance = 0;
  let periodIncome = 0;
  let periodExpense = 0;
  let entryCount = 0;

  for (const e of entries) {
    if (e.date < start) {
      if (e.type === "income") openingBalance += e.amount;
      else if (e.type === "expense") openingBalance -= e.amount;
    } else if (e.date >= start && e.date <= endInclusive) {
      entryCount++;
      if (e.type === "income") periodIncome += e.amount;
      else if (e.type === "expense") periodExpense += e.amount;
    }
  }

  return {
    openingBalance,
    periodIncome,
    periodExpense,
    closingBalance: openingBalance + periodIncome - periodExpense,
    entryCount,
  };
}
