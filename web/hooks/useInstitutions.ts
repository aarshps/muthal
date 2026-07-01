"use client";

import { useEffect, useState } from "react";
import {
  addInstitution,
  deleteInstitution,
  subscribeInstitutions,
} from "@/lib/firestore";
import { prefs } from "@/lib/prefs";
import type { Institution } from "@/lib/types";

/** Subscribes to the user's institutions and tracks the selected one. */
export function useInstitutions(uid: string | null) {
  const [institutions, setInstitutions] = useState<Institution[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedIdState] = useState<string | null>(null);

  useEffect(() => {
    if (!uid) {
      setInstitutions([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    return subscribeInstitutions(uid, (list) => {
      setInstitutions(list);
      setLoading(false);
      // Keep a valid selection: stored one if still present, else the first.
      setSelectedIdState((current) => {
        const stored = current ?? prefs.getSelectedInstitution();
        if (stored && list.some((i) => i.id === stored)) return stored;
        return list[0]?.id ?? null;
      });
    });
  }, [uid]);

  const setSelectedId = (id: string) => {
    setSelectedIdState(id);
    prefs.setSelectedInstitution(id);
  };

  const selected = institutions.find((i) => i.id === selectedId) ?? null;

  return {
    institutions,
    selected,
    selectedId,
    setSelectedId,
    loading,
    add: (input: { name: string; type: string; currency: string }) =>
      uid ? addInstitution(uid, input) : Promise.reject(new Error("no user")),
    remove: (id: string) =>
      uid ? deleteInstitution(uid, id) : Promise.reject(new Error("no user")),
  };
}
