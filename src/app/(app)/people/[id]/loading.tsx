import { Skeleton, SkeletonBanner, SkeletonPage } from "@/components/Skeleton";

/** A person's page while it loads: the back arrow, their banner, the round actions, then their details. */
export default function PersonLoading() {
  return (
    <SkeletonPage className="flat mx-auto max-w-2xl">
      <Skeleton round className="mb-3 h-9 w-9" />
      <SkeletonBanner />
      <div className="flex justify-center gap-2 py-5">
        {[0, 1, 2].map((i) => (
          <span key={i} className="flex w-20 flex-col items-center gap-1.5">
            <Skeleton round className="h-14 w-14" />
            <Skeleton className="h-3 w-12" />
          </span>
        ))}
      </div>
      <Skeleton className="mb-2 mt-6 h-6 w-24" />
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="flex items-center gap-5 px-2 py-2.5">
          <Skeleton className="h-6 w-6 rounded-md" />
          <span className="flex-1 space-y-2">
            <Skeleton className="h-2.5 w-16" />
            <Skeleton className="h-4 w-1/2" />
          </span>
        </div>
      ))}
    </SkeletonPage>
  );
}
