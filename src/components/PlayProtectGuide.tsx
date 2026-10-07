import { APP_NAME } from "@/lib/config";

/*
 * How to get past Google Play Protect's warning while CTR[L]APS isn't on the Play Store (Google is still registering us
 * as a developer, so Android warns about an app it hasn't checked). The two prompts people meet, drawn here rather than
 * copied from screenshots, so they always show our app's name, with the button to tap ringed and numbered. The words
 * and order follow what Play Protect shows on current phones; the look is a plain Android dialog, not Google's art.
 */

/** A numbered ring around the button to tap. */
function Tap({ n, children, wide = false }: { n: number; children: React.ReactNode; wide?: boolean }) {
  return (
    <span className={`relative inline-flex items-center justify-center rounded-full px-4 py-2 text-sm font-semibold text-[#0b57d0] ring-2 ring-gold ring-offset-2 ring-offset-white ${wide ? "w-full" : ""}`}>
      {children}
      <span className="absolute -right-2 -top-2 grid h-5 w-5 place-items-center rounded-full bg-gold text-[11px] font-bold text-ink">{n}</span>
    </span>
  );
}

/** A plain Android dialog: a shield, a title, its words, then its buttons. */
function Dialog({ title, line, children }: { title: string; line: string; children: React.ReactNode }) {
  return (
    <div className="rounded-[26px] bg-white px-5 pb-4 pt-5 text-left text-[#1f1f1f] shadow-xl">
      <svg viewBox="0 0 24 24" className="mx-auto h-7 w-7" aria-hidden>
        <path fill="#0b57d0" d="M12 1 3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 6c1.4 0 2.8 1.1 2.8 2.5V11c.6 0 1.2.6 1.2 1.3v3.5c0 .6-.6 1.2-1.3 1.2H9.2c-.6 0-1.2-.6-1.2-1.3v-3.5c0-.6.6-1.2 1.2-1.2V9.5C9.2 8.1 10.6 7 12 7zm0 1.2c-.8 0-1.5.5-1.5 1.3V11h3V9.5c0-.8-.7-1.3-1.5-1.3z" />
      </svg>
      <p className="mt-3 text-center text-lg font-normal leading-snug">{title}</p>
      <p className="mt-2 text-[13px] leading-relaxed text-[#444746]">{line}</p>
      <div className="mt-4 flex flex-wrap items-center justify-end gap-2">{children}</div>
    </div>
  );
}

export function PlayProtectGuide() {
  return (
    <section className="mt-6">
      <h2 className="mb-1 text-lg font-bold tracking-tight">If Google Play Protect warns you</h2>
      <p className="text-sm text-snow-soft">
        {APP_NAME} isn&apos;t on the Play Store yet: Google is still registering us as a developer, so Android warns about an app it hasn&apos;t
        checked itself. The app is ours and safe to install. You&apos;ll see one of these; tap the ringed buttons in order.
      </p>

      <div className="mt-5 grid gap-6 sm:grid-cols-2">
        {/* The usual one: blocked, until More details shows Install anyway. */}
        <figure>
          <div className="rounded-[28px] bg-night-high p-4">
            <Dialog title="Unsafe app blocked" line={`Play Protect doesn't recognise this app's developer. Apps from unknown developers can sometimes be unsafe.`}>
              <span className="px-3 py-2 text-sm font-semibold text-[#0b57d0]">OK</span>
              <Tap n={1}>More details</Tap>
              <div className="mt-1 w-full border-t border-[#e3e3e3] pt-3 text-center">
                <Tap n={2} wide>
                  Install anyway
                </Tap>
              </div>
            </Dialog>
          </div>
          <figcaption className="mt-3 text-sm text-snow-soft">
            <span className="font-bold text-snow">&ldquo;Unsafe app blocked&rdquo;:</span> tap <b className="text-snow">More details</b>, then{" "}
            <b className="text-snow">Install anyway</b>. Confirm with your fingerprint or PIN if asked.
          </figcaption>
        </figure>

        {/* The other one: a scan offered before installing. */}
        <figure>
          <div className="rounded-[28px] bg-night-high p-4">
            <Dialog title="Send app for a security scan?" line={`Play Protect hasn't seen this app before. Sending it for a scan can help keep your device safe.`}>
              <Tap n={1}>Install without scanning</Tap>
              <span className="px-3 py-2 text-sm font-semibold text-[#0b57d0]">Scan app</span>
            </Dialog>
          </div>
          <figcaption className="mt-3 text-sm text-snow-soft">
            <span className="font-bold text-snow">&ldquo;Send app for a security scan?&rdquo;:</span> tap{" "}
            <b className="text-snow">Install without scanning</b>. (Scan app also works, but takes longer.)
          </figcaption>
        </figure>
      </div>

      <p className="mt-4 text-xs text-snow-faint">
        Seeing &ldquo;App not installed&rdquo; instead? An older copy from somewhere else is in the way: uninstall it, then install this one.
      </p>
    </section>
  );
}
