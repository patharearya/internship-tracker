// Planner: the postings starred on the browse page, as a sheet the student edits. Kept in this browser only
// (localStorage "irf-saved" for the stars, "irf-planner" for everything typed here); no build step.
document.documentElement.classList.add("js");

const MAJOR_COLOURS = {
  "Engineering": "#f4a259", "Business & Management": "#e6d27e", "Finance & Accounting": "#4fd1a1",
  "Computer Science & IT": "#6aa9ff", "Life Sciences & Health": "#9bdc6b", "Data & Mathematics": "#b892ff",
  "Marketing & Communications": "#ff8fa3", "Physical Sciences": "#5fd0e8", "Government, Law & Policy": "#d3b58d",
  "Arts, Design & Media": "#ff7a5c", "Education": "#ffd34e", "Social Sciences & Psychology": "#f0a6e8", "Other": "#8d98a4"
};
const LEVEL = { undergrad: "Undergraduate", grad: "Graduate", both: "Undergrad and grad", unknown: "Not stated" };
const ARR = { "in-person": "In person", hybrid: "Hybrid", remote: "Remote", "not stated": "Not stated" };
const STATUS = [["saved", "Saved"], ["preparing", "Preparing"], ["applied", "Applied"], ["interviewing", "Interviewing"], ["offer", "Offer"], ["rejected", "Rejected"]];
const MATERIALS = [["resume", "Résumé"], ["cover", "Cover letter"], ["transcript", "Transcript"], ["references", "References"], ["portfolio", "Portfolio"]];
// apply-by suggestion: a week before a published deadline; with none, two weeks after we first saw the posting,
// since early applicants are seen first
const BEFORE_DEADLINE = 7, AFTER_FIRST_SEEN = 14, DAY = 86400000;

const $ = (s, r = document) => r.querySelector(s);
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const fmt = n => n.toLocaleString("en-US");
const today = new Date().toISOString().slice(0, 10);
const iso = v => v ? String(v).slice(0, 10) : "";                       // "2026-10-04T13:10:26Z" -> "2026-10-04"
const addDays = (d, n) => new Date(Date.parse(d + "T00:00:00Z") + n * DAY).toISOString().slice(0, 10);
const daysBetween = (a, b) => Math.round((Date.parse(b + "T00:00:00Z") - Date.parse(a + "T00:00:00Z")) / DAY);
const short = d => d ? new Date(d + "T00:00:00Z").toLocaleDateString("en-US", { month: "short", day: "numeric", timeZone: "UTC" }) : "";
const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;

// ---------- storage ----------
const read = (k, fallback) => { try { return JSON.parse(localStorage.getItem(k)) ?? fallback; } catch { return fallback; } };
const write = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} };
const plan = read("irf-planner", {});
plan.rows ??= {};
const save = () => { write("irf-planner", plan); queuePush(); };

// ---------- columns: Posting is always shown; the rest are the student's choice ----------
const COLUMNS = [
  { id: "status", label: "Status", on: true, sort: r => STATUS.findIndex(s => s[0] === r.t.status) },
  { id: "applied", label: "Applied", on: true, sort: r => r.t.applied ? 1 : 0 },
  { id: "applyBy", label: "Apply by", on: true, sort: r => applyBy(r) || "9" },
  { id: "deadline", label: "Deadline", on: true, sort: r => r.s.deadline || "9" },
  { id: "notes", label: "Notes", on: true, sort: r => (r.t.notes || "").toLowerCase() || "~" },
  { id: "appliedOn", label: "Date applied", sort: r => r.t.appliedOn || "9" },
  { id: "followUp", label: "Follow up", sort: r => r.t.followUp || "9" },
  { id: "materials", label: "Materials", sort: r => MATERIALS.filter(([k]) => r.t.materials?.[k]).length },
  { id: "contacts", label: "Contacts", sort: r => (r.t.contacts || "").toLowerCase() || "~" },
  { id: "listing", label: "Listing", sort: r => r.closed || (r.gone ? "0" : "9") },
  { id: "daysOpen", label: "Days open", sort: r => daysOpen(r) ?? -1 },
  { id: "firstSeen", label: "First seen", sort: r => r.s.first || "9" },
  { id: "field", label: "Field", sort: r => r.s.major || "" },
  { id: "location", label: "Location", sort: r => where(r) },
  { id: "arrangement", label: "Arrangement", sort: r => r.s.arr || "" },
  { id: "level", label: "Level", sort: r => r.s.level || "" }
];
let shown = new Set(Array.isArray(plan.cols) ? plan.cols : COLUMNS.filter(c => c.on).map(c => c.id));
let sortBy = null, sortDir = 1, view = "table";

