"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { api, uploadFile } from "@/lib/client";
import { SUPPORT_CATEGORIES } from "@/lib/supportCategories";

type Prefill = { name: string; email: string; phone: string };

/**
 * Raise a ticket: who you are (filled in from the profile when signed in), what it is about, a subject and the
 * details, and (signed in) up to five screenshots.
 */
export function TicketForm({ prefill, signedIn }: { prefill: Prefill; signedIn: boolean }) {
  const [f, setF] = useState({ ...prefill, category: "", subject: "", details: "", website: "" });
  const [files, setFiles] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<{ id: string; label: string } | null>(null);
  const input = useRef<HTMLInputElement>(null);

  if (done) {
    return (
      <div className="card space-y-3">
        <h2 className="text-lg font-semibold">Ticket {done.label} is open</h2>
        <p className="text-sm text-snow-soft">
          Support has your request. {signedIn ? "You'll be told here and by email when they reply." : "The reply comes by email; sign in with this email to chat about it."}
        </p>
        <div className="flex flex-wrap gap-2">
          {signedIn && done.id && <Link href={`/support/tickets/${done.id}`} className="btn-gold">Open the ticket</Link>}
          <button className="btn-ghost" onClick={() => { setDone(null); setF({ ...prefill, category: "", subject: "", details: "", website: "" }); setFiles([]); }}>
            Raise another
          </button>
        </div>
      </div>
    );
  }

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const fileIds = [];
      for (const file of files) fileIds.push(await uploadFile(file));
      const r = await api<{ id: string; label: string }>("/api/support/tickets", { method: "POST", json: { ...f, fileIds } });
      setDone(r);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send it.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="card space-y-3" onSubmit={submit}>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <label className="block">
          <span className="label">Name</span>
          <input className="input" required value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} autoComplete="name" />
        </label>
        <label className="block">
          <span className="label">Email</span>
          <input className="input" type="email" required value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} autoComplete="email" />
        </label>
        <label className="block">
          <span className="label">Contact number (optional)</span>
          <input className="input" type="tel" value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} autoComplete="tel" />
        </label>
        <label className="block">
          <span className="label">What is it about?</span>
          <select className="input" required value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })}>
            <option value="" disabled>Choose…</option>
            {SUPPORT_CATEGORIES.map((c) => <option key={c}>{c}</option>)}
          </select>
        </label>
      </div>
      <label className="block">
        <span className="label">Subject</span>
        <input className="input" required maxLength={200} value={f.subject} onChange={(e) => setF({ ...f, subject: e.target.value })} placeholder="In a few words" />
      </label>
      <label className="block">
        <span className="label">Details</span>
        <textarea className="input min-h-32" required maxLength={5000} value={f.details} onChange={(e) => setF({ ...f, details: e.target.value })} placeholder="What happened, what you expected, and on which phone or browser" />
      </label>
      {/* Left empty by people; bots fill it in. */}
      <input className="hidden" tabIndex={-1} autoComplete="off" aria-hidden value={f.website} onChange={(e) => setF({ ...f, website: e.target.value })} />
      {signedIn && (
        <div className="space-y-2">
          <input ref={input} type="file" accept="image/*" multiple className="hidden" onChange={(e) => setFiles([...files, ...Array.from(e.target.files ?? [])].slice(0, 5))} />
          <button type="button" className="btn-ghost px-4 py-1.5 text-xs" onClick={() => input.current?.click()} disabled={files.length >= 5}>
            Add screenshots
          </button>
          {files.length > 0 && (
            <ul className="flex flex-wrap gap-2 text-xs">
              {files.map((file, i) => (
                <li key={i} className="chip">
                  {file.name}
                  <button type="button" className="ml-1 text-snow-faint hover:text-snow" onClick={() => setFiles(files.filter((_, j) => j !== i))} aria-label={`Remove ${file.name}`}>✕</button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      {error && <p className="error">{error}</p>}
      <button className="btn-gold w-full" disabled={busy}>{busy ? "Sending…" : "Raise ticket"}</button>
    </form>
  );
}
