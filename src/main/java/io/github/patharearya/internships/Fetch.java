package io.github.patharearya.internships;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.patharearya.internships.Discover.Board;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Live HTTP. Everything here is thin: parsing lives in Parse, so tests replay saved responses instead. */
public class Fetch {
    static final ObjectMapper JSON = new ObjectMapper();

    /** Thrown for 403/429: that server must not be called again until a person has looked (grill Q9, Q22). */
    static final class Refused extends IOException {
        final String host;
        Refused(String host, String msg) { super(msg); this.host = host; }
    }

    /** {@code rawCount} is every posting on the board before filtering, for the sudden-drop check. */
    public record Result(List<Posting> postings, int rawCount) {}

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final String userAgent;
    private final String contactEmail;
    private final String usajobsKey;

    public Fetch(String contactEmail, String usajobsKey) {
        this.contactEmail = contactEmail;
        this.usajobsKey = usajobsKey;
        this.userAgent = "internship-tracker/0.1 (+https://github.com/patharearya/internship-tracker; " + contactEmail + ")";
    }

    // ---------- employer boards ----------

    /** @param known previous Workday postings by {@link #workdayKey}, so details are fetched only for new ones */
    public Result board(Board b, Map<String, Posting> known) throws IOException, InterruptedException {
        return switch (b.system()) {
            case "greenhouse" -> one(Parse.greenhouse(b.company(), b.key(), get(b.api())));
            case "lever" -> one(Parse.lever(b.company(), b.key(), get(b.api())));
            case "ashby" -> one(Parse.ashby(b.company(), b.key(), get(b.api())));
            case "workday" -> workday(b, known);
            default -> throw new IllegalArgumentException("unknown system " + b.system());
        };
    }

    private static Result one(List<Posting> ps) { return new Result(ps, ps.size()); }

    /** Board key + the "/job/..." part of a Workday URL; the site segment's capitalisation varies between links. */
    static String workdayKey(Posting p) {
        int i = p.url().indexOf("/job/");
        return i < 0 ? null : p.board() + p.url().substring(i);
    }

    /**
     * Id for a Workday search result, so a rejected row carries the same id as a kept posting (the detail call's
     * jobReqId): the first bullet field is the requisition id in Workday's default setup, but tenants can configure
     * it, so it is used only when the posting path ends with it ("..._REQ-26320", "..._31149049-1"); otherwise the
     * path, which never matches a kept id (devlog 2026-10-05).
     */
    static String listId(String bullet, String path) {
        return !bullet.isBlank() && path.matches(".*_" + java.util.regex.Pattern.quote(bullet) + "(-\\d+)?") ? bullet : path;
    }

    // ponytail: Workday search is relevance-ordered and "intern" also matches internal/international; stop at the
    // first page with no internship title, capped at MAX_WORKDAY_PAGES. Use facets if real internships get cut off.
    static final int MAX_WORKDAY_PAGES = 10;

    private Result workday(Board b, Map<String, Posting> known) throws IOException, InterruptedException {
        String site = "https://" + b.key();   // key is host/site; public posting URL is host/site + path
        List<Posting> out = new ArrayList<>();
        int total = 0;
        for (int page = 0; page < MAX_WORKDAY_PAGES; page++) {
            String body = "{\"appliedFacets\":{},\"limit\":20,\"offset\":" + page * 20 + ",\"searchText\":\"intern\"}";
            JsonNode list = JSON.readTree(post(b.api() + "/jobs", body));
            total = list.path("total").asInt(total);   // Workday reports total only on the first page
            boolean anyInternTitle = false;
            for (JsonNode p : list.path("jobPostings")) {
                String path = p.path("externalPath").asText();
                String title = p.path("title").asText();
                Posting stub = new Posting("workday", b.key(), listId(p.path("bulletFields").path(0).asText(""), path), title, b.company(), site + path,
                        List.of(p.path("locationsText").asText("")), null, null, null, null, null, null, null, null);
                if (Rules.reject(withLocations(stub, List.of())) == null) anyInternTitle = true;
                if (Rules.reject(stub) != null) {   // rejected on title or location: keep the stub so the rejection is logged
                    out.add(stub);
                    continue;
                }
                Posting prev = known.get(b.key() + path);
                if (prev != null) {
                    out.add(prev);
                } else {
                    pause(200);
                    out.add(Parse.workday(b.company(), b.key(), get(b.api() + path)));
                }
            }
            if (!anyInternTitle || (page + 1) * 20 >= total) break;
            pause(200);
        }
        return new Result(out, total);
    }

