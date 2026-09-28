"use client";

import { useState } from "react";
import { Icon } from "./Icon";

/** A password box with an eye to show or hide what is typed. */
export function PasswordInput(props: Omit<React.InputHTMLAttributes<HTMLInputElement>, "type">) {
  const [shown, setShown] = useState(false);
  return (
    <div className="relative">
      <input {...props} type={shown ? "text" : "password"} className={`${props.className ?? "input"} pr-11`} />
      <button
        type="button"
        onClick={() => setShown((v) => !v)}
        className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-snow-faint hover:text-snow"
        aria-label={shown ? "Hide password" : "Show password"}
        title={shown ? "Hide password" : "Show password"}
      >
        <Icon name={shown ? "eyeOff" : "eye"} className="h-5 w-5" />
      </button>
    </div>
  );
}