// ---------- rows: one per employer + title, like the browse list; tracking kept under the row's first key ----------
let rows = [], entries = [];
function build(list) {
  entries = list;
  const byKey = new Map(entries.map(e => [e.key, e]));
  const groups = new Map();
  for (const key of read("irf-saved", [])) {
    const e = byKey.get(key), keep = plan.rows[key] || {};
    const s = e ? snapshot(e) : keep.snap;   // a posting gone from the data keeps the copy taken when it was starred
    const g = s ? s.org + "\u0000" + s.title : key;
    if (!groups.has(g)) groups.set(g, []);
    groups.get(g).push({ key, e, s });
  }
  rows = [...groups.values()].map(list => {
    list.sort((a, b) => a.key.localeCompare(b.key));
    const id = list[0].key, t = plan.rows[id] ??= {};
    const live = list.filter(x => x.e && !x.e.closed);
    const s = list.find(x => x.s)?.s || { title: "A posting no longer listed", org: "", url: "" };
    if (list[0].s) t.snap = list[0].s;
    t.status ??= "saved"; t.materials ??= {};
    const closedAt = !live.length ? list.map(x => iso(x.e?.closed)).filter(Boolean).sort().at(-1) || "" : "";
    return { id, keys: list.map(x => x.key), s, t, locs: [...new Set(list.flatMap(x => x.s?.locs || []))],
      states: [...new Set(list.flatMap(x => x.s?.states || []))], remote: list.some(x => x.s?.remote),
      closed: closedAt, gone: !live.length && !closedAt };
  });
  save();
}
function snapshot(e) {
  const p = e.posting, l = e.labels;
  return { title: p.title, org: p.org || "", url: p.url, deadline: iso(p.deadline), first: iso(e.firstSeen), posted: iso(p.posted),
    major: l.major.value, locs: p.locations || [], states: l.states || [], remote: !!l.remoteUs, arr: l.arrangement.value, level: l.level.value };
}

// ---------- computed in code, never typed ----------
function suggestedApplyBy(r) {
  if (r.s.deadline) return addDays(r.s.deadline, -BEFORE_DEADLINE) < (r.s.first || today) ? r.s.deadline : addDays(r.s.deadline, -BEFORE_DEADLINE);
  return r.s.first ? addDays(r.s.first, AFTER_FIRST_SEEN) : "";
}
const applyBy = r => r.t.applyBy || suggestedApplyBy(r);
function daysOpen(r) {
  const from = r.s.posted || r.s.first; if (!from) return null;
  return Math.max(0, daysBetween(from, r.closed || today));
}
function where(r) {
  const parts = (r.remote ? ["Remote (US)"] : []).concat(r.states.slice(0, 3));
  if (r.states.length > 3) parts.push(`+${r.states.length - 3} more`);
  return parts.join(", ") || r.locs[0] || "Not stated";
}
// the Rare Mark (DESIGN.md): a deadline in ink within 14 days, as on the browse page; an apply-by date within 7, since
// suggestions sit at most 14 days after first seen and 14 would mark nearly every one
const urgent = (d, days = 7) => d && daysBetween(today, d) <= days;

// ---------- load ----------
fetch("data/postings.json").then(r => { if (!r.ok) throw new Error(r.status); return r.json(); })
  .then(list => { build(list); render(); })
  .catch(() => { build([]); render(); $("[data-sum]").textContent += " The latest posting data could not be loaded, so closed postings may not be marked."; });
