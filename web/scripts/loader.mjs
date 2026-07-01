import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";

// Material Color Utilities (built with plain `tsc`) uses extensionless relative
// imports, which raw Node ESM rejects but bundlers accept. This resolve hook
// retries a failed relative specifier with a ".js" suffix so the codegen script
// can run under plain `node`.
export async function resolve(specifier, context, nextResolve) {
  try {
    return await nextResolve(specifier, context);
  } catch (err) {
    if (
      context.parentURL &&
      /^\.\.?\//.test(specifier) &&
      !/\.[mc]?js$/.test(specifier)
    ) {
      const candidate = new URL(specifier + ".js", context.parentURL);
      if (existsSync(fileURLToPath(candidate))) {
        return { url: candidate.href, format: "module", shortCircuit: true };
      }
    }
    throw err;
  }
}
