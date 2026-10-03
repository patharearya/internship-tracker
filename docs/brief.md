# NC Power Grid — Project Brief

## Problem
People can't see when their electricity is cleanest or when the grid is under
strain. The data is public (EIA) but raw and hard to read. This app turns it
into a plain answer: how clean and how strained NC's grid is now, and which
hours are usually cleanest.

## User
Anyone in North Carolina. Browse-only: the app works the moment it opens, with
no input beyond choosing a region. No user-supplied data, no crowdsourcing.

## Decisions log
- Project: NC power grid dashboard (replaced gas prices and research finder:
  no free per-station gas prices; research finder not what we wanted).
- Data: free public data only. Source: EIA API v2 (free key).
- Main answer: cleanest hours as the headline, grid strain alongside.
  Electricity cost is out of v1.
- Regions: NC balancing authorities only (Duke Energy's). Exact codes come
  from the data, not memory.
- Carbon: computed in our code from hourly fuel mix x emission factors kept
  in a file. Not EIA's spreadsheets.
- History: backfill about a year on first run, then poll hourly.
- Language: Java 21, Maven. SQLite for storage. JUnit for tests.
- Build order: terminal first in VS Code; UI only after terminal output is right.
- Runs locally for v1; hosting decided later.

## Known limits (the app must say these plainly)
- EIA has a demand forecast but no fuel-mix forecast. "Best hours today" means
  "typically cleanest hours", from history, never presented as a forecast.
- Data is hourly and delayed. Every output shows "data as of <hour>".
- No capacity figure in hourly data, so strain must be defined relative to the
  region's own recent demand.

## Data source
EIA API v2 (https://www.eia.gov/opendata/):
- electricity/rto/fuel-type-data: hourly generation by fuel, per region.
- electricity/rto/region-data: hourly demand, demand forecast, net generation,
  interchange.
- Values arrive as strings (since Jan 2024) and must be parsed explicitly.
- Hourly data is offered in UTC and local time; pick one deliberately.
- EIA echoes the API key in responses: saved files must have it removed.

## To measure in the spike (do not assume)
- Which region codes cover NC.
- How many hours behind "now" the data runs, per region and per fuel.
- Which fuel types appear, and how often hours are missing or values empty.
- Which fields actually come back.

## Open questions (for grill-me)
1. Grid strain: exact definition (e.g. percentile of trailing N days of demand?)
   and how it's labelled (low / normal / high?).
2. "Typical cleanest hours": grouped by month, weekday vs weekend, season?
   How many weeks of history before a pattern is trusted?
3. Emission factors: which published source, and per which fuels?
   What about imports from neighbouring regions and "other" fuel?
4. Missing or late data: skip the hour, show a gap, or carry forward?
   When is data too stale to show at all?
5. Time zone: store UTC and display Eastern? Daylight saving edge cases.
6. Terminal commands: what exactly do `now` and `best` print?
7. Raw snapshots: commit them to git or keep them local?
8. Polling: what runs the hourly job locally (cron / Task Scheduler)?
9. Anything else the interview uncovers.

## Milestones
1. Spike: list regions, pull 7 days for NC, save raw, print a data summary.
2. Second grill on the spike's real output.
3. Ingest: clean, reject-with-reason, load SQLite; replayable from raw files.
4. Compute: carbon intensity, strain, typical-hour profiles.
5. Terminal report: `now` and `best`.
6. Hourly polling.
7. UI.

## Verification
- Tests replay saved raw responses, not live calls.
- Every test is broken on purpose once to confirm it fails.
- Dev log (docs/devlog.md): one entry per significant defect, including the
  first diagnosis.