fetch("data/report.json").then(r => r.ok ? r.json() : null).then(r => {
  if (r?.started) $("[data-updated]").textContent = `Posting data from the update started ${short(iso(r.started))}, ${r.started.slice(11, 16)} UTC.`;
}).catch(() => {});

// ---------- render ----------
function render() {
  const n = rows.length, c = $("[data-saved-count]");
  c.hidden = !n; c.textContent = n;
  $("[data-tools]").hidden = !n;
  $("[data-sheet-wrap]").hidden = !n || view !== "table";
  $("[data-timeline]").hidden = !n || view !== "timeline";
  const empty = $("[data-empty]"); empty.hidden = !!n;
  if (!n) {
    $("[data-sum]").textContent = "Nothing planned yet.";
    empty.innerHTML = `<p>Star a posting while you browse and it lands here, with a suggested date to apply by and room for your notes.</p><a class="btn primary" href="index.html#browse">Browse internships</a>`;
    return;
  }
  summary(); picks();
  view === "table" ? sheet() : timeline();
  const on = $(view === "table" ? "[data-sheet-wrap]" : "[data-timeline]");
  on.classList.remove("view-in"); void on.offsetWidth; if (!reduce) on.classList.add("view-in");
}

function summary() {
  const applied = rows.filter(r => r.t.applied).length, closed = rows.filter(r => r.closed || r.gone).length;
  const next = rows.filter(r => !r.t.applied && !r.closed && !r.gone).map(applyBy).filter(Boolean).sort()[0];   // overdue first: the most urgent
  $("[data-sum]").innerHTML = [`<b>${fmt(rows.length)}</b> posting${rows.length === 1 ? "" : "s"}`, `<b>${fmt(applied)}</b> applied`,
    next ? (next < today ? `apply-by overdue since <b>${short(next)}</b>` : `next apply-by <b>${short(next)}</b>`) : "", closed ? `<b>${fmt(closed)}</b> closed` : ""].filter(Boolean).map((x, i) => `<span>${i ? '<i aria-hidden="true">·</i> ' : ""}${x}</span>`).join(" ");
}

function picks() {
  $("[data-col-picks]").innerHTML = COLUMNS.map(c =>
    `<button type="button" data-col="${c.id}" aria-pressed="${shown.has(c.id)}"><svg aria-hidden="true"><use href="#i-check"/></svg>${c.label}</button>`).join("");
}
$("[data-col-picks]").addEventListener("click", ev => {
  const b = ev.target.closest("[data-col]"); if (!b) return;
  const id = b.dataset.col;
  shown.has(id) ? shown.delete(id) : shown.add(id);
  plan.cols = COLUMNS.map(c => c.id).filter(x => shown.has(x)); save();
  b.setAttribute("aria-pressed", String(shown.has(id)));
  if (view === "table") sheet(id);
});

function ordered() {
  if (!sortBy) return rows;   // as starred: newest star last is the order the student built
  const key = sortBy === "posting" ? r => (r.s.title || "").toLowerCase() : COLUMNS.find(c => c.id === sortBy).sort;
  return [...rows].sort((a, b) => { const x = key(a), y = key(b); return (x > y ? 1 : x < y ? -1 : 0) * sortDir; });
}

function sheet(added) {
  const cols = COLUMNS.filter(c => shown.has(c.id));
  const head = `<thead><tr><th scope="col" class="c-posting">${sortBtn("posting", "Posting")}</th>${cols.map(c =>
    `<th scope="col" class="c-${c.id}${c.id === added ? " col-in" : ""}">${sortBtn(c.id, c.label)}</th>`).join("")}<th class="c-end"><span class="sr">Remove</span></th></tr></thead>`;
  const body = ordered().map((r, i) => `<tr data-row="${esc(r.id)}" class="${r.closed || r.gone ? "is-closed" : ""}" style="--i:${i % 12}">
    <th scope="row" class="c-posting">${posting(r)}</th>${cols.map(c => `<td class="c-${c.id}${c.id === added ? " col-in" : ""}">${cell(c.id, r)}</td>`).join("")}
    <td class="c-end"><button type="button" class="icon small" data-remove aria-label="Remove ${esc(r.s.title)} from your planner"><svg aria-hidden="true"><use href="#i-x"/></svg></button></td></tr>`).join("");
  const t = $("[data-sheet]");
  t.innerHTML = head + `<tbody>${body}</tbody>`;
  if (!reduce && !added) t.querySelectorAll("tbody tr").forEach(tr => tr.classList.add("arrive"));
}
function sortBtn(id, label) {
  const on = sortBy === id;
  return `<button type="button" class="sort" data-sort="${id}" aria-sort="${on ? (sortDir > 0 ? "ascending" : "descending") : "none"}">${label}<svg aria-hidden="true" class="${on ? (sortDir > 0 ? "up" : "down") : ""}"><use href="#i-sort"/></svg></button>`;
}

