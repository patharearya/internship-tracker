# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Static HTML/CSS/JS on GitHub Pages, no framework and no build step (docs/brief.md). The page reads the
JSON files the hourly Java job commits to `data/`. Deployed through GitHub Actions with only the page and
`data/` published.

## Users

US college students of any major looking for internships and funded research programmes. They browse; they
never enter data. The primary job (owner, 2026-10-06): browse everything open for their own major, narrowed
by filters, then open and save the postings worth applying to.

## Product Purpose

Internship & Research Finder gathers internship postings and funded undergraduate research programmes from
employers' own job boards and federal sources into one place, grouped by major, with when each posting was
first seen. Success: a student opens it, picks their major, and finds real open postings they did not know
about.

## Positioning

Postings come only from first-party sources (employers' Greenhouse, Lever, Ashby and Workday boards,
USAJOBS, NSF REU awards, NIH R25 awards), never aggregators, and the site's own monitoring records when each
posting first appeared and when it closed.

## Operating Context

Updated at least daily, often several times a day (GitHub schedules fire irregularly); the page must not
promise hourly updates. Students may come back repeatedly during application season.

## Capabilities and Constraints

- ~9,400 open postings. `data/postings.json` is ~11 MB (0.5 MB gzipped). Descriptions live in
  `data/descriptions/{n}.json`, 64 shards, loaded only when a posting is opened; `n` is Java
  `String.hashCode` of the posting key, `floorMod 64` (`Main.shard`).
- Each posting: title, organisation, URL, locations, posted date, deadline when published, first seen,
  closed; labels for major (12 fields plus Other), level (undergrad / grad / both / not stated),
  arrangement (in person / hybrid / remote / not stated), US states and remote-US.
- Filters: state or remote, arrangement, level. "Not stated" values appear under every filter.
- Saved list kept in browser storage (star).
- Deadlines shown where published, otherwise "No deadline published", never "Rolling".
- Other is always shown as its own group (owner, 2026-10-06).
- One honest coverage sentence: postings come from N employers' public job boards, federal jobs and funded
  research programmes, not every internship. Some majors are thin (Education ~44, Social Sciences ~40
  open) and the site says so.

## Brand Commitments

Name: Internship & Research Finder. The owner does not want a generic, AI-looking website; it should look
genuinely designed.

## Evidence on Hand

Real data only, from `data/`. No testimonials, user counts, partner logos or success stories exist; none
may be invented.

## Product Principles

1. The data is the product: every posting shown is real, current and links to the employer's own page.
2. Honest about coverage, gaps and freshness; never overstate.
3. Useful the moment it opens: no sign-up, no input required.
4. Fast with thousands of postings, on a phone as well as a laptop.
