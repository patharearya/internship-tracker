package io.github.patharearya.internships;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.patharearya.internships.Discover.Board;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * {@code discover}: Simplify list -> data/boards.json.
 * {@code run [--only system] [--limit n]}: fetch every board, filter, label, compare with the previous run, write data/.
 */
public final class Main {
    static final Path DATA = Path.of("data");
    static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Consecutive plausible fetches without a posting before it counts as closed (grill Q4). */
    static final int MISSES_TO_CLOSE = 3;
    /** NSF paging is unstable: each pass returns a different ~96% of awards (devlog 2026-10-04). A day of misses, not 3 hours. */
    static final Map<String, Integer> MISSES_TO_CLOSE_BY_SOURCE = Map.of("nsf", 24);
    // ponytail: "sudden drop" = below 20% of last count on a board that had at least 5; tune once real churn is logged
    static final int DROP_CHECK_MIN = 5;
    static final double DROP_RATIO = 0.2;
    static final int MAX_BACKOFF_HOURS = 24;
    static final int WORKDAY_EVERY_HOURS = 3;
    /**
     * Descriptions are ~5 KB each and never removed, so one file passes GitHub's 100 MB limit within a season
     * (43 MB at 8,880 postings). They are split into data/descriptions/{shard}.json; the page computes the same shard.
     */
    static final int DESCRIPTION_SHARDS = 64;

    /** One posting as published. {@code closed} is the time of the first miss once MISSES_TO_CLOSE is reached. */
    public record Entry(String key, Posting posting, Rules.Labels labels, String firstSeen, String lastSeen,
                        int misses, String firstMiss, String closed, boolean reopened) {}

    public record Failure(int count, String nextTry, String lastError) {}

