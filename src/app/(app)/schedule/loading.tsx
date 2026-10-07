import { Skeleton, SkeletonPage } from "@/components/Skeleton";

/** Schedule while it loads: the title, the category chips, then race weekends as cards. */
export default function ScheduleLoading() {
  return (
    <SkeletonPage className="space-y-6">
      <Skeleton className="h-9 w-40" />
      <div className="flex gap-2">
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-7 w-14 rounded-full" />
        ))}
      </div>
      <div className="grid grid-cols-1 items-start gap-5 xl:grid-cols-2">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="card space-y-3">
            <div className="flex items-center justify-between gap-3">
              <Skeleton className="h-5 w-1/2" />
              <Skeleton className="h-6 w-6 rounded-md" />
            </div>
            <Skeleton className="h-3.5 w-2/3" />
            <Skeleton className="h-3.5 w-1/3" />
          </div>
        ))}
      </div>
    </SkeletonPage>
  );
}
