"use client";

import { useEffect, useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { ScreenHeader } from "./ScreenHeader";
import { Sheet } from "./Sheet";
import { Button, Field, Segmented, TextInput } from "./controls";
import { Fab } from "./Fab";
import { subscribeCategories, addCategory, deleteCategory } from "@/lib/firestore";
import { analytics } from "@/lib/analytics";
import { haptics } from "@/lib/haptics";
import type { Category, EntryType, Institution } from "@/lib/types";

function CategorySection({
  title,
  items,
  onDelete,
}: {
  title: string;
  items: Category[];
  onDelete: (c: Category) => void;
}) {
  return (
    <>
      <p className="mb-2 mt-5 px-1 text-xs font-bold uppercase tracking-wide text-primary">{title}</p>
      <div className="grouped-list">
        {items.map((c, i) => (
          <div
            key={c.id}
            className={`flex items-center gap-3 bg-surface px-4 py-3 ${
              items.length === 1 ? "item-single" : i === 0 ? "item-first" : i === items.length - 1 ? "item-last" : "item-middle"
            }`}
          >
            <p className="min-w-0 flex-1 truncate font-semibold">{c.name}</p>
            <button
              aria-label={`Delete ${c.name}`}
              onClick={() => onDelete(c)}
              className="rounded-full p-2 text-error transition hover:bg-error/10"
            >
              <Trash2 size={18} />
            </button>
          </div>
        ))}
        {items.length === 0 && (
          <p className="bg-surface px-4 py-3 text-sm text-on-surface-variant item-single">No categories yet.</p>
        )}
      </div>
    </>
  );
}

/** Admin/owner-only category CRUD, scoped to one institution (SPEC §5). */
export function CategoriesView({
  institution,
  onBack,
}: {
  institution: Institution;
  onBack: () => void;
}) {
  const [categories, setCategories] = useState<Category[]>([]);
  const [addOpen, setAddOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<Category | null>(null);
  const [name, setName] = useState("");
  const [kind, setKind] = useState<EntryType>("income");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    analytics.categoriesOpen();
    return subscribeCategories(institution.id, setCategories);
  }, [institution.id]);

  const income = categories.filter((c) => c.kind === "income");
  const expense = categories.filter((c) => c.kind === "expense");

  async function save() {
    const trimmed = name.trim();
    if (!trimmed) {
      haptics.error();
      return;
    }
    setBusy(true);
    try {
      await addCategory(institution.id, trimmed, kind);
      analytics.categoryAdd(kind);
      haptics.success();
      setAddOpen(false);
      setName("");
      setKind("income");
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!pendingDelete) return;
    setBusy(true);
    try {
      await deleteCategory(institution.id, pendingDelete.id);
      analytics.categoryDelete();
      setPendingDelete(null);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="min-h-dvh pb-24">
      <ScreenHeader title="Categories" onBack={onBack} />
      <div className="px-4">
        <CategorySection title="Income" items={income} onDelete={setPendingDelete} />
        <CategorySection title="Expense" items={expense} onDelete={setPendingDelete} />
      </div>

      <Fab icon={<Plus size={20} />} label="Add" onClick={() => setAddOpen(true)} />

      <Sheet
        open={addOpen}
        onClose={() => setAddOpen(false)}
        title="New category"
        footer={
          <Button className="w-full" onClick={save} disabled={busy}>
            {busy ? "Adding…" : "Add"}
          </Button>
        }
      >
        <div className="flex flex-col gap-4 py-2">
          <Segmented<EntryType>
            options={[
              { value: "income", label: "Income" },
              { value: "expense", label: "Expense" },
            ]}
            selected={kind}
            onSelect={setKind}
          />
          <Field label="Category name">
            <TextInput value={name} onChange={(e) => setName(e.target.value)} autoFocus />
          </Field>
        </div>
      </Sheet>

      <Sheet
        open={pendingDelete !== null}
        onClose={() => setPendingDelete(null)}
        title="Delete category?"
        footer={
          <div className="flex gap-3">
            <Button variant="ghost" className="flex-1" onClick={() => setPendingDelete(null)}>
              Cancel
            </Button>
            <Button variant="danger" className="flex-1" onClick={remove} disabled={busy}>
              {busy ? "Deleting…" : "Delete"}
            </Button>
          </div>
        }
      >
        <p className="py-2 text-sm text-on-surface-variant">
          Delete &quot;{pendingDelete?.name}&quot;? Existing entries keep it as text but it will no longer be selectable.
        </p>
      </Sheet>
    </div>
  );
}