function posting(r) {
  const c = MAJOR_COLOURS[r.s.major] || "#8d98a4";
  const mark = r.closed ? `<span class="gone">Closed ${short(r.closed)}</span>` : r.gone ? `<span class="gone words">No longer listed</span>` : "";
  return `<a class="p-title" href="${esc(r.s.url)}" target="_blank" rel="noopener">${esc(r.s.title)}</a>
    <span class="p-org">${esc(r.s.org)}${r.s.major ? `<span class="p-major"><i style="--c:${c}" aria-hidden="true"></i>${esc(r.s.major)}</span>` : ""}</span>${mark}`;
}

function cell(id, r) {
  const t = r.t, lbl = `${esc(r.s.title)}`;
  switch (id) {
    case "status": return `<select class="ed" data-ed="status" aria-label="Status of ${lbl}">${STATUS.map(([v, l]) => `<option value="${v}"${t.status === v ? " selected" : ""}>${l}</option>`).join("")}</select>`;
    case "applied": return `<label class="tick"><input type="checkbox" data-ed="applied"${t.applied ? " checked" : ""}><span class="sr">Applied to ${lbl}</span><svg aria-hidden="true"><use href="#i-check"/></svg></label>`;
    case "applyBy": {
      const d = applyBy(r), own = !!t.applyBy, hot = !t.applied && !r.closed && !r.gone && urgent(d);
      return dateCell("applyBy", d, `Apply by date for ${lbl}${own ? "" : ", suggested"}`, (own ? "" : " suggested") + (hot ? " hot" : ""),
        own ? "Your date" : r.s.deadline ? "Suggested: a week before the deadline" : "Suggested: two weeks after we first saw it");
    }
    case "deadline": return r.s.deadline ? `<span class="mono${!r.closed && !r.gone && urgent(r.s.deadline, 14) && r.s.deadline >= today ? " hot" : ""}">${short(r.s.deadline)}</span>` : `<span class="dim">None published</span>`;
    case "notes": return `<textarea class="ed" data-ed="notes" rows="1" placeholder="Add a note" aria-label="Notes for ${lbl}">${esc(t.notes)}</textarea>`;
    case "appliedOn": return dateCell("appliedOn", t.appliedOn, `Date applied to ${lbl}`);
    case "followUp": return dateCell("followUp", t.followUp, `Follow-up date for ${lbl}`);
    case "materials": return `<span class="mats">${MATERIALS.map(([k, l]) => `<button type="button" data-mat="${k}" aria-pressed="${!!t.materials[k]}" aria-label="${l} ready for ${lbl}"><svg aria-hidden="true"><use href="#i-check"/></svg>${l}</button>`).join("")}</span>`;
    case "contacts": return `<textarea class="ed" data-ed="contacts" rows="1" placeholder="Name, email" aria-label="Contacts for ${lbl}">${esc(t.contacts)}</textarea>`;
    case "listing": return r.closed ? `<span class="mono">Closed ${short(r.closed)}</span>` : r.gone ? `<span>No longer listed</span>` : `<span class="dim">Open</span>`;
    case "daysOpen": { const n = daysOpen(r); return n == null ? `<span class="dim">Unknown</span>` : `<span class="mono">${fmt(n)} day${n === 1 ? "" : "s"}</span>`; }
    case "firstSeen": return r.s.first ? `<span class="mono">${short(r.s.first)}</span>` : `<span class="dim">Unknown</span>`;
    case "field": return r.s.major ? `<span class="p-field"><i style="--c:${MAJOR_COLOURS[r.s.major] || "#8d98a4"}" aria-hidden="true"></i>${esc(r.s.major)}</span>` : "";
    case "location": return `<span>${esc(where(r))}</span>`;
    case "arrangement": return `<span>${esc(ARR[r.s.arr] || r.s.arr || "")}</span>`;
    case "level": return `<span>${esc(LEVEL[r.s.level] || r.s.level || "")}</span>`;
  }
  return "";
}

