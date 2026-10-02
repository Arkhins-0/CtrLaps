// No "server-only" here and no Node built-ins: this module may be imported
// by client components too. Server-only values live in env.ts.

export const APP_NAME = "CTR[L]APS";

const vercelDomain = process.env.NEXT_PUBLIC_VERCEL_PROJECT_PRODUCTION_URL || process.env.VERCEL_PROJECT_PRODUCTION_URL;

/** Where this site is deployed. Used for absolute links and metadata. */
export const SITE_URL = (
  process.env.NEXT_PUBLIC_SITE_URL ||
  (vercelDomain ? `https://${vercelDomain}` : `http://localhost:${process.env.PORT || 3000}`)
).replace(/\/+$/, "");

/** SITE_URL without the scheme, for showing to people. */
export const SITE_HOST = SITE_URL.replace(/^https?:\/\//, "");

