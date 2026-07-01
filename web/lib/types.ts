/** Canonical model — ports android model/*.kt + ios Models/*.swift (SPEC §1). */

export type EntryType = "income" | "expense";

export interface Institution {
  id: string;
  name: string;
  type: string; // Temple | Church | Library | Other
  currency: string; // ISO-ish code (SPEC §3)
  createdAt: number; // epoch ms
}

export interface Entry {
  id: string;
  date: number; // epoch ms
  amount: number; // always > 0; direction carried by `type`
  type: EntryType;
  category: string;
  note: string;
  institutionId: string;
  institutionName: string;
  currency: string;
  userId: string;
}
