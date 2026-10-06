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
  (`workdayStarted` in `state.json`), about 37 min. Every run relabels every
  open posting with the current rules.
- **State after the 2026-10-05 02:47Z run (Workday) and 02:51Z run
  (discovery):** 9,056 open postings; 2,193 boards (15 hand-added); Other
  1,197 (13%); 411 without a state; level both 2,538 / undergrad 3,760 /
  grad 587 / unknown 2,171; Education 42, Social Sciences 39. 34 tests.

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

## Next session, in order

1. **Check the first Workday run with the paging fix:** run time (was ~37
   min, timeout 120), open postings gained, any board newly at
   `MAX_WORKDAY_PAGES`, no sudden-drop anomalies, `lastCount` 0 count.
2. **Day 3: the page** (on hold by the owner). Postings grouped by major,
   filters (state / remote, arrangement, level), posting detail, saved list
   (browser storage), coverage sentence. Must not promise "hourly" (Q24).
   Decide first:
   - Pages source: recommended, deploy only the page and data through
     Actions, so `/docs` stays private.
   - Size: `postings.json` is 11 MB (0.5 MB gzipped). Descriptions are in
     `data/descriptions/{n}.json`, 64 files; load one when a posting is
     opened, `n` = Java `String.hashCode` of the key, non-negative mod 64
     (formula in `Main.shard`, pinned by `ShardTest`).
3. **Thin majors, January:** recheck Education and Social Sciences when
   summer internships in those fields are posted (Pew CenterInternships is
   the seasonal one). More Workday tenants can be found by probing hosts for
   "not found: Job_Posting_Site_ID" (devlog).

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
- Closed postings are dropped and failing boards' postings closed (owner,
  2026-10-06). 14 and 7 days are defaults: 27 postings reopened in the first
  two days, and dropping at once would show each as new.

## Small items

- The ~1,200 Other are mostly titles that name no field ("2027 Summer
  Intern"). 31 sampled rows were settled only by the description; a
  description-based rule or a hand-built employer -> field list are the next
  levers, only if Other is a problem on the page.
- 297 without a state (replay, 2026-10-06): 39 with no location, 19 "United
  States", SpaceX "any site", and short tails of ambiguous cities. Add to the
  city table only with a count behind it.
- Page-1 labels in "kept: random" that agree with the rules may still be
  unverified pre-fills (devlog 2026-10-05).
- 16 Workday tenants answer on no host (Activision, Comcast, Lilly, IDEXX...)
  and 47 Greenhouse/Lever/Ashby boards are gone; stale entries such as
  `intel.../en-us` stay in `state.json` failures. One request a day each;
  prune if the list gets in the way.
- Working on this repo from this machine's shell: `\\` in a command reaches
  the program as `\`. Write anything with regex backslashes through a file.