// a date reads "Oct 18" like every date on the site; the native picker sits over it, so it opens on click or keyboard
function dateCell(f, d, label, cls = "", title = "") {
  return `<label class="datecell${cls}"${title ? ` title="${title}"` : ""}>${d ? `<span>${short(d)}</span>` : `<span class="add">Add date</span>`}` +
    (cls.includes("suggested") && d ? `<small>suggested</small>` : "") +
    `<input type="date" data-ed="${f}" value="${d || ""}" aria-label="${label}"></label>`;
}

// ---------- editing: every change is saved at once ----------
const rowOf = el => rows.find(r => r.id === el.closest("[data-row]")?.dataset.row);
const sheetEl = $("[data-sheet]");
sheetEl.addEventListener("change", ev => {
  const el = ev.target.closest("[data-ed]"), r = el && rowOf(el); if (!r) return;
  const f = el.dataset.ed, t = r.t;
  t.updated = Date.now();
  if (f === "applied") {
    // ticking Applied stamps today and moves the status on, in one motion; unticking undoes only what it did
    t.applied = el.checked;
    if (t.applied) { t.appliedOn ||= today; if (["saved", "preparing"].includes(t.status)) { t.before = t.status; t.status = "applied"; } }
    else { if (t.status === "applied") t.status = t.before || "preparing"; t.appliedOn = ""; delete t.before; }
  } else if (f === "applyBy") {
    t.applyBy = el.value && el.value !== suggestedApplyBy(r) ? el.value : "";   // clearing it, or picking the suggestion, goes back to suggesting
  } else if (f === "status") {
    t.status = el.value;
    if (["applied", "interviewing", "offer", "rejected"].includes(t.status) && !t.applied) { t.applied = true; t.appliedOn ||= today; }
    if (["saved", "preparing"].includes(t.status)) t.applied = false;
  } else {
    t[f] = el.value;
  }
  save(); summary();
  if (f !== "notes" && f !== "contacts") refreshRow(r);
});
sheetEl.addEventListener("input", ev => {   // notes and contacts save as they are typed
  const el = ev.target.closest("textarea[data-ed]"), r = el && rowOf(el); if (!r) return;
  r.t[el.dataset.ed] = el.value; r.t.updated = Date.now(); save();
});
sheetEl.addEventListener("click", ev => {
  if (ev.target.matches(".datecell input")) { try { ev.target.showPicker(); } catch {} return; }
  const s = ev.target.closest("[data-sort]");
  if (s) { const id = s.dataset.sort; sortDir = sortBy === id ? -sortDir : 1; sortBy = id; sheet(); $(`[data-sort="${id}"]`)?.focus(); return; }
  const m = ev.target.closest("[data-mat]"), r = (m || ev.target).closest("[data-row]") && rowOf(ev.target);
  if (m && r) { r.t.updated = Date.now(); r.t.materials[m.dataset.mat] = !r.t.materials[m.dataset.mat]; m.setAttribute("aria-pressed", String(r.t.materials[m.dataset.mat])); save(); return; }
  if (ev.target.closest("[data-remove]") && r) remove(r);
});
function refreshRow(r) {   // redraw one row's cells in place, so focus and scroll stay where the student is
  const tr = sheetEl.querySelector(`[data-row="${CSS.escape(r.id)}"]`); if (!tr) return;
  for (const c of COLUMNS) {
    const td = tr.querySelector(`.c-${c.id}`); if (!td || td.querySelector("textarea") === document.activeElement) continue;
    if (c.id === "applied") { td.querySelector("input").checked = !!r.t.applied; continue; }
    if (c.id === "status") { td.querySelector("select").value = r.t.status; continue; }
    td.innerHTML = cell(c.id, r);
  }
  tr.classList.remove("stamp"); void tr.offsetWidth; if (!reduce && r.t.applied) tr.classList.add("stamp");
}
function remove(r) {
  if (!confirm(`Remove "${r.s.title}" from your planner? Your notes for it are deleted too.`)) return;
  write("irf-saved", read("irf-saved", []).filter(k => !r.keys.includes(k)));
  plan.deleted ??= {};
  r.keys.forEach(k => { delete plan.rows[k]; plan.deleted[k] = Date.now(); }); save();   // the time keeps it deleted on other devices
  rows = rows.filter(x => x !== r); render();
}

