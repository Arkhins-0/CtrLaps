"use client";

import { useState } from "react";
import { Icon } from "./Icon";

/** A small copy icon beside a code: copies it, buzzes a phone once, and says "Code … copied" for a moment. */
export function CopyButton({ value, label = "Copy" }: { value: string; label?: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      navigator.vibrate?.(40);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {}
  };
  return (
    <button
      type="button"
      onClick={copy}
      className="inline-flex shrink-0 items-center gap-1 rounded-md p-1 align-middle text-snow-faint hover:text-snow"
      aria-label={label}
      title={copied ? `Code ${value} copied` : label}
    >
      <Icon name={copied ? "check" : "copy"} className="h-4 w-4" />
      {copied && <span className="text-xs">Code {value} copied</span>}
    </button>
  );
}
