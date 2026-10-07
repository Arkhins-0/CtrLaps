"use client";

import { usePathname } from "next/navigation";
import { Skeleton, SkeletonPage, SkeletonPersonRow } from "../Skeleton";

/** Channels, Volunteers and Delegations take the whole width: a list, not a conversation. */
const isListPage = (p: string) => /^\/chats\/(channels|volunteers|delegations)(\/|$)/.test(p);

/** The tabs over the chats section, and a list of rows. */
function ListSkeleton({ search = true }: { search?: boolean }) {
  return (
    <>
      <div className="mb-3 flex items-center gap-3 pb-2">
        <Skeleton className="h-6 w-16" />
        <Skeleton className="h-6 w-24" />
      </div>
      {search && (
        <>
          <Skeleton className="mb-2 h-10 w-full rounded-xl" />
          <div className="mb-2 flex gap-2">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} className="h-6 w-14 rounded-full" />
            ))}
          </div>
        </>
      )}
      {Array.from({ length: 7 }, (_, i) => (
        <SkeletonPersonRow key={i} size={48} wide />
      ))}
    </>
  );
}

/** An open conversation: its header with the photo and name, then messages from both sides. */
export function ConversationSkeleton() {
  const bubbles: [boolean, string][] = [
    [false, "w-2/5"],
    [false, "w-3/5"],
    [true, "w-1/3"],
    [false, "w-1/2"],
    [true, "w-3/5"],
    [true, "w-1/4"],
  ];
  return (
    <div className="flex h-full min-h-0 flex-1 flex-col">
      <div className="flex items-center gap-3 border-b border-night-line px-4 py-3 lg:px-0">
        <Skeleton round className="h-10 w-10" />
        <span className="flex-1 space-y-2">
          <Skeleton className="h-4 w-1/3" />
          <Skeleton className="h-3 w-1/5" />
        </span>
      </div>
      <div className="flex flex-1 flex-col justify-end gap-2 px-4 py-4 lg:px-0">
        {bubbles.map(([mine, width], i) => (
          <Skeleton key={i} className={`h-9 rounded-2xl ${width} ${mine ? "self-end" : "self-start"}`} />
        ))}
      </div>
    </div>
  );
}

/**
 * The chats section while its list loads (the layout's Suspense): the list, with the conversation beside it on a
 * laptop; on a phone, whichever of the two this address shows.
 */
export function ChatsSkeleton() {
  const pathname = usePathname();
  if (isListPage(pathname)) {
    return (
      <SkeletonPage className="flex h-full min-h-0 flex-col">
        <ListSkeleton search={false} />
      </SkeletonPage>
    );
  }
  const onList = pathname === "/chats";
  return (
    <SkeletonPage className="grid h-full min-h-0 grid-cols-1 gap-6 lg:grid-cols-[22rem_minmax(0,1fr)]">
      <div className={`${onList ? "flex" : "hidden lg:flex"} min-h-0 flex-col overflow-hidden lg:border-r lg:border-night-line lg:pr-2`}>
        <ListSkeleton />
      </div>
      <div className={`${onList ? "hidden lg:flex" : "flex"} min-h-0 flex-col`}>{onList ? <div className="card h-full" /> : <ConversationSkeleton />}</div>
    </SkeletonPage>
  );
}

/** The pane beside the list while a conversation (or a full-width chats page) loads: chats/loading.tsx. */
export function ChatPaneSkeleton() {
  const pathname = usePathname();
  return (
    <SkeletonPage className="flex h-full min-h-0 flex-1 flex-col">
      {isListPage(pathname) ? (
        Array.from({ length: 7 }, (_, i) => <SkeletonPersonRow key={i} size={48} wide />)
      ) : pathname === "/chats" ? (
        <div className="card hidden h-full lg:block" />
      ) : (
        <ConversationSkeleton />
      )}
    </SkeletonPage>
  );
}
