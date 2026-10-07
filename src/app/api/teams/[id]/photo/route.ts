import { handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { one, run } from "@/lib/db";
import { fail, json } from "@/lib/http";
import { dropOldPhoto, storeImage } from "@/lib/profile";
import { storage } from "@/lib/storage";
import { canEditTeam, teamPhotoUrl } from "@/lib/teams";
import { audit } from "@/lib/users";

export const dynamic = "force-dynamic";

/** The team's photo (its logo or car). Anyone signed in may see it. */
export const GET = handle<Params<"id">>(async (_request, { params }) => {
  await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("Not found.", 404);
  const t = await one<{ photo_key: string | null }>("SELECT photo_key FROM teams WHERE id = $1", [id]);
  if (!t?.photo_key) return fail("No photo.", 404);
  const stored = await storage().get(t.photo_key);
  if (!stored) return fail("No photo.", 404);
  return new Response(new Uint8Array(stored.body), {
    headers: { "content-type": stored.contentType, "cache-control": "private, max-age=3600" },
  });
});

/** Set or replace the photo: admins, coordinators and the team's manager. Form field `photo`. */
export const POST = handle<Params<"id">>(async (request, { params }) => {
  const me = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such team.", 404);
  const t = await one<{ photo_key: string | null }>("SELECT photo_key FROM teams WHERE id = $1", [id]);
  if (!t) return fail("No such team.", 404);
  if (!(await canEditTeam(me, id))) return fail("Only an admin, a coordinator or the team's manager changes its photo.", 403);
  const photo = (await request.formData()).get("photo");
  if (!(photo instanceof File) || photo.size === 0) return fail("Choose a photo.");
  const key = await storeImage(`teams/${id}-${Date.now().toString(36)}`, photo);
  if (!key) return fail("The photo must be a JPEG, PNG or WebP under 5 MB.");
  await run("UPDATE teams SET photo_key = $2 WHERE id = $1", [id, key]);
  dropOldPhoto(t.photo_key, key);
  await audit(me.id, null, "team.photo", { id });
  return json({ photoUrl: teamPhotoUrl(id, key) });
});

/** Take the photo away. */
export const DELETE = handle<Params<"id">>(async (_request, { params }) => {
  const me = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such team.", 404);
  const t = await one<{ photo_key: string | null }>("SELECT photo_key FROM teams WHERE id = $1", [id]);
  if (!t) return fail("No such team.", 404);
  if (!(await canEditTeam(me, id))) return fail("Only an admin, a coordinator or the team's manager changes its photo.", 403);
  await run("UPDATE teams SET photo_key = NULL WHERE id = $1", [id]);
  dropOldPhoto(t.photo_key, null);
  await audit(me.id, null, "team.photo_removed", { id });
  return json({ photoUrl: null });
});
