import { Skeleton, SkeletonPage, SkeletonPersonRow } from "@/components/Skeleton";

/** People while it loads: the title, the search pill and filter, then a role's people in rows. */
export default function PeopleLoading() {
  return (
    <SkeletonPage className="space-y-5">
      <div className="flex items-center justify-between gap-2">
        <Skeleton className="h-9 w-36" />
        <Skeleton className="h-8 w-28 rounded-full" />
      </div>
      <div className="flex items-center gap-2.5">
        <Skeleton className="h-[52px] flex-1 rounded-full" />
        <Skeleton className="h-[52px] w-[52px] rounded-[18px]" />
      </div>
      {[6, 4].map((n, g) => (
        <section key={g}>
          <Skeleton className="mb-2 mt-6 h-6 w-32" />
          <div className="grid grid-cols-1 gap-x-4 lg:grid-cols-2">
            {Array.from({ length: n }, (_, i) => (
              <SkeletonPersonRow key={i} />
            ))}
          </div>
        </section>
      ))}
    </SkeletonPage>
  );
}
