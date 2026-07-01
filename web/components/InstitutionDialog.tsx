"use client";

import { useEffect, useState } from "react";
import { Sheet } from "./Sheet";
import { Button, Field, Select, TextInput } from "./controls";
import { CURRENCIES } from "@/lib/currency";
import { INSTITUTION_TYPES } from "@/lib/constants";
import { haptics } from "@/lib/haptics";

export function InstitutionDialog({
  open,
  onClose,
  onCreate,
}: {
  open: boolean;
  onClose: () => void;
  onCreate: (input: { name: string; type: string; currency: string }) => Promise<void>;
}) {
  const [name, setName] = useState("");
  const [type, setType] = useState(INSTITUTION_TYPES[0]);
  const [currency, setCurrency] = useState("INR");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setName("");
    setType(INSTITUTION_TYPES[0]);
    setCurrency("INR");
    setError(null);
  }, [open]);

  async function save() {
    const trimmed = name.trim();
    if (!trimmed) {
      setError("Give the institution a name.");
      haptics.error();
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await onCreate({ name: trimmed, type, currency });
      haptics.success();
      onClose();
    } catch (e) {
      setError((e as Error)?.message ?? "Couldn't create — try again.");
      haptics.error();
    } finally {
      setBusy(false);
    }
  }

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title="New institution"
      footer={
        <Button onClick={save} disabled={busy} className="w-full">
          {busy ? "Creating…" : "Create"}
        </Button>
      }
    >
      <div className="flex flex-col gap-4 py-2">
        <Field label="Name">
          <TextInput
            placeholder="e.g. Sree Krishna Temple"
            value={name}
            onChange={(e) => setName(e.target.value)}
            autoFocus
          />
        </Field>
        <Field label="Type">
          <Select value={type} onChange={(e) => setType(e.target.value)}>
            {INSTITUTION_TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Currency">
          <Select value={currency} onChange={(e) => setCurrency(e.target.value)}>
            {CURRENCIES.map((c) => (
              <option key={c.code} value={c.code}>
                {c.code} · {c.name} ({c.symbol})
              </option>
            ))}
          </Select>
        </Field>
        {error && <p className="text-sm text-error">{error}</p>}
      </div>
    </Sheet>
  );
}
