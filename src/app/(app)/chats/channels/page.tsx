import { ChannelList } from "@/components/channels/ChannelList";
import { listCategoryChannels } from "@/lib/categoryChannels";
import { listChannels } from "@/lib/channels";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Channels" };

/** The race categories' channels this person is in, then every race weekend's channel, season by season. */
export default async function Channels() {
  const user = await requireProfile();
  const [seasons, categories] = await Promise.all([listChannels(user), listCategoryChannels(user)]);
  return <ChannelList initial={seasons} categories={categories} />;
}
