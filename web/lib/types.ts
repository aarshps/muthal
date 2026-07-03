/** Canonical model — ports android model/*.kt / ios Models/*.swift (SPEC §1). Multi-user
 * institutions with roles: see SPEC §3 for the role matrix. */

export type EntryType = "income" | "expense";
export type Role = "owner" | "admin" | "member";

export interface Institution {
  id: string;
  name: string;
  type: string; // Temple | Church | Library | Other
  currency: string; // ISO-ish code (SPEC §4)
  code: string; // 6-character join code
  ownerId: string;
  createdAt: number; // epoch ms
}

/** A user's membership + role within one institution (institutions/{id}/members/{uid}). */
export interface Member {
  uid: string;
  role: Role;
  displayName: string;
  email: string;
  photoUrl: string;
  joinedAt: number;
}

/** A user's index entry for one institution they belong to (users/{uid}/memberships/{id}),
 * with just enough denormalized institution data to render the switcher. */
export interface Membership {
  institutionId: string;
  role: Role;
  institutionName: string;
  institutionType: string;
  currency: string;
}

export interface Category {
  id: string;
  name: string;
  kind: EntryType;
}

export interface Entry {
  id: string;
  date: number; // epoch ms
  amount: number; // always > 0; direction carried by `type`
  type: EntryType;
  category: string;
  note: string;
  createdBy: string;
  createdAt: number;
}

export function isAdminOrOwner(role: Role): boolean {
  return role === "owner" || role === "admin";
}