    /** {@code workdayStarted}: start of the last run that fetched Workday; null before the first. */
    public record State(Map<String, Integer> lastCount, Map<String, Failure> failures, Map<String, String> paused,
                        String workdayStarted) {
        static State empty() { return new State(new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), null); }
    }

    /**
     * Workday is due once its last run started WORKDAY_EVERY_HOURS ago. Measured from the last Workday run, not the
     * clock hour: GitHub drops scheduled runs (5 of ~13 hourly slots fired on 2026-10-04), and with clock hours
     * Workday went 8 h without a run (devlog 2026-10-04). The slack stops a run started a few minutes early from
     * pushing Workday a whole hour later.
     */
    static boolean workdayDue(String lastStarted, Instant started) {
        return lastStarted == null || !Instant.parse(lastStarted)
                .plus(Duration.ofHours(WORKDAY_EVERY_HOURS)).minus(Duration.ofMinutes(30)).isAfter(started);
    }

    /** Outcome of fetching one board or source. Only "ok" units are compared with the previous run. */
    record Unit(String id, String system, String host, String status, String detail, int rawCount, List<Posting> postings) {}

    public static void main(String[] args) throws Exception {
        String email = System.getenv("CONTACT_EMAIL");
        if (email == null || email.isBlank()) throw new IllegalStateException("CONTACT_EMAIL must be set: every request identifies us (grill Q9)");
        Fetch fetch = new Fetch(email.strip(), Objects.toString(System.getenv("USAJOBS_API_KEY"), "").strip());
        Files.createDirectories(DATA);
        if (args.length > 0 && args[0].equals("discover")) {
            List<Board> boards = keepBoardsWithOpenPostings(Discover.fromListings(fetch.listings()),
                    read("boards.json", new TypeReference<List<Board>>() {}, List.of()),
                    read("postings.json", new TypeReference<List<Entry>>() {}, List.of()));
            write("boards.json", boards);
            Map<String, Long> bySystem = boards.stream().collect(Collectors.groupingBy(Board::system, TreeMap::new, Collectors.counting()));
            System.out.println("boards: " + boards.size() + " " + bySystem);
            return;
        }
        String only = flag(args, "--only");
        int limit = Integer.parseInt(Objects.toString(flag(args, "--limit"), "0"));
        run(fetch, only, limit);
    }

    /**
     * The fresh board list plus any previous board that still has open postings. Only fetched boards count misses,
     * so a board Simplify drops would otherwise leave its postings open forever (6 at Sereact, 2026-10-05, devlog).
     * Kept until its postings close; boards with nothing open drop out as before.
     */
    static List<Board> keepBoardsWithOpenPostings(List<Board> fresh, List<Board> previous, List<Entry> postings) {
        Set<String> open = postings.stream().filter(e -> e.closed() == null)
                .map(e -> e.posting().source() + ":" + e.posting().board()).collect(Collectors.toSet());
        Set<String> listed = fresh.stream().map(b -> b.system() + ":" + b.key()).collect(Collectors.toSet());
        List<Board> out = new ArrayList<>(fresh);
        for (Board b : previous) {
            String id = b.system() + ":" + b.key();
            if (!listed.contains(id) && open.contains(id)) {
                out.add(b);
                System.out.println("kept, no longer in the listings but has open postings: " + id);
            }
        }
        out.sort(Comparator.comparing(Board::system).thenComparing(Board::key));
        return out;
    }

    /**
     * Labels every open posting with the current rules, not only those fetched this run: Workday is fetched every
     * third hour and a failing board not at all, so a rule change otherwise reached 5,934 Workday postings hours
     * late (devlog 2026-10-05). Closed postings keep the labels they closed with.
     */
    static List<Entry> relabel(List<Entry> entries, Map<String, String> descriptions) {
        List<Entry> out = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            if (e.closed() != null) { out.add(e); continue; }
            String d = e.posting().description() != null ? e.posting().description() : descriptions.get(e.key());
            out.add(new Entry(e.key(), e.posting(), Rules.label(withDescription(e.posting(), d)), e.firstSeen(), e.lastSeen(),
                    e.misses(), e.firstMiss(), e.closed(), e.reopened()));
        }
        return out;
    }

    static void run(Fetch fetch, String only, int limit) throws Exception {
        Instant started = Instant.now();
        String now = started.truncatedTo(ChronoUnit.SECONDS).toString();
        List<Board> boards = read("boards.json", new TypeReference<List<Board>>() {}, List.of());
        List<Entry> previous = read("postings.json", new TypeReference<List<Entry>>() {}, List.of());
        Map<String, String> descriptions = readDescriptions();
        State state = read("state.json", new TypeReference<State>() {}, State.empty());
        state = new State(new TreeMap<>(state.lastCount()), new TreeMap<>(state.failures()), new TreeMap<>(state.paused()),
                state.workdayStarted());

        Map<String, Posting> knownWorkday = new HashMap<>();
        for (Entry e : previous) {
            Posting p = withDescription(e.posting(), descriptions.get(e.key()));
            if (p.source().equals("workday") && Fetch.workdayKey(p) != null) knownWorkday.put(Fetch.workdayKey(p), p);
        }

        // one thread per system, one request at a time within it (grill Q9)
        Map<String, List<Board>> bySystem = new TreeMap<>(boards.stream().collect(Collectors.groupingBy(Board::system)));
        // single-feed sources; keys must equal the board Parse gives their postings, or merge never sees them missing
        bySystem.put("usajobs", List.of(new Board("usajobs", "usajobs", null, "https://data.usajobs.gov/api/search", "usajobs")));
        bySystem.put("nsf", List.of(new Board("nsf", "nsf-reu", null, "https://api.nsf.gov/services/v1/awards.json", "nsf")));
        bySystem.put("nih", List.of(new Board("nih", "nih-r25", null, "https://api.reporter.nih.gov/v2/projects/search", "nih")));
        if (only != null) bySystem.keySet().retainAll(Set.of(only));
        // Workday takes ~95 min for all boards (first full run), so it runs every third hour (grill Q21); its postings
        // are simply not compared in the other runs
        if (only == null && !workdayDue(state.workdayStarted(), started)) bySystem.remove("workday");
        if (bySystem.containsKey("workday")) state = new State(state.lastCount(), state.failures(), state.paused(), now);
        final State st = state;
        ExecutorService pool = Executors.newFixedThreadPool(bySystem.size());
        Map<String, Future<List<Unit>>> futures = new TreeMap<>();
        Map<String, Long> seconds = new ConcurrentHashMap<>();
        bySystem.forEach((system, list) -> futures.put(system, pool.submit(() -> {
            long t = System.nanoTime();
            List<Unit> units = system(fetch, system, limit > 0 ? list.subList(0, Math.min(limit, list.size())) : list, knownWorkday, st, now);
            seconds.put(system, (System.nanoTime() - t) / 1_000_000_000);
            return units;
        })));
        pool.shutdown();
        List<Unit> units = new ArrayList<>();
        for (var f : futures.values()) units.addAll(f.get());

        // state: counts for the drop check, back-off for failures, pause on refusal
        for (Unit u : units) {
            switch (u.status()) {
                case "ok" -> { state.lastCount().put(u.id(), u.rawCount()); state.failures().remove(u.id()); }
                case "failed" -> {
                    int n = state.failures().containsKey(u.id()) ? state.failures().get(u.id()).count() + 1 : 1;
                    long hours = Math.min(1L << Math.min(n - 1, 5), MAX_BACKOFF_HOURS);   // 1h, 2h, 4h ... capped at 24h
                    state.failures().put(u.id(), new Failure(n, started.plus(Duration.ofHours(hours)).truncatedTo(ChronoUnit.SECONDS).toString(), u.detail()));
                }
                case "refused" -> state.paused().put(u.host(), u.detail());
                default -> {}
            }
        }

        // filter and label; every rejection is kept with its reason
        Map<String, List<Kept>> kept = new HashMap<>();
        List<String> rejections = new ArrayList<>();
        Map<String, Map<String, Integer>> rejectedBy = new TreeMap<>();
        for (Unit u : units) {
            if (!u.status().equals("ok")) continue;
            List<Kept> k = new ArrayList<>();
            for (Posting p : u.postings()) {
                String reason = Rules.reject(p);
                if (reason == null) {
                    k.add(new Kept(p, Rules.label(p)));
                } else {
                    rejections.add(rejectionRow(u.id(), p, reason));
                    rejectedBy.computeIfAbsent(u.system(), x -> new TreeMap<>()).merge(reason.replaceAll(":.*", ""), 1, Integer::sum);
                }
            }
            kept.put(u.id(), k);
        }

        List<Entry> merged = relabel(merge(previous, kept, now), descriptions);
        Map<String, String> newDescriptions = new TreeMap<>();
        List<Entry> published = new ArrayList<>();
        for (Entry e : merged) {
            String d = e.posting().description() != null ? e.posting().description() : descriptions.get(e.key());
            if (d != null) newDescriptions.put(e.key(), d);
            published.add(new Entry(e.key(), withDescription(e.posting(), null), e.labels(), e.firstSeen(), e.lastSeen(),
                    e.misses(), e.firstMiss(), e.closed(), e.reopened()));
        }

        write("postings.json", published);
        writeDescriptions(newDescriptions);
        write("state.json", state);
        Files.writeString(DATA.resolve("rejections.tsv"), REJECTION_HEADER + "\n" + String.join("\n", rejections) + "\n");
        Map<String, Object> report = report(units, merged, rejectedBy, seconds, started, state);
        write("report.json", report);
        System.out.println(JSON.writeValueAsString(report.get("systems")));
    }

    record Kept(Posting posting, Rules.Labels labels) {}

    static List<Unit> system(Fetch fetch, String system, List<Board> boards, Map<String, Posting> knownWorkday,
                                     State state, String now) throws InterruptedException {
        List<Unit> out = new ArrayList<>();
        long gap = system.equals("lever") ? 1000 : 200;   // Lever's robots.txt asks for Crawl-delay: 1
        Map<String, String> refusedNow = new HashMap<>();   // state is only read here; the main thread writes it after
        for (Board b : boards) {
            String id = system + ":" + b.key();
            String host = URI.create(b.api()).getHost();   // Workday: one host per employer; Lever, Greenhouse...: one shared host
            String paused = refusedNow.getOrDefault(host, state.paused().get(host));
            if (paused != null) {
                out.add(new Unit(id, system, host, "skipped", "server paused: " + paused, 0, List.of()));
                continue;
            }
            Failure f = state.failures().get(id);
            if (f != null && f.nextTry().compareTo(now) > 0) {
                out.add(new Unit(id, system, host, "skipped", "backing off until " + f.nextTry() + " after: " + f.lastError(), 0, List.of()));
                continue;
            }
            try {
                Fetch.Result r = switch (system) {
                    case "usajobs" -> fetch.usajobs();
                    case "nsf" -> fetch.nsf();
                    case "nih" -> fetch.nih();
                    default -> fetch.board(b, knownWorkday);
                };
                Integer last = state.lastCount().get(id);
                if (last != null && last >= DROP_CHECK_MIN && r.rawCount() < last * DROP_RATIO) {
                    out.add(new Unit(id, system, host, "anomaly", "sudden drop " + last + " -> " + r.rawCount() + "; postings left unchanged", r.rawCount(), List.of()));
                } else {
                    out.add(new Unit(id, system, host, "ok", null, r.rawCount(), r.postings()));
                }
            } catch (Fetch.Refused e) {
                String at = Instant.now().truncatedTo(ChronoUnit.SECONDS) + " " + e.getMessage();   // when the refusal came, not the run start
                out.add(new Unit(id, system, e.host, "refused", at, 0, List.of()));
                refusedNow.put(e.host, at);   // later boards on the same server are skipped this run
            } catch (Exception e) {
                out.add(new Unit(id, system, host, "failed", e.getClass().getSimpleName() + ": " + e.getMessage(), 0, List.of()));
            }
            Fetch.pause(gap);
        }
        return out;
    }

    /**
     * Compare this run with the previous one. Only boards fetched OK this run are compared; every other entry is
     * carried over unchanged, so an outage never closes postings.
     */
    static List<Entry> merge(List<Entry> previous, Map<String, List<Kept>> kept, String now) {
        Map<String, Entry> out = new TreeMap<>();
        for (Entry e : previous) out.put(e.key(), e);
        Set<String> seen = new HashSet<>();
        for (List<Kept> list : kept.values()) {
            for (Kept k : list) {
                String key = key(k.posting());
                if (!seen.add(key)) continue;   // same posting twice in one feed: first wins
                Entry prev = out.get(key);
                out.put(key, prev == null
                        ? new Entry(key, k.posting(), k.labels(), now, now, 0, null, null, false)
                        : new Entry(key, k.posting(), k.labels(), prev.firstSeen(), now, 0, null, null, prev.reopened() || prev.closed() != null));
            }
        }
        for (Entry e : previous) {
            String unit = e.posting().source() + ":" + e.posting().board();
            if (!kept.containsKey(unit) || seen.contains(e.key()) || e.closed() != null) continue;
            int misses = e.misses() + 1;
            String firstMiss = e.firstMiss() != null ? e.firstMiss() : now;
            out.put(e.key(), new Entry(e.key(), e.posting(), e.labels(), e.firstSeen(), e.lastSeen(), misses, firstMiss,
                    misses >= MISSES_TO_CLOSE_BY_SOURCE.getOrDefault(e.posting().source(), MISSES_TO_CLOSE) ? firstMiss : null, e.reopened()));
        }
        return new ArrayList<>(out.values());
    }

    static String key(Posting p) { return p.source() + ":" + p.board() + ":" + p.id(); }

    private static Map<String, Object> report(List<Unit> units, List<Entry> merged, Map<String, Map<String, Integer>> rejectedBy,
                                              Map<String, Long> seconds, Instant started, State state) {
        Map<String, Object> systems = new TreeMap<>();
        for (String s : units.stream().map(Unit::system).collect(Collectors.toCollection(TreeSet::new))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("units", units.stream().filter(u -> u.system().equals(s)).collect(Collectors.groupingBy(Unit::status, TreeMap::new, Collectors.counting())));
            m.put("open", merged.stream().filter(e -> e.posting().source().equals(s) && e.closed() == null).count());
            m.put("rejected", rejectedBy.getOrDefault(s, Map.of()));
            m.put("seconds", seconds.get(s));
            systems.put(s, m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("started", started.truncatedTo(ChronoUnit.SECONDS).toString());
        r.put("seconds", Duration.between(started, Instant.now()).toSeconds());
        r.put("open", merged.stream().filter(e -> e.closed() == null).count());
        r.put("byMajor", merged.stream().filter(e -> e.closed() == null)
                .collect(Collectors.groupingBy(e -> e.labels().major().value(), TreeMap::new, Collectors.counting())));
        r.put("systems", systems);
        r.put("paused", state.paused());
        r.put("problems", units.stream().filter(u -> !u.status().equals("ok"))
                .map(u -> u.status() + " " + u.id() + ": " + u.detail()).sorted().toList());
        return r;
    }

    // ---------- small helpers ----------

    static Posting withDescription(Posting p, String d) {
        return new Posting(p.source(), p.board(), p.id(), p.title(), p.org(), p.url(), p.locations(), p.country(), p.category(),
                p.workplace(), p.employment(), p.eligibility(), p.posted(), p.deadline(), d);
    }

    /** Java's String.hashCode; the page must compute the same: h = (31 * h + charCodeAt(i)) | 0 over UTF-16 units. */
    static int shard(String key) { return Math.floorMod(key.hashCode(), DESCRIPTION_SHARDS); }

    static Map<String, String> readDescriptions() throws IOException {
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i < DESCRIPTION_SHARDS; i++)
            out.putAll(read("descriptions/" + i + ".json", new TypeReference<Map<String, String>>() {}, Map.of()));
        return out;
    }

    /** Every shard is written, empty ones as {}, so a shard never keeps descriptions that should be gone. */
    static void writeDescriptions(Map<String, String> descriptions) throws IOException {
        List<Map<String, String>> shards = new ArrayList<>();
        for (int i = 0; i < DESCRIPTION_SHARDS; i++) shards.add(new TreeMap<>());
        descriptions.forEach((k, v) -> shards.get(shard(k)).put(k, v));
        Files.createDirectories(DATA.resolve("descriptions"));
        for (int i = 0; i < DESCRIPTION_SHARDS; i++) write("descriptions/" + i + ".json", shards.get(i));
    }

    static final String REJECTION_HEADER = "unit\tid\ttitle\treason\tlocations\tcountry\teligibility";

    /**
     * One rejections.tsv row. Locations, country and USAJOBS eligibility are what the "outside US" and "not open to
     * students" rules read, so a sampled rejection can be replayed (labelled sample row i196 could not be).
     */
    static String rejectionRow(String unit, Posting p, String reason) {
        return String.join("\t", unit, clean(p.id()), clean(p.title()), clean(reason),
                clean(p.locations() == null ? "" : String.join(" | ", p.locations())), clean(p.country()), clean(p.eligibility()));
    }

    private static String clean(String s) { return Objects.toString(s, "").replaceAll("[\\t\\n\\r]+", " "); }

    private static String flag(String[] args, String name) {
        for (int i = 0; i < args.length - 1; i++) if (args[i].equals(name)) return args[i + 1];
        return null;
    }

    private static <T> T read(String file, TypeReference<T> type, T fallback) throws IOException {
        Path p = DATA.resolve(file);
        return Files.exists(p) ? JSON.readValue(p.toFile(), type) : fallback;
    }

    private static void write(String file, Object value) throws IOException {
        JSON.writeValue(DATA.resolve(file).toFile(), value);
    }
}
