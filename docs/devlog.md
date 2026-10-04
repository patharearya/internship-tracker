# Dev log

One entry per significant defect: what broke, the first diagnosis, and the
measurement that settled it.

## 2026-10-04 — REU programme-link rate overstated in the spike

**What broke:** the spike reported that 5 of 25 sampled NSF REU abstracts
contain a programme URL, and that number went into the brief.

**First diagnosis:** none at the time; the count was taken as correct. It
came from a regex that matched any `http(s)://` or `www.` in the abstract.

**What settled it:** listing every match while writing the adapter's test
values. Two of the five were only `https://etap.nsf.gov`, NSF's generic
application portal, which many abstracts name. Real programme pages: 3 of 25.
The instrument (the regex) was measuring "mentions a URL", not "links to the
programme". The adapter now skips ETAP links and falls back to the NSF award
page.

## 2026-10-04 — NSF paging returns a different ~96% of awards on each pass

**What broke:** the first live run published 708 NSF awards; NSF reported
724 and only 1 was rejected. Because closure counts consecutive misses, awards
skipped at random would eventually be closed by mistake.

**First diagnosis:** awards lost in our own code: deduplication in `merge`,
or an off-by-one in the 1-based `offset`.

**What settled it:** paging the same query four times with only `printFields=id`
and counting. Each pass returned 693–704 unique of 724, with duplicates across
pages (28 in one pass) standing in for skipped awards; the union grew 693 ->
704 -> 715 -> 723. The API's ordering is unstable between page requests, so
the defect is upstream, not in `merge`. Splitting by state was worse (672).
Fix: NSF closes after 24 consecutive misses instead of 3; entries carry
over, so the published set fills in across runs.

## 2026-10-04 — one employer's 403 paused all 1,158 Workday boards

**What broke:** the first full run skipped 1,090 Workday boards. One tenant
(`amplify.wd1.myworkdayjobs.com`) answered 403, and the rule "a refusal
pauses the whole hiring system" (grill Q9) applied it to every Workday
employer, persisted in `state.json`, so every later run would skip them too.

**First diagnosis:** none needed; the run report listed 1,090 "skipped:
system paused" lines. The cause was the rule's premise: one system = one
server. True for Lever, Greenhouse and Ashby; false for Workday, where every
employer is its own host.

**What settled it:** a test with a fake fetcher refusing one tenant
(`PauseTest`): pausing by system skips the other tenant's board, pausing by
host does not. Fix: pause per host (grill Q22); stale pause cleared.
Also fixed from the same run: Ashby board names with spaces (6 boards, URL
not encoded) and Greenhouse EU boards (10, `boards-api.eu.greenhouse.io` does
not resolve; EU boards are served by the main API host).

## 2026-10-04 — USAJOBS "25/25 are internships" was the first page only

**What broke:** the spike recorded that `Keyword=intern` results are all
internships. The first full run rejected 592 of 632 for having no internship
keyword in the title.

**First diagnosis:** the title filter missing real internships.

**What settled it:** reading the rejected titles: "Interdisciplinary",
"General Engineer", "Physician (Internal Medicine)". The keyword search
matches "internal" and description text; results are relevance-ordered, so
the 25 sampled in the spike were the best matches, not a fair sample. The
filter was right; the spike sample was biased.

## 2026-10-04 — descriptions.json heading for GitHub's 100 MB file limit

**What broke:** nothing yet. After the first complete Workday pass,
`descriptions.json` was 43 MB for 8,880 postings and only grows (closed
postings keep theirs). GitHub rejects files over 100 MB, which would fail the
hourly push and stop the job.

**First diagnosis:** wasted space: HTML-escaped markup, or descriptions kept
for closed postings.

**What settled it:** measuring the file. No entity escaping, 2.6 MB of tags in
43 MB, no closed postings yet: it is real text, ~5 KB per posting (Workday 30
MB). Fix: split into 64 files by Java `String.hashCode` of the key
(`data/descriptions/{n}.json`, 0.5–0.8 MB each now); the page computes the
same hash. Migration done with the Java code itself: 8,880 in, 8,880 back,
identical; the JS formula finds the right file for all 8,880 keys.
`ShardTest` pins Java to the JS values and went red when the count or the
negative-hash handling was changed.

**Slip on the way:** the first migration produced 32 files, because the
break-on-purpose test had compiled `DESCRIPTION_SHARDS = 32` and the source
was restored without recompiling. The round trip still said "identical",
since writer and reader shared the wrong constant. A round trip through the
same code cannot catch a wrong constant; the file count did.
