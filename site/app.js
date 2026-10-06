// Internship & Research Finder. Reads data/postings.json (written by the hourly Java job) and, when a posting is
// opened, one of the 64 description shards. No build step, no framework.
document.documentElement.classList.add("js");

const MAJOR_COLOURS = {
  "Engineering": "#f4a259", "Business & Management": "#e6d27e", "Finance & Accounting": "#4fd1a1",
  "Computer Science & IT": "#6aa9ff", "Life Sciences & Health": "#9bdc6b", "Data & Mathematics": "#b892ff",
  "Marketing & Communications": "#ff8fa3", "Physical Sciences": "#5fd0e8", "Government, Law & Policy": "#d3b58d",
  "Arts, Design & Media": "#ff7a5c", "Education": "#ffd34e", "Social Sciences & Psychology": "#f0a6e8", "Other": "#8d98a4"
};
const STATES = { AL: "Alabama", AK: "Alaska", AZ: "Arizona", AR: "Arkansas", CA: "California", CO: "Colorado", CT: "Connecticut",
  DE: "Delaware", DC: "District of Columbia", FL: "Florida", GA: "Georgia", HI: "Hawaii", ID: "Idaho", IL: "Illinois", IN: "Indiana",
  IA: "Iowa", KS: "Kansas", KY: "Kentucky", LA: "Louisiana", ME: "Maine", MD: "Maryland", MA: "Massachusetts", MI: "Michigan",
  MN: "Minnesota", MS: "Mississippi", MO: "Missouri", MT: "Montana", NE: "Nebraska", NV: "Nevada", NH: "New Hampshire",
  NJ: "New Jersey", NM: "New Mexico", NY: "New York", NC: "North Carolina", ND: "North Dakota", OH: "Ohio", OK: "Oklahoma",
  OR: "Oregon", PA: "Pennsylvania", RI: "Rhode Island", SC: "South Carolina", SD: "South Dakota", TN: "Tennessee", TX: "Texas",
  UT: "Utah", VT: "Vermont", VA: "Virginia", WA: "Washington", WV: "West Virginia", WI: "Wisconsin", WY: "Wyoming", PR: "Puerto Rico" };
const LEVEL = { undergrad: "Undergraduate", grad: "Graduate", both: "Undergraduate and graduate", unknown: "Not stated" };
const ARR = { "in-person": "In person", hybrid: "Hybrid", remote: "Remote", "not stated": "Not stated" };
const TYPE = { internship: "Internship", "co-op": "Co-op", "research programme": "Research programme", fellowship: "Fellowship", apprenticeship: "Apprenticeship" };
const PAGE = 50, THIN = 100, DAY = 86400000;

const $ = (s, r = document) => r.querySelector(s);
const fmt = n => n.toLocaleString("en-US");
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const day = iso => iso ? new Date(iso.length === 10 ? iso + "T00:00:00Z" : iso) : null;
const daysAgo = d => Math.floor(Date.now() / DAY) - Math.floor(d.getTime() / DAY);
const short = d => d.toLocaleDateString("en-US", { month: "short", day: "numeric", timeZone: "UTC" });
const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;

const state = { major: "", q: "", where: "", arr: "", level: "", shown: PAGE };
let all = [], majors = [], view = [];

// ---------- stars: this browser only; the planner page reads them ----------
// A star also keeps a copy of the posting (planner.js snapshot()), so the planner still shows it after the
// posting leaves postings.json, 14 days after it closes. Unstarring here keeps the student's notes; only the planner deletes.
const saved = (() => {
  let keys = new Set();
  try { keys = new Set(JSON.parse(localStorage.getItem("irf-saved") || "[]")); } catch {}
  const write = () => { try { localStorage.setItem("irf-saved", JSON.stringify([...keys])); } catch {} };
  // the times let Drive sync (sync.js mergePlans) tell a new star from an old one, and an unstar from both
  const keep = (e, on) => { try {
    const plan = JSON.parse(localStorage.getItem("irf-planner") || "{}"); plan.rows ??= {}; plan.unstarred ??= {};
    if (on) {
      Object.assign(plan.rows[e.key] ??= {}, { starred: Date.now(), snap: { title: e.title, org: e.org, url: e.url, deadline: iso(e.deadline),
        first: iso(e.first), posted: iso(e.posted), major: e.major, locs: e.locs, states: e.states, remote: !!e.remote, arr: e.arr, level: e.level } });
      delete plan.unstarred[e.key];
    } else plan.unstarred[e.key] = Date.now();
    localStorage.setItem("irf-planner", JSON.stringify(plan));
  } catch {} };
  return { has: k => keys.has(k), get keys() { return keys; }, toggle(e) { const on = !keys.has(e.key); on ? keys.add(e.key) : keys.delete(e.key); write(); keep(e, on); },
    get size() { return keys.size; } };
})();
const iso = d => d ? d.toISOString().slice(0, 10) : "";

