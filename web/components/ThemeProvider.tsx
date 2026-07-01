"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { prefs, type Appearance } from "@/lib/prefs";

interface ThemeContextValue {
  appearance: Appearance;
  setAppearance: (a: Appearance) => void;
  useRoundedFont: boolean;
  setUseRoundedFont: (v: boolean) => void;
}

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

function applyTheme(appearance: Appearance, roundedFont: boolean) {
  const root = document.documentElement;
  root.setAttribute("data-theme", appearance);
  root.setAttribute("data-font", roundedFont ? "rounded" : "system");
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [appearance, setAppearanceState] = useState<Appearance>("system");
  const [useRoundedFont, setUseRoundedFontState] = useState(true);

  useEffect(() => {
    const a = prefs.getAppearance();
    const f = prefs.getUseGoogleFont();
    setAppearanceState(a);
    setUseRoundedFontState(f);
    applyTheme(a, f);
  }, []);

  const setAppearance = useCallback(
    (a: Appearance) => {
      setAppearanceState(a);
      prefs.setAppearance(a);
      applyTheme(a, useRoundedFont);
    },
    [useRoundedFont],
  );

  const setUseRoundedFont = useCallback(
    (v: boolean) => {
      setUseRoundedFontState(v);
      prefs.setUseGoogleFont(v);
      applyTheme(appearance, v);
    },
    [appearance],
  );

  return (
    <ThemeContext.Provider
      value={{ appearance, setAppearance, useRoundedFont, setUseRoundedFont }}
    >
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error("useTheme must be used within ThemeProvider");
  return ctx;
}
