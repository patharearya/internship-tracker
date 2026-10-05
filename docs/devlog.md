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

## 2026-10-04 — a queued run computed on stale data

**What broke:** the 15:48 scheduled run failed after 49 minutes with a
rebase conflict on `data/` when pushing.

**First diagnosis:** two runs writing `data/` at once, i.e. the `concurrency`
group not working.

**What settled it:** the run log. Concurrency worked: the run waited in the
queue until the manual run finished at 16:33. But `actions/checkout` checks
out the commit that triggered the run (`318ffa1`), not the branch head, so it
compared against the previous `postings.json` and then collided with the
manual run's data commit. A clean rebase would have been worse: results from
stale data published silently. Every Workday run (81 min) queues the next
hourly run, so this was going to recur every third hour. Fix: check out
`ref: main`, read when the queued job actually starts.

## 2026-10-04 — the labelled sample: first reading of the labels was backwards

**What broke:** the first scoring of the 197 hand labels said 96 of 97
rejected postings were wrongly rejected, including "Vice President,
Operations", "Dean, College of Aeronautics" and "Plumber (South West)".

**First diagnosis:** none adopted; it looked like the labelling page (wrong
row saved, or keyboard shortcut hitting the wrong row).

**What settled it:** the saved answers' timestamps and the owner. Answers
were in page order, one write each, the rejected rows at 0.6-1.7 s per row
against 4.6 s on kept rows. Asked directly: Yes meant "the rule was right",
not "belongs in the app". The page showed "Rule: rejected" under each title
and never said which question Yes answered. Read the right way round, the
labels agreed with the rules on all but one rejected row; going through the
fast-labelled rows with the owner found 14 real misses (agreed), in four
rules. Next labelling page asks one unmistakable question per row.

## 2026-10-04 — four filter rules wrong on the labelled sample

**What broke:** 14 of 97 sampled rejections were real student roles:
"Engineering/Manufacturing Co-op_Spring 2027" (no keyword),
"Product Manager, Intern" and "Junior IWMS Project Manager - Intern" (runs
the programme), "Early Career Intern - ETF Product" (not a student role),
"Thermal Associate Engineer (Summer 2027)" (no keyword).

**First diagnosis:** the keyword list too short.

**What settled it:** reading the regexes against each title. Only the last
was a short list. `_` is a word character, so `\bco-?op\b` cannot match
"Co-op_Spring"; fixed for every title rule (type, major, level, arrangement
too), not just the filter. "Manager, Intern" matched the programme-runner
pattern; it now needs "intern program(me)". "Early career" vetoed an explicit
"Intern"; now not when followed by it. A dated term ("Summer 2027") now keeps,
except "starting summer 2027", a full-time start date found by replaying the
change over the whole run. Replay over all 75,220 title rejections and 8,882
kept postings: 77 rejected -> kept (all read, all student roles), 0 kept ->
rejected. `LabelledSampleTest` replays the 197 rows; 14 wrong -> 0; one row
not replayable (USAJOBS eligibility is not in rejections.tsv).

## 2026-10-04 — Workday skipped for 8 hours by dropped scheduled runs

**What broke:** after the filter fix, the open count rose by 25, not the ~77
the replay predicted. All 5,844 open Workday postings were last seen at
15:12Z; the two runs on the new code (19:36Z, 23:08Z) did not fetch Workday.

**First diagnosis:** Workday simply had not come round yet; wait for the next
third hour.

**What settled it:** the run start times. Workday ran only when a run started
in a UTC hour divisible by 3, and GitHub fired 5 of ~13 hourly slots that day
(11:06, 12:09, 15:48, 19:36, 23:08), so no run landed in 18, 21 or 00 and
Workday could wait indefinitely. The 25 were all checked against the 16:33Z
run's rejections.tsv: every one was a rejection the fixed rules now keep,
none brand new; the rest of the 77 are on Workday. Workday now runs when its
last run started 3 h ago (30 min slack), recorded as `workdayStarted` in
`state.json`; `WorkdayDueTest`. No posting was harmed: skipped systems are
not compared, so Workday postings gained no misses.

## 2026-10-05 — rule-fix postings looked missing from the Workday run

**What broke (apparently):** the first Workday run on the new rules
(37244506396) added 90 Workday postings, and matching them against the 16:33Z
run's rejections.tsv found none of the expected rule flips; 39 postings the
fixed rules should keep (e.g. all 23 GE Appliances "Co-op_Spring 2027") were
neither kept nor rejected.