// ---------- load ----------
fetch("data/postings.json").then(r => { if (!r.ok) throw new Error(r.status); return r.json(); }).then(entries => {
  all = entries.filter(e => !e.closed).map(e => {
    const p = e.posting, l = e.labels, posted = day(p.posted), first = day(e.firstSeen);
    return { key: e.key, title: p.title, org: p.org || "", url: p.url, locs: p.locations || [], states: l.states || [], remote: l.remoteUs,
      major: l.major.value, level: l.level.value, arr: l.arrangement.value, type: l.type, source: p.source,
      posted, first, deadline: day(p.deadline), sort: (posted || first || new Date(0)).getTime(), research: p.source === "nsf" || p.source === "nih",
      age: posted ? daysAgo(posted) : null };
  }).sort((a, b) => b.sort - a.sort);
  const counts = {};
  all.forEach(e => counts[e.major] = (counts[e.major] || 0) + 1);
  majors = Object.entries(counts).sort((a, b) => (a[0] === "Other") - (b[0] === "Other") || b[1] - a[1])
    .map(([name, n]) => ({ name, n, c: MAJOR_COLOURS[name] || "#8d98a4" }));
  intro(); fields(); whereOptions(); render(); river(); savedCount();
}).catch(() => {
  $("[data-summary]").textContent = "The postings could not be loaded.";
  const note = $("[data-note]"); note.hidden = false;
  note.textContent = "Check your connection and reload the page. If it keeps happening, the data file may be updating; try again in a minute.";
});
fetch("data/report.json").then(r => r.ok ? r.json() : null).then(r => {
  if (!r || !r.started) return;
  const d = new Date(r.started);
  $("[data-updated]").textContent = `Last update started ${short(d)}, ${d.toISOString().slice(11, 16)} UTC.`;
}).catch(() => {});

// ---------- counts in the copy ----------
function intro() {
  const boards = new Set(all.filter(e => !e.research && e.source !== "usajobs").map(e => e.source + ":" + e.key.split(":")[1])).size;
  const by = s => all.filter(e => e.source === s).length;
  const employer = all.filter(e => !e.research && e.source !== "usajobs").length;
  $("[data-lede]").innerHTML = `<b>${fmt(all.length)}</b> open internships and research programmes from <b>${fmt(boards)}</b> employers' own job boards, USAJOBS, NSF and NIH, checked several times a day.`;
  $("[data-coverage]").innerHTML = `Postings come from <b>${fmt(boards)}</b> employers whose public job boards we can read, plus federal internships and funded research programmes. It is not every internship, and some fields are thin: employers' boards lean toward engineering and business.`;
  $("[data-sources]").innerHTML = [
    [employer, "postings on employers' own job boards (Greenhouse, Lever, Ashby, Workday)"],
    [by("usajobs"), "federal internships from USAJOBS, with real closing dates"],
    [by("nsf"), "NSF-funded undergraduate research sites (REU)"],
    [by("nih"), "NIH-funded summer research programmes (R25)"]
  ].map(([n, t]) => `<dt>${fmt(n)}</dt><dd>${t}</dd>`).join("");
}

