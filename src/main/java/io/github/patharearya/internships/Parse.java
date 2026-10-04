package io.github.patharearya.internships;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Source adapters: one raw response in, common records out. No fetching here, so tests replay saved responses. */
public final class Parse {
    private Parse() {}

    private static final ObjectMapper JSON = new ObjectMapper();
    static final String RESEARCH = "Research programme";

    public static List<Posting> greenhouse(String company, String board, String json) {
        List<Posting> out = new ArrayList<>();
        for (JsonNode j : read(json).path("jobs")) {
            out.add(new Posting("greenhouse", board, s(j, "id"), s(j, "title"), company, s(j, "absolute_url"),
                    list(s(j.path("location"), "name")), null, s(j.path("departments").path(0), "name"),
                    null, null, null, day(s(j, "first_published")), day(s(j, "application_deadline")),
                    text(unescape(s(j, "content")))));   // Greenhouse escapes its HTML once more
        }
        return out;
    }

    public static List<Posting> lever(String company, String board, String json) {
        List<Posting> out = new ArrayList<>();
        for (JsonNode j : read(json)) {
            JsonNode c = j.path("categories");
            List<String> locations = new ArrayList<>();
            c.path("allLocations").forEach(l -> locations.add(l.asText()));
            if (locations.isEmpty()) locations.addAll(list(s(c, "location")));
            StringBuilder description = new StringBuilder(Objects.toString(s(j, "descriptionPlain"), ""));
            for (JsonNode l : j.path("lists")) {   // duties and qualifications live here, not in the description
                description.append("\n").append(Objects.toString(s(l, "text"), ""))
                        .append("\n").append(Objects.toString(text(s(l, "content")), ""));
            }
            String created = s(j, "createdAt");
            out.add(new Posting("lever", board, s(j, "id"), s(j, "text"), company, s(j, "hostedUrl"),
                    locations, s(j, "country"), s(c, "department"), s(j, "workplaceType"), s(c, "commitment"), null,
                    created == null ? null : Instant.ofEpochMilli(Long.parseLong(created)).atOffset(ZoneOffset.UTC).toLocalDate().toString(),
                    null, description.toString().strip()));
        }
        return out;
    }

    public static List<Posting> ashby(String company, String board, String json) {
        List<Posting> out = new ArrayList<>();
        for (JsonNode j : read(json).path("jobs")) {
            List<String> locations = new ArrayList<>(list(s(j, "location")));
            j.path("secondaryLocations").forEach(l -> locations.addAll(list(s(l, "location"))));
            out.add(new Posting("ashby", board, s(j, "id"), s(j, "title"), company, s(j, "jobUrl"),
                    locations, null, s(j, "department"), s(j, "workplaceType"), s(j, "employmentType"), null,
                    day(s(j, "publishedAt")), null, s(j, "descriptionPlain")));
        }
        return out;
    }

    /** Workday list call: the paths to fetch details for. The list itself has no description or real date. */
    public static List<String> workdayPaths(String json) {
        List<String> out = new ArrayList<>();
        read(json).path("jobPostings").forEach(p -> out.add(s(p, "externalPath")));
        return out;
    }

    /** Workday detail call for one posting. */
    public static Posting workday(String company, String board, String json) {
        JsonNode i = read(json).path("jobPostingInfo");
        List<String> locations = new ArrayList<>(list(s(i, "location")));
        i.path("additionalLocations").forEach(l -> locations.add(l.asText()));
        return new Posting("workday", board, first(s(i, "jobReqId"), s(i, "id")), s(i, "title"), company,
                s(i, "externalUrl"), locations, s(i.path("country"), "descriptor"), null, null, s(i, "timeType"),
                null, day(s(i, "startDate")), day(s(i, "endDate")), text(s(i, "jobDescription")));
    }

    public static List<Posting> usajobs(String json) {
        List<Posting> out = new ArrayList<>();
        for (JsonNode item : read(json).path("SearchResult").path("SearchResultItems")) {
            JsonNode m = item.path("MatchedObjectDescriptor");
            JsonNode d = m.path("UserArea").path("Details");
            List<String> locations = new ArrayList<>();
            m.path("PositionLocation").forEach(l -> locations.add(s(l, "LocationName")));
            List<String> paths = new ArrayList<>();
            d.path("HiringPath").forEach(p -> paths.add(p.asText()));
            String workplace = d.path("RemoteIndicator").asBoolean() ? "Remote"
                    : d.path("TeleworkEligible").asBoolean() ? "Telework eligible" : null;
            String description = String.join("\n", list(s(d, "JobSummary"), s(m, "QualificationSummary"), s(d, "Education")));
            out.add(new Posting("usajobs", "usajobs", s(item, "MatchedObjectId"), s(m, "PositionTitle"),
                    s(m, "OrganizationName"), s(m, "PositionURI"), locations,
                    s(m.path("PositionLocation").path(0), "CountryCode"), s(m.path("JobCategory").path(0), "Code"),
                    workplace, null, String.join(",", paths), day(s(m, "PublicationStartDate")),
                    day(s(m, "ApplicationCloseDate")), description));
        }
        return out;
    }

