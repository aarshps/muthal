"use client";

import { useEffect, useState } from "react";
import { subscribeEntries } from "@/lib/firestore";
import type { Entry } from "@/lib/types";

/** Subscribes to every entry (flat mirror, newest first). The home filters by
 * the selected institution; the history view shows all of them. */
export function useEntries(uid: string | null) {
  const [entries, setEntries] = useState<Entry[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!uid) {
      setEntries([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    return subscribeEntries(uid, (list) => {
      setEntries(list);
      setLoading(false);
    });
  }, [uid]);

  return { entries, loading };
}
