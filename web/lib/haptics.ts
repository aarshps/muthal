import { createHaptics } from "./haptics-core";
import { prefs } from "./prefs";

/**
 * App-wired family haptics — the shared M3E vibration scheme
 * (tick/click/success/warning/error) gated by the user's "Haptic feedback"
 * setting. The factory lives in the generated `lib/haptics-core.ts` (synced from
 * hora-core; do not hand-edit that file).
 */
export const haptics = createHaptics(() => prefs.getHaptics());
