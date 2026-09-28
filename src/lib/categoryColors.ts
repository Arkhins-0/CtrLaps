// Shared by server and client: no "server-only".

/** The colours a category can take: distinct from each other and from the status colours, readable on dark and light. */
export const CATEGORY_COLORS = [
  "#3B82F6", // blue
  "#F97316", // orange
  "#EC4899", // pink
  "#14B8A6", // teal
  "#A855F7", // violet
  "#84CC16", // lime
  "#D97706", // amber-brown
  "#06B6D4", // cyan
  "#EF4444", // red
  "#64748B", // slate
];

export const isColor = (value: string): boolean => /^#[0-9A-Fa-f]{6}$/.test(value);
