import type { Config } from "tailwindcss";

const v = (name: string) => `rgb(var(--${name}) / <alpha-value>)`;

/**
 * Night with one gold accent: the CTR yellow on near-black, or its light twin. The same palettes as the
 * Android app's Theme.kt, chosen the same way (Account → Settings → Theme), so the site and the app read as one.
 */
const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      // Each colour is a CSS variable (globals.css), as the app's palette: light or dark, pure black, the accent.
      colors: {
        night: { DEFAULT: v("night"), panel: v("panel"), line: v("line"), high: v("high") },
        snow: { DEFAULT: v("snow"), soft: v("snow-soft"), faint: v("snow-faint") },
        gold: { DEFAULT: v("gold"), deep: v("gold-deep") },
        // Words and icons on the accent: near-black, or white on a dark custom colour.
        ink: v("ink"),
        danger: v("danger"),
      },
      fontFamily: {
        body: ["var(--font-body)", "system-ui", "sans-serif"],
      },
      boxShadow: {
        card: "0 1px 0 rgb(255 255 255 / 0.04) inset, 0 20px 50px -20px rgb(0 0 0 / 0.8)",
        glow: "0 0 80px -20px rgb(var(--gold) / 0.35)",
      },
    },
  },
  plugins: [],
};

export default config;
