# Internship & Research Finder — Project Brief

## Problem
Students check dozens of career pages every day to catch internships the
moment they open, because early applicants are seen first. Postings are
scattered across employers' own sites, and there is no single place that shows,
for a student's own major, what is open now and when it opened.

## User and promise
A US college student, any major. Browse-only: the app is useful the moment it
opens. Users filter and save; they never supply the data. Target: built in a
few days, kept simple.

The app shows:
- Live internship postings and funded research programmes, updated several
  times a day (at least daily, Q24), grouped by major.
- When each posting was first seen and when it closed (our own monitoring).
- Deadlines where the source publishes one.
- Filters: state / remote; in person / hybrid / remote; undergrad / grad.
- Full descriptions where the source provides them.
- A saved list (star button, stored in the browser).
- One honest coverage sentence: postings come from N employers' public job
  boards, federal jobs and funded research programmes, not every internship.

## Project parameters (fixed)
1. Free (data, tools, hosting).
2. Java.
3. Solves a real problem; at minimum, a real convenience.
4. Real-world data from original (first-party) sources only. No aggregators.
5. Live: updates constantly.
6. Works without users entering data.
7. Passes the test "would people actually open it?"

## Data sources
| Source | What | Access |
|---|---|---|
| Greenhouse, Lever, Ashby | Employers' own job boards | Public, no key |
| Workday | Employers' own job boards (banks, Big 4, hospitals, retail) | `POST https://{tenant}.wd{N}.myworkdayjobs.com/wday/cxs/{tenant}/{site}/jobs`, no key |
| USAJOBS API | Federal internships incl. Pathways, real closing dates | Free key |
| NSF Award API | Active "REU Site" awards (funded undergrad research) | `api.nsf.gov/services/v1/awards.json`, no key |
| NIH RePORTER | R25 education/training awards (life-science research) | `POST api.reporter.nih.gov/v2/projects/search`, no key |

**Discovery.** Greenhouse/Lever/Ashby boards come from the application links in
SimplifyJobs `listings.json` (tech-only list). We store only the extracted
(company, hiring system, board) mapping, never the list itself, and tag each
board with where it was found so Simplify-derived boards can be removed if the
maintainers object. No permission request filed: owner's decision, since only
public application URLs are read and no listing content is reused. Workday employers come from a hand-built list of ~50
(company, host, site) chosen so every major has some; each tenant's robots.txt
names its sites. Every list entry is validated live; failures are logged with
a reason.

**Not used:** The Muse, Adzuna and other aggregators (second-hand; The Muse
also 403s unless the User-Agent is spoofed). SmartRecruiters (robots.txt
`Disallow: /`). NEOGOV / governmentjobs.com (robots.txt, empty feed). NSF's REU
search page and CSV export (robots.txt-disallowed, bot challenge). University
pages (no scraping for deadlines). LinkedIn, Handshake, Indeed, Glassdoor
(terms). Teamtailor, Recruitee, Workable skipped for now (EU-heavy or empty).

Known gap: the employer boards lean tech; for other majors Workday, USAJOBS
and research awards carry most of the weight, and some majors will be thin.
The site says so.

## How it works
One Java program, run hourly by GitHub Actions. No AI in the pipeline.

    for each source: fetch -> internship filter -> classify (major, level,
    arrangement, state) -> compare with previous postings.json -> write
    postings.json + rejections.json -> commit
    GitHub Pages: static HTML/JS reads postings.json

- **Filter.** Keep intern, internship, co-op, "summer analyst/associate";
  apprenticeships and fellowships kept as their own type. Reject new-grad,
  entry level, full-time, and false hits ("internal", "international",
  "Internship Program Manager"). Research awards bypass the title filter.
- **Major.** One per posting, from 12 categories plus Other: Computer Science
  & IT; Engineering; Data & Mathematics; Business & Management; Finance &
  Accounting; Marketing & Communications; Life Sciences & Health; Physical
  Sciences; Social Sciences & Psychology; Government, Law & Policy; Education;
  Arts, Design & Media; Other. Keyword rules on title/department, first match
  wins in a fixed order. USAJOBS maps by occupational series, NSF by programme,
  NIH to Life Sciences & Health. Generic title words ("quality", "technology")
  count only when neither the title's subject words nor the category decide.
  The employer's name is not used. Other is always shown.
- **Level.** `undergrad`, `grad`, `both`, `unknown` by keyword. Grad uses
  phrases ("graduate student"), never bare "graduate". Unknown shows as "level
  not stated" and appears under both filters.
- **Arrangement.** `in-person`, `hybrid`, `remote`, `not stated` by keyword. A
  bare city is not taken as in-person. Not stated appears under all filters.
