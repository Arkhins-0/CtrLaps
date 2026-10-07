"use client";

import { NewTeamNote } from "./NewTeamNote";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";
import { ROLE_LABEL, type Role } from "@/lib/roles";
import { MenuButton } from "./AppUI";
import { Sheet } from "./Sheet";
import type { PublicUser } from "@/lib/users";

/**
 * Promote someone who registered, or change the role of someone this person manages: the roles they may give,
 * then only what the chosen role needs — a team for a team manager; for racers and crew, the giver's team when
 * they are a team manager, else a team to type if wanted; for a delegate, a delegation if wanted. The server checks
 * the same rules.
 */
export function PromoteForm({
  person,
  roles,
  myRole,
  myTeam,
  teamNames = [],
  delegations = [],
}: {
  person: PublicUser;
  roles: Role[];
  myRole: Role;
  myTeam: string | null;
  /** Existing teams, suggested while typing so a team is not made twice by a typo. */
  teamNames?: string[];
  /** The delegations a new delegate can be put in. */
  delegations?: { id: string; name: string }[];
}) {
  const router = useRouter();
  const choices = roles.filter((r) => r !== person.role);
  const [open, setOpen] = useState(false);
  const [role, setRole] = useState<Role>(choices[0]);
  const [team, setTeam] = useState(person.teamName ?? "");
  const [delegation, setDelegation] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  if (choices.length === 0) return null;

  const iAmTeamManager = myRole === "team_manager";
  const asksTeam = role === "team_manager" || ((role === "racer" || role === "crew") && !iAmTeamManager);
  const name = person.name ?? person.email;

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api("/api/users", {
        method: "POST",
        json: {
          email: person.email,
          role,
          teamName: asksTeam ? team.trim() : "",
          delegationId: role === "race_official" && delegation ? delegation : undefined,
        },
      });
      setOpen(false);
      setNote(`Now a ${ROLE_LABEL[role]}.`);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not change the role.");
    } finally {
      setBusy(false);
    }
  };

  const title = person.role === "user" ? `Promote ${name}` : `Change ${name}'s role`;
  // A row, as the app's; the form opens in a sheet.
  const row = (
    <MenuButton
      icon="badge"
      title={person.role === "user" ? "Promote" : "Change role"}
      hint={note ?? `Now ${ROLE_LABEL[person.role]}`}
      highlight={!!note}
      onClick={() => {
        setNote(null);
        setOpen(true);
      }}
    />
  );
  if (!open) return row;

  return (
    <>
      {row}
      <Sheet title={title} onClose={() => setOpen(false)}>
        <form onSubmit={save} className="space-y-4 pb-2">
          <datalist id="team-names">
            {teamNames.map((n) => (
              <option key={n} value={n} />
            ))}
          </datalist>
          {error && <p className="error">{error}</p>}
          <div>
            <span className="label">New role</span>
            <div className="flex flex-wrap gap-2">
              {choices.map((r) => (
                <button
                  key={r}
                  type="button"
                  className={`chip ${role === r ? "border-gold bg-gold text-ink" : "hover:border-snow/40"}`}
                  onClick={() => setRole(r)}
                  disabled={busy}
                >
                  {ROLE_LABEL[r]}
                </button>
              ))}
            </div>
          </div>
          {role === "team_manager" && (
            <div>
              <label className="label" htmlFor="pr-team">
                Team name
              </label>
              <input id="pr-team" list="team-names" autoComplete="off" className="input" required value={team} onChange={(e) => setTeam(e.target.value)} />
              <NewTeamNote name={team} teams={teamNames} />
            </div>
          )}
          {(role === "racer" || role === "crew") &&
            (iAmTeamManager ? (
              myTeam && <p className="text-sm text-snow-soft">Team: {myTeam}</p>
            ) : (
              <div>
                <label className="label" htmlFor="pr-team">
                  Team <span className="text-snow-faint">(optional)</span>
                </label>
                <input id="pr-team" list="team-names" autoComplete="off" className="input" value={team} onChange={(e) => setTeam(e.target.value)} />
                <NewTeamNote name={team} teams={teamNames} />
              </div>
            ))}
          {role === "race_official" && (
            <div>
              <label className="label" htmlFor="pr-delegation">
                Delegation
              </label>
              <select id="pr-delegation" className="input" value={delegation} onChange={(e) => setDelegation(e.target.value)} disabled={busy}>
                <option value="">None for now</option>
                {delegations.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </select>
            </div>
          )}
          <p className="text-xs text-snow-faint">{name} is told by email. Their chats and pages change to the new role at once.</p>
          <button className="btn-gold w-full py-3" disabled={busy || (role === "team_manager" && !team.trim())}>
            {busy ? "Saving…" : `Make ${ROLE_LABEL[role]}`}
          </button>
        </form>
      </Sheet>
    </>
  );
}
