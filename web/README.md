# getskymap.app

Static Astro site for Sky Map, served by Cloudflare (Workers static assets; see `wrangler.jsonc`;
build `npm run build`, output `dist`,
root `web/`).

- `npm run dev` / `npm run build` / `npm run preview`
- `/privacy`, `/terms` and `/help` are rendered at build time from the repo root's
  `PRIVACY_POLICY.md`, `TERMS.md` and `troubleshooting.md`; edit those, not the pages.
- Copy lives in `src/i18n/en.json`; use `useT()`. Locale routing is configured in `astro.config.mjs`
  (English only for now, no prefix).
- Numbers and store links: `src/data/stats.json` (shown only when `verified`) and `links.json`.
- Claims: `docs/claims.md`. Design reference: `docs/design/landing-mockup.html` (visual only;
  its copy contains claims that are false).

## Analytics
Cloudflare Web Analytics (enable in the Pages project; the beacon is injected, no code here).
Donation intent = visits to `/support`, which then forwards to Buy Me a Coffee. The Play button
carries a `referrer=utm_...` so Play Console reports installs from the site. Share links from the
app should use `utm_source=skymap&utm_medium=app&utm_campaign=<flavor>`. Update
`PRIVACY_POLICY.md` when this goes live.