// an edge fades in on whichever side of the sheet has more columns to scroll to
const scroller = $("[data-scroll]");
const edges = () => { const w = scroller.scrollWidth - scroller.clientWidth, wrap = $("[data-sheet-wrap]");
  wrap.style.setProperty("--pin", ($(".c-posting", scroller)?.offsetWidth || 0) + "px");
  wrap.classList.toggle("more-right", scroller.scrollLeft < w - 2);
  wrap.classList.toggle("more-left", scroller.scrollLeft > 2); };
scroller.addEventListener("scroll", edges, { passive: true }); addEventListener("resize", edges);
new ResizeObserver(edges).observe($("[data-sheet]"));

// ---------- views ----------
$("[data-view]").addEventListener("click", ev => {
  const b = ev.target.closest("button"); if (!b || b.dataset.v === view) return;
  view = b.dataset.v;
  $("[data-view]").querySelectorAll("button").forEach(x => x.setAttribute("aria-pressed", String(x === b)));
  render();
});

// the season as lines: first seen, through the apply-by date, to the deadline; today as one rule across all of them
function timeline() {
  const el = $("[data-timeline]");
  const list = ordered().map(r => ({ r, a: r.s.first || r.s.posted || today, by: applyBy(r), end: r.closed || r.s.deadline || "" }));
  const dates = list.flatMap(x => [x.a, x.by, x.end, x.r.t.appliedOn]).filter(Boolean).concat(today).sort();
  const lo = addDays(dates[0], -3), hi = addDays(dates.at(-1), 10), span = daysBetween(lo, hi);
  const x = d => (daysBetween(lo, d) / span * 100).toFixed(2) + "%";
  const months = [];
  for (let d = lo.slice(0, 8) + "01"; d <= hi; d = addDays(d.slice(0, 8) + "28", 4).slice(0, 8) + "01") if (d >= lo) months.push(d);
  el.innerHTML = `<div class="tl-axis" aria-hidden="true">${months.map(m => `<span style="left:${x(m)}">${new Date(m + "T00:00:00Z").toLocaleDateString("en-US", { month: "short", timeZone: "UTC" })}</span>`).join("")}
      <span class="tl-today-label" style="left:${x(today)}">Today</span></div>
    <ol class="tl-rows"><li class="tl-today" style="--p:${(daysBetween(lo, today) / span).toFixed(4)}" aria-hidden="true"></li>${list.map(({ r, a, by, end }, i) => {
      const stop = end || hi, closed = !!(r.closed || r.gone), c = MAJOR_COLOURS[r.s.major] || "#8d98a4";
      return `<li class="tl-row${closed ? " is-closed" : ""}${r.t.applied ? " is-applied" : ""}" style="--i:${i % 12}">
        <div class="tl-name"><a href="${esc(r.s.url)}" target="_blank" rel="noopener">${esc(r.s.title)}</a><span>${esc(r.s.org)}</span></div>
        <div class="tl-track" role="img" aria-label="${esc(describe(r, a, by, end))}">
          <span class="tl-line${end ? "" : " open-ended"}" style="left:${x(a)};width:calc(${x(stop)} - ${x(a)})"></span>
          <span class="tl-start" style="left:${x(a)};--c:${c}"></span>
          ${by && !closed && by !== end ? `<span class="tl-by${!r.t.applied && !closed && urgent(by) ? " hot" : ""}" style="left:${x(by)}" title="Apply by ${short(by)}"></span>` : ""}
          ${r.t.appliedOn ? `<span class="tl-applied" style="left:${x(r.t.appliedOn)}" title="Applied ${short(r.t.appliedOn)}"></span>` : ""}
          ${end ? `<span class="${closed ? "tl-cut" : by === end ? "tl-end tl-both" : "tl-end"}" style="left:${x(end)}" title="${closed ? "Closed" : by === end ? "Apply by and deadline" : "Deadline"} ${short(end)}"></span>` : ""}
        </div></li>`;
    }).join("")}</ol>
    <p class="tl-key"><span><i class="k-start"></i>First seen</span><span><i class="k-by"></i>Apply by</span><span><i class="k-applied"></i>Applied</span><span><i class="k-end"></i>Deadline</span><span><i class="k-cut"></i>Closed</span><span><i class="k-open"></i>No deadline published</span></p>`;
}
function describe(r, a, by, end) {
  return [`First seen ${short(a)}`, by && `apply by ${short(by)}`, r.t.appliedOn && `applied ${short(r.t.appliedOn)}`,
    end ? `${r.closed || r.gone ? "closed" : "deadline"} ${short(end)}` : "no deadline published"].filter(Boolean).join(", ");
}

