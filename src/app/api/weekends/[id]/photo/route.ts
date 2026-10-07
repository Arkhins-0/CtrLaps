import { handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { one, run } from "@/lib/db";
import { fail, json } from "@/lib/http";
import { dropOldPhoto, storeImage } from "@/lib/profile";
import { weekendPhotoUrl } from "@/lib/races";
import { storage } from "@/lib/storage";
import { audit } from "@/lib/users";

export const dynamic = "force-dynamic";

/** The weekend's photo (the track). Anyone signed in may see it. */
export const GET = handle<Params<"id">>(async (_request, { params }) => {
  await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("Not found.", 404);
  const w = await one<{ photo_key: string | null }>("SELECT photo_key FROM race_weekends WHERE id = $1", [id]);
  if (!w?.photo_key) return fail("No photo.", 404);
  const stored = await storage().get(w.photo_key);
  if (!stored) return fail("No photo.", 404);
  return new Response(new Uint8Array(stored.body), {
    headers: { "content-type": stored.contentType, "cache-control": "private, max-age=3600" },
  });
});

/** Set or replace the photo: admins, as for the weekend's other details. Form field `photo`. */
export const POST = handle<Params<"id">>(async (request, { params }) => {
  const admin = await requireUser(["admin"]);
  const { id } = await params;
  if (!isUuid(id)) return fail("No such race weekend.", 404);
  const w = await one<{ photo_key: string | null }>("SELECT photo_key FROM race_weekends WHERE id = $1", [id]);
  if (!w) return fail("No such race weekend.", 404);
  const photo = (await request.formData()).get("photo");
  if (!(photo instanceof File) || photo.size === 0) return fail("Choose a photo.");
  const key = await storeImage(`weekends/${id}-${Date.now().toString(36)}`, photo);
  if (!key) return fail("The photo must be a JPEG, PNG or WebP under 5 MB.");
  await run("UPDATE race_weekends SET photo_key = $2 WHERE id = $1", [id, key]);
  dropOldPhoto(w.photo_key, key);
  await audit(admin.id, id, "weekend.photo");
  return json({ photoUrl: weekendPhotoUrl(id, key) });
});

/** Take the photo away. */
export const DELETE = handle<Params<"id">>(async (_request, { params }) => {
  const admin = await requireUser(["admin"]);
  const { id } = await params;
  if (!isUuid(id)) return fail("No such race weekend.", 404);
  const w = await one<{ photo_key: string | null }>("SELECT photo_key FROM race_weekends WHERE id = $1", [id]);
  if (!w) return fail("No such race weekend.", 404);
  await run("UPDATE race_weekends SET photo_key = NULL WHERE id = $1", [id]);
  dropOldPhoto(w.photo_key, null);
  await audit(admin.id, id, "weekend.photo_removed");
  return json({ photoUrl: null });
});
