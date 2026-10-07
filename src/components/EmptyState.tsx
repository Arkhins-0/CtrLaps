import Link from "next/link";
import { Icon, type IconName } from "./Icon";

/** The next step from an empty list: a page to open, or (from a client component) something to do on this one. */
export type EmptyAction = { label: string; href: string; plain?: boolean } | { label: string; onClick: () => void };

/**
 * What a main list shows when there's nothing in it yet, as the app's: an icon in an accent circle, a short title,
 * one line on why or what comes here, and the next step as a button when the viewer can take one. `section` is the
 * smaller one, for an empty part of a page (a team's people) rather than the whole page.
 */
export function EmptyState({
  icon,
  title,
  line,
  action,
  section = false,
}: {
  icon: IconName;
  title: string;
  line?: string;
  action?: EmptyAction;
  section?: boolean;
}) {
  const button = section ? "btn-ghost mt-3 px-4 py-1.5 text-xs" : "btn-gold mt-5 px-5 py-2.5";
  return (
    <div className={`flex flex-col items-center text-center ${section ? "px-4 py-5" : "px-6 py-12"}`}>
      <span className={`grid place-items-center rounded-full bg-gold/15 text-gold ${section ? "h-12 w-12" : "h-16 w-16"}`}>
        <Icon name={icon} className={section ? "h-6 w-6" : "h-8 w-8"} />
      </span>
      <p className={`font-bold ${section ? "mt-3" : "mt-4 text-lg"}`}>{title}</p>
      {line && <p className="mt-1 max-w-sm text-sm text-snow-soft">{line}</p>}
      {action &&
        ("onClick" in action ? (
          <button type="button" className={button} onClick={action.onClick}>
            {action.label}
          </button>
        ) : action.plain ? (
          <a href={action.href} className={button}>
            {action.label}
          </a>
        ) : (
          <Link href={action.href} className={button}>
            {action.label}
          </Link>
        ))}
    </div>
  );
}
