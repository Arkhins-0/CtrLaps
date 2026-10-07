import { Skeleton, SkeletonCard, SkeletonPage, SkeletonPersonRow } from "@/components/Skeleton";

/** Home while it loads: announcements on the left; the next race and recent chats on the right (on top on a phone). */
export default function HomeLoading() {
  return (
    <SkeletonPage className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
      <section className="space-y-4">
        <Skeleton className="h-9 w-56" />
        {[0, 1, 2].map((i) => (
          <div key={i} className="card space-y-3">
            <div className="flex items-center gap-3">
              <Skeleton round className="h-9 w-9" />
              <span className="flex-1 space-y-2">
                <Skeleton className="h-3.5 w-1/3" />
                <Skeleton className="h-3 w-1/5" />
              </span>
            </div>
            <Skeleton className="h-3.5 w-full" />
            <Skeleton className="h-3.5 w-4/5" />
          </div>
        ))}
      </section>
      <aside className="order-first space-y-5 lg:order-none">
        <SkeletonCard lines={4} />
        <div className="card p-3">
          <Skeleton className="mx-2 mb-2 h-3 w-16" />
          {[0, 1, 2].map((i) => (
            <SkeletonPersonRow key={i} size={36} />
          ))}
        </div>
      </aside>
    </SkeletonPage>
  );
}
