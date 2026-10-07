/**
 * Under a team box: when the typed name matches none of the teams there are, say a new team of that name will be made,
 * so a typo doesn't quietly make a second "Ahura Racing".
 */
export function NewTeamNote({ name, teams }: { name: string; teams: string[] }) {
  const typed = name.trim();
  if (!typed || teams.length === 0 || teams.some((t) => t.toLowerCase() === typed.toLowerCase())) return null;
  return (
    <p className="mt-1.5 text-xs text-gold" role="status">
      No team called &ldquo;{typed}&rdquo; yet: saving makes a new team with that name. If that&apos;s not right, pick one from the list.
    </p>
  );
}
