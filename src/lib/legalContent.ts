import { SITE_HOST } from "./config";
import { CONTACT_EMAIL, OPERATOR, TERMS_UPDATED, TERMS_VERSION } from "./legal";

/*
 * The Privacy Policy and the Terms, written once: the website renders them
 * as pages, and /api/legal/<doc> gives them to the app, which shows them on
 * its own screen and keeps a copy. Text may carry **bold** and
 * [links](href); an href of /privacy or /terms points at the other document.
 */

export type Block = { p: string } | { ul: string[] };
export type LegalSection = { title: string; blocks: Block[] };
export type LegalDoc = { title: string; updated: string; version: string; intro: Block[]; sections: LegalSection[] };
export type LegalKey = "privacy" | "terms";

const mail = `[${CONTACT_EMAIL}](mailto:${CONTACT_EMAIL})`;

const privacy: LegalDoc = {
  title: "Privacy Policy",
  updated: TERMS_UPDATED,
  version: TERMS_VERSION,
  intro: [
    {
      p: `CTR[L]APS is a communication app for race weekends, run by ${OPERATOR} (“we”, “us”). This policy explains what personal data CTR[L]APS handles, why, who it is shared with, how long it is kept, and the rights you have under India’s Digital Personal Data Protection Act, 2023. It covers the website at ${SITE_HOST} and the CTR[L]APS Android app.`,
    },
  ],
  sections: [
    {
      title: "1. Who can use CTR[L]APS",
      blocks: [
        {
          p: "Anyone can register for CTR[L]APS with an email address, which we confirm by sending a link before the account is made. A new account can read announcements and race-weekend channels. The organisers of an event (admins, coordinators, team managers and security heads) give people their role, such as racer, crew or volunteer, either by adding their email address, which sends an invite, or by promoting an existing account.",
        },
      ],
    },
    {
      title: "2. What we collect",
      blocks: [
        {
          ul: [
            "**Account details** entered by the person who created your account: your email address, role, team, and who you report to.",
            "**Your profile**, completed when you first sign in: full name, date of birth, contact number and photo. Your account also has a verification code and a QR code.",
            "**What you send**: announcements, race-weekend channel posts and private chat messages, with any edits and deletions; documents, photos, voice notes and audio files you attach; and your location, only when you choose to share it in a message.",
            "**Message status**: when a message reached a device and when it was read. Private chats show this to the sender as ticks.",
            "**Technical data**: sign-in sessions (whether web or app, and when last used), the push-notification token of your device, and, to stop password guessing, the email address and IP address of recent sign-in attempts.",
            "**A record of account changes**: who changed a profile, a status or a race time, and when.",
          ],
        },
        { p: "We do not use advertising, analytics or tracking tools, and we do not sell personal data." },
      ],
    },
    {
      title: "3. What the app asks your phone for",
      blocks: [
        {
          ul: [
            "**Notifications**, to show messages as they arrive, and an exemption from battery optimisation so they still arrive while the phone sleeps.",
            "**Storage** (older Android versions only), to save documents in Downloads/CTRLAPS.",
            "**Microphone**, only while you record a voice note; **location**, only when you share it; **camera**, only while scanning someone’s ID code.",
          ],
        },
        {
          p: "The app keeps a copy of what it shows you (your chats, announcements, channel posts, the schedule, the people list, your account, these documents, and the photos and voice notes in them) in its own storage on your phone, so it opens quickly and without signal. You can clear it at any time under Account → Kept on this phone. It is cleared automatically if another person signs in on the same phone, or if your account is banned.",
        },
      ],
    },
    {
      title: "4. Why we use it",
      blocks: [
        {
          ul: [
            "To run the service: deliver messages, documents and schedule changes, and show the race schedule and countdown.",
            "To let event staff confirm who you are, by scanning your QR code or entering your verification code.",
            "To notify you by push notification and, for urgent messages, by email.",
            "To keep CTR[L]APS secure: sign-in limits, session management, and a record of account changes.",
          ],
        },
        {
          p: "We process this data on the basis of your consent, which you give when you set up your account, and for the legitimate uses the Act allows. You may withdraw consent at any time by writing to us; your account will then be closed.",
        },
      ],
    },
    {
      title: "5. Who can see your data",
      blocks: [
        {
          ul: [
            "The people you message see what you send them, your name, role and photo.",
            "The people above you in your event’s structure, and all admins, can see your profile and account status.",
            "Anyone signed in to CTR[L]APS who scans your QR code or types your verification code sees your photo, name, role, team and account status.",
            "Service providers who run parts of CTR[L]APS for us: Vercel (hosting), Neon (database), Amazon Web Services (file storage, United States), Google Firebase (push notifications), Brevo (email) and GitHub (app downloads). They process data only to provide their service to us.",
          ],
        },
        {
          p: "Some of these providers store or process data outside India, including in the United States. We may also disclose data where Indian law requires it.",
        },
      ],
    },
    {
      title: "6. How long we keep it",
      blocks: [
        {
          ul: [
            "Your account and profile are kept until you delete your account (see below).",
            "Messages are grouped by season. Admins can archive a season (read-only) or delete it, which permanently removes that season’s messages.",
            "A private message you delete loses its text and attachment straight away, for both people.",
            "Sign-in attempt records are kept only as long as needed to limit repeated attempts.",
          ],
        },
        { p: "Emails and notifications that were already delivered cannot be recalled from the devices or inboxes that received them." },
        {
          p: "**Deleting your account.** You can delete your account yourself in the app (Account → Settings → Delete account) or on the website (Account → Delete account), or ask us to do it for you. You are signed out everywhere at once, and the account is deleted 7 days later; if you sign in again before then, the deletion is cancelled. When it is deleted:",
        },
        {
          ul: [
            "erased: your name, email, contact number, date of birth, photo, team, password, signed-in devices and notification registrations, email choices, followed categories, poll votes and event replies, group memberships, and your support tickets with their messages;",
            "kept, shown as from “Deleted user”: the messages, photos and documents you sent in private chats and groups, so the people you wrote to keep their conversations whole (they can no longer see who you were), and announcements or channel posts you made as an organiser;",
            "race results that were published keep the name as it was published, as part of the championship record; they are no longer linked to any account. Write to us if you want the name withheld;",
            "our records keep only that an account was deleted, and when;",
            "copies in our service providers’ backups are removed as those backups expire, within 30 days;",
            "copies we cannot reach stay where they are: messages, notifications and emails already on other people’s phones or in their inboxes, and anything they saved or forwarded.",
          ],
        },
      ],
    },
    {
      title: "7. Security",
      blocks: [
        {
          p: "Passwords are stored only as salted hashes. All traffic uses HTTPS, and files are only served to people who are signed in. No system is perfectly secure; if a breach affects your data, we will tell you and the Data Protection Board of India as the Act requires.",
        },
      ],
    },
    {
      title: "8. Children",
      blocks: [
        {
          p: "Some people on a race weekend, such as young racers, may be under 18. Their account may only be used with the consent of a parent or lawful guardian, which the organiser creating the account must obtain. We do not track or monitor children’s behaviour or show them advertising.",
        },
      ],
    },
    {
      title: "9. Your rights",
      blocks: [
        { p: "You can ask us to:" },
        {
          ul: [
            "tell you what personal data we hold about you and who it has been shared with;",
            "correct or complete it (your profile is locked after setup, so your manager, an admin or we can change it for you);",
            "erase it, and close your account: you can do this yourself at any time (see section 6), or we will do it on your request;",
            "name someone to act for you if you die or become unable to act.",
          ],
        },
        {
          p: `Write to ${mail}. We will reply within 30 days. If you are not satisfied with our answer, you can complain to the Data Protection Board of India.`,
        },
      ],
    },
    {
      title: "10. Changes to this policy",
      blocks: [
        {
          p: "If we change this policy in a way that matters, we will update the date above and tell you in the app or by email before the change takes effect.",
        },
      ],
    },
    { title: "11. Contact", blocks: [{ p: `${OPERATOR} · ${mail}` }] },
  ],
};

