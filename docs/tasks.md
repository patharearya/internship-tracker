# Task list

Last session: 2026-10-04. Plan and decisions: [brief.md](brief.md). Defects
and how they were settled: [devlog.md](devlog.md).

## Where we left off

- **Day 1 done:** adapters for Greenhouse, Lever, Ashby, Workday, USAJOBS, NSF
  REU awards, NIH R25 into one `Posting` record; replay tests on saved raw
  responses.
- **Day 2 done (code):** discovery (2,188 boards from the Simplify list),
  fetching with politeness rules, internship filter, major / level /
  arrangement / state rules, open-closed comparison, hourly GitHub Actions
  workflow. 26 tests; every rule and merge step broken on purpose once.
- **First full live run:** 3,396 open postings in 342 s. Workday was cut
  short by one tenant's 403; now fixed (pause per server, grill Q22) and
  Workday runs every 3rd hour (~95 min for all of it).
- **The hourly workflow is pushed but fails** until the GitHub secrets exist.

## Next session, in order

1. **Set GitHub secrets** `CONTACT_EMAIL` and `USAJOBS_API_KEY` (repo Settings
   -> Secrets -> Actions, or `gh secret set` from `.env`). Then trigger the
   workflow by hand (Actions -> update -> Run workflow) and check the first
   run: report.json, committed data, rejections artifact.
2. **Check the rules against real postings (hand-labelled sample).** Pull
   ~100 kept and ~100 rejected titles across all sources; label them; count
   wrongly kept / wrongly rejected per rule. Fix rules, re-run the sample.
   - Review the 401 postings labelled **Other** (12%): which major rules miss.
   - Settle the open titles: "part-time student worker", "Summer 2027
     Software Engineer" (no "intern"), "Graduate Student Research Assistant",
     "AI Residency".
3. **Watch the first complete Workday pass** under the per-server pause:
   time it, list paused servers, look at the two Activision 422s (site
   names).
4. **Day 3: the page** (GitHub Pages). Postings grouped by major, filters
   (state / remote, arrangement, level), posting detail, saved list
   (browser storage), coverage sentence. Decide first:
   - Pages source: repo root or `/docs`.
   - Size: `postings.json` is 4 MB and `descriptions.json` 14 MB; load
     descriptions only when a posting is opened.
5. **Thin majors:** Education (19) and Social Sciences (23) are thin. If they
   stay thin after the full Workday pass, hand-add employers for them (Q21).

## Small items

- Tune the "sudden drop" constants (`DROP_CHECK_MIN`, `DROP_RATIO`) once a
  few days of runs are logged.
- No test covers the leading word boundary on foreign country names (no real
  US place name found that would exercise it).
- `state.json` was hand-edited to clear the stale Workday pause; the next run
  rewrites it in the normal format.
