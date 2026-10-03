/** A steady colour for a person's name in a group chat, from their id: eight shades that read well on dark. */
const PALETTE = ["#FFD100", "#5BD1C4", "#FF8AB3", "#B7A4FF", "#FFA14A", "#8BE28B", "#6EC6FF", "#FF7B6B"];

export function nameColor(id: string | null | undefined): string {
  if (!id) return PALETTE[0];
  let h = 0;
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0;
  return PALETTE[h % PALETTE.length];
}