// ---------- fields: strip and chips ----------
function fields() {
  const strip = $("[data-strip]"), chips = $("[data-chips]");
  strip.innerHTML = majors.map((m, i) =>
    `<button type="button" data-major="${esc(m.name)}" aria-pressed="false" style="--n:${m.n};--c:${m.c};--i:${i}" aria-label="${esc(m.name)}, ${fmt(m.n)} open" title="${esc(m.name)}: ${fmt(m.n)} open">${m.n / all.length >= 0.035 ? `<span><b>${fmt(m.n)}</b>${esc(m.name)}</span>` : ""}</button>`).join("");
  const fit = () => strip.querySelectorAll("span").forEach(s => {
    s.lastChild.textContent = s.dataset.name; s.style.visibility = "";
    if (s.scrollWidth > s.clientWidth + 1) s.lastChild.textContent = "";
    const b = s.firstChild;
    if (s.scrollWidth > s.clientWidth + 1 || b.scrollWidth > b.clientWidth + 1) s.style.visibility = "hidden";   // not even the count fits: the chip carries it
  });
  strip.querySelectorAll("span").forEach(s => s.dataset.name = s.lastChild.textContent);
  requestAnimationFrame(fit); document.fonts.ready.then(fit); addEventListener("resize", fit);
  chips.innerHTML = `<button type="button" data-major="" aria-pressed="true">All fields <b>${fmt(all.length)}</b></button>` + majors.map(m =>
    `<button type="button" data-major="${esc(m.name)}" aria-pressed="false" style="--c:${m.c}"><i></i>${esc(m.name)} <b>${fmt(m.n)}</b></button>`).join("");
  [strip, chips].forEach(el => el.addEventListener("click", e => {
    const b = e.target.closest("[data-major]"); if (!b) return;
    pick(state.major === b.dataset.major && el === strip ? "" : b.dataset.major);
  }));
}
function pick(major, scroll) {
  state.major = major; state.shown = PAGE;
  document.querySelectorAll("[data-major]").forEach(b => b.setAttribute("aria-pressed", String(b.dataset.major === major)));
  $("[data-strip]").classList.toggle("has-pick", !!major);
  render(true);
  if (scroll) $(".list").scrollIntoView({ behavior: reduce ? "auto" : "smooth" });
}

function whereOptions() {
  const seen = new Set(); all.forEach(e => e.states.forEach(s => seen.add(s)));
  const sel = $("[data-where]");
  sel.insertAdjacentHTML("beforeend", [...seen].filter(s => STATES[s]).sort((a, b) => STATES[a].localeCompare(STATES[b]))
    .map(s => `<option value="${s}">${STATES[s]}</option>`).join(""));
}

// ---------- filters ----------
$("[data-q]").addEventListener("input", e => { state.q = e.target.value.trim().toLowerCase(); state.shown = PAGE; render(true); });
$("[data-where]").addEventListener("change", e => { state.where = e.target.value; state.shown = PAGE; render(true); });
document.querySelectorAll("[data-seg]").forEach(seg => seg.addEventListener("click", e => {
  const b = e.target.closest("button"); if (!b) return;
  seg.querySelectorAll("button").forEach(x => x.setAttribute("aria-pressed", String(x === b)));
  state[seg.dataset.seg] = b.dataset.v; state.shown = PAGE; render(true);
}));
$("[data-more]").addEventListener("click", () => { state.shown += PAGE; render(false, true); });

// "not stated" is shown under every arrangement and level, since the posting may well fit
function match(e) {
  if (state.major && e.major !== state.major) return false;
  if (state.where === "remote" ? !(e.remote || e.arr === "remote") : state.where && !e.states.includes(state.where)) return false;
  if (state.arr && e.arr !== state.arr && e.arr !== "not stated" && !(state.arr === "remote" && e.remote)) return false;
  if (state.level && e.level !== state.level && e.level !== "both" && e.level !== "unknown") return false;
  if (state.q && !(e.title.toLowerCase().includes(state.q) || e.org.toLowerCase().includes(state.q))) return false;
  return true;
}

// same employer and title in several places reads as one row
function groups(list) {
  const out = new Map();
  for (const e of list) { const k = e.org + "\u0000" + e.title; (out.get(k) || out.set(k, []).get(k)).push(e); }
  return [...out.values()];
}

