import { describe, expect, it } from "vitest";
import { normalizePositiveMoney } from "./money";

describe("normalizePositiveMoney", () => {
  it.each([
    ["12", "12.00"],
    ["12.3", "12.30"],
    ["12.34", "12.34"],
    [" 12.34 ", "12.34"],
  ])("normalizes %s as the decimal string %s", (input, expected) => {
    expect(normalizePositiveMoney(input)).toBe(expected);
  });

  it.each(["0", "0.00", "-1.00", "1.001", "1e2", "", "abc"])(
    "rejects invalid positive money value %s",
    (input) => {
      expect(normalizePositiveMoney(input)).toBeNull();
    },
  );
});
