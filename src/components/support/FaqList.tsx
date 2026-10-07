"use client";

import { useState } from "react";
import { api } from "@/lib/client";
import { SUPPORT_CATEGORIES } from "@/lib/supportCategories";
import { Icon } from "../Icon";

export type Faq = { id: string; category: string; question: string; answer: string; steps: string[]; position: number };

/** The FAQs: a search box, then the questions by category, each opening to its answer and steps. Developers edit them. */
export function FaqList({ initial, canEdit = false }: { initial: Faq[]; canEdit?: boolean }) {
  const [faqs, setFaqs] = useState(initial);
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState<string | null>(null);
  const [editing, setEditing] = useState<Faq | "new" | null>(null);
  const [error, setError] = useState<string | null>(null);

  const words = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
  const shown = faqs.filter((f) => {
    const text = `${f.question} ${f.answer} ${f.steps.join(" ")} ${f.category}`.toLowerCase();
    return words.every((w) => text.includes(w));
  });
  const categories = Array.from(new Set(shown.map((f) => f.category)));

  const reload = async () => setFaqs((await api<{ faqs: Faq[] }>("/api/support/faqs")).faqs);
  const act = async (fn: () => Promise<unknown>) => {
    setError(null);
    try {
      await fn();
      await reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save.");
    }
  };
  // Swap a question with its neighbour in the whole list.
  const move = (f: Faq, by: -1 | 1) => {
    const i = faqs.findIndex((x) => x.id === f.id);
    const other = faqs[i + by];
    if (!other) return;
    act(async () => {
      await api(`/api/support/faqs/${f.id}`, { method: "PATCH", json: { position: other.position } });
      await api(`/api/support/faqs/${other.id}`, { method: "PATCH", json: { position: f.position === other.position ? f.position + by : f.position } });
    });
  };

  return (
    <div className="space-y-4">
      <div className="relative">
        <Icon name="search" className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-snow-faint" />
        <input className="input pl-9" type="search" placeholder="Search the questions" value={query} onChange={(e) => setQuery(e.target.value)} />
      </div>
      {error && <p className="error">{error}</p>}
      {canEdit && editing === null && (
        <button className="btn-ghost px-4 py-1.5 text-xs" onClick={() => setEditing("new")}>
          + Add a question
        </button>
      )}
      {editing === "new" && <FaqEditor onCancel={() => setEditing(null)} onSave={(d) => act(async () => { await api("/api/support/faqs", { method: "POST", json: d }); setEditing(null); })} />}
      {shown.length === 0 && <p className="card text-sm text-snow-faint">{faqs.length === 0 ? "No questions yet." : "Nothing matches. Try other words, or raise a ticket."}</p>}
      {categories.map((c) => (
        <section key={c} className="space-y-2">
          <h2 className="text-xs font-semibold uppercase tracking-wide text-snow-faint">{c}</h2>
          {shown.filter((f) => f.category === c).map((f) =>
            editing !== "new" && editing?.id === f.id ? (
              <FaqEditor key={f.id} faq={f} onCancel={() => setEditing(null)} onSave={(d) => act(async () => { await api(`/api/support/faqs/${f.id}`, { method: "PATCH", json: d }); setEditing(null); })} />
            ) : (
              <div key={f.id} className="rounded-2xl border border-night-line bg-night-panel/60">
                <button className="flex w-full items-center gap-3 px-4 py-3 text-left" onClick={() => setOpen(open === f.id ? null : f.id)} aria-expanded={open === f.id}>
                  <span className="min-w-0 flex-1 text-sm font-medium">{f.question}</span>
                  <span className={`text-gold transition-transform ${open === f.id ? "rotate-180" : ""}`} aria-hidden>⌄</span>
                </button>
                {open === f.id && (
                  <div className="space-y-3 border-t border-night-line px-4 py-3 text-sm text-snow-soft">
                    <p className="whitespace-pre-wrap">{f.answer}</p>
                    {f.steps.length > 0 && (
                      <ol className="space-y-1.5">
                        {f.steps.map((s, i) => (
                          <li key={i} className="flex gap-3">
                            <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-gold text-[11px] font-bold text-ink">{i + 1}</span>
                            <span>{s}</span>
                          </li>
                        ))}
                      </ol>
                    )}
                    {canEdit && (
                      <div className="flex flex-wrap gap-2 pt-1">
                        <button className="btn-ghost px-3 py-1 text-xs" onClick={() => setEditing(f)}>Edit</button>
                        <button className="btn-ghost px-3 py-1 text-xs" onClick={() => move(f, -1)}>Move up</button>
                        <button className="btn-ghost px-3 py-1 text-xs" onClick={() => move(f, 1)}>Move down</button>
                        <button
                          className="btn-ghost px-3 py-1 text-xs text-danger"
                          onClick={() => confirm("Delete this question?") && act(() => api(`/api/support/faqs/${f.id}`, { method: "DELETE" }))}
                        >
                          Delete
                        </button>
                      </div>
                    )}
                  </div>
                )}
              </div>
            ),
          )}
        </section>
      ))}
    </div>
  );
}

type FaqDraft = { category: string; question: string; answer: string; steps: string[] };

/** Add or change a question: its category, the question, the answer, and the steps (one per line). */
function FaqEditor({ faq, onSave, onCancel }: { faq?: Faq; onSave: (d: FaqDraft) => Promise<void>; onCancel: () => void }) {
  const [f, setF] = useState({ category: faq?.category ?? SUPPORT_CATEGORIES[0], question: faq?.question ?? "", answer: faq?.answer ?? "", steps: faq?.steps.join("\n") ?? "" });
  const [busy, setBusy] = useState(false);
  const categories = Array.from(new Set([...SUPPORT_CATEGORIES, f.category]));
  return (
    <form
      className="card space-y-3"
      onSubmit={async (e) => {
        e.preventDefault();
        setBusy(true);
        await onSave({ category: f.category, question: f.question, answer: f.answer, steps: f.steps.split("\n").map((s) => s.trim()).filter(Boolean) });
        setBusy(false);
      }}
    >
      <label className="block">
        <span className="label">Category</span>
        <select className="input" value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })}>
          {categories.map((c) => <option key={c}>{c}</option>)}
        </select>
      </label>
      <label className="block">
        <span className="label">Question</span>
        <input className="input" required value={f.question} onChange={(e) => setF({ ...f, question: e.target.value })} />
      </label>
      <label className="block">
        <span className="label">Answer</span>
        <textarea className="input min-h-24" required value={f.answer} onChange={(e) => setF({ ...f, answer: e.target.value })} />
      </label>
      <label className="block">
        <span className="label">Steps, one per line (optional)</span>
        <textarea className="input min-h-24" value={f.steps} onChange={(e) => setF({ ...f, steps: e.target.value })} />
      </label>
      <div className="flex gap-2">
        <button className="btn-gold flex-1" disabled={busy}>{busy ? "Saving…" : "Save"}</button>
        <button type="button" className="btn-ghost" onClick={onCancel} disabled={busy}>Cancel</button>
      </div>
    </form>
  );
}
