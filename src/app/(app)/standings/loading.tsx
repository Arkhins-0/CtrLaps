import { Skeleton, SkeletonPage } from "@/components/Skeleton";

/** Standings while they load: the title, the category chips, then the drivers' table. */
export default function StandingsLoading() {
  return (
    <SkeletonPage className="space-y-5">
      <Skeleton className="h-9 w-44" />
      <div className="flex gap-2">
        {[0, 1, 2].map((i) => (
          <Skeleton key={i} className="h-7 w-14 rounded-full" />
        ))}
      </div>
      <section className="card space-y-1">
        <Skeleton className="mb-3 h-5 w-48" />
        {Array.from({ length: 8 }, (_, i) => (
          <div key={i} className="flex items-center gap-3 py-2">
            <Skeleton className="h-7 w-7 rounded-md" />
            <Skeleton round className="h-8 w-8" />
            <span className="flex-1 space-y-1.5">
              <Skeleton className="h-3.5 w-2/5" />
              <Skeleton className="h-2.5 w-1/4" />
            </span>
            <Skeleton className="h-5 w-10" />
          </div>
        ))}
      </section>
    </SkeletonPage>
  );
}
