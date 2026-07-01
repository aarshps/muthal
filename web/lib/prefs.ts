/**
 * localStorage-backed preferences, mirroring the keys in
 * android Preferences / ios Preferences.swift. SSR-safe: every getter guards
 * against `window` being undefined.
 */

const KEY = {
  haptics: "haptics_enabled",
  useGoogleFont: "use_google_font",
  appearance: "appearance_mode",
  appLock: "app_lock_enabled",
  selectedInstitution: "selected_institution",
} as const;

export type Appearance = "system" | "light" | "dark";

function get(key: string): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(key);
}
function set(key: string, value: string): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(key, value);
}
function getBool(key: string, fallback: boolean): boolean {
  const v = get(key);
  return v === null ? fallback : v === "true";
}

export const prefs = {
  getAppearance: (): Appearance =>
    (get(KEY.appearance) as Appearance | null) ?? "system",
  setAppearance: (v: Appearance) => set(KEY.appearance, v),

  getUseGoogleFont: (): boolean => getBool(KEY.useGoogleFont, true),
  setUseGoogleFont: (v: boolean) => set(KEY.useGoogleFont, String(v)),

  getHaptics: (): boolean => getBool(KEY.haptics, true),
  setHaptics: (v: boolean) => set(KEY.haptics, String(v)),

  getAppLock: (): boolean => getBool(KEY.appLock, false),
  setAppLock: (v: boolean) => set(KEY.appLock, String(v)),

  getSelectedInstitution: (): string | null => get(KEY.selectedInstitution),
  setSelectedInstitution: (id: string) => set(KEY.selectedInstitution, id),
};
