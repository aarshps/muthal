"use client";

import { useMemo } from "react";
import { Plus, Receipt } from "lucide-react";
import { EmptyState } from "./EmptyState";
import { Fab } from "./Fab";
import { formatCurrency } from "@/lib/currency";
import { summarize } from "@/lib/summary";
import type { Entry, Institution } from "@/lib/types";

function itemClass(i: number, n: number): string {
  if (n === 1) return "item-single";
  if (i === 0) return "item-first";
  if (i === n - 1) return "item-last";
  return "item-middle";
}

const dayKey = (ms: number) =>
  new Date(ms).toLocaleDateString(undefined, {
    weekday: "short",
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  });

export function Home({
  institution,
  entries,
  onAdd,
  onEdit,
}: {
  institution: Institution;
  entries: Entry[]; // already filtered to this institution, newest first
  onAdd: () => void;
  onEdit: (entry: Entry) => void;
}) {
  const cur = institution.currency;
  const summary = useMemo(() => summarize(entries, Date.now()), [entries]);

  // Group newest-first entries by calendar day, preserving order.
  const groups = useMemo(() => {
    const out: { key: string; items: Entry[] }[] = [];
    for (const e of entries) {
      const key = dayKey(e.date);
      const last = out[out.length - 1];
      if (last && last.key === key) last.items.push(e);
      else out.push({ key, items: [e] });
    }
    return out;
  }, [entries]);

  return (
    <div className="pb-28">
      {/* Hero */}
      <section className="card p-5">
        <p className="text-sm text-on-surface-variant">
          {institution.name} · {institution.type}
        </p>
        <p className="mt-3 text-xs font-bold uppercase tracking-wide text-on-surface-variant">
          Balance
        </p>
        <p
          className={`text-4xl font-extrabold tracking-tight ${
            summary.balance < 0 ? "text-error" : ""
          }`}
        >
          {formatCurrency(summary.balance, cur)}
        </p>
        <div className="mt-4 grid grid-cols-2 gap-3">
          <div className="rounded-2xl bg-surface-2 p-3">
            <p className="text-xs text-on-surface-variant">Income · this month</p>
            <p className="mt-0.5 text-lg font-bold text-primary">
              {formatCurrency(summary.monthIncome, cur)}
            </p>
          </div>
          <div className="rounded-2xl bg-surface-2 p-3">
            <p className="text-xs text-on-surface-variant">Expense · this month</p>
            <p className="mt-0.5 text-lg font-bold text-error">
              {formatCurrency(summary.monthExpense, cur)}
            </p>
          </div>
        </div>
      </section>

      {/* Entries */}
      {entries.length === 0 ? (
        <div className="mt-4">
          <EmptyState
            icon={<Receipt size={28} className="text-on-surface-variant" />}
            title="No entries yet"
            description="Record your first income or expense for this institution."
            actionLabel="Add entry"
            onAction={onAdd}
          />
        </div>
      ) : (
        <div className="mt-5 space-y-5">
          {groups.map((g) => (
            <div key={g.key}>
              <p className="mb-2 px-1 text-xs font-bold uppercase tracking-wide text-on-surface-variant">
                {g.key}
              </p>
              <div className="grouped-list">
                {g.items.map((e, i) => (
                  <button
                    key={e.id}
                    onClick={() => onEdit(e)}
                    className={`flex items-center gap-3 bg-surface px-4 py-3 text-left transition active:scale-[0.99] ${itemClass(
                      i,
                      g.items.length,
                    )}`}
                  >
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-semibold">{e.category || "Uncategorized"}</p>
                      {e.note && (
                        <p className="truncate text-sm text-on-surface-variant">
                          {e.note}
                        </p>
                      )}
                    </div>
                    <p
                      className={`shrink-0 font-bold ${
                        e.type === "income" ? "text-primary" : "text-error"
                      }`}
                    >
                      {e.type === "income" ? "+" : "−"}
                      {formatCurrency(e.amount, e.currency || cur)}
                    </p>
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      <Fab icon={<Plus size={20} />} label="Add" onClick={onAdd} />
    </div>
  );
}
