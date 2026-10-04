package io.github.patharearya.internships;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.patharearya.internships.Discover.Board;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

    /** One posting as published. {@code closed} is the time of the first miss once MISSES_TO_CLOSE is reached. */
    public record Entry(String key, Posting posting, Rules.Labels labels, String firstSeen, String lastSeen,
                        int misses, String firstMiss, String closed, boolean reopened) {}

    public record Failure(int count, String nextTry, String lastError) {}

    public record State(Map<String, Integer> lastCount, Map<String, Failure> failures, Map<String, String> paused) {
        static State empty() { return new State(new TreeMap<>(), new TreeMap<>(), new TreeMap<>()); }
    }

    /** Outcome of fetching one board or source. Only "ok" units are compared with the previous run. */
    record Unit(String id, String system, String status, String detail, int rawCount, List<Posting> postings) {}

    public static void main(String[] args) throws Exception {
        String email = System.getenv("CONTACT_EMAIL");
        if (email == null || email.isBlank()) throw new IllegalStateException("CONTACT_EMAIL must be set: every request identifies us (grill Q9)");
        Fetch fetch = new Fetch(email.strip(), Objects.toString(System.getenv("USAJOBS_API_KEY"), "").strip());
        Files.createDirectories(DATA);
        if (args.length > 0 && args[0].equals("discover")) {
            List<Board> boards = Discover.fromListings(fetch.listings());
            write("boards.json", boards);
            Map<String, Long> bySystem = boards.stream().collect(Collectors.groupingBy(Board::system, TreeMap::new, Collectors.counting()));
            System.out.println("boards: " + boards.size() + " " + bySystem);
            return;
        }
        String only = flag(args, "--only");
        int limit = Integer.parseInt(Objects.toString(flag(args, "--limit"), "0"));
        run(fetch, only, limit);
    }

    static void run(Fetch fetch, String only, int limit) throws Exception {
        Instant started = Instant.now();
        String now = started.truncatedTo(ChronoUnit.SECONDS).toString();
        List<Board> boards = read("boards.json", new TypeReference<List<Board>>() {}, List.of());
        List<Entry> previous = read("postings.json", new TypeReference<List<Entry>>() {}, List.of());
        Map<String, String> descriptions = read("descriptions.json", new TypeReference<Map<String, String>>() {}, Map.of());
        State state = read("state.json", new TypeReference<State>() {}, State.empty());
        state = new State(new TreeMap<>(state.lastCount()), new TreeMap<>(state.failures()), new TreeMap<>(state.paused()));

        Map<String, Posting> knownWorkday = new HashMap<>();
        for (Entry e : previous) {
            Posting p = withDescription(e.posting(), descriptions.get(e.key()));
            if (p.source().equals("workday") && Fetch.workdayKey(p) != null) knownWorkday.put(Fetch.workdayKey(p), p);
        }

        // one thread per system, one request at a time within it (grill Q9)
        Map<String, List<Board>> bySystem = new TreeMap<>(boards.stream().collect(Collectors.groupingBy(Board::system)));
        // single-feed sources; keys must equal the board Parse gives their postings, or merge never sees them missing
        bySystem.put("usajobs", List.of(new Board("usajobs", "usajobs", null, null, "usajobs")));
        bySystem.put("nsf", List.of(new Board("nsf", "nsf-reu", null, null, "nsf")));
        bySystem.put("nih", List.of(new Board("nih", "nih-r25", null, null, "nih")));
        if (only != null) bySystem.keySet().retainAll(Set.of(only));
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
                case "refused" -> state.paused().put(u.system(), now + " " + u.detail());
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
                    rejections.add(String.join("\t", u.id(), Objects.toString(p.id(), ""), clean(p.title()), reason));
                    rejectedBy.computeIfAbsent(u.system(), x -> new TreeMap<>()).merge(reason.replaceAll(":.*", ""), 1, Integer::sum);
                }
            }
            kept.put(u.id(), k);
        }

        List<Entry> merged = merge(previous, kept, now);
        Map<String, String> newDescriptions = new TreeMap<>();
        List<Entry> published = new ArrayList<>();
        for (Entry e : merged) {
            String d = e.posting().description() != null ? e.posting().description() : descriptions.get(e.key());
            if (d != null) newDescriptions.put(e.key(), d);
            published.add(new Entry(e.key(), withDescription(e.posting(), null), e.labels(), e.firstSeen(), e.lastSeen(),
                    e.misses(), e.firstMiss(), e.closed(), e.reopened()));
        }

        write("postings.json", published);
        write("descriptions.json", newDescriptions);
        write("state.json", state);
        Files.writeString(DATA.resolve("rejections.tsv"), "unit\tid\ttitle\treason\n" + String.join("\n", rejections) + "\n");
        Map<String, Object> report = report(units, merged, rejectedBy, seconds, started, state);
        write("report.json", report);
        System.out.println(JSON.writeValueAsString(report.get("systems")));
    }

    record Kept(Posting posting, Rules.Labels labels) {}

    private static List<Unit> system(Fetch fetch, String system, List<Board> boards, Map<String, Posting> knownWorkday,
                                     State state, String now) throws InterruptedException {
        List<Unit> out = new ArrayList<>();
        long gap = system.equals("lever") ? 1000 : 200;   // Lever's robots.txt asks for Crawl-delay: 1
        String refused = state.paused().get(system);   // state is only read here; the main thread writes it after
        for (Board b : boards) {
            String id = system + ":" + b.key();
            if (refused != null) {
                out.add(new Unit(id, system, "skipped", "system paused: " + refused, 0, List.of()));
                continue;
            }
            Failure f = state.failures().get(id);
            if (f != null && f.nextTry().compareTo(now) > 0) {
                out.add(new Unit(id, system, "skipped", "backing off until " + f.nextTry() + " after: " + f.lastError(), 0, List.of()));
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
                    out.add(new Unit(id, system, "anomaly", "sudden drop " + last + " -> " + r.rawCount() + "; postings left unchanged", r.rawCount(), List.of()));
                } else {
                    out.add(new Unit(id, system, "ok", null, r.rawCount(), r.postings()));
                }
            } catch (Fetch.Refused e) {
                out.add(new Unit(id, system, "refused", e.getMessage(), 0, List.of()));
                refused = now + " " + e.getMessage();   // stops the rest of this system's boards this run
            } catch (Exception e) {
                out.add(new Unit(id, system, "failed", e.getClass().getSimpleName() + ": " + e.getMessage(), 0, List.of()));
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
