// Shared by server and client: no "server-only".

import { CATEGORY_COLORS } from "./categoryColors";

/** "#RRGGBB" as [r, g, b] (0–255); null for anything else. */
function rgb(hex: string): [number, number, number] | null {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex.trim());
  if (!m) return null;
  const n = parseInt(m[1], 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

/** WCAG relative luminance, 0 (black) to 1 (white). */
export function luminance(hex: string): number {
  const c = rgb(hex);
  if (!c) return 0;
  const [r, g, b] = c.map((v) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/** Near-black or white, whichever reads better on `hex` (the higher WCAG contrast): text on a filled chip. */
export function contrastText(hex: string): string {
  const l = luminance(hex);
  return (l + 0.05) / 0.05 >= 1.05 / (l + 0.05) ? "#0B0B0C" : "#FFFFFF";
}

/** `hex` see-through: "rgba(…)" with alpha 0–1, for a tinted background. */
export function withAlpha(hex: string, alpha: number): string {
  const c = rgb(hex);
  return c ? `rgba(${c[0]}, ${c[1]}, ${c[2]}, ${alpha})` : hex;
}

/**
 * The nearest of the category palette's colours to `hex` (red, green and blue weighted 2, 4, 3, roughly as the eye
 * tells them apart), so any colour saved for a category stays on the brand's list. An unreadable one is the first.
 */
export function snapToPalette(hex: string, palette: readonly string[] = CATEGORY_COLORS): string {
  const c = rgb(hex);
  if (!c) return palette[0];
  let best = palette[0];
  let bestDistance = Infinity;
  for (const p of palette) {
    const q = rgb(p)!;
    const d = 2 * (c[0] - q[0]) ** 2 + 4 * (c[1] - q[1]) ** 2 + 3 * (c[2] - q[2]) ** 2;
    if (d < bestDistance) {
      bestDistance = d;
      best = p;
    }
  }
  return best;
}
