"use client";

import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
  writeBatch,
  type DocumentData,
  type Unsubscribe,
} from "firebase/firestore";
import { db } from "./firebase";
import { INCOME_CATEGORIES, EXPENSE_CATEGORIES } from "./categories";
import type { Category, Entry, EntryType, Institution, Member, Membership, Role } from "./types";

/**
 * Firestore data layer for the v2 multi-user institution model (SPEC §1-3). Institutions
 * are top-level documents shared by members with a role each; join is by a 6-character
 * code. Institution creation and joining are SEQUENCES of awaited writes (not a batch) —
 * see the security-rules comments in shared/firebase/firestore.rules for why (rules
 * can't see sibling writes within the same batch).
 */

const institutions = () => collection(db, "institutions");
const institution = (instId: string) => doc(db, "institutions", instId);
const members = (instId: string) => collection(db, "institutions", instId, "members");
const categories = (instId: string) => collection(db, "institutions", instId, "categories");
const entries = (instId: string) => collection(db, "institutions", instId, "entries");
const codes = () => collection(db, "institutionCodes");
const myMemberships = (uid: string) => collection(db, "users", uid, "memberships");

function tsToMs(v: unknown): number {
  if (v && typeof (v as Timestamp).toMillis === "function") return (v as Timestamp).toMillis();
  if (typeof v === "number") return v;
  return Date.now();
}

// ── Memberships (the switcher's data source) ──

function mapMembership(id: string, data: DocumentData): Membership {
  return {
    institutionId: id,
    role: (data.role ?? "member") as Role,
    institutionName: data.institutionName ?? "",
    institutionType: data.institutionType ?? "Temple",
    currency: data.currency ?? "INR",
  };
}

export function subscribeMemberships(uid: string, cb: (list: Membership[]) => void): Unsubscribe {
  return onSnapshot(myMemberships(uid), (snap) => {
    const list = snap.docs
      .map((d) => mapMembership(d.id, d.data()))
      .sort((a, b) => a.institutionName.localeCompare(b.institutionName));
    cb(list);
  });
}

// ── Institution create ──

const CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no 0/O, 1/I/L

function randomCode(): string {
  let out = "";
  for (let i = 0; i < 6; i++) out += CODE_ALPHABET[Math.floor(Math.random() * CODE_ALPHABET.length)];
  return out;
}

async function generateUniqueCode(): Promise<string> {
  for (let i = 0; i < 8; i++) {
    const code = randomCode();
    const existing = await getDoc(doc(codes(), code));
    if (!existing.exists()) return code;
  }
  throw new Error("Could not generate a unique institution code");
}

/** Sequential create: institution doc -> owner member doc -> memberships index ->
 * code reservation -> seed categories. Each step is awaited; the rules only allow the
 * next step once the previous one has actually committed. */
export async function createInstitution(
  uid: string,
  profile: { displayName: string; email: string; photoUrl: string },
  input: { name: string; type: string; currency: string },
): Promise<Institution> {
  const ref = doc(institutions());
  const code = await generateUniqueCode();

  await setDoc(ref, {
    name: input.name,
    type: input.type,
    currency: input.currency,
    code,
    ownerId: uid,
    createdAt: serverTimestamp(),
  });

  await setDoc(doc(members(ref.id), uid), {
    role: "owner",
    displayName: profile.displayName,
    email: profile.email,
    photoUrl: profile.photoUrl,
    joinedAt: serverTimestamp(),
  });

  await setDoc(doc(myMemberships(uid), ref.id), {
    role: "owner",
    institutionName: input.name,
    institutionType: input.type,
    currency: input.currency,
  });

  await setDoc(doc(codes(), code), { institutionId: ref.id, name: input.name });

  const batch = writeBatch(db);
  const seed: [string, EntryType][] = [
    ...INCOME_CATEGORIES.map((c): [string, EntryType] => [c, "income"]),
    ...EXPENSE_CATEGORIES.map((c): [string, EntryType] => [c, "expense"]),
  ];
  for (const [name, kind] of seed) {
    batch.set(doc(categories(ref.id)), { name, kind, createdAt: serverTimestamp() });
  }
  await batch.commit();

  return { id: ref.id, name: input.name, type: input.type, currency: input.currency, code, ownerId: uid, createdAt: Date.now() };
}