**First diagnosis:** the Workday fetch was returning fewer postings, since the
run took 34 min against 81.

**What settled it:** querying the GE Appliances board directly: the co-ops
were on page 2 of the "intern" search, and in postings.json under
`REQ-24832`-style ids. The analysis was wrong, not the fetch. rejections.tsv
logs a Workday row by its list path (`/job/.../..._REQ-24832`) and a kept
Workday posting carries the requisition id from the detail call, so joining
rejections to postings by id finds nothing for Workday. Matched by board and
title: 44 of the 90 were rule flips, 46 new; with the 25 Greenhouse/Ashby
flips, 69 of the 77, plus 6 now rejected as outside US. The shorter run is
the detail cache: known postings skip the detail call.

## 2026-10-05 — the page-1 major labels were the pre-fill

**What broke:** 16 of the 30 sampled "Other" postings had been labelled
"Other" on the first labelling page, which pre-filled the rule's answer.

**First diagnosis:** that the owner judged them Other.

**What settled it:** asking again blind (second page: one question, nothing
pre-filled, rule's answer hidden): 0 of the 16 stayed Other. An unchanged
pre-fill and an agreement look identical in the saved answer. Three more
pre-filled labels in "kept: random" (i001 EHS, i016 FP&A, i020, i050
"Biofilms" -> Arts from the film-in-Biofilms match) were replaced when the new
rules disagreed with them. Other pre-filled labels that happen to agree with
the rules are still in the sample, unverified.

## 2026-10-05 — NSF computing awards never mapped to a major

**What broke:** 42 open NSF REU awards were "Other".

**First diagnosis:** titles too vague for the keyword rules.

**What settled it:** counting NSF categories in postings.json. The API's
`dirAbbr` for the computing directorate is `CSE`; the map had `CISE`, which
never occurs. Present since the first run.

## 2026-10-05 — a comment silently dropped three NSF directorates

**What broke:** replaying the new major rules moved 51 Engineering and 51
Physical Sciences postings to Other.

**First diagnosis:** adding keywords cannot remove a major, so the replay
looked wrong.

**What settled it:** the 102 were all NSF awards with "no rule matched". The
`CSE` fix put a `//` comment mid-line inside `Map.of(...)`, commenting out
the ENG, TIP and MPS entries; it compiled. `RulesTest` already covered ENG
and would have failed, but only `LabelledSampleTest` had been run. Now
`RulesTest` checks every directorate seen in the data.

## 2026-10-05 — generic words as ordinary rules took real subjects

**What broke:** replay over all 8,995 open postings, read move by move:
"Air Quality" and "Water quality" REUs -> Engineering ("quality"),
"Structural ... Biology" -> Engineering, an astrophysics REU at the American
Museum of Natural History -> Arts ("museum"), the CURATE addiction programme
-> Arts ("curat"), "Sports Medicine" -> Marketing, "Abbott Nutrition
Consumer Sales" -> Life Sciences.

**First diagnosis:** rule order; move the words later in the list.

**What settled it:** later in the list still beats the NSF directorate and
the board's department, which are better evidence than a generic word. These
words (quality, structural, construction, analytical, quantitative, sports,
curat, museum, technology) are now `WEAK_RULES`, tried only after the title's
subject words and the category. Same pass: Finance's `\bquant` matched
"Quantum" (9 quantum-computing postings were Finance); now `\bquants?\b`,
with "quantitative" weak and "quantum" Physical Sciences.

## 2026-10-05 — employer-name fallback: 22 decisions that were 171

**What broke:** the owner chose title-then-employer. I measured the employer
fallback at 22 decisions, about half wrong, and on that the owner dropped it.

**First diagnosis:** none at the time; 22 was taken as the count.

**What settled it:** the final Other count came out 162 higher than predicted.
The 22 came from grepping the replay's printed examples, capped at 6 per
group, not from every posting. Measured properly (fallback re-added
temporarily, every decision printed): 171 postings, about 75 right (The
Aerospace Corporation, Vertex, Regeneron, Starr Insurance), about 80 wrong
(Fidelity "Investor Center" x29 -> Finance where the owner labelled one
Business, Clearwater Analytics client service -> Data, St. Luke's University
Health Network radiology -> CS via "Network"), the rest arguable. Still
dropped; owner told the real count.

## 2026-10-05 — deliberate breaks that did not break

**What broke:** after the rule changes, two of three deliberate breaks left
every test green.

**First diagnosis:** the tests could not fail.

**What settled it:** the breaks never applied. The shell collapses `\\` to
`\` before a command runs, so `\\b` typed into sed or an inline Python string
became a backspace character in the Java source: a pattern that compiles and
never matches. The same slip had already broken three anchors in Rules.java
(caught by the "Thermofluids" row). A Python runner then called `./mvnw`
through cmd.exe, which ran nothing and reported 0 failures. Applied from a
script file and run through bash, all three breaks go red in two tests each.

## 2026-10-05 — one posting in five had no state

**What broke:** 1,931 of 8,995 open postings (21%) had no state and were not
remote-US, so any state filter would hide them. 45 of them were abroad
(Auckland, Stuttgart, Belgrade, Calgary, Surrey BC, Kuala Lumpur) and should
have been rejected as outside US.

**First diagnosis:** Workday's free-text locations ("Office - Boise",
"Carmel Headquarters") that no rule can read.

**What settled it:** counting the state-less locations. Most were readable:
bare major cities ("San Francisco" x155, Chicago, Boston, Austin) and state
codes in positions the code pattern did not accept ("TX-Dallas", "WI
Madison", "Atlanta GA", "US.CO.Denver", "MN-Mankato; WI-Baldwin"). Added
those positions as separate patterns (the old pattern's matches cannot
change), a table of US cities that name one state (ambiguous names such as
Portland, Columbus, Rochester left out), and the foreign cities seen. Replay:
1,931 -> 408 state-less; every existing state kept except about 60 wrong
KS. Same shape found in passing: the state name "Kansas" matched inside
"Kansas City", so Kansas City, Missouri postings were listed under Kansas.
Labelled row i055 (three German cities, page-1 "keep") was relabelled: the
brief rejects non-US-only roles.

## 2026-10-05 — 39 Workday boards failing, three different causes

**What broke:** 37 Workday boards answered HTTP 422 and 2 answered 404 on
every run (Activision, Netflix, Comcast, Lilly, Takeda, Intel...).

**First diagnosis:** one shared cause, likely a request shape some tenants
reject.

**What settled it:** probing each board by hand. A wrong site name gives 404
with "not found: Job_Posting_Site_ID", so 422 is not a bad site. Three
causes:
1. Hyphenated tenants (osv-chegg, vhr-otsuka, sallie-mae; 12 boards): the
   page config names the tenant `osv_chegg`; the API path needs the
   underscore. Fixed in discovery.
2. Tenants whose site page redirects to Workday's maintenance page (28):
   moved hosts or left Workday. Trying other hosts found 8 (Cambia, CFF,
   Insmed, Otis, Plexus, Symbotic on wd504, Netflix on wd108, Takeda on
   wd502), now in a hand table. 16 answer nowhere (Activision, Comcast,
   Lilly, IDEXX...) and stay failing at one request a day.
3. A language segment read as the site: the pattern accepted only "en-US",
   so "en-us" (Intel) and "en" (Ticketmaster) became sites. 14 working
   boards have real two-letter sites (Transamerica "us", J&J "JJ"), so a
   bare two-letter segment counts as a language only when a site and then
   job/details follow.
Every new or changed board address was called once: 23 of 24 answer 200.
The 50 Greenhouse/Lever/Ashby 404s are boards that moved to systems we have
no link for: none of their names exists on the other two systems; 3
employers already have a working board under another name.

## 2026-10-05 — postings on a dropped board could never close

**What broke:** found while diffing a rebuilt board list against the
committed one: Simplify had deleted rows for 9 boards since the last
discovery, and 3 of those boards had 8 open postings (6 at Sereact).

**First diagnosis:** none needed; the merge code shows it. Misses are counted
only for boards fetched successfully, so a board that leaves the list keeps
its postings open forever.

**What settled it:** `keepBoardsWithOpenPostings`: discovery keeps a dropped
board while it has open postings, so they close normally. Boards with
nothing open still drop out. A board that starts failing for good has the
same problem (failed fetches count no misses); 0 open postings sit on failing
boards today, so it is a task, not a fix.
