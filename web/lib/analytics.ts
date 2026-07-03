"use client";

import { logEvent, type Analytics } from "firebase/analytics";
import { getAnalyticsClient } from "./firebase";

/**
 * Thin wrapper around Firebase Analytics. Mirrors the Android/iOS analytics
 * surface 1:1 so dashboards stay readable across platforms. Scalar params only
 * (no institution names, amounts, or document IDs). Best-effort and browser-only;
 * every call no-ops on the server or when measurement isn't supported.
 */
function track(event: string, params?: Record<string, unknown>): void {
  const a: Analytics | null = getAnalyticsClient();
  if (!a) return;
  try {
    logEvent(a, event, params);
  } catch {
    // never let analytics break a user action
  }
}

export const analytics = {
  institutionAddOpen: () => track("institution_add_open"),
  institutionSave: () => track("institution_save"),
  institutionDelete: () => track("institution_delete"),
  institutionSwitch: () => track("institution_switch"),
  institutionSwitcherOpen: () => track("institution_switcher_open"),
  institutionJoinOpen: () => track("institution_join_open"),
  institutionJoin: () => track("institution_join"),
  institutionLeave: () => track("institution_leave"),
  institutionShare: () => track("institution_share"),

  membersOpen: () => track("members_open"),
  memberRoleChange: (role: string) => track("member_role_change", { role }),
  memberRemove: () => track("member_remove"),

  categoriesOpen: () => track("categories_open"),
  categoryAdd: (kind: string) => track("category_add", { kind }),
  categoryDelete: () => track("category_delete"),

  exportOpen: () => track("export_open"),
  exportRun: () => track("export_run"),
  exportShare: () => track("export_share"),

  entryAddOpen: () => track("entry_add_open"),
  entryEditOpen: () => track("entry_edit_open"),
  entrySave: (isNew: boolean, type: string) =>
    track("entry_save", { is_new: isNew, type }),
  entryDelete: () => track("entry_delete"),

  screenHistoryOpen: () => track("screen_history_open"),
  screenSettingsOpen: () => track("screen_settings_open"),
  screenAboutOpen: () => track("screen_about_open"),
  homeRefreshPull: () => track("home_refresh_pull"),

  settingThemeChange: (theme: string) =>
    track("setting_theme_change", { theme }),
  settingHapticsToggle: (enabled: boolean) =>
    track("setting_haptics_toggle", { enabled }),
  settingFontChange: (font: string) => track("setting_font_change", { font }),
  settingAppLockToggle: (enabled: boolean) =>
    track("setting_app_lock_toggle", { enabled }),

  authSignIn: (provider: string) => track("auth_sign_in", { provider }),
  authSignOut: () => track("auth_sign_out"),
};
