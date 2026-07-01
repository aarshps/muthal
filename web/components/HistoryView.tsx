"use client";

import { useMemo } from "react";
import { Receipt } from "lucide-react";
import { ScreenHeader } from "./ScreenHeader";
import { EmptyState } from "./EmptyState";
import { formatCurrency } from "@/lib/currency";
import type { Entry } from "@/lib/types";

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

export function HistoryView({
  entries,
  onBack,
  onEdit,
}: {
  entries: Entry[]; // all entries across institutions, newest first
  onBack: () => void;
  onEdit: (entry: Entry) => void;
}) {
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
    <div className="min-h-dvh">
      <ScreenHeader title="All entries" onBack={onBack} />
      <div className="px-4 pb-10">
        {entries.length === 0 ? (
          <EmptyState
            icon={<Receipt size={28} className="text-on-surface-variant" />}
            title="Nothing here yet"
            description="Entries from every institution will appear here."
          />
        ) : (
          <div className="space-y-5">
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
                        <p className="truncate font-semibold">
                          {e.category || "Uncategorized"}
                        </p>
                        <p className="truncate text-sm text-on-surface-variant">
                          {e.institutionName}
                          {e.note ? ` · ${e.note}` : ""}
                        </p>
                      </div>
                      <p
                        className={`shrink-0 font-bold ${
                          e.type === "income" ? "text-primary" : "text-error"
                        }`}
                      >
                        {e.type === "income" ? "+" : "−"}
                        {formatCurrency(e.amount, e.currency)}
                      </p>
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
