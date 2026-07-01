/** Ports android CurrencyHelper.kt / ios Currency.swift — keep in sync (SPEC §3). */

export interface CurrencyItem {
  code: string;
  name: string;
  symbol: string;
}

export const CURRENCIES: CurrencyItem[] = [
  { code: "INR", name: "Indian Rupee", symbol: "₹" },
  { code: "USD", name: "US Dollar", symbol: "$" },
  { code: "EUR", name: "Euro", symbol: "€" },
  { code: "GBP", name: "British Pound", symbol: "£" },
  { code: "JPY", name: "Japanese Yen", symbol: "¥" },
  { code: "AUD", name: "Australian Dollar", symbol: "$" },
  { code: "CAD", name: "Canadian Dollar", symbol: "$" },
  { code: "CHF", name: "Swiss Franc", symbol: "₣" },
  { code: "CNY", name: "Chinese Yuan", symbol: "¥" },
  { code: "HKD", name: "Hong Kong Dollar", symbol: "$" },
  { code: "NZD", name: "New Zealand Dollar", symbol: "$" },
  { code: "SEK", name: "Swedish Krona", symbol: "kr" },
  { code: "KRW", name: "South Korean Won", symbol: "₩" },
  { code: "SGD", name: "Singapore Dollar", symbol: "$" },
  { code: "MXN", name: "Mexican Peso", symbol: "$" },
  { code: "KES", name: "Kenyan Shilling", symbol: "KSh" },
  { code: "UNT", name: "Generic Unit", symbol: "#" },
];

export function currencySymbol(code: string): string {
  return CURRENCIES.find((c) => c.code === code)?.symbol ?? "$";
}

export function currencyItem(code: string): CurrencyItem | undefined {
  return CURRENCIES.find((c) => c.code === code);
}

/** "₹ 649" — integer when whole, else two decimals; sign kept as-is. */
export function formatCurrency(amount: number, code: string): string {
  const sym = currencySymbol(code);
  const rounded = amount % 1 === 0 ? amount.toFixed(0) : amount.toFixed(2);
  return `${sym} ${rounded}`;
}

/** Compact form using k / l (lakh) / m suffixes (Indian-influenced). */
export function compactFormat(amount: number): string {
  if (amount === 0) return "0";
  const abs = Math.abs(amount);
  if (abs >= 1_000_000) return trim(amount / 1_000_000, "m");
  if (abs >= 100_000) return trim(amount / 100_000, "l");
  if (abs >= 1_000) return trim(amount / 1_000, "k");
  return Math.round(amount).toFixed(0);
}

function trim(value: number, suffix: string): string {
  const formatted = value.toFixed(1);
  if (formatted.endsWith(".0")) return formatted.slice(0, -2) + suffix;
  return formatted + suffix;
}
