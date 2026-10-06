# Task list

Last session: 2026-10-06. Plan and decisions: [brief.md](brief.md). Defects
and how they were settled: [devlog.md](devlog.md).

## Where we left off

- **Day 1 done:** adapters for Greenhouse, Lever, Ashby, Workday, USAJOBS, NSF
  REU awards, NIH R25 into one `Posting` record; replay tests on saved raw
  responses.
- **Day 2 done (code):** discovery, fetching with politeness rules, internship
  filter, major / level / arrangement / state rules, open-closed comparison,
  GitHub Actions workflow.
- **Live:** hourly schedule, of which GitHub fires only some; updates at least
  daily are enough (Q24). Workday runs when its last run started 3 h ago
  (`workdayStarted` in `state.json`), about 62 min since the paging fix. Every
  run relabels every open posting with the current rules.
- **Day 3, the page:** built in `site/` (not yet live, see next session).
- **State after the 2026-10-06 03:50Z run (Workday, first with the paging
  fix):** 10,963 open postings (Workday 6,337 -> 7,894); Other 1,451 (13%);
  404 without a state; Education 50, Social Sciences 46; Workday `lastCount`
  0 on 19 boards (was 485). 36 tests.

## Done on 2026-10-05 (all checked on real runs)

- Workday scheduled by time since its last run, not clock hours.
- Filter fixes of 2026-10-04 confirmed: 69 of 77 rule flips kept.
- Majors relabelled by the owner with nothing pre-filled (148 rows,
  https://claude.ai/artifact/XSKuirRcRQiYWwkwLHmMSB); labelled sample 297
  rows. New keywords, NSF `CSE` directorate, generic words as weak rules,
  quant/Quantum, SkillBridge and "Post Doctoral" rejected, counselling and
  psychology before Life Sciences. Other 1,648 -> 1,197.
- Location: codes in more positions, one-state city table, foreign cities,
  Kansas City. No state 1,931 -> 411; 45 foreign postings rejected.
- Level: "undergraduate students" no longer reads as grad (783 postings).
- Every open posting relabelled each run (`Main.relabel`).
- `rejections.tsv` has locations, country, eligibility; 94% of Workday rows
  carry the requisition id (5.7% keep the path: tenants whose first bullet
  field is not the id).
- Failing boards: 19 Workday boards revived (underscore tenants, moved hosts,
  Intel); a board Simplify drops stays while it has open postings (Sereact's
  6 then closed normally on 3 misses).
- Thin majors: USAJOBS student hiring path (40 -> 55 open); 15 hand-added
  education / social-science boards (`Discover.HAND`).

## Done on 2026-10-06

- The 06:05Z discovery ran at 14:17Z (GitHub delays the daily cron by hours)
  and kept all 15 hand-added boards (2,193 boards).
- The 12 hand-added Workday boards all answer; none failed. They have no open
  postings: every row is a correct title rejection (their "intern" search
  returns everything), and Wiley's one intern is in Germany. Pew
  CenterInternships, Success Academy and Uncommon Schools list nothing at all,
  even with no search text; no other site name answers on those tenants.
- Workday paging stopped at page 2 on many tenants: later pages answer
  "total":0 (devlog 2026-10-06). 485 boards had `lastCount` 0, 390 stopped at
  exactly 40 rows. Fixed, `WorkdayPagingTest`.
- A paused host now records when the 403 came. "Oberlin" pins the leading
  word boundary on foreign names. 36 tests.
- 18 employer site names in the city table ("Carmel Headquarters", "UT MAIN
  CAMPUS", "Stamford Hub"...; one employer each, 3+ open postings): replay
  over the 01:27Z postings, no state 430 -> 297, no other posting changed.
  Left out on purpose: Batavia, Conway, Bethlehem (other states share them),
  "US Headquarters", "Airport Headquarters", "Any SpaceX Site".

- Closed postings leave `postings.json` 14 days after closing; a board
  failing 7 days has its open postings closed (`Main.retire`, counts in
  `report.json` under `retired`). Replay over the saved data: nothing changes
  today, the 13 postings closed on 10-04 go on 10-18. The 78 failures from
  before this have no start date, so their 7 days count from their next
  failure.
- Census places table (`us-places.tsv`, 1,654 names naming one state):
  a whole location segment, the first in each part. Replay: no state
  297 -> 266; every change checked by hand ("Fort Collins - Lincoln Campus"
  is CO only).
- Bangladesh / Dhaka rejected as outside the US (one open posting).
- Other is not sorted by a model reading descriptions (owner: finish fast).
- First Workday run with the paging fix (03:50Z): 62 min of 120, +1,541 open,
  `lastCount` 0 on 19 boards, 2 sudden-drop anomalies (see below), 12 boards
  at the 10-page cap (see below).
- **The page** (`site/index.html`, `style.css`, `app.js`; plain HTML/CSS/JS):
  first screen is a river of one point per open posting in 13 streams by
  field (owner chose it over a glow and a starlight version); field strip and
  chips; filters (search, state or remote, arrangement, level) applied live;
  list grouped by employer + title, 50 at a time, rows arrive on scroll;
  detail drawer with the description from its shard; saved list in the
  browser; "How it works" with real counts. Built with the Impeccable design
  plugin: product record `PRODUCT.md`, direction in
  `.impeccable/surfaces/site-index-html.md`. Finish review round 1: 8 fixes,
  6 resolved, 1 partial, 2 regressions. Round 2 (fresh = today only, rows
  visible by default, thin note first): all resolved, river click and lit
  stream verified on captures; it found rows blinking (visible, then blank,
  then fading in). Round 3 fixed that (rows on screen arrive at render,
  observer starts 15% below the screen; DOM test 1/6 -> 0/0 rows seen before
  arriving) and the wave order it broke (test: delays 6..11,0..4 -> 0..10).
  "See what's new" removed by the owner (did the same as "Choose your
  major"). Verdict: ship, for the scored fixes. Captures were taken with
  headless Chrome over the DevTools protocol from Node (no install).
  `.github/workflows/pages.yml` publishes `site/` + `data/` only.
  Locally: `site/data` is a junction to `data/` (gitignored); serve `site/`
  with `python -m http.server`.
- **Live** at https://patharearya.github.io/internship-tracker/ (Pages source
  "GitHub Actions", switched on 2026-10-06). First deploy 37474089311: page,
  `report.json`, `postings.json` (14 MB, 0.8 MB gzipped) and description
  shards all answer 200. Each successful `update` run redeploys.
- Open postings a new rule rejects now leave on the next run, logged as
  "still open, now rejected" (they used to count as misses and close as if
  the employer had closed them; devlog). Walmart "(USA) TX ..." locations
  read: no state 408 -> 332. Riverside Natural Foods (Toronto) rejected by
  board. Replay: Dhaka and Riverside leave on the next run. 37 tests.
- Favicon: a night-ground tile with ink waves (was Engineering's orange;
  plain ink would vanish on a light tab strip). Checked rendered at 16 and
  32 px on light and dark.
- Workday boards with more than 200 "intern" results (249) also read their
  student category in full (facet values up to 500 jobs, one facet). Adds
  roughly 15-20 min to a Workday run (52 min before; limit 120); check the
  first Workday run after this. CVS store pharmacy internships (3,279) stay
  a sample: the intern search stops early on CVS, so fewer of them will be
  open (devlog).

## Next session, in order

1. **Two sudden-drop anomalies** on 2026-10-06 03:50Z: `ashby:nory-co` 24 -> 0
   and `raymondjames.../raymondjamesearlycareers` 17 -> 2. Check whether real.
2. **Planner and tracker page, browser only (owner, 2026-10-06; Q26).** A
   spreadsheet-like page built from the saved (starred) postings, to plan
   and track applying. No login yet: kept in browser storage like the stars.
   - Every column is optional: the user ticks which ones appear on their
     planner (checkboxes), and the choice is remembered.
   - Columns to offer: status (saved / preparing / applied / interviewing /
     offer / rejected), applied checkmark, date applied, apply-by date
     (before the published deadline; with none, within N days of first
     seen), deadline, days open, first seen, closed warning ("closed Oct 4",
     from our own records), materials checklist (resume, cover letter,
     transcript, references, portfolio), follow-up date, notes, contacts,
     field, location, arrangement, level.
   - Timeline view: each planned posting from first seen to apply-by to
     deadline.
   - Sortable columns, cells edited in place, CSV export.
   - A saved posting keeps a copy of its title, employer, link and deadline,
     because closed postings leave `postings.json` after 14 days.
   - No reminders for now (owner).
   - Built with Impeccable inside the existing world (DESIGN.md): new surface
     brief, finish review, documenter.
3. **Google sign-in, data in the user's own Google Drive (B, after 2).**
   Sign in with Google; the planner is kept in the hidden app-data folder of
   the user's Drive, so there is still no server and no student data held by
   us. Needs: a Google Cloud project and OAuth client (owner's Google
   account), merging what is in the browser with what is in Drive on first
   sign-in, sign-out. Check first whether the `drive.appdata` scope needs
   Google's app verification (unverified apps can be capped at 100 users).
   Firebase (C) rejected: we would hold student data.

## After a few days of runs

- Tune the "sudden drop" constants (`DROP_CHECK_MIN`, `DROP_RATIO`) and check
  that 3 misses fits real churn.
- `ubuntu-latest` moves to Ubuntu 26 from 2026-10-19; watch the first run.

## Settled

- "Summer 2027 ..." titles are kept (a dated term); "starting summer 2027"
  is a full-time start date and is not.
- Student jobs (work-study, student ambassador, "Graduate Student Research
  Assistant", "Working Student") are out: open only to that university's
  students (owner default, 2026-10-04).
- "Research Analyst (Economics) - July 2027" stays rejected: a full-time job.
- Majors follow the work, not the employer (Q23); employer-name matching was
  tried and dropped. SkillBridge is rejected.
- Updates at least daily are enough (Q24).
- "AI Residency" stays rejected: a research job (Q25).
- Other stays a group on the page; no model reads descriptions to sort it
  (owner, 2026-10-06: finish fast).
- Closed postings are dropped and failing boards' postings closed (owner,
  2026-10-06). 14 and 7 days are defaults: 27 postings reopened in the first
  two days, and dropping at once would show each as new.
- No January recheck of thin majors: the owner does not plan to come back to
  the project once the task list is done (2026-10-06).

## Small items

- 266 without a state (replay, 2026-10-06): 39 with no location, 19 "United
  States", SpaceX "any site", cities several states share (Columbus,
  Charleston, Rochester, Madison) and site names. Left as they are.
- Page-1 labels in "kept: random" that agree with the rules may still be
  unverified pre-fills (devlog 2026-10-05).
- 16 Workday tenants answer on no host (Activision, Comcast, Lilly, IDEXX...)
  and 47 Greenhouse/Lever/Ashby boards are gone; stale entries such as
  `intel.../en-us` stay in `state.json` failures. One request a day each;
  prune if the list gets in the way.
- Working on this repo from this machine's shell: `\\` in a command reaches
  the program as `\`. Write anything with regex backslashes through a file.
