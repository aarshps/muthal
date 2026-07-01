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