function render(changed, appending) {
  if (!all.length) return;
  const list = all.filter(match);
  view = groups(list);
  const shown = view.slice(0, state.shown);
  const ul = $("[data-rows]");
  const start = appending ? ul.children.length : 0;
  const html = shown.slice(start).map((g, i) => row(g, i)).join("");
  if (appending) ul.insertAdjacentHTML("beforeend", html); else ul.innerHTML = html;
  // rows already on screen arrive now; waiting for the observer would show them for a frame, then blank them
  // the wave counts only rows on screen, so it always runs top to bottom; rows above the screen arrive with no delay
  let wave = 0;
  if (!reduce) [...ul.children].slice(start).filter(li => li.classList.contains("row")).forEach(li => {
    const top = li.getBoundingClientRect().top;
    if (top < innerHeight) { li.style.setProperty("--i", top < 0 ? 0 : wave++ % 12); li.classList.add("arrive"); }
    else rowsIn.observe(li);
  });
  const more = $("[data-more]");
  more.hidden = view.length <= state.shown;
  more.textContent = `Show ${fmt(Math.min(PAGE, view.length - state.shown))} more of ${fmt(view.length - state.shown)} listings`;
  summary(list.length);
  const gr = $("[data-grouped]");
  gr.hidden = view.length === list.length || !list.length;
  gr.textContent = `Shown as ${fmt(view.length)} listings: the same role at the same employer in several places is one row.`;
}

// each batch of rows entering the screen arrives as one staggered wave, once
let batch = 0, batchAt = 0;
const rowsIn = new IntersectionObserver(es => {
  const now = performance.now(); if (now - batchAt > 120) batch = 0; batchAt = now;
  es.filter(e => e.isIntersecting).forEach(e => {
    e.target.style.setProperty("--i", batch++ % 12);
    e.target.classList.add("arrive"); rowsIn.unobserve(e.target);
  });
}, { rootMargin: "0px 0px 15% 0px" });   // starts just below the screen, so a row is never seen before it fades in

function summary(n) {
  const h = $("[data-summary]"), note = $("[data-note]");
  const filtered = state.q || state.where || state.arr || state.level;
  note.hidden = true;
  h.textContent = `${fmt(n)} open ${state.major ? "in " + state.major : "across every field"}${filtered ? " matching your filters" : ""}`;
  const m = majors.find(x => x.name === state.major);
  if (state.major === "Other") {
    note.textContent = "These titles name no field we can sort by, like \"2027 Summer Intern\". Open one to see what the work involves.";
    note.hidden = false;
  } else if (m && m.n < THIN) {
    note.textContent = `Only ${fmt(m.n)} open right now. This field is thin in what we can read: employers' own boards lean toward engineering and business.`;
    note.hidden = false;
  }
  if (!n) {
    const q = $("[data-q]").value.trim();
    $("[data-rows]").innerHTML = `<li class="empty"><b>${q ? `Nothing matches "${esc(q)}"` : "Nothing matches all of these filters"}${state.major ? " in " + esc(state.major) : ""}.</b> ${q ? "Check the spelling or try a shorter word" : "Try another state, or set arrangement or level back to Any"}.<br><button type="button" class="btn ghost" data-clear>Clear filters</button></li>`;
    $("[data-clear]").addEventListener("click", clearFilters);
  }
}

function clearFilters() {
  Object.assign(state, { q: "", where: "", arr: "", level: "", shown: PAGE });
  $("[data-q]").value = ""; $("[data-where]").value = "";
  document.querySelectorAll("[data-seg]").forEach(seg => seg.querySelectorAll("button").forEach((b, i) => b.setAttribute("aria-pressed", String(i === 0))));
  render(true);
}

