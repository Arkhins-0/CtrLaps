/*
 * Loading placeholders: grey blocks the shape of the page that's coming (a title, rows with a round photo and two
 * lines), so a slow page shows its outline at once instead of nothing. The shimmer is in globals.css (.skeleton) and
 * stays still for those who ask for less motion. Used by the signed-in routes' loading.tsx files.
 */

/** One grey block; size it with classes. `round` for a photo. */
export function Skeleton({ className = "", round = false, style }: { className?: string; round?: boolean; style?: React.CSSProperties }) {
  return <span aria-hidden className={`skeleton block shrink-0 ${round ? "rounded-full" : "rounded-lg"} ${className}`} style={style} />;
}

/** The whole placeholder: said once as "Loading" to a screen reader, the blocks themselves hidden. */
export function SkeletonPage({ className = "", children }: { className?: string; children: React.ReactNode }) {
  return (
    <div role="status" aria-busy="true" aria-label="Loading" className={className}>
      <span className="sr-only">Loading…</span>
      {children}
    </div>
  );
}

/** A large page title, with room for its big icon on the right (PageHeader's), and a back arrow above when wanted. */
export function SkeletonHeader({ back = false, icon = true, width = "w-48" }: { back?: boolean; icon?: boolean; width?: string }) {
  return (
    <div className="mb-4">
      {back && <Skeleton round className="mb-3 h-9 w-9" />}
      <div className="flex items-center gap-4">
        <Skeleton className={`h-9 ${width} max-w-[70%]`} />
        <span className="flex-1" />
        {icon && <Skeleton className="h-10 w-10 rounded-xl" />}
      </div>
    </div>
  );
}

/** A person or a chat in a list: the round photo, a bold line and a quieter one. */
export function SkeletonPersonRow({ size = 46, wide = false }: { size?: number; wide?: boolean }) {
  return (
    <div className="flex items-center gap-4 px-2 py-3">
      <Skeleton round style={{ width: size, height: size }} />
      <span className="min-w-0 flex-1 space-y-2">
        <Skeleton className={`h-4 ${wide ? "w-2/3" : "w-1/2"}`} />
        <Skeleton className={`h-3 ${wide ? "w-5/6" : "w-1/3"}`} />
      </span>
    </div>
  );
}

/** A settings-style row (MenuLink): the accent icon, a title and a hint. */
export function SkeletonMenuRow() {
  return (
    <div className="flex items-center gap-5 px-2 py-3.5">
      <Skeleton className="h-6 w-6 rounded-md" />
      <span className="min-w-0 flex-1 space-y-2">
        <Skeleton className="h-4 w-1/3" />
        <Skeleton className="h-3 w-2/3" />
      </span>
    </div>
  );
}

/** The profile banner at the top of Account, a person's page and a team's page, with the photo on the right. */
export function SkeletonBanner() {
  return (
    <div className="flex h-32 items-center gap-4 rounded-[18px] border border-night-line bg-night-high px-5">
      <span className="min-w-0 flex-1 space-y-2.5">
        <Skeleton className="h-6 w-1/2" />
        <Skeleton className="h-3.5 w-1/3" />
        <Skeleton className="h-5 w-16 rounded-full" />
      </span>
      <Skeleton round className="h-20 w-20" />
    </div>
  );
}

/** A boxed card (Home's, Schedule's weekends), its lines given. */
export function SkeletonCard({ lines = 3, className = "" }: { lines?: number; className?: string }) {
  const widths = ["w-1/4", "w-2/3", "w-1/2", "w-5/6", "w-1/3"];
  return (
    <div className={`card space-y-3 ${className}`}>
      {Array.from({ length: lines }, (_, i) => (
        <Skeleton key={i} className={`${i === 1 ? "h-5" : "h-3.5"} ${widths[i % widths.length]}`} />
      ))}
    </div>
  );
}

/**
 * A plain flat page (a settings page, a form) while it loads: a back arrow, the large title and rows. The nested pages
 * of Account and People use it, so they don't borrow their parent's placeholder, which is shaped differently.
 */
export function SkeletonFlatPage({ rows = 4, wide = false }: { rows?: number; wide?: boolean }) {
  return (
    <SkeletonPage className={`flat mx-auto ${wide ? "max-w-4xl" : "max-w-2xl"}`}>
      <SkeletonHeader back />
      {Array.from({ length: rows }, (_, i) => (
        <SkeletonMenuRow key={i} />
      ))}
    </SkeletonPage>
  );
}
