"use client";

import { useEffect, useState } from "react";
import { Share2 } from "lucide-react";
import { ScreenHeader } from "./ScreenHeader";
import { Sheet } from "./Sheet";
import { Button } from "./controls";
import { subscribeMembers, setMemberRole, removeMember, joinLink } from "@/lib/firestore";
import { analytics } from "@/lib/analytics";
import { haptics } from "@/lib/haptics";
import type { Institution, Member, Role } from "@/lib/types";

const roleLabel = (role: Role) => (role === "owner" ? "Owner" : role === "admin" ? "Admin" : "Member");

export function ManageMembersView({
  institution,
  myUid,
  onBack,
}: {
  institution: Institution;
  myUid: string;
  onBack: () => void;
}) {
  const [members, setMembers] = useState<Member[]>([]);
  const [target, setTarget] = useState<Member | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    analytics.membersOpen();
    return subscribeMembers(institution.id, setMembers);
  }, [institution.id]);

  async function share() {
    const text = `Join "${institution.name}" on Muthal\n\nCode: ${institution.code}\n${joinLink(institution.code)}`;
    analytics.institutionShare();
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

  async function promote(member: Member) {
    setBusy(true);
    try {
      await setMemberRole(institution.id, member.uid, "admin");
      analytics.memberRoleChange("admin");
      haptics.success();
    } finally {
      setBusy(false);
      setTarget(null);
    }
  }

  async function demote(member: Member) {
    setBusy(true);
    try {
      await setMemberRole(institution.id, member.uid, "member");
      analytics.memberRoleChange("member");
      haptics.success();
    } finally {
      setBusy(false);
      setTarget(null);
    }
  }

  async function remove(member: Member) {
    setBusy(true);
    try {
      await removeMember(institution.id, member.uid);
      analytics.memberRemove();
      haptics.success();
    } finally {
      setBusy(false);
      setTarget(null);
    }
  }

  return (
    <div className="min-h-dvh">
      <ScreenHeader title="Manage members" onBack={onBack} />
      <div className="px-4 pb-10">
        <section className="card mb-4 flex items-center gap-3 p-4">
          <div className="min-w-0 flex-1">
            <p className="text-xs font-semibold text-on-surface-variant">Join code</p>
            <p className="text-2xl font-extrabold tracking-[0.15em]">{institution.code}</p>
          </div>
          <button
            aria-label="Share"
            onClick={share}
            className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-primary text-on-primary transition hover:opacity-90"
          >
            <Share2 size={18} />
          </button>
        </section>

        <div className="grouped-list">
          {members.map((m, i) => (
            <button
              key={m.uid}
              onClick={() => {
                if (m.uid === myUid || m.role === "owner") return;
                setTarget(m);
              }}
              className={`flex w-full items-center gap-3 bg-surface px-4 py-3 text-left ${
                i === 0 ? "item-first" : i === members.length - 1 ? "item-last" : "item-middle"
              }`}
            >
              <div className="min-w-0 flex-1">
                <p className="truncate font-semibold">{m.displayName || m.email || "Member"}</p>
                {m.email && m.displayName && (
                  <p className="truncate text-sm text-on-surface-variant">{m.email}</p>
                )}
              </div>
              <span className="shrink-0 rounded-full bg-secondary-container px-3 py-1 text-xs font-semibold text-on-secondary-container">
                {roleLabel(m.role)}
              </span>
            </button>
          ))}
        </div>
      </div>

      <Sheet
        open={target !== null}
        onClose={() => setTarget(null)}
        title={target?.displayName || target?.email || "Member"}
      >
        <div className="flex flex-col gap-2 py-2">
          {target?.role === "member" && (
            <Button variant="outline" disabled={busy} onClick={() => target && promote(target)}>
              Promote to admin
            </Button>
          )}
          {target?.role === "admin" && (
            <Button variant="outline" disabled={busy} onClick={() => target && demote(target)}>
              Demote to member
            </Button>
          )}
          <Button variant="danger" disabled={busy} onClick={() => target && remove(target)}>
            Remove member
          </Button>
        </div>
      </Sheet>
    </div>
  );
}
