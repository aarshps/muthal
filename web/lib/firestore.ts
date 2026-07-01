"use client";

import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  getDocs,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  Timestamp,
  where,
  writeBatch,
  type DocumentData,
} from "firebase/firestore";
import { db } from "./firebase";
import type { Entry, EntryType, Institution } from "./types";

/**
 * Firestore data layer. Entries are dual-written to the nested authoritative path
 * and the flat mirror with the same id (SPEC §2). Reads come from the flat mirror
 * `users/{uid}/entries` ordered by date; the home filters by institution
 * client-side. Mirrors android EntryRepository.kt / ios FirestoreService.swift.
 */

const instCol = (uid: string) => collection(db, "users", uid, "institutions");
const instEntriesCol = (uid: string, instId: string) =>
  collection(db, "users", uid, "institutions", instId, "entries");
const mirrorCol = (uid: string) => collection(db, "users", uid, "entries");

function tsToMs(v: unknown): number {
  if (v && typeof (v as Timestamp).toMillis === "function") {
    return (v as Timestamp).toMillis();
  }
  if (typeof v === "number") return v;
  return Date.now();
}

// ── Institutions ───────────────────────────────────────────────────────────

function mapInstitution(id: string, data: DocumentData): Institution {
  return {
    id,
    name: data.name ?? "",
    type: data.type ?? "Temple",
    currency: data.currency ?? "INR",
    createdAt: tsToMs(data.createdAt),
  };
}

export function subscribeInstitutions(
  uid: string,
  cb: (institutions: Institution[]) => void,
): () => void {
  const q = query(instCol(uid), orderBy("createdAt", "asc"));
  return onSnapshot(q, (snap) => {
    cb(snap.docs.map((d) => mapInstitution(d.id, d.data())));
  });
}

export async function addInstitution(
  uid: string,
  input: { name: string; type: string; currency: string },
): Promise<string> {
  const ref = await addDoc(instCol(uid), {
    name: input.name,
    type: input.type,
    currency: input.currency,
    createdAt: serverTimestamp(),
  });
  return ref.id;
}

export async function deleteInstitution(
  uid: string,
  instId: string,
): Promise<void> {
  const batch = writeBatch(db);
  const nested = await getDocs(instEntriesCol(uid, instId));
  nested.forEach((d) => batch.delete(d.ref));
  const mirror = await getDocs(
    query(mirrorCol(uid), where("institutionId", "==", instId)),
  );
  mirror.forEach((d) => batch.delete(d.ref));
  batch.delete(doc(db, "users", uid, "institutions", instId));
  await batch.commit();
}

// ── Entries ──────────────────────────────────────────────────────────────────

function mapEntry(id: string, data: DocumentData, uid: string): Entry {
  return {
    id,
    date: tsToMs(data.date),
    amount: typeof data.amount === "number" ? data.amount : 0,
    type: (data.type === "expense" ? "expense" : "income") as EntryType,
    category: data.category ?? "",
    note: data.note ?? "",
    institutionId: data.institutionId ?? "",
    institutionName: data.institutionName ?? "",
    currency: data.currency ?? "INR",
    userId: data.userId ?? uid,
  };
}

export function subscribeEntries(
  uid: string,
  cb: (entries: Entry[]) => void,
): () => void {
  const q = query(mirrorCol(uid), orderBy("date", "desc"));
  return onSnapshot(q, (snap) => {
    cb(snap.docs.map((d) => mapEntry(d.id, d.data(), uid)));
  });
}

export interface EntryDraft {
  id?: string;
  date: number;
  amount: number;
  type: EntryType;
  category: string;
  note: string;
}

export async function saveEntry(
  uid: string,
  inst: Institution,
  draft: EntryDraft,
): Promise<void> {
  const id = draft.id ?? doc(mirrorCol(uid)).id;
  const payload = {
    date: Timestamp.fromMillis(draft.date),
    amount: draft.amount,
    type: draft.type,
    category: draft.category,
    note: draft.note,
    institutionId: inst.id,
    institutionName: inst.name,
    currency: inst.currency,
    userId: uid,
  };
  const batch = writeBatch(db);
  batch.set(doc(db, "users", uid, "institutions", inst.id, "entries", id), payload);
  batch.set(doc(db, "users", uid, "entries", id), payload);
  await batch.commit();
}

export async function deleteEntry(
  uid: string,
  instId: string,
  id: string,
): Promise<void> {
  const batch = writeBatch(db);
  batch.delete(doc(db, "users", uid, "institutions", instId, "entries", id));
  batch.delete(doc(db, "users", uid, "entries", id));
  await batch.commit();
}

export async function deleteAllUserData(uid: string): Promise<void> {
  const mirror = await getDocs(mirrorCol(uid));
  await Promise.all(mirror.docs.map((d) => deleteDoc(d.ref)));
  const insts = await getDocs(instCol(uid));
  for (const inst of insts.docs) {
    const nested = await getDocs(instEntriesCol(uid, inst.id));
    await Promise.all(nested.docs.map((d) => deleteDoc(d.ref)));
    await deleteDoc(inst.ref);
  }
}