function when(e) {
  if (e.research) return ["Funded programme", false, true];
  if (e.age === null) return [e.first ? `Seen ${short(e.first)}` : "", false];
  const t = e.age <= 0 ? "Posted today" : e.age === 1 ? "Posted yesterday" : e.age < 7 ? `Posted ${e.age} days ago` : `Posted ${short(e.posted)}`;
  return [t, e.age <= 0];   // only today is marked, so the mark stays rare
}
function due(e) {
  if (!e.deadline) return ["No deadline published", false];
  const days = -daysAgo(e.deadline);
  return [`Apply by ${short(e.deadline)}`, days <= 14];
}
function where(g) {
  const states = [...new Set(g.flatMap(e => e.states))], remote = g.some(e => e.remote);
  const parts = (remote ? ["Remote (US)"] : []).concat(states.slice(0, 3));
  if (states.length > 3) parts.push(`+${states.length - 3} more`);
  return parts.length ? parts.join(", ") : (g[0].locs[0] || "Location not stated");
}
function row(g, i) {
  const e = g[0], [w, fresh, prog] = when(e), [d, soon] = due(e), on = g.some(x => saved.has(x.key));
  return `<li class="row" data-key="${esc(e.key)}" style="--i:${i % 24};--c:${MAJOR_COLOURS[e.major] || "#8d98a4"}">
    <span class="when${fresh ? " fresh" : ""}${prog ? " prog" : ""}">${esc(w)}</span>
    <span class="what"><button type="button" class="title" data-open>${esc(e.title)}</button><span class="org">${esc(e.org)}</span>
      <span class="kind"><i></i>${esc(e.major)}${e.type !== "internship" ? " · " + esc(TYPE[e.type] || e.type) : ""}</span></span>
    <span class="where">${esc(where(g))}${g.length > 1 ? ` <span class="due">(${g.length} openings)</span>` : ""}</span>
    <span class="due${soon ? " soon" : ""}">${esc(d)}</span>
    <button type="button" class="star" data-star aria-pressed="${on}" aria-label="${on ? "Remove from your planner" : "Add to your planner"}: ${esc(e.title)}"><svg aria-hidden="true"><use href="#i-star"/></svg></button>
  </li>`;
}

// ---------- list clicks: open or star ----------
$("[data-rows]").addEventListener("click", ev => {
  const li = ev.target.closest(".row"); if (!li) return;
  const g = view.find(x => x[0].key === li.dataset.key); if (!g) return;
  if (ev.target.closest("[data-star]")) { star(g); return; }
  open(g);
});
function star(g) {
  const on = g.some(x => saved.has(x.key));
  g.forEach(x => { if (saved.has(x.key) === on) saved.toggle(x); });
  savedCount();
  document.querySelectorAll(`.row[data-key="${CSS.escape(g[0].key)}"] [data-star]`).forEach(b => {
    b.setAttribute("aria-pressed", String(!on)); b.setAttribute("aria-label", `${!on ? "Remove from your planner" : "Add to your planner"}: ${g[0].title}`);
  });
  const ds = $("[data-d-star]");
  if (current === g) { ds.setAttribute("aria-pressed", String(!on)); ds.querySelector("span").textContent = !on ? "In your planner" : "Add to planner"; }
}
// counted as the planner counts its rows: one per employer + title, however many places it is listed in
function savedCount() {
  const byKey = new Map(all.map(e => [e.key, e]));
  let kept = {}; try { kept = JSON.parse(localStorage.getItem("irf-planner") || "{}").rows || {}; } catch {}
  const n = new Set([...saved.keys].map(k => { const e = byKey.get(k) || kept[k]?.snap; return e ? e.org + "\u0000" + e.title : k; })).size;
  const c = $("[data-saved-count]"); c.hidden = !n; c.textContent = n;
}

// ---------- posting detail ----------
const shardCache = new Map();
// Java's String.hashCode, floorMod 64: the same shard the Java job wrote the description to (Main.shard)
const shard = key => { let h = 0; for (let i = 0; i < key.length; i++) h = (Math.imul(31, h) + key.charCodeAt(i)) | 0; return ((h % 64) + 64) % 64; };
const description = key => {
  const n = shard(key);
  if (!shardCache.has(n)) shardCache.set(n, fetch(`data/descriptions/${n}.json`).then(r => r.ok ? r.json() : {}).catch(() => ({})));
  return shardCache.get(n).then(d => d[key]);
};

