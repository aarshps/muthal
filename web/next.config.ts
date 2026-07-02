import type { NextConfig } from "next";

const FIREBASE_AUTH_HANDLER = "https://hora-muthal.firebaseapp.com";

const nextConfig: NextConfig = {
  // Reverse-proxy the Firebase Auth helper so it is served same-origin. This is
  // the Firebase-recommended fix for signInWithRedirect/popup breaking under
  // browser third-party-storage partitioning (mobile Chrome, iOS Safari). Paired
  // with authDomain = window.location.hostname in lib/firebase.ts.
  async rewrites() {
    return [
      {
        source: "/__/auth/:path*",
        destination: `${FIREBASE_AUTH_HANDLER}/__/auth/:path*`,
      },
      {
        source: "/__/firebase/:path*",
        destination: `${FIREBASE_AUTH_HANDLER}/__/firebase/:path*`,
      },
    ];
  },
};

export default nextConfig;
