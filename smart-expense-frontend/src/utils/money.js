const MONEY_PATTERN = /^(0|[1-9]\d*)(?:\.\d{1,2})?$/;

export function normalizePositiveMoney(value) {
  const text = String(value ?? "").trim();
  if (!MONEY_PATTERN.test(text)) return null;

  const [whole, fraction = ""] = text.split(".");
  const cents = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0") || "0");
  if (cents <= 0n) return null;

  return `${whole}.${fraction.padEnd(2, "0")}`;
}
