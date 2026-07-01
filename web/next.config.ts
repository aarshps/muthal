import type { NextConfig } from "next";

// Muthal web ships as a fully client-rendered PWA on Firebase Hosting, so we
// emit a static export (no Node server / Cloud Functions needed). Auth uses the
// project's own authDomain (`*.firebaseapp.com`), which natively serves the
// Firebase auth handler — no reverse proxy required.
const nextConfig: NextConfig = {
  output: "export",
  images: { unoptimized: true },
  // Firebase Hosting serves clean URLs; trailing slashes keep export paths tidy.
  trailingSlash: true,
};

export default nextConfig;
