// Shared by server and client: no "server-only", no Node built-ins.

/** What a support ticket is about, as the form's dropdown lists it. */
export const SUPPORT_CATEGORIES = [
  "Account & sign-in",
  "App problem",
  "Chats & messages",
  "Schedule & results",
  "Notifications",
  "Other",
] as const;

export type SupportCategory = (typeof SUPPORT_CATEGORIES)[number];

export const isSupportCategory = (v: unknown): v is SupportCategory => SUPPORT_CATEGORIES.includes(v as SupportCategory);

/** How long after a ticket is closed the person who raised it may reopen it. */
export const REOPEN_WINDOW_MS = 2 * 24 * 60 * 60 * 1000;

/** A ticket's number as people see it: #0012. */
export const ticketNumber = (n: number): string => `#${String(n).padStart(4, "0")}`;
