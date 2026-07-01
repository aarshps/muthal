/** Ports android Categories.kt / ios Categories.swift — keep in sync (SPEC §4). */

export const INCOME_CATEGORIES: string[] = [
  "Donation",
  "Offering",
  "Membership",
  "Grant",
  "Other",
];

export const EXPENSE_CATEGORIES: string[] = [
  "Salary",
  "Utilities",
  "Maintenance",
  "Supplies",
  "Event",
  "Other",
];

/** Default category suggestions for a given entry type; unknown type → []. */
export function categoriesFor(type: string): string[] {
  if (type === "income") return INCOME_CATEGORIES;
  if (type === "expense") return EXPENSE_CATEGORIES;
  return [];
}
