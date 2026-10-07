import { NextResponse } from "next/server";
import { apkDownloadUrl } from "@/lib/appReleases";
import { fail } from "@/lib/http";

export const dynamic = "force-dynamic";

/**
 * The latest APK — public, like /api/app-version. The phone (or a browser)
 * is sent on to a download link GitHub signs for a few minutes, so the
 * repository can stay private and the file never passes through here.
 * `?abi=arm64-v8a` asks for the smaller APK for one processor type; without
 * it (or when the release has none) it is the universal one.
 */
export async function GET(request: Request) {
  const url = await apkDownloadUrl(new URL(request.url).searchParams.get("abi"));
  if (!url) return fail("No release has been published yet.", 404);
  return NextResponse.redirect(url, 302);
}