// ---------- CSV: every column, whatever is shown ----------
$("[data-csv]").addEventListener("click", () => {
  const q = v => `"${String(v ?? "").replace(/"/g, '""')}"`;
  const head = ["Posting", "Employer", "Link", "Status", "Applied", "Date applied", "Apply by", "Deadline", "Follow up", "Notes", "Contacts",
    ...MATERIALS.map(m => m[1]), "Listing", "Days open", "First seen", "Field", "Location", "Arrangement", "Level"];
  const lines = ordered().map(r => [r.s.title, r.s.org, r.s.url, STATUS.find(s => s[0] === r.t.status)?.[1], r.t.applied ? "Yes" : "No", r.t.appliedOn,
    applyBy(r), r.s.deadline || "None published", r.t.followUp, r.t.notes, r.t.contacts, ...MATERIALS.map(([k]) => r.t.materials[k] ? "Ready" : ""),
    r.closed ? "Closed " + r.closed : r.gone ? "No longer listed" : "Open", daysOpen(r) ?? "", r.s.first, r.s.major, where(r),
    ARR[r.s.arr] || r.s.arr || "", LEVEL[r.s.level] || r.s.level || ""].map(q).join(","));
  const url = URL.createObjectURL(new Blob(["﻿" + [head.map(q).join(","), ...lines].join("\r\n")], { type: "text/csv" }));
  const a = Object.assign(document.createElement("a"), { href: url, download: `internship-planner-${today}.csv` });
  document.body.append(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000);
});

// ---------- Google Drive sync (sync.js): optional, the planner works the same without it ----------
const CLIENT_ID = "814004045316-2gq2c5bhfj38mq8qrln0jdrve9jbr436.apps.googleusercontent.com";
const SCOPE = "https://www.googleapis.com/auth/drive.appdata";   // this site's own hidden folder in the student's Drive, nothing else
const sync = read("irf-sync", {});   // { on, at }: whether this browser syncs, and when it last did
let token = null, fileId = null, pushTimer = null;
const clock = t => new Date(t).toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" });
const day = t => new Date(t).toLocaleDateString("en-US", { month: "short", day: "numeric" });

function syncLine(state, msg = "") {
  const el = $("[data-sync]");
  const go = label => `<button type="button" class="btn ghost" data-sync-go>${label}</button>`;
  el.innerHTML = {
    off: `${go("Sync with Google Drive")}<p>Keep this planner on every device you use. It is saved to a hidden file in your own Google Drive that only this site can open.</p>`,
    paused: `${go("Resume Drive sync")}<p>Changes are saved in this browser${sync.at ? `; last synced with Drive ${day(sync.at)} at ${clock(sync.at)}` : ""}. Google asks you to confirm each visit.</p>`,
    busy: `<p class="on">Syncing with Google Drive…</p>`,
    ok: `<p class="on">Synced with Google Drive at ${clock(sync.at)}.</p><button type="button" class="linkish" data-sync-stop>Sign out</button>`,
    out: `${go("Sync with Google Drive")}<p>Signed out. Your planner is safe in your Google Drive; sync again to bring it back. Anything you star until then is added to it when you do.</p>`,
    error: `${go("Try again")}<p>${esc(msg)}</p>`
  }[state];
}
const doc = () => ({ v: 1, saved: read("irf-saved", []), rows: plan.rows, unstarred: plan.unstarred || {}, deleted: plan.deleted || {}, cols: plan.cols });

