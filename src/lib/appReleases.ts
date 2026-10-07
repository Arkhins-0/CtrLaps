import "server-only";

import { SITE_URL } from "./config";
import { env } from "./env";

/**
 * The latest GitHub release of the Android app: what GET /api/app-version
 * returns and what the app compares its own version against.
 *
 * The repository may be private. GitHub is then asked with GITHUB_TOKEN (a
 * read-only token that stays on the server), and the APK is handed out
 * through this site — /api/app-version/apk sends the phone on to a
 * short-lived download link — so neither the app nor a browser needs
 * access to GitHub.
 *
 * Only full releases count: GitHub's /releases/latest leaves out drafts and
 * pre-releases (tags with a dash, v0.2.1.0-beta, which the release workflow
 * publishes as such), so no phone is ever offered a beta.
 */

const CHECK_INTERVAL_MS = 30 * 60 * 1000;
// A user tapping "check for updates" skips the long cache, but not
// entirely: GitHub allows 60 unauthenticated calls an hour, so a burst of
// taps must still collapse into one call.
const FORCED_INTERVAL_MS = 60 * 1000;

/** One line of release notes, and the roles it is for (empty: everyone). */
export type NoteItem = { text: string; roles: string[] };
/** A heading of release notes (New, Improved, Fixed; "" before any heading) and its lines. */
export type NoteSection = { title: string; items: NoteItem[] };

export type ReleaseInfo = {
  version: string;
  releaseUrl: string;
  /** The universal APK: runs on every phone. */
  apkUrl: string | null;
  /** Smaller APKs for one processor type each ("arm64-v8a", …), when the release has them; the app picks its own. */
  apks: Record<string, string>;
  /** The changes as plain lines ("- …"), for older apps: no headings, no role tags. */
  notes: string;
  /** The same changes by heading, each line with the roles it is for (the app shows a person only theirs). */
  sections: NoteSection[];
  /**
   * Each APK's SHA-256 (lowercase hex) from the release's SHA256SUMS, keyed "universal" (for [apkUrl]) or by
   * processor type (as in [apks]). The app checks its download against it before installing. Empty for a
   * release made before the workflow published the file; the app then installs without the check.
   */
  sha256: Record<string, string>;
};

let cache: { at: number; info: ReleaseInfo | null } | null = null;
type Asset = { apiUrl: string; publicUrl: string };
/**
 * Where GitHub keeps the latest release's APKs (the API address and the public one), for /api/app-version/apk: the
 * universal one, and one per processor type.
 */
let asset: { version: string; universal: Asset | null; abis: Record<string, Asset> } | null = null;

/** A per-processor APK is named "CTRLAPS-v1.2.3.4.arm64-v8a.apk" (after the universal "CTRLAPS-v1.2.3.4.apk" in a listing). */
const ABI_APK = /\.(arm64-v8a|armeabi-v7a|x86_64|x86)\.apk$/i;

/** The checksums file the release workflow attaches ("SHA256SUMS.txt" on releases up to v0.2.0.6). */
const SUMS_FILE = /^SHA256SUMS(\.txt)?$/i;

const headers = (accept = "application/vnd.github+json"): Record<string, string> => ({
  Accept: accept,
  "User-Agent": "ctrlaps-server",
  ...(env.githubToken ? { Authorization: `Bearer ${env.githubToken}` } : {}),
});

/**
 * Release notes (release-notes/<version>.md, also each GitHub release's body) by heading: "## New" starts a section,
 * "- [admin, coordinator] …" is a line for those roles only, "- …" one for everyone.
 */
