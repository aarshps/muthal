import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  globalIgnores([
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
  {
    rules: {
      // We intentionally hydrate client state from localStorage and subscribe to
      // Firestore inside effects — the synchronous setState those require is what
      // this experimental rule flags. Disable it rather than contort the code.
      "react-hooks/set-state-in-effect": "off",
    },
  },
]);

export default eslintConfig;
