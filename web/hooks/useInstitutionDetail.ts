"use client";

import { useEffect, useState } from "react";
import { subscribeCategories, subscribeEntries, subscribeInstitution } from "@/lib/firestore";
import type { Category, Entry, Institution } from "@/lib/types";

/** Live institution doc + its categories + entries, for the currently selected
 * institution (SPEC §1-2). */
export function useInstitutionDetail(instId: string | null) {
  const [institution, setInstitution] = useState<Institution | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [entries, setEntries] = useState<Entry[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!instId) {
      setInstitution(null);
      setCategories([]);
      setEntries([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    setInstitution(null);
    setCategories([]);
    setEntries([]);
    let pending = 3;
    const done = () => {
      pending--;
      if (pending <= 0) setLoading(false);
    };
    const u1 = subscribeInstitution(instId, (i) => {
      setInstitution(i);
      done();
    });
    const u2 = subscribeCategories(instId, (c) => {
      setCategories(c);
      done();
    });
    const u3 = subscribeEntries(instId, (e) => {
      setEntries(e);
      done();
    });
    return () => {
      u1();
      u2();
      u3();
    };
  }, [instId]);

  return { institution, categories, entries, loading };
}
