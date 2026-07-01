"use client";

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import {
  deleteUser,
  getRedirectResult,
  onAuthStateChanged,
  signInWithRedirect,
  signOut as fbSignOut,
  type User,
} from "firebase/auth";
import {
  auth,
  firebaseReady,
  getAnalyticsClient,
  googleProvider,
} from "@/lib/firebase";
import { deleteAllUserData } from "@/lib/firestore";
import { analytics } from "@/lib/analytics";

interface AuthContextValue {
  user: User | null;
  initialized: boolean;
  authError: string | null;
  signInWithGoogle: () => Promise<void>;
  signOut: () => Promise<void>;
  deleteAccount: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function friendlyAuthError(err: unknown): string {
  const code = (err as { code?: string }).code ?? "";
  switch (code) {
    case "auth/account-exists-with-different-credential":
      return "An account already exists with this email using a different sign-in method.";
    case "auth/network-request-failed":
      return "Network error — check your connection and try again.";
    case "auth/popup-closed-by-user":
    case "auth/cancelled-popup-request":
      return "Sign-in was cancelled.";
    case "auth/unauthorized-domain":
      return "This domain isn't authorized for sign-in. Add it in Firebase Auth settings.";
    default:
      return (err as Error)?.message || "Sign-in failed — please try again.";
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [initialized, setInitialized] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);

  useEffect(() => {
    if (!firebaseReady) {
      setInitialized(true);
      return;
    }
    getAnalyticsClient();

    getRedirectResult(auth)
      .then((res) => {
        if (res?.user) analytics.authSignIn("google");
      })
      .catch((err) => setAuthError(friendlyAuthError(err)));

    return onAuthStateChanged(auth, (u) => {
      setUser(u);
      setInitialized(true);
    });
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      initialized,
      authError,
      signInWithGoogle: async () => {
        setAuthError(null);
        // Full-page redirect everywhere: it has no popup-relay step, so it works
        // identically in installed PWAs, mobile, iOS Safari, and desktop. The
        // result is completed by getRedirectResult on the next load (above).
        await signInWithRedirect(auth, googleProvider);
      },
      signOut: async () => {
        analytics.authSignOut();
        await fbSignOut(auth);
      },
      deleteAccount: async () => {
        const current = auth.currentUser;
        if (!current) throw new Error("You're not signed in.");
        try {
          await deleteAllUserData(current.uid);
        } catch {
          // continue — auth-level delete is the required step
        }
        await deleteUser(current);
      },
    }),
    [user, initialized, authError],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
