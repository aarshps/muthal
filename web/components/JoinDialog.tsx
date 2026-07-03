"use client";

import { useEffect, useState } from "react";
import { Sheet } from "./Sheet";
import { Button, Field, TextInput } from "./controls";
import { haptics } from "@/lib/haptics";
import type { CodePreview } from "@/lib/firestore";

/** Enter a 6-character join code, resolve it to a preview, confirm, then join
 * (SPEC §2). Two-step within one sheet: code entry, then a confirmation. */
export function JoinDialog({
  open,
  onClose,
  onResolve,
  onJoin,
  initialCode,
}: {
  open: boolean;
  onClose: () => void;
  onResolve: (code: string) => Promise<CodePreview | null>;
  onJoin: (preview: CodePreview) => Promise<void>;
  initialCode?: string | null;
}) {
  const [code, setCode] = useState("");
  const [preview, setPreview] = useState<CodePreview | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setCode(initialCode ?? "");
    setPreview(null);
    setError(null);
    if (initialCode && initialCode.length === 6) {
      void resolve(initialCode);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, initialCode]);

  async function resolve(value: string) {
    const trimmed = value.trim().toUpperCase();
    if (trimmed.length < 6) {
      setError("Enter the 6-character code.");
      haptics.error();
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const result = await onResolve(trimmed);
      if (!result) {
        setError("Invalid or expired code.");
        haptics.error();
      } else {
        setPreview(result);
      }
    } catch (e) {
      setError((e as Error)?.message ?? "Couldn't resolve — try again.");
      haptics.error();
    } finally {
      setBusy(false);
    }
  }

  async function confirmJoin() {
    if (!preview) return;
    setBusy(true);
    setError(null);
    try {
      await onJoin(preview);
      haptics.success();
      onClose();
    } catch (e) {
      setError((e as Error)?.message ?? "Couldn't join — try again.");
      haptics.error();
    } finally {
      setBusy(false);
    }
  }

  if (preview) {
    return (
      <Sheet
        open={open}
        onClose={onClose}
        title="Join institution?"
        footer={
          <div className="flex gap-3">
            <Button variant="ghost" className="flex-1" onClick={() => setPreview(null)}>
              Back
            </Button>
            <Button className="flex-1" onClick={confirmJoin} disabled={busy}>
              {busy ? "Joining…" : "Join"}
            </Button>
          </div>
        }
      >
        <p className="py-2 text-sm text-on-surface-variant">
          Join &quot;{preview.institutionName}&quot;? You&apos;ll be added as a member.
        </p>
        {error && <p className="text-sm text-error">{error}</p>}
      </Sheet>
    );
  }

  return (
    <Sheet
      open={open}
      onClose={onClose}
      title="Join institution"
      footer={
        <Button className="w-full" onClick={() => resolve(code)} disabled={busy}>
          {busy ? "Checking…" : "Join"}
        </Button>
      }
    >
      <div className="flex flex-col gap-4 py-2">
        <p className="text-sm text-on-surface-variant">
          Enter the 6-character code shared with you.
        </p>
        <Field label="Code">
          <TextInput
            placeholder="ABC123"
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            maxLength={6}
            autoFocus
            className="tracking-[0.2em] uppercase"
          />
        </Field>
        {error && <p className="text-sm text-error">{error}</p>}
      </div>
    </Sheet>
  );
}
