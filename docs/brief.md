# Internship Tracker — Project Brief

## Problem
Students check dozens of career pages and lists every day to catch internships
the moment they open, because early applicants are seen first. Postings are
scattered across employers' own sites, there is no single place that shows when
each one opened and closed, and nothing predicts when a company will open again.
NC students have it worst: national lists are dominated by big-city roles.

## User and promise
A college student, NC-focused. Browse-only: the app is useful the moment it
opens. Users filter and plan; they never supply the data.

The app shows:
- Live internship postings, updated hourly.
- When each posting opened and when it closed (recorded by our own monitoring).
- Deadlines where the source publishes one.
- Filters: NC / remote / national; in person / hybrid / remote; undergrad / grad.
- Full descriptions where the source provides them.
- A planner: save postings and see them as a schedule.
- "Opening soon" predictions from last season's recorded open dates.

## Project parameters (fixed)
1. Free (data, tools, hosting choices).
2. Java.
3. Solves a real problem; at minimum, a real convenience.
4. Real-world data from original (first-party) sources.
5. Live: updates constantly.
6. Works without users entering data.
7. Passes the test "would people actually open it?"

## Data sources
Postings come ONLY from first-party sources:
- **Greenhouse, Lever, Ashby** public job board feeds: each employer's own
  openings (title, location, description, dates, link). No key believed needed.
- **USAJOBS API** (official, free key): federal internships incl. Pathways,
  with real closing dates.
- Later candidate: **NSF REU Sites** (funded undergrad research programmes,
  many at NC universities, real deadlines).

Community lists (e.g. SimplifyJobs Summer2027-Internships on GitHub) are used
ONLY for discovery: their application links reveal which companies use which
hiring system. No posting data is taken from them. Their repo states no
licence; permission question open (see SimplifyJobs issue #9700).

Not used: LinkedIn, Handshake, Indeed, Glassdoor, Nextdoor (no free public API,
terms prohibit scraping). Reddit deferred: low signal, terms risk.

Known coverage gap: employers on Workday (and others without public feeds) are
not visible. The app must say coverage is partial, never imply completeness.

## How it works
Two jobs, both plain code. No AI in the pipeline (models judge, code computes).

**Discovery (builds the watch list)**
1. Harvest application links from community lists (and their git history);
   extract (company, hiring system, board name), e.g.
   `boards.greenhouse.io/acme` -> Acme, Greenhouse, `acme`.
2. Probe a seed list of company names (NC employers + large national ones)
   against each hiring system's board address. Hits are added; misses logged
   with a reason.

**Monitoring (hourly)**
For each watched board, and for USAJOBS:
1. Fetch the feed; save the raw response unchanged (data/raw/).
2. Keep internships (title and other signals); log every rejected posting with
   its reason.
3. Compare with stored state: new -> opened; gone -> closed; changed -> record
   the change.
4. Store in SQLite. The website reads only from the database.

    discovery -> watch list
    hourly: fetch feed -> raw snapshot -> internship filter -> diff vs DB -> SQLite -> site
                                               |
                                               +-> rejections logged with reasons

## Stack
- Java 21, Maven, Java's built-in HTTP client, Jackson for JSON.
- SQLite via JDBC (sqlite-jdbc). JUnit 5.
- Later: Javalin for the web server; plain HTML/CSS/JavaScript front end.
- Runs locally first; hosting decided later (needs persistent disk for SQLite).

## Field availability (what we can honestly show)
| Field | Source | Notes |
|---|---|---|
| Opened date | Our monitoring (first seen) | Feed posting date where provided |
| Closed date | Our monitoring (disappeared) | Only from when we start recording |
| Deadline | USAJOBS, REU | Most company internships are rolling |
| Undergrad / grad | Title, description text | Label uncertain cases as uncertain |
| In person / hybrid / remote | Location field, text | Only when stated |
| NC / national | Location field | Normalise messy location text |
| Description | Greenhouse, Lever, Ashby, USAJOBS | Fetched from source |

## To measure in the spike (do not assume)
- Exact endpoints and fields for Greenhouse, Lever, Ashby board feeds; whether
  descriptions and posting dates are included; rate limits or terms.
- USAJOBS: key/registration requirements (believed: key plus an email in the
  User-Agent), fields, NC and internship filters.
- From the Simplify list: how many application links point to Greenhouse,
  Lever, Ashby, Workday, or other; i.e. how much a first-party pipeline covers.
- How many NC internships those boards actually contain right now.
- Typical feed sizes and response times (sets a polite polling rate).

## Open questions (for grill-me)
1. Scope of roles: CS/tech only, or all majors?
2. Which companies: NC criteria, and what makes a national company "large"
   enough to include?
3. Internship detection rules: intern, co-op, summer, part-time? New-grad roles
   excluded? How do we check the filter isn't missing real internships?
4. Undergrad vs grad classification and how uncertainty is labelled.
5. Remote / hybrid / in-person classification from messy location text.
6. Closure: a posting missing from one fetch could be a glitch. How many
   consecutive misses before "closed"? (named constant)
7. Duplicates: the same internship in two sources or two locations.
8. Deadlines: shown only where published; how rolling postings are labelled.
9. Planner: what users can do (save, status, notes?) and where it's stored
   (browser only vs accounts).
10. "Opening soon": what history counts, and minimum evidence before predicting.
11. Discovery sources in v1, and how we measure companies we're missing.
12. Polling politeness: rate limits, identifying User-Agent, backoff on errors.
13. REU in v1 or later.
14. Permission for using community lists as a discovery index.

## Milestones
1. **Spike:** verify each feed's endpoints and fields with real requests; save
   raw responses; harvest and count links by hiring system; count NC internships.
2. **Second grill** on the spike's real output.
3. **Discovery:** build the watch list from links and name probing; log misses.
4. **Monitoring:** hourly fetch, internship filter, diff, opened/closed history,
   SQLite. Replayable from raw snapshots.
5. **Terminal commands:** e.g. `new`, `closing`, `search --nc --remote`, to
   check results before any UI.
6. **USAJOBS** adapter into the same common record.
7. **UI:** list, filters, posting detail, planner.
8. **Opening-soon predictions** once history exists.
9. **Hosting** so it is a live website.

## Verification
- Tests replay saved raw responses, never live calls.
- Every test is broken on purpose once to confirm it fails.
- The internship filter is checked against a hand-labelled sample of real
  postings (kept, and wrongly rejected), and re-checked after every rule change.
- Rejections and discovery misses are always logged with reasons.
- Dev log (docs/devlog.md): one entry per significant defect: what broke, the
  first diagnosis, and the measurement that settled it.

## Decisions log
- Project: NC-focused live internship tracker and planner (replaced gas prices,
  research finder, and power grid ideas).
- Data: first-party sources only (employer hiring feeds, USAJOBS). Community
  lists used only to discover companies.
- No AI in the pipeline.
- Language: Java 21, Maven. Storage: SQLite.
- Build order: terminal first; UI after results are right. Local first.