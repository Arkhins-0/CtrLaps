import { Skeleton, SkeletonBanner, SkeletonMenuRow, SkeletonPage } from "@/components/Skeleton";

/** Account while it loads: Search settings and the QR button, the profile banner, then the rows. */
export default function AccountLoading() {
  return (
    <SkeletonPage className="flat mx-auto max-w-2xl space-y-4">
      <div className="flex items-center gap-2.5">
        <Skeleton className="h-[52px] flex-1 rounded-full" />
        <Skeleton className="h-[52px] w-[52px] rounded-[18px]" />
      </div>
      <SkeletonBanner />
      <div>
        {[0, 1, 2, 3, 4, 5].map((i) => (
          <SkeletonMenuRow key={i} />
        ))}
      </div>
    </SkeletonPage>
  );
}