    public static List<Posting> nsf(String json) {
        List<Posting> out = new ArrayList<>();
        for (JsonNode a : read(json).path("response").path("award")) {
            String id = s(a, "id");
            String abstractText = s(a, "abstractText");
            String url = first(programmeUrl(abstractText), "https://www.nsf.gov/awardsearch/showAward?AWD_ID=" + id);
            out.add(new Posting("nsf", "nsf-reu", id, s(a, "title"), s(a, "awardeeName"), url,
                    list(place(s(a, "perfCity"), s(a, "perfStateCode"))), s(a, "perfCountryCode"), s(a, "dirAbbr"),
                    null, RESEARCH, null, mdy(s(a, "date")), null, text(abstractText)));
        }
        return out;
    }

    public static List<Posting> nih(String json) {
        List<Posting> out = new ArrayList<>();
        for (JsonNode p : read(json).path("results")) {
            JsonNode o = p.path("organization");
            out.add(new Posting("nih", "nih-r25", s(p, "appl_id"), s(p, "project_title"), s(o, "org_name"),
                    s(p, "project_detail_url"), list(place(s(o, "org_city"), s(o, "org_state"))), s(o, "org_country"),
                    "NIH", null, RESEARCH, null, null, null, s(p, "abstract_text")));
        }
        return out;
    }

    // --- helpers ---

    private static final Pattern URL = Pattern.compile("(https?://|www\\.)[^\\s()<>,;\"]+");

    /** First link in an NSF abstract that is a programme page; etap.nsf.gov is NSF's generic portal (see devlog). */
    static String programmeUrl(String abstractText) {
        if (abstractText == null) return null;
        Matcher m = URL.matcher(abstractText);
        while (m.find()) {
            String u = m.group().replaceAll("[.]+$", "");
            if (u.contains("etap.nsf.gov")) continue;
            return u.startsWith("www.") ? "https://" + u : u;
        }
        return null;
    }

    private static JsonNode read(String json) {
        try {
            return JSON.readTree(json);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A field as trimmed text, or null when missing, null, blank or not a plain value. */
    private static String s(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull() || v.isContainerNode() || v.asText().isBlank()) return null;
        return v.asText().strip();
    }

    private static List<String> list(String... xs) {
        List<String> out = new ArrayList<>();
        for (String x : xs) if (x != null) out.add(x);
        return out;
    }

    private static String first(String a, String b) { return a != null ? a : b; }

    private static String place(String city, String state) {
        return city == null ? state : state == null ? city : city + ", " + state;
    }

    /** ISO timestamp to its date part. */
    private static String day(String iso) { return iso == null ? null : iso.substring(0, 10); }

    /** NSF's MM/dd/yyyy to ISO. */
    private static String mdy(String d) {
        return d == null ? null : d.substring(6, 10) + "-" + d.substring(0, 2) + "-" + d.substring(3, 5);
    }

    /** HTML to plain text, keeping paragraph breaks. Text, not HTML, so the page never renders third-party markup. */
    static String text(String html) {
        if (html == null) return null;
        String s = html.replaceAll("(?i)<br\\s*/?>|</(p|li|div|h[1-6]|ul|ol)>", "\n").replaceAll("<[^>]*>", "");
        return unescape(s).replace(' ', ' ').replaceAll("[ \\t\\f\\r]+", " ").replaceAll(" ?\\n[ \\n]*", "\n").strip();
    }

    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos|nbsp);");

    static String unescape(String s) {
        if (s == null) return null;
        return ENTITY.matcher(s).replaceAll(m -> Matcher.quoteReplacement(switch (m.group(1)) {
            case "amp" -> "&";
            case "lt" -> "<";
            case "gt" -> ">";
            case "quot" -> "\"";
            case "apos" -> "'";
            case "nbsp" -> " ";
            default -> Character.toString(m.group(1).startsWith("#x")
                    ? Integer.parseInt(m.group(1).substring(2), 16) : Integer.parseInt(m.group(1).substring(1)));
        }));
    }
}
