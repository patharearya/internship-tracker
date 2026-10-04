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
