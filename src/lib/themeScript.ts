// Shared by server and client: no "server-only".

/** The website's theme, kept in this browser (localStorage "ctrlaps-theme"), as the app's Theme page. */
export type ThemeSettings = {
  mode?: "dark" | "light" | "system";
  black?: boolean;
  accent?: "Gold" | "Orange" | "Green" | "Blue" | "Violet";
  custom?: boolean;
  /** "#RRGGBB", used while [custom] is on. */
  color?: string;
};

export const THEME_KEY = "ctrlaps-theme";

/** The accent presets, as the app's `Accent`: [dark, dark deep, light, light deep]. No red: red means danger. */
export const ACCENTS = {
  Gold: ["#FFD100", "#E0A800", "#C89B00", "#A67F00"],
  Orange: ["#FF8A3D", "#E8701A", "#E8701A", "#C25A10"],
  Green: ["#4ADE80", "#22C55E", "#16A34A", "#15803D"],
  Blue: ["#60A5FA", "#3B82F6", "#3B82F6", "#2563EB"],
  Violet: ["#B7A4FF", "#9F86FF", "#8B5CF6", "#7C3AED"],
} as const;

/**
 * Applies a theme to the page: light or dark (or the system's), pure black, the accent (a preset, or a custom colour
 * fitted to the page: lighter on dark, darker on light, contrast ≥ 3), and black or white words on it. Runs in the
 * page's head before it paints (so no flash), and again from the Theme page; `window.__ctrlapsTheme(settings)`.
 */
export const THEME_SCRIPT = `(function () {
  var A = ${JSON.stringify(ACCENTS)};
  function rgb(h) { var n = parseInt(h.slice(1), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; }
  function lum(c) { var a = c.map(function (v) { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }); return 0.2126 * a[0] + 0.7152 * a[1] + 0.0722 * a[2]; }
  function contrast(a, b) { var x = lum(a) + 0.05, y = lum(b) + 0.05; return x > y ? x / y : y / x; }
  function hsv(c) { var r = c[0] / 255, g = c[1] / 255, b = c[2] / 255, mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn, h = 0;
    if (d) { h = mx === r ? ((g - b) / d) % 6 : mx === g ? (b - r) / d + 2 : (r - g) / d + 4; h *= 60; if (h < 0) h += 360; }
    return [h, mx ? d / mx : 0, mx]; }
  function back(h, s, v) { var c = v * s, x = c * (1 - Math.abs(((h / 60) % 2) - 1)), m = v - c, p = h < 60 ? [c, x, 0] : h < 120 ? [x, c, 0] : h < 180 ? [0, c, x] : h < 240 ? [0, x, c] : h < 300 ? [x, 0, c] : [c, 0, x];
    return p.map(function (q) { return Math.round((q + m) * 255); }); }
  function fit(c, page, dark) { var k = hsv(c), out = c, i = 0;
    while (contrast(out, page) < 3 && i++ < 40) { if (dark) { k[2] = Math.min(1, k[2] + 0.03); if (k[2] >= 1) k[1] = Math.max(0, k[1] - 0.03); } else k[2] = Math.max(0, k[2] - 0.03); out = back(k[0], k[1], k[2]); }
    var d = hsv(out); return [out, back(d[0], d[1], d[2] * 0.85)]; }
  window.__ctrlapsTheme = function (s) {
    s = s || {};
    var mode = s.mode || "dark";
    var dark = mode === "system" ? !window.matchMedia("(prefers-color-scheme: light)").matches : mode !== "light";
    var root = document.documentElement;
    root.dataset.mode = dark ? "dark" : "light";
    if (s.black && dark) root.dataset.black = ""; else delete root.dataset.black;
    var page = !dark ? [243, 243, 245] : s.black ? [0, 0, 0] : [11, 11, 12];
    var acc, deep;
    if (s.custom && /^#[0-9a-f]{6}$/i.test(s.color || "")) { var f = fit(rgb(s.color), page, dark); acc = f[0]; deep = f[1]; }
    else { var p = A[s.accent] || A.Gold; acc = rgb(dark ? p[0] : p[2]); deep = rgb(dark ? p[1] : p[3]); }
    root.style.setProperty("--gold", acc.join(" "));
    root.style.setProperty("--gold-deep", deep.join(" "));
    var ink = contrast(acc, [11, 11, 12]) >= contrast(acc, [255, 255, 255]) ? "11 11 12" : "255 255 255";
    root.style.setProperty("--ink", ink);
  };
  var saved = {};
  try { saved = JSON.parse(localStorage.getItem("${THEME_KEY}") || "{}") || {}; } catch (e) {}
  window.__ctrlapsTheme(saved);
  // "As the system is": follow it when it changes.
  window.matchMedia("(prefers-color-scheme: light)").addEventListener("change", function () {
    try { var s = JSON.parse(localStorage.getItem("${THEME_KEY}") || "{}") || {}; if (s.mode === "system") window.__ctrlapsTheme(s); } catch (e) {}
  });
})();`;
