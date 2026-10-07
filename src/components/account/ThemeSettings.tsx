"use client";

import { useEffect, useState } from "react";
import { ACCENTS, THEME_KEY, type ThemeSettings as Settings } from "@/lib/themeScript";
import { Icon, type IconName } from "../Icon";

declare global {
  interface Window {
    __ctrlapsTheme?: (s: Settings) => void;
  }
}

const MODES: { mode: NonNullable<Settings["mode"]>; icon: IconName; label: string; hint: string }[] = [
  { mode: "light", icon: "lightMode", label: "Light", hint: "Light pages, dark text" },
  { mode: "dark", icon: "darkMode", label: "Dark", hint: "Dark pages, light text" },
  { mode: "system", icon: "autoMode", label: "As the system is", hint: "Light or dark, as this device is set" },
];

function Switch({ on, onChange, label }: { on: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      aria-label={label}
      onClick={() => onChange(!on)}
      className={`relative h-8 w-14 shrink-0 rounded-full border transition-colors ${on ? "border-gold bg-gold" : "border-snow-faint bg-night-high"}`}
    >
      <span className={`absolute top-1 h-5.5 w-5.5 rounded-full transition-all ${on ? "left-7 bg-ink" : "left-1 bg-snow-faint"}`} style={{ width: 22, height: 22 }} />
    </button>
  );
}

function SwitchRow({ icon, title, hint, on, onChange }: { icon: IconName; title: string; hint: string; on: boolean; onChange: (v: boolean) => void }) {
  return (
    <div className="flex items-center gap-5 px-2 py-3.5">
      <Icon name={icon} className="h-6 w-6 shrink-0 text-gold" />
      <span className="min-w-0 flex-1">
        <span className="block font-bold">{title}</span>
        <span className="block text-sm text-snow-soft">{hint}</span>
      </span>
      <Switch on={on} onChange={onChange} label={title} />
    </div>
  );
}

/**
 * The website's theme, as the app's Theme page: light, dark or the system's; a colour theme; pure black; a custom
 * colour. Kept in this browser; the whole site changes at once.
 */
export function ThemeSettings() {
  const [s, setS] = useState<Settings>({});
  useEffect(() => {
    try {
      setS(JSON.parse(localStorage.getItem(THEME_KEY) || "{}") || {});
    } catch {
      setS({});
    }
  }, []);
  const save = (next: Settings) => {
    setS(next);
    try {
      localStorage.setItem(THEME_KEY, JSON.stringify(next));
    } catch {
      // Private browsing: the change still applies until the page is left.
    }
    window.__ctrlapsTheme?.(next);
  };
  const mode = s.mode ?? "dark";
  const accent = s.accent ?? "Gold";
  const color = /^#[0-9a-f]{6}$/i.test(s.color ?? "") ? s.color! : "#FFD100";
  const light = mode === "light";

  return (
    <div>
      <div className="flex items-center gap-2 px-2 py-2">
        <h2 className="flex-1 text-xl font-bold">Mode</h2>
        {MODES.map((m) => (
          <button
            key={m.mode}
            type="button"
            className={`btn-icon h-11 w-11 ${mode === m.mode ? "text-gold" : "text-snow-faint/60"}`}
            aria-label={m.label}
            aria-pressed={mode === m.mode}
            title={m.label}
            onClick={() => save({ ...s, mode: m.mode })}
          >
            <Icon name={m.icon} className="h-7 w-7" />
          </button>
        ))}
      </div>
      <p className="px-2 text-sm text-snow-faint">{MODES.find((m) => m.mode === mode)?.hint}</p>

      <label className="mt-6 block px-2">
        <span className="label">Colour theme</span>
        <select
          className="input"
          value={s.custom ? "custom" : accent}
          onChange={(e) => (e.target.value === "custom" ? save({ ...s, custom: true, color }) : save({ ...s, custom: false, accent: e.target.value as Settings["accent"] }))}
        >
          {Object.keys(ACCENTS).map((a) => (
            <option key={a} value={a}>
              {a === "Gold" ? "CTR Gold" : a}
            </option>
          ))}
          <option value="custom">Custom colour</option>
        </select>
      </label>
      <div className="mt-3 flex gap-3 px-2">
        {(Object.keys(ACCENTS) as (keyof typeof ACCENTS)[]).map((a) => (
          <button
            key={a}
            type="button"
            aria-label={a}
            className={`h-10 w-10 rounded-full border-2 ${!s.custom && accent === a ? "border-snow" : "border-transparent"}`}
            style={{ backgroundColor: light ? ACCENTS[a][2] : ACCENTS[a][0] }}
            onClick={() => save({ ...s, custom: false, accent: a })}
          />
        ))}
      </div>

      <div className="mt-4">
        <SwitchRow icon="darkMode" title="Pure black" hint="As dark as it gets: a black page in the dark theme" on={!!s.black} onChange={(v) => save({ ...s, black: v })} />
        <SwitchRow icon="palette" title="Custom colour" hint="Your own colour for the accent" on={!!s.custom} onChange={(v) => save({ ...s, custom: v, color })} />
        <div className="flex items-center gap-5 px-2 py-3.5">
          <Icon name="palette" className="h-6 w-6 shrink-0 text-gold" />
          <span className="min-w-0 flex-1">
            <span className="block font-bold">Colour picker</span>
            <span className="block text-sm text-snow-soft">Choose a colour: {color.toUpperCase()}</span>
          </span>
          {/* The browser's own colour picker; picking turns Custom colour on. */}
          <input
            type="color"
            aria-label="Pick a colour"
            value={color}
            onChange={(e) => save({ ...s, custom: true, color: e.target.value })}
            className="h-10 w-10 shrink-0 cursor-pointer rounded-full border-2 border-snow/60 bg-transparent p-0"
          />
        </div>
      </div>
      <p className="px-2 pt-2 text-xs text-snow-faint">Kept in this browser. The phone app has its own setting under Account → Settings → Theme.</p>
    </div>
  );
}
