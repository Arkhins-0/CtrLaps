"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/client";
import { Icon } from "../Icon";
import { WeekendMenu } from "../schedule/WeekendMenu";
import { ManagersDialog } from "./ManagersDialog";

/**
 * Admin: the ⋮ inside a channel (a weekend's or a category's) — close or reopen it, and pick the coordinators who
 * manage it. [locked]: its season is archived, so it can't be reopened from here.
 */
export function ChannelMenu({ name, channelUrl, managersUrl, open, locked }: { name: string; channelUrl: string; managersUrl: string; open: boolean; locked: boolean }) {
  const router = useRouter();
  const [managing, setManaging] = useState(false);
  // Flips as soon as the server agrees; the page's own copy follows when it re-renders.
  const [isOpen, setIsOpen] = useState(open);
  useEffect(() => setIsOpen(open), [open]);
  const [toast, setToast] = useState<string | null>(null);
  const timer = useRef<number>(undefined);
  useEffect(() => () => window.clearTimeout(timer.current), []);
  const say = (text: string) => {
    setToast(text);
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setToast(null), 2_500);
  };

  const toggle = async () => {
    try {
      await api(channelUrl, { method: "PATCH", json: { open: !isOpen } });
      say(isOpen ? "Channel closed" : "Channel reopened");
      setIsOpen(!isOpen);
      router.refresh();
    } catch (e) {
      say(e instanceof Error ? e.message : "Could not change the channel.");
    }
  };

  return (
    <>
      <WeekendMenu
        label="Channel options"
        items={[
          {
            label: isOpen ? "Close channel" : "Reopen channel",
            icon: <Icon name={isOpen ? "archive" : "unarchive"} />,
            tone: isOpen ? "soft" : "gold",
            onClick: toggle,
            hidden: !isOpen && locked,
          },
          { label: "Managers", icon: <Icon name="people" />, tone: "soft", onClick: () => setManaging(true) },
        ]}
      />
      {managing && (
        <ManagersDialog
          name={name}
          url={managersUrl}
          onClose={() => setManaging(false)}
          onSaved={() => {
            setManaging(false);
            say("Managers saved");
            router.refresh();
          }}
        />
      )}
      {toast && (
        <div className="pointer-events-none fixed inset-x-0 bottom-20 z-50 flex justify-center" role="status" aria-live="polite">
          <span className="rounded-full border border-night-line bg-night-panel px-4 py-2 text-sm text-snow shadow-card">{toast}</span>
        </div>
      )}
    </>
  );
}
