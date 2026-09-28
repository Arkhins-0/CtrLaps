"use client";

/* eslint-disable @next/next/no-img-element */
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api, shrinkImage } from "@/lib/client";
import { ROLE_LABEL, type Role } from "@/lib/roles";

/** An email, a role and optionally a photo. A new email gets an invite; one that already has an account is promoted. */
export function NewPersonForm({ roles, teamName, creatorRole }: { roles: Role[]; teamName: string | null; creatorRole: Role }) {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<Role>(roles[0]);
  const [team, setTeam] = useState("");
  const [photo, setPhoto] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [createdId, setCreatedId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!photo) return setPreview(null);
    const url = URL.createObjectURL(photo);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [photo]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    let id: string;
    try {
      const r = await api<{ user: { id: string }; promoted: boolean }>("/api/users", { method: "POST", json: { email, role, teamName: team } });
      id = r.user.id;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create.");
      setBusy(false);
      return;
    }
    if (photo) {
      try {
        const form = new FormData();
        form.set("photo", await shrinkImage(photo), "photo.jpg");
        await api(`/api/users/${id}/photo`, { method: "POST", body: form });
      } catch (err) {
        setCreatedId(id);
        setError(`The role was given, but the photo didn't upload: ${err instanceof Error ? err.message : "unknown error"}`);
        setBusy(false);
        return;
      }
    }
    router.push(`/people/${id}`);
  };

  return (
    <form onSubmit={submit} className="card max-w-md space-y-4">
      {error && (
        <p className="error">
          {error}
          {createdId && (
            <>
              {" "}
              <Link href={`/people/${createdId}`} className="underline">
                Open their page
              </Link>{" "}
              to add it.
            </>
          )}
        </p>
      )}
      <label className="flex cursor-pointer items-center gap-4">
        {preview ? (
          <img src={preview} alt="" className="h-20 w-20 rounded-full border border-night-line object-cover" />
        ) : (
          <span className="flex h-20 w-20 items-center justify-center rounded-full border border-dashed border-snow/30 text-xs text-snow-faint">
            Photo
          </span>
        )}
        <span className="flex flex-col items-start gap-1">
          <span className="btn-ghost text-xs">{photo ? "Change photo" : "Add photo"}</span>
          <span className="text-xs text-snow-faint">Optional. They can add their own when they set up.</span>
        </span>
        <input type="file" accept="image/*" className="hidden" disabled={busy || createdId !== null} onChange={(e) => setPhoto(e.target.files?.[0] ?? null)} />
      </label>
      <div>
        <label className="label" htmlFor="email">
          Email
        </label>
        <input id="email" className="input" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
      </div>
      <div>
        <span className="label">Role</span>
        <div className="flex flex-wrap gap-2">
          {roles.map((r) => (
            <button
              key={r}
              type="button"
              className={`chip ${role === r ? "border-gold bg-gold text-night" : "hover:border-snow/40"}`}
              onClick={() => setRole(r)}
            >
              {ROLE_LABEL[r]}
            </button>
          ))}
        </div>
      </div>
      {role === "team_manager" && (
        <div>
          <label className="label" htmlFor="team">
            Team
          </label>
          <input id="team" className="input" required value={team} onChange={(e) => setTeam(e.target.value)} />
        </div>
      )}
      {(role === "racer" || role === "crew") &&
        (creatorRole === "team_manager" ? (
          teamName && <p className="text-xs text-snow-faint">Team: {teamName}</p>
        ) : (
          <div>
            <label className="label" htmlFor="team">
              Team <span className="text-snow-faint">(optional)</span>
            </label>
            <input id="team" className="input" value={team} onChange={(e) => setTeam(e.target.value)} />
          </div>
        ))}
      <p className="text-xs text-snow-faint">
        A new email gets a link to choose a password and fill in their profile. If the email already has an account, they are given this role and told by email.
      </p>
      <button className="btn-gold w-full" disabled={busy || createdId !== null}>
        {busy ? "Saving…" : "Give role"}
      </button>
    </form>
  );
}