// ── Join by code ──

export interface CodePreview {
  code: string;
  institutionId: string;
  institutionName: string;
}

export async function resolveCode(code: string): Promise<CodePreview | null> {
  const snap = await getDoc(doc(codes(), code.toUpperCase()));
  if (!snap.exists()) return null;
  const data = snap.data();
  if (typeof data.institutionId !== "string") return null;
  return { code: code.toUpperCase(), institutionId: data.institutionId, institutionName: data.name ?? "" };
}

/** Joins as a plain member (SPEC §3: default role is always member), then reads back
 * the full institution doc (now readable, since membership just committed) to populate
 * an accurate memberships index entry. */
export async function joinInstitution(
  uid: string,
  profile: { displayName: string; email: string; photoUrl: string },
  preview: CodePreview,
): Promise<Institution> {
  const existing = await getDoc(doc(members(preview.institutionId), uid));
  if (!existing.exists()) {
    await setDoc(doc(members(preview.institutionId), uid), {
      role: "member",
      displayName: profile.displayName,
      email: profile.email,
      photoUrl: profile.photoUrl,
      joinedAt: serverTimestamp(),
    });
  }
  const instSnap = await getDoc(institution(preview.institutionId));
  const inst = mapInstitution(instSnap.id, instSnap.data() ?? {});
  await setDoc(doc(myMemberships(uid), inst.id), {
    role: existing.exists() ? (existing.data()?.role ?? "member") : "member",
    institutionName: inst.name,
    institutionType: inst.type,
    currency: inst.currency,
  });
  return inst;
}

export function joinLink(code: string): string {
  return `https://muthal-web.vercel.app/join/${code}`;
}

/** Owner only (SPEC §3). Permanently removes the institution and everything in it. A
 * sequence of separately-awaited deletes, ordered so every step's rule check still has
 * what it needs — see SPEC §2 for the exact reasoning per step. */
export async function deleteInstitution(uid: string, instId: string): Promise<void> {
  const instSnap = await getDoc(institution(instId));
  const code = instSnap.data()?.code as string | undefined;

  const entrySnap = await getDocs(entries(instId));
  const entryDeletes = entrySnap.docs.map(d => deleteDoc(d.ref));

  const categorySnap = await getDocs(categories(instId));
  const categoryDeletes = categorySnap.docs.map(d => deleteDoc(d.ref));

  const memberSnap = await getDocs(members(instId));
  const memberDeletes: Promise<void>[] = [];
  for (const m of memberSnap.docs) {
    if (m.id === uid) continue;
    memberDeletes.push(deleteDoc(doc(collection(db, "users", m.id, "memberships"), instId)).catch(() => {}));
    memberDeletes.push(deleteDoc(m.ref));
  }

  const codeDelete = code ? [deleteDoc(doc(codes(), code))] : [];

  await Promise.all([...codeDelete, ...entryDeletes, ...categoryDeletes, ...memberDeletes]);

  await deleteDoc(doc(myMemberships(uid), instId));
  await deleteDoc(institution(instId));
  await deleteDoc(doc(members(instId), uid));
}

// ── Institution detail / members ──

function mapInstitution(id: string, data: DocumentData): Institution {
  return {
    id,
    name: data.name ?? "",
    type: data.type ?? "Temple",
    currency: data.currency ?? "INR",
    code: data.code ?? "",
    ownerId: data.ownerId ?? "",
    createdAt: tsToMs(data.createdAt),
  };
}

export function subscribeInstitution(instId: string, cb: (inst: Institution | null) => void): Unsubscribe {
  return onSnapshot(institution(instId), (snap) => cb(snap.exists() ? mapInstitution(snap.id, snap.data()) : null));
}

function mapMember(id: string, data: DocumentData): Member {
  return {
    uid: id,
    role: (data.role ?? "member") as Role,
    displayName: data.displayName ?? "",
    email: data.email ?? "",
    photoUrl: data.photoUrl ?? "",
    joinedAt: tsToMs(data.joinedAt),
  };
}

