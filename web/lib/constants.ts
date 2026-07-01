/** App-wide constants — keep in sync with android Constants.kt / ios Constants.swift. */

export const APP_NAME = "Muthal";

export const APP_TAGLINE = "Income & expense tracking for institutions.";

export const PRIVACY_URL =
  "https://github.com/aarshps/muthal/blob/main/web/PRIVACY.md";

/** Institution types (SPEC §1). Order is significant across platforms. */
export const INSTITUTION_TYPES: string[] = [
  "Temple",
  "Church",
  "Library",
  "Other",
];
