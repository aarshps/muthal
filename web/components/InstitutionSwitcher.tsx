"use client";

import { Sheet } from "./Sheet";
import { Button } from "./controls";
import type { Membership } from "@/lib/types";

const roleLabel = (role: string) =>
  role === "owner" ? "Owner" : role === "admin" ? "Admin" : "Member";

export function InstitutionSwitcher({
  open,
  onClose,
  memberships,
  selectedId,
  onSelect,
  onCreate,
  onJoin,
}: {
  open: boolean;
  onClose: () => void;
  memberships: Membership[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  onCreate: () => void;
  onJoin: () => void;
}) {
  return (
    <Sheet open={open} onClose={onClose} title="Switch institution">
      <div className="flex flex-col gap-1 py-2">
        {memberships.map((m) => (
          <button
            key={m.institutionId}
            onClick={() => {
              onSelect(m.institutionId);
              onClose();
            }}
            className={`flex items-center gap-3 rounded-2xl px-4 py-3 text-left transition ${
              m.institutionId === selectedId ? "bg-primary/10" : "hover:bg-surface-2"
            }`}
          >
            <div className="min-w-0 flex-1">
              <p className="truncate font-semibold">{m.institutionName}</p>
              <p className="truncate text-sm text-on-surface-variant">
                {m.institutionType} · {m.currency}
              </p>
            </div>
            <span className="shrink-0 rounded-full bg-secondary-container px-3 py-1 text-xs font-semibold text-on-secondary-container">
              {roleLabel(m.role)}
            </span>
          </button>
        ))}

        <div className="mt-3 flex gap-2">
          <Button variant="outline" className="flex-1" onClick={onCreate}>
            Create institution
          </Button>
          <Button variant="outline" className="flex-1" onClick={onJoin}>
            Join institution
          </Button>
        </div>
      </div>
    </Sheet>
  );
}
