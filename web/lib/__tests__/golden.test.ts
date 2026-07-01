/**
 * Parity guard: runs the shared, language-neutral vectors in
 * shared/domain/golden-vectors.json through the web implementations. The same
 * vectors are loaded by the Android and iOS unit tests so all three platforms
 * provably agree. See shared/domain/SPEC.md.
 */
import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { compactFormat, formatCurrency } from "../currency";
import { categoriesFor } from "../categories";
import { summarize, type Summary } from "../summary";

interface Vectors {
  currency: {
    format: { amount: number; code: string; expected: string }[];
    compact: { amount: number; expected: string }[];
  };
  categories: { type: string; expected: string[] }[];
  summary: {
    now: string;
    entries: { amount: number; type: string; date: string }[];
    expected: Summary;
  }[];
}

const vectors: Vectors = JSON.parse(
  readFileSync(
    fileURLToPath(
      new URL("../../../shared/domain/golden-vectors.json", import.meta.url),
    ),
    "utf8",
  ),
);

describe("golden vectors — currency", () => {
  it.each(vectors.currency.format)(
    "format($amount, $code) → $expected",
    ({ amount, code, expected }) => {
      expect(formatCurrency(amount, code)).toBe(expected);
    },
  );

  it.each(vectors.currency.compact)(
    "compact($amount) → $expected",
    ({ amount, expected }) => {
      expect(compactFormat(amount)).toBe(expected);
    },
  );
});

describe("golden vectors — categories", () => {
  it.each(vectors.categories)("categoriesFor($type)", ({ type, expected }) => {
    expect(categoriesFor(type)).toEqual(expected);
  });
});

describe("golden vectors — summary", () => {
  it.each(vectors.summary)("summarize case #%#", ({ now, entries, expected }) => {
    const result = summarize(
      entries.map((e) => ({
        amount: e.amount,
        type: e.type,
        date: Date.parse(e.date),
      })),
      Date.parse(now),
    );
    expect(result).toEqual(expected);
  });
});
