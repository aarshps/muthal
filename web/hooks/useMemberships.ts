"use client";

import { useEffect, useState } from "react";
import { subscribeMemberships } from "@/lib/firestore";
import { prefs } from "@/lib/prefs";
import type { Membership } from "@/lib/types";

/** Subscribes to the user's institution memberships (SPEC §1) and tracks the
 * selected one. Replaces the old single-owner institutions list. */
export function useMemberships(uid: string | null) {
  const [memberships, setMemberships] = useState<Membership[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedIdState] = useState<string | null>(null);

  useEffect(() => {
    if (!uid) {
      setMemberships([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    return subscribeMemberships(uid, (list) => {
      setMemberships(list);
      setLoading(false);
      setSelectedIdState((current) => {
        const stored = current ?? prefs.getSelectedInstitution();
        if (stored && list.some((m) => m.institutionId === stored)) return stored;
        return list[0]?.institutionId ?? null;
      });
    });
  }, [uid]);

  const setSelectedId = (id: string) => {
    setSelectedIdState(id);
    prefs.setSelectedInstitution(id);
  };

  const selected = memberships.find((m) => m.institutionId === selectedId) ?? null;

  return { memberships, selected, selectedId, setSelectedId, loading };
}
