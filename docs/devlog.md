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
