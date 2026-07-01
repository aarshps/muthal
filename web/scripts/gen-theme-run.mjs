// Registers the extensionless-import resolve hook, then runs the theme generator.
// Usage:  node scripts/gen-theme-run.mjs   (or: npm run gen-theme)
import { register } from "node:module";
register("./loader.mjs", import.meta.url);
await import("./gen-theme.mjs");