export function noteSections(body: string): NoteSection[] {
  const sections: NoteSection[] = [];
  for (const raw of body.split("\n")) {
    const line = raw.trim();
    if (!line || line.includes("Full Changelog")) continue;
    const heading = line.match(/^#{1,6}\s+(.*)$/);
    if (heading) {
      sections.push({ title: heading[1].trim(), items: [] });
      continue;
    }
    // Older releases' lines ended "; v0.1.2.3" (their release commit); that goes.
    const m = line.replace(/^[-*]\s*/, "").replace(/;\s*v\d+(\.\d+)+\s*$/i, "").match(/^(?:\[([a-z_,\s]+)\]\s*)?(.+)$/i);
    if (!m) continue;
    const roles = m[1] ? m[1].split(",").map((r) => r.trim().toLowerCase()).filter(Boolean) : [];
    if (sections.length === 0) sections.push({ title: "", items: [] });
    sections[sections.length - 1].items.push({ text: m[2].trim(), roles });
  }
  return sections.filter((x) => x.items.length > 0);
}

/** Plain lines for older apps: every line, its role tag left out (they can't tell roles apart). */
const plainItems = (sections: NoteSection[]): string[] => sections.flatMap((x) => x.items.map((i) => i.text));

/**
 * A release's SHA256SUMS ("<hex>  <file>" a line, as sha256sum writes it; "*<file>" in binary mode) by file name.
 * Fetched like an APK: through the API with the token, so it works for a private repository, else the public link.
 * Any failure gives an empty map — the app then installs without the check, as before checksums existed.
 */
async function fetchChecksums(file: Asset | null): Promise<Record<string, string>> {
  if (!file) return {};
  for (const [url, accept] of [
    [file.apiUrl, "application/octet-stream"],
    [file.publicUrl, "*/*"],
  ] as const) {
    try {
      const response = await fetch(url, { headers: headers(accept), cache: "no-store" });
      if (!response.ok) continue;
      const sums: Record<string, string> = {};
      for (const line of (await response.text()).split("\n")) {
        const m = line.trim().match(/^([0-9a-f]{64})\s+\*?(.+)$/i);
        if (m) sums[m[2].trim()] = m[1].toLowerCase();
      }
      return sums;
    } catch {
      // Try the next link.
    }
  }
  return {};
}

/** The release body reduced to the list of changes, for older apps' update card. */
const changesOnly = (body: string): string => plainItems(noteSections(body)).map((t) => `- ${t}`).join("\n").slice(0, 600);

async function fetchLatestRelease(): Promise<ReleaseInfo | null> {
  if (!env.githubRepo) return null;
  try {
    const response = await fetch(`https://api.github.com/repos/${env.githubRepo}/releases/latest`, { headers: headers(), cache: "no-store" });
    if (!response.ok) return null;
    const data = (await response.json()) as {
      tag_name?: string;
      html_url?: string;
      body?: string;
      assets?: { name: string; url: string; browser_download_url: string }[];
      prerelease?: boolean;
      draft?: boolean;
    };
    if (!data.tag_name || !data.html_url) return null;
    // /releases/latest never answers with these; checked anyway, so a beta can't reach phones by another route.
    if (data.prerelease || data.draft) return null;
    // A release carries both a signed release APK and a debug one. The debug
    // build has its own application id, so it would install beside the app
    // rather than update it — prefer the one that isn't debug.
    const apks = (data.assets ?? []).filter((a) => a.name.toLowerCase().endsWith(".apk") && !/debug/i.test(a.name));
    const apk = apks.find((a) => !ABI_APK.test(a.name)) ?? null;
    const version = data.tag_name.replace(/^v/i, "");
    const abis: Record<string, Asset> = {};
    for (const a of apks) {
      const abi = a.name.match(ABI_APK)?.[1]?.toLowerCase();
      if (abi) abis[abi] = { apiUrl: a.url, publicUrl: a.browser_download_url };
    }
    asset = { version, universal: apk ? { apiUrl: apk.url, publicUrl: apk.browser_download_url } : null, abis };
    const sumsAsset = (data.assets ?? []).find((a) => SUMS_FILE.test(a.name));
    const sums = await fetchChecksums(sumsAsset ? { apiUrl: sumsAsset.url, publicUrl: sumsAsset.browser_download_url } : null);
    const sha256: Record<string, string> = {};
    if (apk && sums[apk.name]) sha256.universal = sums[apk.name];
    for (const a of apks) {
      const abi = a.name.match(ABI_APK)?.[1]?.toLowerCase();
      if (abi && sums[a.name]) sha256[abi] = sums[a.name];
    }
    const link = (abi?: string) => `${SITE_URL}/api/app-version/apk?v=${encodeURIComponent(version)}${abi ? `&abi=${abi}` : ""}`;
    return {
      version,
      releaseUrl: data.html_url,
      apkUrl: apk ? link() : null,
      apks: Object.fromEntries(Object.keys(abis).map((abi) => [abi, link(abi)])),
      notes: changesOnly(data.body ?? ""),
      sections: noteSections(data.body ?? ""),
      sha256,
    };
  } catch {
    return null;
  }
}

/**
 * The latest release, checked at most once every 30 minutes across all
 * requests (an in-memory cache — fine for a single process, and kind to
 * GitHub's rate limit). A failed check falls back to whatever was cached
 * before rather than blanking the version out.
 */
export async function latestRelease(force = false): Promise<ReleaseInfo | null> {
  if (cache && Date.now() - cache.at < (force ? FORCED_INTERVAL_MS : CHECK_INTERVAL_MS)) return cache.info;
  const info = await fetchLatestRelease();
  const resolved = info ?? cache?.info ?? null;
  cache = { at: Date.now(), info: resolved };
  return resolved;
}

/** `changes`: plain lines for older apps; `sections`: by heading, with each line's roles. */
export type ChangelogEntry = { version: string; date: string; changes: string[]; sections: NoteSection[] };

let listCache: { at: number; list: ChangelogEntry[] } | null = null;

/**
 * Every published release, newest first, for the app's "What's new" page.
 * Cached like the latest release: GitHub is asked at most every 30 minutes.
 */
export async function allReleases(): Promise<ChangelogEntry[]> {
  if (listCache && Date.now() - listCache.at < CHECK_INTERVAL_MS) return listCache.list;
  if (!env.githubRepo) return [];
  try {
    const response = await fetch(`https://api.github.com/repos/${env.githubRepo}/releases?per_page=50`, { headers: headers(), cache: "no-store" });
    if (!response.ok) return listCache?.list ?? [];
    const data = (await response.json()) as { tag_name?: string; published_at?: string; body?: string; draft?: boolean; prerelease?: boolean }[];
    const list = data
      .filter((r) => r.tag_name && !r.draft && !r.prerelease)
      .map((r) => {
        const sections = noteSections(r.body ?? "");
        return { version: r.tag_name!.replace(/^v/i, ""), date: r.published_at ?? "", changes: plainItems(sections), sections };
      });
    listCache = { at: Date.now(), list };
    return list;
  } catch {
    return listCache?.list ?? [];
  }
}

/**
 * Where to fetch the latest APK right now: GitHub's short-lived signed
 * link (asked for with the token, so it works for a private repository),
 * or the plain public link when that fails. Null when there is no APK.
 */
export async function apkDownloadUrl(abi?: string | null): Promise<string | null> {
  // The same half-hour check as the version: a new release replaces the APK here too.
  await latestRelease();
  // The phone's own processor type when the release has it, else the universal APK.
  const file = (abi ? asset?.abis[abi.toLowerCase()] : undefined) ?? asset?.universal;
  if (!file) return null;
  try {
    const response = await fetch(file.apiUrl, { headers: headers("application/octet-stream"), redirect: "manual", cache: "no-store" });
    const location = response.headers.get("location");
    if (response.status >= 300 && response.status < 400 && location) return location;
  } catch {
    // Fall through to the public link.
  }
  return file.publicUrl;
}

/** The GitHub releases page, for when there is no release yet. */
export const releasesPage = (): string => `https://github.com/${env.githubRepo}/releases`;
