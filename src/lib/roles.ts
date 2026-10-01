// Shared by server and client: no "server-only", no Node built-ins.

export const ROLES = [
  "admin",
  "coordinator",
  "race_official",
  "team_manager",
  "racer",
  "crew",
  "security_head",
  "security",
  "volunteer",
  "user",
] as const;

export type Role = (typeof ROLES)[number];

export const STATUSES = ["pending", "active", "suspended", "dismissed", "banned"] as const;
export type Status = (typeof STATUSES)[number];

export const ROLE_LABEL: Record<Role, string> = {
  admin: "Admin",
  coordinator: "Coordinator",
  race_official: "Race official",
  team_manager: "Team manager",
  racer: "Racer",
  crew: "Crew",
  security_head: "Security head",
  security: "Security",
  volunteer: "Volunteer",
  user: "User",
};

/** A developer is an admin who also answers support; everywhere but support they show as this. */
export const DEVELOPER_LABEL = "Developer";

/** A person's designation: their role's name, or "Developer" for an admin who is one. */
export const roleLabel = (role: Role, isDev?: boolean | null): string => (isDev && role === "admin" ? DEVELOPER_LABEL : ROLE_LABEL[role]);

/** Whether someone is a developer: an admin with the flag (a flag left on someone no longer an admin counts for nothing). */
export const isDeveloper = (u: { role: Role; is_dev?: boolean | null }): boolean => u.role === "admin" && Boolean(u.is_dev);

export const STATUS_LABEL: Record<Status, string> = {
  pending: "Pending",
  active: "Active",
  suspended: "Suspended",
  dismissed: "Dismissed",
  banned: "Banned",
};

/**
 * Which roles each role may give: to a new email (an invite) or to someone who
 * already has an account (a promotion). Anyone not listed gives none. 'user' is
 * never given: it is what people who register themselves start as.
 */
export const CREATE_RULES: Partial<Record<Role, Role[]>> = {
  admin: ["admin", "coordinator", "race_official", "team_manager", "racer", "crew", "security_head", "security", "volunteer"],
  coordinator: ["coordinator", "race_official", "team_manager", "racer", "crew", "security_head", "security", "volunteer"],
  team_manager: ["racer", "crew"],
  security_head: ["security"],
};

/** Roles with no private chats or groups, and no Chats tab: they read announcements and race-weekend channels only. */
export const NO_CHAT_ROLES: Role[] = ["user"];
export const hasChats = (role: Role): boolean => !NO_CHAT_ROLES.includes(role);

/** Roles that change their own name, contact, date of birth, photo and email: users (no role yet), and admins, who have no manager above them. */
export const EDITS_OWN_PROFILE: Role[] = ["user", "admin"];
export const editsOwnProfile = (role: Role): boolean => EDITS_OWN_PROFILE.includes(role);

export const canCreate = (creator: Role, role: Role): boolean => (CREATE_RULES[creator] ?? []).includes(role);

/** Roles that never get automatic email; their coordinator forwards by hand. */
export const NO_AUTO_EMAIL: Role[] = ["volunteer", "security"];

/** Roles allowed to post in a race-weekend channel. */
export const CHANNEL_POSTERS: Role[] = ["admin", "coordinator"];

export const isRole = (value: unknown): value is Role => ROLES.includes(value as Role);
export const isStatus = (value: unknown): value is Status => STATUSES.includes(value as Status);
