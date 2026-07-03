"use client";

import { useState } from "react";
import { Share2 } from "lucide-react";
import { ScreenHeader } from "./ScreenHeader";
import { Button, Field, TextInput } from "./controls";
import { getEntriesOnce } from "@/lib/firestore";
import { formatCurrency } from "@/lib/currency";
import { periodSummarize, type PeriodSummary } from "@/lib/summary";
import { analytics } from "@/lib/analytics";
import { haptics } from "@/lib/haptics";
import type { Entry, Institution } from "@/lib/types";

const toDateInput = (ms: number) => new Date(ms).toISOString().slice(0, 10);
const dayFmt = (ms: number) =>
  new Date(ms).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" });

function startOfDayUtc(dateStr: string): number {
  return Date.parse(`${dateStr}T00:00:00.000Z`);
}
function endOfDayUtc(dateStr: string): number {
  return Date.parse(`${dateStr}T23:59:59.999Z`);
}

/** Start/end date range → opening balance, in-range entries, closing balance
 * (SPEC §7). */
export function PeriodExportView({
  institution,
  onBack,
}: {
  institution: Institution;
  onBack: () => void;
}) {
  const [startStr, setStartStr] = useState(() => {
    const firstOfMonth = new Date();
    firstOfMonth.setUTCDate(1);
    return toDateInput(firstOfMonth.getTime());
  });
  const [endStr, setEndStr] = useState(() => toDateInput(Date.now()));
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<PeriodSummary | null>(null);
  const [inRange, setInRange] = useState<Entry[]>([]);
  const [error, setError] = useState<string | null>(null);

  const cur = institution.currency;

  async function run() {
    const start = startOfDayUtc(startStr);
    const end = endOfDayUtc(endStr);
    if (end < start) {
      setError("End date is before start date.");
      haptics.error();
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const all = await getEntriesOnce(institution.id);
      const items = all.map((e) => ({ amount: e.amount, type: e.type, date: e.date }));
      const summary = periodSummarize(items, start, end);
      setResult(summary);
      setInRange(all.filter((e) => e.date >= start && e.date <= end).sort((a, b) => b.date - a.date));
      analytics.exportRun();
      haptics.success();
    } catch (e) {
      setError((e as Error)?.message ?? "Couldn't run export.");
      haptics.error();
    } finally {
      setBusy(false);
    }
  }

  async function share() {
    if (!result) return;
    const lines: string[] = [];
    lines.push(`${institution.name} — period export`);
    lines.push(`${dayFmt(startOfDayUtc(startStr))} to ${dayFmt(endOfDayUtc(endStr))}`);
    lines.push("");
    lines.push(`Opening balance: ${formatCurrency(result.openingBalance, cur)}`);
    for (const e of [...inRange].sort((a, b) => a.date - b.date)) {
      const sign = e.type === "income" ? "+" : "-";
      lines.push(
        `${dayFmt(e.date)}  ${e.category || "Uncategorized"}  ${sign}${formatCurrency(e.amount, cur)}${e.note ? `  (${e.note})` : ""}`,
      );
    }
    lines.push("");
    lines.push(`Income: +${formatCurrency(result.periodIncome, cur)}`);
    lines.push(`Expense: -${formatCurrency(result.periodExpense, cur)}`);
    lines.push(`Closing balance: ${formatCurrency(result.closingBalance, cur)}`);
    const text = lines.join("\n");
    analytics.exportShare();
    if (navigator.share) {
      try {
        await navigator.share({ text });
        return;
      } catch {
        // fall through to clipboard
      }
    }
    await navigator.clipboard.writeText(text);
    haptics.success();
  }

  return (
    <div className="min-h-dvh">
      <ScreenHeader title="Period export" onBack={onBack} />
      <div className="px-4 pb-12">
        <div className="flex gap-3">
          <Field label="Start date">
            <TextInput type="date" value={startStr} onChange={(e) => setStartStr(e.target.value)} />
          </Field>
          <Field label="End date">
            <TextInput type="date" value={endStr} onChange={(e) => setEndStr(e.target.value)} />
          </Field>
        </div>

        <Button className="mt-4 w-full" onClick={run} disabled={busy}>
          {busy ? "Running…" : "Run export"}
        </Button>

        {error && <p className="mt-3 text-sm text-error">{error}</p>}

        {result && (
          <>
            <section className="card mt-5 p-5">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-xs font-semibold text-on-surface-variant">Opening balance</p>
                  <p className="text-xl font-extrabold">{formatCurrency(result.openingBalance, cur)}</p>
                </div>
                <div>
                  <p className="text-xs font-semibold text-on-surface-variant">Closing balance</p>
                  <p className="text-xl font-extrabold">{formatCurrency(result.closingBalance, cur)}</p>
                </div>
              </div>
              <p className="mt-3 text-sm text-on-surface-variant">
                +{formatCurrency(result.periodIncome, cur)} / −{formatCurrency(result.periodExpense, cur)} ·{" "}
                {result.entryCount} {result.entryCount === 1 ? "entry" : "entries"}
              </p>
            </section>

            <div className="mt-4 grouped-list">
              {inRange.map((e, i) => (
                <div
                  key={e.id}
                  className={`flex items-center gap-3 bg-surface px-4 py-3 ${
                    inRange.length === 1 ? "item-single" : i === 0 ? "item-first" : i === inRange.length - 1 ? "item-last" : "item-middle"
                  }`}
                >
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-semibold">{e.category || "Uncategorized"}</p>
                    <p className="truncate text-sm text-on-surface-variant">{dayFmt(e.date)}{e.note ? ` · ${e.note}` : ""}</p>
                  </div>
                  <p className={`shrink-0 font-bold ${e.type === "income" ? "text-primary" : "text-error"}`}>
                    {e.type === "income" ? "+" : "−"}
                    {formatCurrency(e.amount, cur)}
                  </p>
                </div>
              ))}
            </div>

            <Button variant="outline" className="mt-4 w-full" onClick={share}>
              <Share2 size={16} />
              Share
            </Button>
          </>
        )}
      </div>
    </div>
  );
}