const terms: LegalDoc = {
  title: "Terms and Conditions",
  updated: TERMS_UPDATED,
  version: TERMS_VERSION,
  intro: [
    {
      p: `These terms apply to your use of CTR[L]APS, the race-weekend communication service at ${SITE_HOST} and in the CTR[L]APS Android app, run by ${OPERATOR} (“we”, “us”). By setting up your account you agree to them and to our [Privacy Policy](/privacy). If you do not agree, do not set up or use an account.`,
    },
  ],
  sections: [
    {
      title: "1. Accounts",
      blocks: [
        {
          ul: [
            "You can register yourself, or an organiser of your event can add you. Your role is given by an organiser, who may change it later.",
            "Keep your password to yourself. You are responsible for what is sent from your account.",
            "The profile you complete when you first sign in must be accurate: name, date of birth, contact number and a photo that shows you. It is used to confirm who you are at the venue. After setup it can only be changed by your manager or an admin.",
            "If you are under 18, a parent or lawful guardian must agree to these terms for you.",
          ],
        },
      ],
    },
    {
      title: "2. Using CTR[L]APS",
      blocks: [
        { p: "Use CTR[L]APS for communication about your event. Do not:" },
        {
          ul: [
            "send anything unlawful, abusive, harassing, discriminatory, or that you have no right to share;",
            "share other people’s personal data or confidential event information outside the people who need it;",
            "use someone else’s account, QR code or verification code, or try to get into parts of CTR[L]APS you are not given;",
            "upload malware, or try to disrupt, overload or reverse-engineer the service;",
            "mark messages urgent without need: urgent messages also go out by email.",
          ],
        },
      ],
    },
    {
      title: "3. Not a safety system",
      blocks: [
        {
          p: "CTR[L]APS carries messages as quickly as it can, but delivery depends on networks, phone settings and third-party services such as Google’s notification service. Do not rely on CTR[L]APS for emergencies, race control or any safety-critical instruction. Always follow the official procedures and channels of your event.",
        },
      ],
    },
    {
      title: "4. What you send",
      blocks: [
        {
          ul: [
            "You remain responsible for your messages and files. You let us store and deliver them to the people you send them to, which is all we use them for.",
            "You can edit or delete a private chat message for two hours after sending it. Deleting removes it for both people, but a notification or email that was already delivered cannot be recalled.",
            "Organisers can archive or delete a whole season of messages.",
          ],
        },
      ],
    },
    {
      title: "5. Account status",
      blocks: [
        {
          p: "Organisers and admins can suspend, dismiss or ban an account, for example when someone leaves the event or breaks these terms. A suspended or dismissed account cannot sign in. When an account is banned, the app also removes that person’s chats from their phone.",
        },
      ],
    },
    {
      title: "6. The app",
      blocks: [
        {
          p: "The Android app is offered as a download from our site and GitHub, and updates itself from there. Keep it up to date: older versions may stop working with the service.",
        },
      ],
    },
    {
      title: "7. Availability and liability",
      blocks: [
        {
          p: `CTR[L]APS is provided as it is, and we do not promise it will always be available or free of errors. To the extent Indian law allows, ${OPERATOR} is not liable for indirect or consequential loss, or for loss caused by a message that was delayed, not delivered, or misread. Nothing in these terms limits liability that cannot be limited by law.`,
        },
      ],
    },
    {
      title: "8. Ending your use",
      blocks: [
        {
          p: `You can stop using CTR[L]APS at any time and delete your account yourself (Account → Settings → Delete account in the app, or Account → Delete account on the website), or ask us to by writing to ${mail}. The account is deleted 7 days after you ask, and signing in before then cancels it. What is erased and what is kept, such as messages you sent to others and published race results, is set out in section 6 of the [Privacy Policy](/privacy). We may suspend or close an account that breaks these terms.`,
        },
      ],
    },
    {
      title: "9. Changes",
      blocks: [
        {
          p: "If we change these terms in a way that matters, we will update the date above and tell you in the app or by email before the change takes effect.",
        },
      ],
    },
    {
      title: "10. Law",
      blocks: [{ p: "These terms are governed by the laws of India, and the courts in India have jurisdiction over any dispute about them." }],
    },
    { title: "11. Contact", blocks: [{ p: `${OPERATOR} · ${mail}` }] },
  ],
};

export const LEGAL: Record<LegalKey, LegalDoc> = { privacy, terms };

export const isLegalKey = (v: string): v is LegalKey => v === "privacy" || v === "terms";