- **Location.** Each location parsed to a US state, `remote-us` or `unknown`.
  Non-US-only roles rejected ("outside US"). Multi-location postings carry a
  location list.
- **Every classified value records the rule and matched text** that produced
  it. Every rejection is logged with the rule that rejected it.
- **Identity** is (source, board, source posting ID), never the title.
  Same-company, same-title postings in several cities are grouped in the UI.
  Cross-source duplicates are not merged.
- **Closure.** Only a successful, plausible fetch (HTTP 200, valid JSON, no
  sudden drop to near zero) counts as a miss; a sudden drop is logged as a
  fetch anomaly. Closed after 3 consecutive misses (named constant); closed
  time = first miss. Reappearing postings are marked reopened. Closed postings
  stay in the file so saved items still resolve.
- **Deadlines.** Shown where published; otherwise "No deadline published",
  never "Rolling", plus days open ("open 9 days").
- **Politeness.** User-Agent names the project, repo URL and owner's contact
  email (from a GitHub Actions secret, never committed). One request at a time
  per source. A failing board backs off 1h -> 2h -> 4h, capped at 24h, each
  failure logged. 403/429 stops that source and flags it; no automatic retry.
  robots.txt respected on every host.

## Stack
- Java 21, Maven, built-in HTTP client, Jackson for JSON, JUnit 6 (Jupiter API).
- GitHub Actions (hourly job), GitHub Pages (plain HTML/CSS/JS front end).
- Git history of `postings.json` is the record of first-seen and closed dates.
  No database, no server.
- Secrets (USAJOBS key, contact email) in GitHub Actions secrets.

## Spike results (2026-10-03, raw responses in src/test/resources/raw/)
- Simplify `listings.json`: 16,838 rows, 4,460 active. Active links by system:
  Workday 36%, other 29%, Greenhouse 10%, Oracle 7%, Ashby 6%, iCIMS 4%,
  Lever 2%, SmartRecruiters 2%. Distinct boards across all rows: Workday
  1,159, Greenhouse 502, Ashby 358, Lever 170.
- robots.txt: Greenhouse API disallows only `/embed/`; Lever allows all with
  `Crawl-delay: 1`; Ashby API, NSF API and NIH API have none (401/404).
- Greenhouse: `first_published`, `application_deadline`, departments, full
  description. Lever: `createdAt` (epoch ms), `workplaceType`,
  department, `commitment`. Ashby: `publishedAt`, `workplaceType`,
  `employmentType`, department. All 200 in under 1 s.
- Workday: list gives only relative dates ("Posted 3 Days Ago"); detail call
  gives `startDate`, country, full description. "intern" search is precise
  (20/20 titles) but worldwide (6/20 US); `Location_Country` and
  `jobFamilyGroup` facets exist. List call ~2.4 s.
- NSF: 724 active "REU Site" awards. Only 3 of 25 sampled abstracts link to a
  programme page (5 contain a URL, but 2 of those are only the generic
  `etap.nsf.gov` application portal; see devlog). No deadline field.
  `dirAbbr` (BIO, CISE, ENG, MPS...) maps to majors.
- USAJOBS (needs `Authorization-Key` header and the registered email as
  User-Agent): `Keyword=intern` gives 632; the first 25 (relevance-ordered)
  are all internships, but across all 632 only ~40 are (the search also
  matches "internal" and description text; see devlog). Each has occupational series code, `PublicationStartDate`,
  `ApplicationCloseDate`, `HiringPath`, `RemoteIndicator`, `TeleworkEligible`.
  `HiringPath=student` is imprecise (123, only 3/25 titles internships: it
  means "students may apply", not "internship"). Some intern postings are
  internal-only (`fed-internal-search`), which students can't apply to.
- NIH R25 FY2026: 881 total, mostly faculty/trainee programmes, not student
  openings. Text filter "undergraduate summer research" gives 222, all real
  summer undergraduate programmes in the sample.

## Originally listed for the spike
- Exact endpoints and fields for each source; whether descriptions and posting
  dates are included; rate limits or terms.
- USAJOBS key/User-Agent requirements; internship filter parameters.
- Simplify links by hiring system: how much Greenhouse/Lever/Ashby covers.
- NSF: how many REU award abstracts contain a programme URL.
- Typical response sizes and times; the "sudden drop" threshold; whether 3
  misses is right for real churn.
- A hand-labelled sample of real titles for the filter and major rules,
  including "part-time student worker" and titles with no "intern".

## Milestones
1. **Day 1 am — spike:** fetch one real example per source, save raw responses
   as test data, answer the measurements above. Then a 10-minute check-in on
   the numbers, flagging anything that changes a decision.
