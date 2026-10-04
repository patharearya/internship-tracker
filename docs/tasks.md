# Task list

Last session: 2026-10-04 (second session that day). Plan and decisions:
[brief.md](brief.md). Defects and how they were settled: [devlog.md](devlog.md).

## Where we left off

- **Day 1 done:** adapters for Greenhouse, Lever, Ashby, Workday, USAJOBS, NSF
  REU awards, NIH R25 into one `Posting` record; replay tests on saved raw
  responses.
- **Day 2 done (code):** discovery (2,188 boards from the Simplify list),
  fetching with politeness rules, internship filter, major / level /
  arrangement / state rules, open-closed comparison, hourly GitHub Actions
  workflow.
- **Hourly workflow live** (secrets set 2026-10-04). First complete run
  (manual, 16:33Z): 8,882 open postings, 81 min, nearly all of it Workday.
- **This session:**
  - Descriptions split into `data/descriptions/{0..63}.json`: one file was
    43 MB and heading for GitHub's 100 MB limit.
  - Workflow checks out `main`, not the triggering commit: a run queued
    behind the 81-min Workday run had computed on stale data and failed on
    push.
  - Filter checked against 197 hand-labelled postings (labelling page:
    https://claude.ai/artifact/YGvMeM3xcQs3T8aZ4JqQLL). Four rules fixed
    (underscore in titles, "Manager, Intern", "Early Career Intern", "Summer
    2027" titles); 14 wrong -> 0 on the sample; across the whole run 77
    rejected -> kept, 0 kept -> rejected. `LabelledSampleTest` replays the
    sample on every test run. 28 tests.

## Next session, in order

1. **Check the first Workday run on the `workdayStarted` code** (third
   session, 2026-10-04): it should run on the first scheduled run after the
   push, and the Workday open count should rise by most of the ~52 remaining
   newly kept postings. Already confirmed: 64 description files committed, no
   stale-checkout conflict, the 25 non-Workday postings all rule flips (devlog).
2. **Majors.** "Other" is 1,659 of 8,882 (19%). The owner was unsure of the
   major on 18 sampled rows (14 in Other). Sample Other titles, label the
   major only, add missing major rules, extend `labelled-sample.json` and
   re-run `LabelledSampleTest`. Labelling page lesson: one unmistakable
   question per row (devlog 2026-10-04: "Yes" was read as "the rule was
   right").
3. **Log location and eligibility in `rejections.tsv`.** Without them,
   "outside US" and USAJOBS-eligibility rejections cannot be sampled or
   replayed (sample row i196 is unreplayable for this reason).
4. **Day 3: the page** (GitHub Pages). Postings grouped by major, filters
   (state / remote, arrangement, level), posting detail, saved list
   (browser storage), coverage sentence. Decide first:
   - Pages source: repo root or `/docs`.
   - Size: `postings.json` is 11 MB (0.5 MB gzipped). Descriptions are in
     `data/descriptions/{n}.json`, 64 files; load one when a posting is
     opened, `n` = Java `String.hashCode` of the key, non-negative mod 64
     (formula in `Main.shard`, pinned by `ShardTest`).
5. **Thin majors:** Education (36) and Social Sciences (36). Revisit after
   the major rules (item 2); if still thin, hand-add employers (Q21).

## Settled this session

- "Summer 2027 ..." titles are kept (a dated term); "starting summer 2027"
  is a full-time start date and is not.
- Student jobs (work-study, student ambassador, "Graduate Student Research
  Assistant", "Working Student") are out: open only to that university's
  students (owner default, 2026-10-04).
- "Research Analyst (Economics) - July 2027" stays rejected: a full-time job.
- Still open: "AI Residency".

## Small items

- 37 Workday boards answer HTTP 422 (Activision, Netflix, Comcast, Lilly,
  Takeda...), likely one shared cause. 49 Greenhouse/Lever/Ashby boards 404
  (moved systems?).
- Pause times in `state.json` record the run start, not when the 403 came.
- `postings.json` only grows (closed entries kept); ~80k entries before
  100 MB. Decide a retention rule for closed postings before then.
- Tune the "sudden drop" constants (`DROP_CHECK_MIN`, `DROP_RATIO`) once a
  few days of runs are logged.
- No test covers the leading word boundary on foreign country names (no real
  US place name found that would exercise it).
- `ubuntu-latest` moves to Ubuntu 26 from 2026-10-19 (Actions annotation);
  watch the first run after that.
