import { handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { run } from "@/lib/db";
import { fail, json } from "@/lib/http";
import { dropOldPhoto, profileFromForm, storePhoto } from "@/lib/profile";
import { audit, toPublic, userById } from "@/lib/users";

export const dynamic = "force-dynamic";

/**
 * Someone who registered and has no role yet changes their own name, date of
 * birth, contact number and (optionally) photo. Once promoted, the profile is
 * locked and only their manager or an admin changes it.
 */
export const POST = handle(async (request) => {
  const user = await requireUser();
  if (user.role !== "user") return fail("Your profile is locked. Ask your manager or an admin to change it.", 403);
  if (!user.profile_completed_at) return fail("Finish setting up your profile first.", 409);
  const form = await request.formData();
  const fields = profileFromForm(form);
  if ("error" in fields) return fail(fields.error);
  const photo = form.get("photo");
  let key = user.photo_key;
  if (photo instanceof File && photo.size > 0) {
    key = await storePhoto(user.id, photo);
    if (!key) return fail("The photo must be a JPEG, PNG or WebP under 5 MB.");
  }
  await run("UPDATE users SET name = $2, dob = $3, phone = $4, photo_key = $5 WHERE id = $1", [
    user.id,
    fields.name,
    fields.dob,
    fields.phone,
    key,
  ]);
  dropOldPhoto(user.photo_key, key);
  await audit(user.id, user.id, "profile.edited");
  return json({ user: toPublic((await userById(user.id))!) });
});
