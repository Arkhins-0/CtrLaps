import Link from "next/link";
import { Icon, type IconName } from "./Icon";

/*
 * The app's settings look, on the website (the Account tab and its pages, a person's page): no cards; a large title
 * with a large icon; rows with an accent icon, a bold title, a quieter line and an arrow; details as icon rows.
 */

/** A page's top: a back link, then the title large with its icon large on the right, and a line under it if wanted. */
export function PageHeader({ title, icon, back, sub }: { title: string; icon: IconName; back?: string; sub?: React.ReactNode }) {
  return (
    <div className="mb-4">
      {back && (
        <Link href={back} className="btn-icon -ml-2 mb-2" aria-label="Back">
          <Icon name="back" />
        </Link>
      )}
      <div className="flex items-center gap-4">
        <h1 className="flex-1 text-3xl font-bold tracking-tight sm:text-4xl">{title}</h1>
        <Icon name={icon} className="h-10 w-10 shrink-0 text-snow" />
      </div>
      {sub && <p className="mt-1 text-sm text-snow-faint">{sub}</p>}
    </div>
  );
}

type RowLook = { icon: IconName; title: string; hint?: string; highlight?: boolean; danger?: boolean; arrow?: boolean };

function RowBody({ icon, title, hint, highlight, danger, arrow = true }: RowLook) {
  return (
    <>
      <Icon name={icon} className={`h-6 w-6 shrink-0 ${danger ? "text-danger" : "text-gold"}`} />
      <span className="min-w-0 flex-1">
        <span className={`block font-bold ${danger ? "text-danger" : "text-snow"}`}>{title}</span>
        {hint && <span className={`block truncate text-sm ${highlight ? "text-gold" : "text-snow-soft"}`}>{hint}</span>}
      </span>
      {arrow && <Icon name="chevronRight" className={`h-5 w-5 shrink-0 ${danger ? "text-danger" : "text-gold"}`} />}
    </>
  );
}

const ROW = "flex w-full items-center gap-5 rounded-xl px-2 py-3.5 text-left transition-colors hover:bg-snow/5 disabled:opacity-50";

/** A row that opens a page. `plain` for a link that leaves the site (/download sends to the APK), so it isn't prefetched. */
export function MenuLink({ href, plain, ...look }: RowLook & { href: string; plain?: boolean }) {
  return plain ? (
    <a href={href} className={ROW}>
      <RowBody {...look} />
    </a>
  ) : (
    <Link href={href} className={ROW}>
      <RowBody {...look} />
    </Link>
  );
}

/** A row that does something on the page (opens a sheet). */
export function MenuButton({ onClick, disabled, ...look }: RowLook & { onClick: () => void; disabled?: boolean }) {
  return (
    <button type="button" className={ROW} onClick={onClick} disabled={disabled}>
      <RowBody arrow={false} {...look} />
    </button>
  );
}

/** One of the round actions under a person's banner: Message, QR code, Edit. */
export function QuickAction({ icon, label, onClick, disabled }: { icon: IconName; label: string; onClick: () => void; disabled?: boolean }) {
  return (
    <button type="button" className="group flex w-20 flex-col items-center gap-1.5 disabled:opacity-50" onClick={onClick} disabled={disabled}>
      <span className="grid h-14 w-14 place-items-center rounded-full bg-gold/15 text-gold transition-colors group-hover:bg-gold/25">
        <Icon name={icon} className="h-6 w-6" />
      </span>
      <span className="text-xs font-semibold text-snow-soft">{label}</span>
    </button>
  );
}

/** One detail: an accent icon, what it is in small letters, the value; something at the end (a copy button). */
export function DetailRow({ icon, label, value, end }: { icon: IconName; label: string; value: React.ReactNode; end?: React.ReactNode }) {
  return (
    <div className="flex items-center gap-5 px-2 py-2.5">
      <Icon name={icon} className="h-6 w-6 shrink-0 text-gold" />
      <div className="min-w-0 flex-1">
        <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-snow-faint">{label}</p>
        <div className="truncate font-semibold">{value}</div>
      </div>
      {end}
    </div>
  );
}

/** The pill search box at the top of a list (Account, People), with a cross to clear it. */
export function SearchPill({ query, onChange, placeholder }: { query: string; onChange: (q: string) => void; placeholder: string }) {
  return (
    <label className="relative min-w-0 flex-1">
      <span className="sr-only">{placeholder}</span>
      <Icon name="search" className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-snow-faint" />
      <input className="input rounded-full py-3.5 pl-12 pr-10" placeholder={placeholder} value={query} onChange={(e) => onChange(e.target.value)} />
      {query && (
        <button type="button" className="btn-icon absolute right-2 top-1/2 -translate-y-1/2" aria-label="Clear" onClick={() => onChange("")}>
          <Icon name="close" />
        </button>
      )}
    </label>
  );
}

/** The square button beside a search pill (QR code, Filter). */
export const SQUARE_BUTTON =
  "relative flex h-[52px] w-[52px] shrink-0 items-center justify-center rounded-[18px] border border-night-line bg-night-panel text-gold hover:border-gold/50";

/** A small accent link at the end of a heading's row ("See all", "Email"): the next step without scrolling for it. */
export type HeadingActionProps = { label: string; href?: string; onClick?: () => void };

export function HeadingAction({ label, href, onClick }: HeadingActionProps) {
  const look = "shrink-0 text-sm font-bold text-gold hover:underline underline-offset-2";
  return href ? (
    <Link href={href} className={look}>
      {label}
    </Link>
  ) : (
    <button type="button" className={look} onClick={onClick}>
      {label}
    </button>
  );
}

/** A list's group title, as the app's: bold, with how many beside it, and a link on the right if there's a next step. */
export function GroupTitle({ title, count, action }: { title: string; count?: number; action?: HeadingActionProps }) {
  const heading = (
    <h2 className={`flex items-baseline gap-2 text-xl font-bold tracking-tight ${action ? "min-w-0" : "mb-1 mt-6 px-1"}`}>
      {title}
      {count !== undefined && <span className="text-base font-semibold text-snow-faint">{count}</span>}
    </h2>
  );
  if (!action) return heading;
  return (
    <div className="mb-1 mt-6 flex items-baseline justify-between gap-3 px-1">
      {heading}
      <HeadingAction {...action} />
    </div>
  );
}

/** A section's title on a flat page. */
export function SectionHeading({ children }: { children: React.ReactNode }) {
  return <h2 className="mb-1 mt-6 text-xl font-bold tracking-tight">{children}</h2>;
}

/**
 * The profile at the top of the Account page and a person's page, after Arkhime's account card: the photo blurred
 * behind, the name in the accent, the role and status, and the photo on the right.
 */
export function ProfileBanner({ photo, name, role, status, photoSlot }: { photo: string | null; name: string; role: string; status: React.ReactNode; photoSlot?: React.ReactNode }) {
  return (
    <div className="relative h-32 overflow-hidden rounded-[18px] border border-night-line bg-night-high">
      {photo && (
        // eslint-disable-next-line @next/next/no-img-element -- our own versioned photo
        <img src={photo} alt="" className="absolute inset-0 h-full w-full scale-110 object-cover blur-2xl" />
      )}
      <div className="absolute inset-0 bg-gradient-to-r from-black/80 to-black/35" />
      <div className="relative flex h-full items-center gap-4 px-5">
        <div className="min-w-0 flex-1">
          <p className="truncate text-2xl font-bold text-gold">{name}</p>
          <p className="truncate text-sm text-white/85">{role}</p>
          <div className="mt-1.5">{status}</div>
        </div>
        {photoSlot}
      </div>
    </div>
  );
}
