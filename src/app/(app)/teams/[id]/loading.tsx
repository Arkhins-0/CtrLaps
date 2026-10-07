import { Skeleton, SkeletonBanner, SkeletonPage, SkeletonPersonRow } from "@/components/Skeleton";

/** A team's page while it loads: the back arrow, its banner, where it stands, then its people. */
export default function TeamLoading() {
  return (
    <SkeletonPage className="flat mx-auto max-w-2xl">
      <Skeleton round className="mb-3 h-9 w-9" />
      <SkeletonBanner />
      <Skeleton className="mb-2 mt-6 h-6 w-28" />
      <div className="flex items-center gap-4 px-2 py-3">
        <span className="flex-1 space-y-2">
          <Skeleton className="h-4 w-1/3" />
          <Skeleton className="h-7 w-24" />
          <Skeleton className="h-3 w-2/5" />
        </span>
        <Skeleton className="h-7 w-10" />
      </div>
      <Skeleton className="mb-2 mt-6 h-6 w-24" />
      {[0, 1, 2, 3].map((i) => (
        <SkeletonPersonRow key={i} />
      ))}
    </SkeletonPage>
  );
}
