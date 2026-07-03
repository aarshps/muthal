"use client";

import { useEffect, useState } from "react";
import { Trash2 } from "lucide-react";
import { Sheet } from "./Sheet";
import { Button, Field, Segmented, Select, TextInput } from "./controls";
import { haptics } from "@/lib/haptics";
import type { Category, Entry, EntryType, Institution } from "@/lib/types";
import type { EntryDraft } from "@/lib/firestore";

const toDateInput = (ms: number) => new Date(ms).toISOString().slice(0, 10);
const todayInput = () => new Date().toISOString().slice(0, 10);

export function AddEntryDialog({
  open,
  onClose,
  institution,
  categories,
  entry,
  onSave,
  onDelete,
}: {
  open: boolean;
  onClose: () => void;
  institution: Institution;
  categories: Category[]; // this institution's live category list (SPEC §5)
  entry: Entry | null;
  onSave: (draft: EntryDraft) => Promise<void>;
  onDelete: (entry: Entry) => Promise<void>;
}) {
  const [type, setType] = useState<EntryType>("income");
  const [amount, setAmount] = useState("");
  const [category, setCategory] = useState("");
  const [dateStr, setDateStr] = useState(todayInput());
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const categoriesFor = (t: EntryType) => categories.filter((c) => c.kind === t).map((c) => c.name);

  // Reset form whenever the sheet is (re)opened for a new/existing entry —
  // date defaults to today, or the entry's existing date when editing (SPEC §1).
  useEffect(() => {
    if (!open) return;
    if (entry) {
      setType(entry.type);
      setAmount(String(entry.amount));
      setCategory(entry.category || categoriesFor(entry.type)[0] || "");
      setDateStr(toDateInput(entry.date));
      setNote(entry.note);
    } else {
      setType("income");
      setAmount("");
      setCategory(categoriesFor("income")[0] || "");
      setDateStr(todayInput());
      setNote("");
    }
    setError(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, entry]);

  const currentOptions = categoriesFor(type);

  function changeType(t: EntryType) {
    setType(t);
    const list = categoriesFor(t);
    if (!list.includes(category)) setCategory(list[0] || "");
  }

  async function save() {
    const value = parseFloat(amount);
    if (!Number.isFinite(value) || value <= 0) {
      setError("Enter an amount greater than zero.");
      haptics.error();
      return;
    }
    if (!category) {
      setError("Pick a category.");
      haptics.error();
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await onSave({
        id: entry?.id,
        date: Date.parse(`${dateStr}T00:00:00.000Z`),
        amount: value,
        type,
        category,
        note: note.trim(),
      });
      haptics.success();
      onClose();
    } catch (e) {
      setError((e as Error)?.message ?? "Couldn't save — try again.");
      haptics.error();
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!entry) return;
    setBusy(true);
    try {
      await onDelete(entry);
      haptics.success();
      onClose();
    } catch (e) {
      setError((e as Error)?.message ?? "Couldn't delete — try again.");
      haptics.error();
    } finally {
      setBusy(false);
    }
  }

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title={entry ? "Edit entry" : "Add entry"}
      footer={
        <div className="flex gap-2">
          {entry && (
            <Button variant="danger" onClick={remove} disabled={busy}>
              <Trash2 size={16} />
              Delete
            </Button>
          )}
          <Button onClick={save} disabled={busy} className="flex-1">
            {busy ? "Saving…" : "Save"}
          </Button>
        </div>
      }
    >
      <div className="flex flex-col gap-4 py-2">
        <Segmented<EntryType>
          options={[
            { value: "income", label: "Income" },
            { value: "expense", label: "Expense" },
          ]}
          selected={type}
          onSelect={changeType}
        />

        <Field label={`Amount (${institution.currency})`}>
          <TextInput
            type="number"
            inputMode="decimal"
            min="0"
            step="0.01"
            placeholder="0"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            autoFocus
          />
        </Field>

        <Field label="Category">
          {currentOptions.length === 0 ? (
            <p className="text-sm text-on-surface-variant">
              No {type} categories yet — add one from Institution &gt; Categories.
            </p>
          ) : (
            <Select value={category} onChange={(e) => setCategory(e.target.value)}>
              {currentOptions.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </Select>
          )}
        </Field>

        <Field label="Date">
          <TextInput type="date" value={dateStr} onChange={(e) => setDateStr(e.target.value)} />
        </Field>

        <Field label="Note (optional)">
          <TextInput
            placeholder="e.g. Annual festival collection"
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
        </Field>

        {error && <p className="text-sm text-error">{error}</p>}
      </div>
    </Sheet>
  );
}