2. **Day 1 — adapters:** Greenhouse, Lever, Ashby, Workday, USAJOBS, NSF, NIH
   into one common record; tests replay saved responses.
3. **Day 2 — rules:** filter, major, level, arrangement, state; checked against
   the hand-labelled sample; rejections logged.
4. **Day 2 — compare and output:** first-seen / 3-miss closure vs previous
   `postings.json`; hourly GitHub Actions job.
5. **Day 3 — page:** grouped by major, filters, detail, saved list, coverage
   sentence; published on GitHub Pages.

## Verification
- Tests replay saved raw responses, never live calls.
- Every test is broken on purpose once to confirm it fails.
- Filter and major rules are checked against a hand-labelled sample of real
  postings, and re-checked after every rule change.
- Rejections and discovery failures are always logged with reasons.
- Dev log (docs/devlog.md): one entry per significant defect: what broke, the
  first diagnosis, and the measurement that settled it.

## Decisions log
Grill held 2026-10-03. Superseded entries are kept, marked, for the record.

- Project replaced earlier ideas (gas prices, research finder, power grid).
- No AI in the pipeline. Java 21, Maven.
- Q1 tech only. **Superseded by Q15** (all majors).
- Q2 NC-focused location values. **Superseded by Q18** (national, by state).
- Q3 level keyword rules, `unknown` under both filters. Current.
- Q3c arrangement keyword rules, `not stated`, bare city not in-person. Current.
- Q4 closure: plausible fetches only, 3 misses, closed = first miss. Current.
- Q5 opening-soon predictions from own records only. **Cut by Q15.**
- Q6 "No deadline published" + days open, never "Rolling". Current.
- Q7/Q7c Google sign-in accounts, delete-account, privacy note. **Superseded
  by Q15** (browser-only saved list; no personal data stored).
- Q8 internship and research finder. Current. Q8b formal programmes incl.
  SULI/NASA: SULI/NASA **cut by Q15**. Q8c REU via NSF Award API only
  (official REU search robots-disallowed and bot-challenged). Current. Q8d
  tech-only research **superseded by Q15**.
- Q9 politeness rules; contact email from a secret. Current.
- Q10 Simplify list for discovery only. Current. Filing a permission issue
  **dropped** (owner's call: public URLs only, no listing content reused).
- Q11 internship detection rules. Current.
- Q12 no company filter; location filters postings. Current. NC seed probing
  **cut by Q15**.
- Q13 identity tuple, UI grouping, no cross-source merge. Current.
- Q14 coverage stats page. **Cut by Q15** (one coverage sentence).
- Q15 re-scope: few days, all majors grouped by major, GitHub Actions +
  `postings.json` + GitHub Pages, no SQLite/Javalin/server.
- Q16 first-party only stays; no aggregators.
- Q17 the 12 majors plus Other, one per posting, first match wins.
- Q18 national, state filter.
- Q19 add Workday (hand-built list of ~50) and NIH RePORTER R25. The Muse,
  SmartRecruiters, NEOGOV rejected; Teamtailor/Recruitee/Workable skipped.
- Q20 compressed 3-day plan; second grill becomes a 10-minute spike check-in.
- Q21 (after spike): Workday sites come from the Simplify list (1,159), with
  a short hand-added list only for majors still thin after the first run
  (supersedes Q19's hand-built ~50). NIH uses R25 + text "undergraduate summer
  research". REU entries without a programme link point to NSF's public award
  page (linked, not fetched). Workday frequency set after timing the first
  full run: over an hour -> Workday every 3 hours, rest hourly.
- First full run (2026-10-04): 3,396 open postings in 342 s, but Workday
  measured ~5 s per board (~95 min for all), so Workday runs every 3rd hour.
- Q22 a 403/429 pauses only the server that sent it, not the whole hiring
  system (supersedes that part of Q9). Workday is one server per employer;
  Greenhouse, Lever, Ashby each one shared server. Pauses persist in
  `data/state.json` until a person removes them.
- Workday's 3 hours are measured from its last run, not the clock hour:
  GitHub dropped 8 of ~13 scheduled runs on 2026-10-04 (devlog).
- Q23 (2026-10-05, from the second labelling page) majors follow the work,
  not the employer's industry; the employer-name fallback was tried and
  dropped (171 decisions, about half wrong). Claims, equity research -> Finance;
  learning/training & development -> Education; EHS/HSE/HES -> Life Sciences.
  DoD SkillBridge internships are rejected (service members only).
- Q24 (2026-10-05) updates at least daily are enough. The hourly schedule
  stays; GitHub fires only some of its slots, which still gives several
  updates a day. The page must not promise "hourly".
- Q25 (2026-10-05) "AI Residency" is a research job, not an internship or a
  funded student programme: stays rejected.
