# Task list

Last session: 2026-10-05. Plan and decisions: [brief.md](brief.md). Defects
and how they were settled: [devlog.md](devlog.md).

## Where we left off

- **Day 1 done:** adapters for Greenhouse, Lever, Ashby, Workday, USAJOBS, NSF
  REU awards, NIH R25 into one `Posting` record; replay tests on saved raw
  responses.
- **Day 2 done (code):** discovery (2,188 boards from the Simplify list),
  fetching with politeness rules, internship filter, major / level /
  arrangement / state rules, open-closed comparison, hourly GitHub Actions
  workflow.
- **Hourly workflow live.** GitHub fires only some scheduled runs (5 of ~13
  on 2026-10-04), so Workday now runs when its last run started 3 h ago
  (`workdayStarted` in `state.json`), not on clock hours. Last Workday run
  (manual, 2026-10-04 23:38Z) took 34 min; 8,995 open postings.
- **This session (2026-10-05):**
  - Workday scheduling fix above; the filter fixes of 2026-10-04 confirmed on
    a real run: 69 of the 77 rule flips kept, 6 more now rejected as outside
    US.
  - Majors relabelled on a second page with nothing pre-filled
    (https://claude.ai/artifact/XSKuirRcRQiYWwkwLHmMSB, 148 rows, answers in
    its `majors` collection). `labelled-sample.json` now 297 rows;
    `majorFrom: description` rows are printed, not scored.
  - Major rules: keywords from the labels, NSF `CSE` directorate (42 awards
    were Other), weak generic words, `\bquant` no longer matches "Quantum",
    SkillBridge rejected. Replay over the 23:38Z run: Other 1,648 -> 1,183
    (18% -> 13%), 466 out of Other, 120 moved between majors (read through),
    28 SkillBridge postings now rejected. The next run applies it.
  - `rejections.tsv` gained locations, country, eligibility; Workday
    rejected rows now carry the requisition id (item 2).
  - Location rules: 1,931 -> 408 postings with no state, 45 foreign postings
    now rejected, Kansas City, MO no longer under Kansas (devlog).
  - Owner: updates at least daily are enough (Q24); level and arrangement
    rules checked by me, not by labels (their numbers fit the design: 78%
    "not stated" arrangement is the bare-city rule working).
  - 32 tests. Every new rule was broken on purpose and goes red.

## Next session, in order

1. **Check the first runs on this session's code:**
   - Major rules: Other near 1,183, no NSF award in Other unless its
     directorate is O/D.
   - Location rules: about 408 open postings with no state (was 1,931), no
     Kansas City, MO posting under KS.
   - Discovery (run with discover on, or the 06:05Z run): boards.json has the
     19 revived Workday boards (underscore tenants, moved hosts, Intel) and
     the 3 hand-added boards; their failures stop in state.json.
   - USAJOBS student hiring path: done, 40 -> 55 open on the 01:41Z run
     (no Education posting among them yet; Social Sciences 2).
   - Every open posting is relabelled each run now: Other near 1,183, no
     state near 408, level "both" near 2,523 on the next run.
   - `rejections.tsv` (run artifact) has the new `locations`, `country`,
     `eligibility` columns, and in the first Workday run most Workday rows
     carry a requisition id, not a `/job/...` path. Rows that keep the path
     come from tenants whose first bullet field is not the id; count them
     before deciding whether that matters.
2. **Done (2026-10-05):** `rejections.tsv` logs locations, country and
   USAJOBS eligibility; Workday search results use the requisition id when
   the posting path confirms it (`Fetch.listId`), so rejected and kept
   Workday rows join by id. `RejectionLogTest`.
3. **Day 3: the page** (GitHub Pages). Postings grouped by major, filters
   (state / remote, arrangement, level), posting detail, saved list
   (browser storage), coverage sentence. Decide first:
   - Pages source: repo root or `/docs`.
   - Size: `postings.json` is 11 MB (0.5 MB gzipped). Descriptions are in
     `data/descriptions/{n}.json`, 64 files; load one when a posting is
     opened, `n` = Java `String.hashCode` of the key, non-negative mod 64
     (formula in `Main.shard`, pinned by `ShardTest`).
4. **Daily discovery** first ran on schedule 2026-10-05 06:05Z: check it
   rebuilt `boards.json`.
5. **Thin majors:** Education 42, Social Sciences 38 after today's rules.
   Done now (2026-10-05): USAJOBS student hiring path; 15 hand-added boards
   (`from: hand` in Discover.HAND): Brookings, Morning Consult, Khan Academy,
   RAND, Pew Research Center (incl. its internship site), Teach For America,
   City Year, Success Academy, Uncommon Schools, College Board, Scholastic,
   Cengage, Wiley, Curriculum Associates. Few have internships up in October.
   Recheck in January, when these fields post summer internships. More
   Workday tenants can be found the same way (devlog 2026-10-05).

## Settled

- "Summer 2027 ..." titles are kept (a dated term); "starting summer 2027"
  is a full-time start date and is not.
- Student jobs (work-study, student ambassador, "Graduate Student Research
  Assistant", "Working Student") are out: open only to that university's
  students (owner default, 2026-10-04).
- "Research Analyst (Economics) - July 2027" stays rejected: a full-time job.
- Majors follow the work, not the employer (Q23). SkillBridge is rejected.
- "AI Residency" stays rejected: a research job (Q25).

## Small items

- The remaining ~1,180 Other are mostly titles that name no field ("2027
  Summer Intern", "Starr Summer Intern"). 31 sampled rows were settled only
  by the description; a description-based rule is the next lever if Other
  must shrink further.
- A hand-built employer -> field list would fix some of them without the
  employer-name guessing (Aerospace Corp, Vertex, Regeneron, Starr were the
  employers the dropped fallback got right). Only if Other is a problem on
  the page.
- Page-1 labels in "kept: random" that agree with the rules may still be
  unverified pre-fills (devlog 2026-10-05).
- 16 Workday tenants answer on no host (Activision, Comcast, Lilly, IDEXX...)
  and 47 Greenhouse/Lever/Ashby boards are gone (devlog 2026-10-05). One
  request a day each; prune if the failure list gets in the way.
- A board that fails for good keeps its open postings open (failed fetches
  count no misses). 0 such postings today; decide a rule (e.g. close after
  N days of 404) before it happens.
- Pause times in `state.json` record the run start, not when the 403 came.
- `postings.json` only grows (closed entries kept); ~80k entries before
  100 MB. Decide a retention rule for closed postings before then.
- Tune the "sudden drop" constants (`DROP_CHECK_MIN`, `DROP_RATIO`) once a
  few days of runs are logged.
- No test covers the leading word boundary on foreign country names (no real
  US place name found that would exercise it).
- Still no state (408): mostly employer site names ("Carmel Headquarters",
  "UT MAIN CAMPUS", "Stamford Hub") and ambiguous cities. Add to the city
  table only with a count behind it.
- `ubuntu-latest` moves to Ubuntu 26 from 2026-10-19 (Actions annotation);
  watch the first run after that.
- Working on this repo from this machine's shell: `\\` in a command reaches
  the program as `\`. Write anything with regex backslashes through a file.