export function subscribeMembers(instId: string, cb: (list: Member[]) => void): Unsubscribe {
  return onSnapshot(members(instId), (snap) => {
    const roleRank = (r: Role) => (r === "owner" ? 0 : r === "admin" ? 1 : 2);
    const list = snap.docs
      .map((d) => mapMember(d.id, d.data()))
      .sort((a, b) => roleRank(a.role) - roleRank(b.role) || a.displayName.localeCompare(b.displayName));
    cb(list);
  });
}

/** Owner only (enforced server-side); also fans the role out to the member's own
 * memberships index so their switcher reflects the change immediately. */
export async function setMemberRole(instId: string, memberUid: string, role: Role): Promise<void> {
  await updateDoc(doc(members(instId), memberUid), { role });
  await updateDoc(doc(collection(db, "users", memberUid, "memberships"), instId), { role });
}

/** Owner removes a member, or a member leaves on their own. */
export async function removeMember(instId: string, memberUid: string): Promise<void> {
  await deleteDoc(doc(members(instId), memberUid));
  await deleteDoc(doc(collection(db, "users", memberUid, "memberships"), instId)).catch(() => {});
}

// ── Categories (admin/owner write; SPEC §5) ──

function mapCategory(id: string, data: DocumentData): Category {
  return { id, name: data.name ?? "", kind: (data.kind === "expense" ? "expense" : "income") as EntryType };
}

export function subscribeCategories(instId: string, cb: (list: Category[]) => void): Unsubscribe {
  return onSnapshot(categories(instId), (snap) => {
    const list = snap.docs
      .map((d) => mapCategory(d.id, d.data()))
      .sort((a, b) => a.kind.localeCompare(b.kind) || a.name.localeCompare(b.name));
    cb(list);
  });
}

export async function addCategory(instId: string, name: string, kind: EntryType): Promise<void> {
  await setDoc(doc(categories(instId)), { name, kind, createdAt: serverTimestamp() });
}

export async function deleteCategory(instId: string, categoryId: string): Promise<void> {
  await deleteDoc(doc(categories(instId), categoryId));
}

// ── Entries (admin/owner write, all members read; SPEC §2) ──

function mapEntry(id: string, data: DocumentData): Entry {
  return {
    id,
    date: tsToMs(data.date),
    amount: typeof data.amount === "number" ? data.amount : 0,
    type: (data.type === "expense" ? "expense" : "income") as EntryType,
    category: data.category ?? "",
    note: data.note ?? "",
    createdBy: data.createdBy ?? "",
    createdAt: tsToMs(data.createdAt),
  };
}

export function subscribeEntries(instId: string, cb: (list: Entry[]) => void): Unsubscribe {
  const q = query(entries(instId), orderBy("date", "desc"));
  return onSnapshot(q, (snap) => cb(snap.docs.map((d) => mapEntry(d.id, d.data()))));
}

/** One-shot read, for the period export screen (no need for a live listener there). */
export async function getEntriesOnce(instId: string): Promise<Entry[]> {
  const q = query(entries(instId), orderBy("date", "asc"));
  const snap = await getDocs(q);
  return snap.docs.map((d) => mapEntry(d.id, d.data()));
}

export interface EntryDraft {
  id?: string;
  date: number;
  amount: number;
  type: EntryType;
  category: string;
  note: string;
}

export async function saveEntry(uid: string, instId: string, draft: EntryDraft): Promise<void> {
  const id = draft.id ?? doc(entries(instId)).id;
  await setDoc(doc(entries(instId), id), {
    date: Timestamp.fromMillis(draft.date),
    amount: draft.amount,
    type: draft.type,
    category: draft.category,
    note: draft.note,
    createdBy: uid,
    createdAt: serverTimestamp(),
  });
}

export async function deleteEntry(instId: string, id: string): Promise<void> {
  await deleteDoc(doc(entries(instId), id));
}

/** Settings → Delete account: removes only the user's OWN presence (their member doc
 * in each institution + their memberships index), never shared entries/categories other
 * members depend on. If the user was the sole owner of an institution, it is left
 * ownerless per SPEC §3 (acceptable at this scale). */
export async function deleteAllUserData(uid: string): Promise<void> {
  const memberships = await getDocs(myMemberships(uid));
  for (const m of memberships.docs) {
    await deleteDoc(doc(members(m.id), uid)).catch(() => {});
    await deleteDoc(m.ref);
  }
}