let current = null, lastFocus = null;
function open(g) {
  current = g; lastFocus = document.activeElement;
  const e = g[0], dr = $("[data-drawer]");
  $("[data-d-org]").textContent = e.org;
  $("[data-d-title]").textContent = e.title;
  const facts = [
    ["Field", e.major], ["Type", TYPE[e.type] || e.type],
    ["Where", [...new Set(g.flatMap(x => x.remote ? ["Remote (US)", ...x.locs] : x.locs))].join(" · ") || "Not stated"],
    ["Arrangement", ARR[e.arr] || e.arr], ["Level", LEVEL[e.level] || e.level],
    [e.research ? "Award date" : "Posted", e.posted ? short(e.posted) + (e.research ? "" : ", by the employer") : "Not published"],
    ["Deadline", e.deadline ? short(e.deadline) : "No deadline published"],
    ["First seen here", e.first ? short(e.first) : "Unknown"]
  ];
  $("[data-d-facts]").innerHTML = facts.map(([k, v]) => `<dt>${esc(k)}</dt><dd>${esc(v)}</dd>`).join("");
  const apply = $("[data-d-apply]"); apply.href = e.url;
  apply.childNodes[0].textContent = e.research ? "Open the programme's page " : "Apply on the employer's site ";
  const on = g.some(x => saved.has(x.key)), ds = $("[data-d-star]");
  ds.setAttribute("aria-pressed", String(on)); ds.querySelector("span").textContent = on ? "In your planner" : "Add to planner";
  const desc = $("[data-d-desc]");
  desc.innerHTML = `<p class="muted">Loading the description…</p>`;
  description(e.key).then(t => {
    if (current !== g) return;
    desc.innerHTML = "";
    if (!t) { desc.innerHTML = `<p class="muted">This source doesn't publish a description here. The employer's page has the full details.</p>`; return; }
    t.split(/\n{2,}/).map(s => s.trim()).filter(Boolean).forEach(s => { const p = document.createElement("p"); p.textContent = s; desc.append(p); });
    if (g.length > 1) {
      const p = document.createElement("p"); p.className = "muted";
      p.textContent = `This role is listed ${g.length} times, once per location; the button above opens the first.`;
      desc.prepend(p);
    }
  });
  dr.hidden = false; $("[data-scrim]").hidden = false; document.body.style.overflow = "hidden";
  $("[data-drawer-in]").scrollTop = 0; $("[data-drawer-in]").focus();
}
function close() {
  $("[data-drawer]").hidden = true; $("[data-scrim]").hidden = true; document.body.style.overflow = ""; current = null;
  if (lastFocus) lastFocus.focus();
}
$("[data-close]").addEventListener("click", close);
$("[data-scrim]").addEventListener("click", close);
$("[data-d-star]").addEventListener("click", () => current && star(current));
addEventListener("keydown", e => {
  if (e.key === "Escape" && current) close();
  if (e.key === "Tab" && current) {   // keep focus inside the open panel
    const f = [...$("[data-drawer]").querySelectorAll("button, a[href]")];
    if (e.shiftKey && document.activeElement === f[0]) { e.preventDefault(); f.at(-1).focus(); }
    else if (!e.shiftKey && document.activeElement === f.at(-1)) { e.preventDefault(); f[0].focus(); }
  }
});

// ---------- scroll reveal ----------
const io = new IntersectionObserver(es => es.forEach(e => { if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); } }), { threshold: 0.15 });
document.querySelectorAll(".reveal").forEach((el, i) => { el.style.setProperty("--d", `${(i % 3) * 80}ms`); io.observe(el); });

