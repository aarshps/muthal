"use client";

import { getApp, getApps, initializeApp, type FirebaseApp } from "firebase/app";
import { getAuth, GoogleAuthProvider } from "firebase/auth";
import {
  getFirestore,
  initializeFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
  type Firestore,
} from "firebase/firestore";
import {
  getAnalytics,
  isSupported as analyticsIsSupported,
  type Analytics,
} from "firebase/analytics";

// Public Firebase web config for Muthal's own project. These values are not
// secret — they ship inside every client (the native apps embed the same project
// via google-services.json / GoogleService-Info.plist). Security is enforced by
// Firestore rules, not by hiding this config.
/** True only when the real SDK config is present (drives the "add config" notice). */
export const firebaseReady = Boolean(process.env.NEXT_PUBLIC_FIREBASE_API_KEY);

// `getAuth` throws `auth/invalid-api-key` when the key is absent, which would
// crash the static-export prerender (and any pre-config local build). Fall back
// to a placeholder so module init never throws; `firebaseReady` above is what
// gates real usage, and the UI shows a "configure Firebase" notice when false.
const firebaseConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY || "missing-api-key",
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID,
  storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID,
  measurementId: process.env.NEXT_PUBLIC_FIREBASE_MEASUREMENT_ID,
};

export const app: FirebaseApp = getApps().length
  ? getApp()
  : initializeApp(firebaseConfig);

export const auth = getAuth(app);

// Web uses Google sign-in only (same accounts as the native apps).
export const googleProvider = new GoogleAuthProvider();

// db must be a REAL Firestore instance — firebase modular APIs do an
// `instanceof Firestore` check. In the browser we enable an IndexedDB persistent
// cache (offline-first parity with the native apps); on the server (SSR) a plain
// instance, never queried server-side.
export const db: Firestore =
  typeof window !== "undefined"
    ? initializeFirestore(app, {
        localCache: persistentLocalCache({
          tabManager: persistentMultipleTabManager(),
        }),
      })
    : getFirestore(app);

let analyticsInstance: Analytics | null = null;
let analyticsTried = false;

function maybeInitAnalytics(): void {
  if (analyticsTried || typeof window === "undefined" || !firebaseReady) return;
  analyticsTried = true;
  analyticsIsSupported()
    .then((ok) => {
      if (ok) analyticsInstance = getAnalytics(app);
    })
    .catch(() => {});
}

export function getAnalyticsClient(): Analytics | null {
  maybeInitAnalytics();
  return analyticsInstance;
}
