"use client";

import { Share2, Users, ListTree, CalendarRange, LogOut, Trash2 } from "lucide-react";
import { Sheet } from "./Sheet";
import type { Role } from "@/lib/types";

function Row({
  icon,
  label,
  onClick,
  danger = false,
}: {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
  danger?: boolean;
}) {
  return (
    <button
      onClick={onClick}
      className={`flex w-full items-center gap-4 rounded-2xl px-4 py-3.5 text-left transition hover:bg-surface-2 ${danger ? "text-error" : ""}`}
    >
      <span className={danger ? "text-error" : "text-on-surface-variant"}>{icon}</span>
      <span className="font-semibold">{label}</span>
    </button>
  );
}

/** Role-gated action list for the currently open institution (SPEC §3): sharing
 * is available to every member, member management + categories are admin/owner-
 * only, period export and leaving are available to everyone, deleting is
 * owner-only. */
export function InstitutionActionsSheet({
  open,
  onClose,
  institutionName,
  role,
  onShare,
  onMembers,
  onCategories,
  onExport,
  onLeave,
  onDelete,
}: {
  open: boolean;
  onClose: () => void;
  institutionName: string;
  role: Role;
  onShare: () => void;
  onMembers: () => void;
  onCategories: () => void;
  onExport: () => void;
  onLeave: () => void;
  onDelete: () => void;
}) {
  const isAdminOrOwner = role === "owner" || role === "admin";
  const act = (fn: () => void) => () => {
    onClose();
    fn();
  };

  return (
    <Sheet open={open} onClose={onClose} title={institutionName}>
      <div className="flex flex-col gap-0.5 py-2">
        <Row icon={<Share2 size={20} />} label="Share institution" onClick={act(onShare)} />
        {isAdminOrOwner && (
          <Row icon={<Users size={20} />} label="Manage members" onClick={act(onMembers)} />
        )}
        {isAdminOrOwner && (
          <Row icon={<ListTree size={20} />} label="Categories" onClick={act(onCategories)} />
        )}
        <Row icon={<CalendarRange size={20} />} label="Period export" onClick={act(onExport)} />
        <Row icon={<LogOut size={20} />} label="Leave institution" onClick={act(onLeave)} />
        {role === "owner" && (
          <Row icon={<Trash2 size={20} />} label="Delete institution" onClick={act(onDelete)} danger />
        )}
      </div>
    </Sheet>
  );
}