    private static Posting withLocations(Posting p, List<String> locations) {
        return new Posting(p.source(), p.board(), p.id(), p.title(), p.org(), p.url(), locations, p.country(), p.category(),
                p.workplace(), p.employment(), p.eligibility(), p.posted(), p.deadline(), p.description());
    }

    // ---------- government and research sources ----------

    /**
     * Two searches, merged by posting id: "intern" anywhere in the text, and the student hiring path, which reaches
     * Pathways "Student Trainee (...)" postings whose text never says intern (education, social science and
     * psychology series among them; thin majors, 2026-10-05). The title filter decides what is kept.
     */
    public Result usajobs() throws IOException, InterruptedException {
        Map<String, Posting> out = new LinkedHashMap<>();
        for (String query : List.of("Keyword=intern", "HiringPath=student")) {
            int total = Integer.MAX_VALUE, seen = 0;
            for (int page = 1; seen < total; page++) {
                String body = send(HttpRequest.newBuilder(URI.create("https://data.usajobs.gov/api/search?" + query + "&ResultsPerPage=500&Page=" + page))
                        .header("User-Agent", contactEmail).header("Authorization-Key", usajobsKey).GET());
                total = JSON.readTree(body).path("SearchResult").path("SearchResultCountAll").asInt(0);
                List<Posting> ps = Parse.usajobs(body);
                if (ps.isEmpty()) break;
                seen += ps.size();
                for (Posting p : ps) out.putIfAbsent(p.id(), p);
                pause(500);
            }
        }
        return new Result(new ArrayList<>(out.values()), out.size());
    }

    public Result nsf() throws IOException, InterruptedException {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        String base = "https://api.nsf.gov/services/v1/awards.json?keyword=" + URLEncoder.encode("\"REU Site\"", StandardCharsets.UTF_8)
                + "&expDateStart=" + today + "&rpp=25&printFields=id,title,awardeeName,perfCity,perfStateCode,perfCountryCode,"
                + "date,startDate,expDate,dirAbbr,abstractText";
        List<Posting> out = new ArrayList<>();
        int total = Integer.MAX_VALUE;
        for (int offset = 1; offset <= total; offset += 25) {   // NSF offsets are 1-based
            String body = get(base + "&offset=" + offset);
            total = JSON.readTree(body).path("response").path("metadata").path("totalCount").asInt(0);
            out.addAll(Parse.nsf(body));
            pause(500);
        }
        return new Result(out, out.size());
    }

    public Result nih() throws IOException, InterruptedException {
        // ponytail: fiscal year of six months ago, i.e. the latest year with a full set of awards; fine until NIH changes cadence
        LocalDate d = LocalDate.now().minusMonths(6);
        int fy = d.getMonthValue() >= 10 ? d.getYear() + 1 : d.getYear();
        String body = "{\"criteria\":{\"activity_codes\":[\"R25\"],\"fiscal_years\":[" + fy + "],\"include_active_projects\":true,"
                + "\"advanced_text_search\":{\"operator\":\"and\",\"search_field\":\"projecttitle,abstracttext\","
                + "\"search_text\":\"undergraduate summer research\"}},\"limit\":500,\"offset\":0}";
        List<Posting> ps = Parse.nih(post("https://api.reporter.nih.gov/v2/projects/search", body));
        return new Result(ps, ps.size());
    }

    public JsonNode listings() throws IOException, InterruptedException {
        return JSON.readTree(get(Discover.LISTINGS));
    }

    // ---------- HTTP ----------

    private String get(String url) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(URI.create(url)).GET());
    }

    private String post(String url, String json) throws IOException, InterruptedException {
        return send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    private String send(HttpRequest.Builder req) throws IOException, InterruptedException {
        HttpRequest r = req.timeout(Duration.ofSeconds(60)).setHeader("Accept", "application/json")
                .setHeader("User-Agent", req.build().headers().firstValue("User-Agent").orElse(userAgent)).build();
        HttpResponse<String> res = http.send(r, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int s = res.statusCode();
        if (s == 403 || s == 429) throw new Refused(r.uri().getHost(), "HTTP " + s + " from " + r.uri().getHost());
        if (s != 200) throw new IOException("HTTP " + s + " for " + r.uri());
        return res.body();
    }

    static void pause(long ms) throws InterruptedException { Thread.sleep(ms); }
}
