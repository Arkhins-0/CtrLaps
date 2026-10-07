import { handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { run } from "@/lib/db";
import { fail, json } from "@/lib/http";
import { dropOldPhoto, storePhoto, userPhotoUrl } from "@/lib/profile";
import { editsOwnProfile } from "@/lib/roles";
import { audit } from "@/lib/users";

export const dynamic = "force-dynamic";

/** Change your own photo, for those who edit their own profile (as /api/me/details). Form field `photo`. */
export const POST = handle(async (request) => {
  const user = await requireUser();
  if (!editsOwnProfile(user.role)) return fail("Your profile is locked. Ask your manager or an admin to change it.", 403);
  const photo = (await request.formData()).get("photo");
  if (!(photo instanceof File) || photo.size === 0) return fail("Choose a photo.");
  const key = await storePhoto(user.id, photo);
  if (!key) return fail("The photo must be a JPEG, PNG or WebP under 5 MB.");
  await run("UPDATE users SET photo_key = $2 WHERE id = $1", [user.id, key]);
  dropOldPhoto(user.photo_key, key);
  await audit(user.id, user.id, "user.photo");
  return json({ ok: true, photoUrl: userPhotoUrl(user.id, key) });
});

/** Take your own photo away. */
export const DELETE = handle(async () => {
  const user = await requireUser();
  if (!editsOwnProfile(user.role)) return fail("Your profile is locked. Ask your manager or an admin to change it.", 403);
  await run("UPDATE users SET photo_key = NULL WHERE id = $1", [user.id]);
  dropOldPhoto(user.photo_key, null);
  await audit(user.id, user.id, "user.photo_removed");
  return json({ ok: true, photoUrl: null });
});