// ---------- the river: one point per open posting, one stream per field ----------
function river() {
  const cv = $("#field"), ctx = cv.getContext("2d"), label = $("#label");
  const total = all.length;
  let W, H, top, span, bands = [], pts, starts = [], hover = -1, mx = -1e4, my = -1e4, running = true;
  function layout() {
    const dpr = Math.min(devicePixelRatio || 1, 2);
    W = cv.clientWidth; H = cv.clientHeight;
    cv.width = W * dpr; cv.height = H * dpr; ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    top = H * (W < 760 ? 0.1 : 0.12); span = H * (W < 760 ? 0.22 : 0.27);
    let y = 0; bands = majors.map(m => { const h = Math.max(6, span * m.n / total), b = { ...m, y0: y, h }; y += h; return b; });
    const k = span / y; bands.forEach(b => { b.y0 *= k; b.h *= k; });
    ctx.fillStyle = "#0a0f15"; ctx.fillRect(0, 0, W, H);
  }
  function seed() {
    pts = new Float32Array(total * 3);   // x, offset within band, speed
    let i = 0;
    bands.forEach((b, bi) => { starts[bi] = i; for (let j = 0; j < b.n; j++, i++) {
      pts[i*3] = Math.random(); pts[i*3+1] = (Math.random() + Math.random() + Math.random()) / 3; pts[i*3+2] = 0.6 + Math.random() * 0.8;
    }});
  }
  const bend = (x, t) => Math.sin(x * 3.4 + t * 0.00012 + 0.6) * 0.075 + Math.sin(x * 6.1 - t * 0.00008) * 0.018 + (x - 0.5) * 0.06;
  function frame(t) {
    ctx.globalCompositeOperation = "source-over";
    ctx.globalAlpha = 1; ctx.fillStyle = "rgba(10,15,21,0.22)"; ctx.fillRect(0, 0, W, H);   // short trails
    ctx.globalCompositeOperation = "lighter";
    for (let bi = 0; bi < bands.length; bi++) {
      const b = bands[bi], lit = hover >= 0 ? hover : bands.findIndex(x => x.name === state.major), dim = lit >= 0 && lit !== bi;
      ctx.fillStyle = b.c; ctx.globalAlpha = dim ? 0.1 : (lit === bi ? 1 : 0.55);
      for (let i = starts[bi], end = starts[bi] + b.n; i < end; i++) {
        if (!reduce) { pts[i*3] += 0.000032 * pts[i*3+2]; if (pts[i*3] > 1.02) pts[i*3] -= 1.04; }
        const fx = pts[i*3], x = fx * W;
        let y = top + b.y0 + pts[i*3+1] * b.h + bend(fx, t) * H + Math.sin(fx * 9 + bi + t * 0.0004) * 2.2;
        const dx = x - mx, dy = y - my, d2 = dx*dx + dy*dy;   // the cursor parts the stream a little
        if (d2 < 6400) { const f = (1 - d2 / 6400) * 10; y += dy > 0 ? f : -f; }
        ctx.fillRect(x, y, 1.3, 1.3);
      }
    }
    ctx.globalAlpha = 1;
    if (!reduce && running) requestAnimationFrame(frame);
  }
  function bandAt(x, y, t) {
    const off = top + bend(x / W, t) * H;
    for (let i = 0; i < bands.length; i++) { const b = bands[i]; if (y >= off + b.y0 - 2 && y <= off + b.y0 + b.h + 2) return i; }
    return -1;
  }
  const hero = cv.parentElement;
  hero.addEventListener("pointermove", e => {
    if (e.pointerType === "touch") return;
    const r = cv.getBoundingClientRect(); mx = e.clientX - r.left; my = e.clientY - r.top;
    hover = e.target.closest(".lede, .bar, .hint") ? -1 : bandAt(mx, my, performance.now());
    cv.classList.toggle("pointing", hover >= 0);
    if (hover >= 0) {
      const b = bands[hover];
      label.innerHTML = `${esc(b.name)}<b>${fmt(b.n)} open</b>`;
      label.style.left = Math.min(mx, W - 320) + "px"; label.style.top = my + "px"; label.classList.add("on");
    } else label.classList.remove("on");
    if (reduce) frame(performance.now());
  });
  hero.addEventListener("pointerleave", () => { hover = -1; mx = my = -1e4; label.classList.remove("on"); if (reduce) frame(performance.now()); });
  cv.addEventListener("click", e => {
    const r = cv.getBoundingClientRect(), i = bandAt(e.clientX - r.left, e.clientY - r.top, performance.now());
    if (i >= 0) pick(bands[i].name, true);
  });
  // stop drawing while the river is off screen
  new IntersectionObserver(([e]) => { const was = running; running = e.isIntersecting; if (running && !was && !reduce) requestAnimationFrame(frame); }).observe(cv);
  addEventListener("resize", () => { layout(); if (reduce) frame(0); });
  layout(); seed(); cv.classList.add("on"); requestAnimationFrame(frame);
}
