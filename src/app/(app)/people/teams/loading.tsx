import { SkeletonFlatPage } from "@/components/Skeleton";

/** Its own placeholder, not the parent page's (shaped differently). */
export default function Loading() {
  return <SkeletonFlatPage wide />;
}