function connect() {
  const g = window.google?.accounts?.oauth2;
  if (!g) { syncLine("error", "Google's sign-in did not load. If a blocker stops accounts.google.com, allow it for this site and try again."); return; }
  g.initTokenClient({
    client_id: CLIENT_ID, scope: SCOPE,
    callback: async resp => {
      if (resp.error || !g.hasGrantedAllScopes(resp, SCOPE)) { syncLine("error", "Drive access was not allowed, so nothing was synced. Your planner is still saved in this browser."); return; }
      token = resp.access_token;
      setTimeout(() => { token = null; if (sync.on) syncLine("paused"); }, Math.max(60, (resp.expires_in || 3600) - 60) * 1000);
      await pull();
    },
    error_callback: e => syncLine("error", e?.type === "popup_closed" ? "The Google window closed before you signed in. Nothing was synced."
      : "The Google window could not open. Allow pop-ups for this site and try again.")
  }).requestAccessToken({ prompt: sync.on ? "" : "consent" });
}

// first the Drive copy is read and merged with this browser's, then the merged planner is written back to both.
// Resuming (still signed in, the session ran out) merges everything, removals included: they were made on the account.
// Signing in from signed out adds this browser's planner to the account; its removals never reach it.
async function pull() {
  syncLine("busy");
  try {
    fileId = await drive.find(token);
    const remote = fileId ? await drive.read(token, fileId) : null;
    const merged = sync.on ? mergePlans(doc(), remote) : mergePlans(remote, signedOutCopy(doc()));
    write("irf-saved", merged.saved);
    Object.assign(plan, { rows: merged.rows, unstarred: merged.unstarred, deleted: merged.deleted });
    if (merged.cols) { plan.cols = merged.cols; shown = new Set(merged.cols); }
    write("irf-planner", plan);
    fileId = await drive.write(token, fileId, doc());
    Object.assign(sync, { on: true, at: Date.now() }); write("irf-sync", sync);
    build(entries); render(); syncLine("ok");
  } catch (e) { failed(e); }
}
function queuePush() {
  if (!sync.on) return;
  if (!token) { syncLine("paused"); return; }
  clearTimeout(pushTimer);
  pushTimer = setTimeout(async () => {
    try { fileId = await drive.write(token, fileId, doc()); sync.at = Date.now(); write("irf-sync", sync); syncLine("ok"); }
    catch (e) { failed(e); }
  }, 1200);
}
function failed(e) {
  if (e.expired) { token = null; syncLine("paused"); return; }
  syncLine("error", `Could not reach Google Drive (${e.message}). Your planner is still saved in this browser.`);
}
$("[data-sync]").addEventListener("click", ev => {
  if (ev.target.closest("[data-sync-go]")) connect();
  if (ev.target.closest("[data-sync-stop]")) signOut();
});
// signing out takes the account's planner off this browser, as signing out of any site does; it stays in Drive
function signOut() {
  clearTimeout(pushTimer);
  if (token) window.google?.accounts?.oauth2?.revoke(token, () => {});
  token = null; fileId = null;
  for (const k in sync) delete sync[k];
  for (const k in plan) delete plan[k];
  plan.rows = {}; shown = new Set(COLUMNS.filter(c => c.on).map(c => c.id));
  ["irf-sync", "irf-saved", "irf-planner"].forEach(k => { try { localStorage.removeItem(k); } catch {} });
  build(entries); render(); syncLine("out");
}
syncLine(sync.on ? "paused" : "off");

// stars changed on the browse page in another tab arrive here too
addEventListener("storage", e => { if (e.key === "irf-saved") location.reload(); });
