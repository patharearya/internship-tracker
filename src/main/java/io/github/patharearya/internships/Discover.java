package io.github.patharearya.internships;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Builds the watch list from SimplifyJobs listings.json. Only the application URL and company name of each row
 * are read, to learn (company, hiring system, board); no listing content is kept.
 */
public final class Discover {
    private Discover() {}

    public static final String LISTINGS =
            "https://raw.githubusercontent.com/SimplifyJobs/Summer2027-Internships/dev/.github/scripts/listings.json";

    /** A board to watch. {@code api} is the base URL Fetch calls; {@code key} is the board's identity within its system. */
    public record Board(String system, String key, String company, String api, String from) {}

    public static List<Board> fromListings(JsonNode rows) {
        // one board per system + case-insensitive key; company name counts so the most common spelling wins
        Map<String, Board> boards = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> names = new HashMap<>();
        for (JsonNode r : rows) {
            Board b = board(r.path("url").asText(""));
            if (b == null) continue;
            String id = b.system() + ":" + b.key().toLowerCase(Locale.ROOT);
            boards.putIfAbsent(id, b);
            names.computeIfAbsent(id, k -> new HashMap<>()).merge(r.path("company_name").asText("").strip(), 1, Integer::sum);
        }
        List<Board> out = new ArrayList<>();
        boards.forEach((id, b) -> {
            String company = names.get(id).entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
            out.add(new Board(b.system(), b.key().toLowerCase(Locale.ROOT), company.isEmpty() ? b.key() : company, b.api(), b.from()));
        });
        out.sort(Comparator.comparing(Board::system).thenComparing(Board::key));
        return out;
    }

    /** The board an application URL points to, or null if it is not a system we read. Company is filled in later. */
    static Board board(String url) {
        URI u;
        try {
            u = URI.create(url.strip().replace(" ", "%20"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        String host = Objects.toString(u.getHost(), "").toLowerCase(Locale.ROOT);
        List<String> path = new ArrayList<>();
        for (String p : Objects.toString(u.getRawPath(), "").split("/")) {
            String d = URLDecoder.decode(p, StandardCharsets.UTF_8).strip();
            if (!d.isEmpty()) path.add(d);
        }
        String from = "simplify";
        if (host.endsWith("greenhouse.io")) {
            // EU boards (job-boards.eu.greenhouse.io) are served by the same API host; boards-api.eu does not resolve
            String b = !path.isEmpty() && !path.get(0).equals("embed") ? path.get(0) : query(u, "for");
            return b == null ? null : new Board("greenhouse", b.toLowerCase(Locale.ROOT), null,
                    "https://boards-api.greenhouse.io/v1/boards/" + enc(b.toLowerCase(Locale.ROOT)) + "/jobs?content=true", from);
        }
        if (host.endsWith("lever.co") && !path.isEmpty()) {
            String b = path.get(0).toLowerCase(Locale.ROOT);
            String api = host.contains(".eu.") ? "https://api.eu.lever.co" : "https://api.lever.co";
            return new Board("lever", b, null, api + "/v0/postings/" + enc(b) + "?mode=json", from);
        }
        if (host.equals("jobs.ashbyhq.com") && !path.isEmpty()) {
            String b = path.get(0);   // case-sensitive and may contain spaces, e.g. "Hippocratic AI"
            return new Board("ashby", b.toLowerCase(Locale.ROOT), null, "https://api.ashbyhq.com/posting-api/job-board/" + enc(b), from);
        }
        if (host.endsWith(".myworkdayjobs.com") && !path.isEmpty()) {
            // optional language segment: /en-US/{site}/job/...
            String site = path.get(0).matches("[a-z]{2}-[A-Z]{2}") && path.size() > 1 ? path.get(1) : path.get(0);
            String tenant = host.substring(0, host.indexOf('.'));
            return new Board("workday", host + "/" + site, null, "https://" + host + "/wday/cxs/" + tenant + "/" + enc(site), from);
        }
        return null;
    }

    /** One URL path segment: spaces as %20, not "+". */
    private static String enc(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String query(URI u, String name) {
        for (String kv : Objects.toString(u.getRawQuery(), "").split("&")) {
            String[] p = kv.split("=", 2);
            if (p.length == 2 && p[0].equals(name)) return URLDecoder.decode(p[1], StandardCharsets.UTF_8);
        }
        return null;
    }
}
