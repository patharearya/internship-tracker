package io.github.patharearya.internships;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Replays the filter and major rules over 297 hand-labelled postings (labelled by the owner 2026-10-04 and 10-05).
 * Every disagreement with the labels must be listed in KNOWN with a reason: a rule change that breaks a new row
 * fails here, and so does one that fixes a listed row until the list is updated. Run it after every rule change.
 */
class LabelledSampleTest {

    /** Rows where the rules still disagree with the labels, and why we accept it for now. */
    static final Map<String, String> KNOWN = new HashMap<>(Map.of(
            "i196", "not replayable: rejected on USAJOBS eligibility, which rejections.tsv does not record",
            "i050", "\"Environments\" (Physical) is matched before \"Biofilms\"; labelled Life Sciences",
            "i071", "\"Technology\" is the only field word (weak rule -> CS); labelled Life Sciences from the employer (Momentive)",
            "m036", "title names no field, NSF directorate CSE gives CS; labelled Data (\"Computing and Evolution\")"));
    static {
        // titles that name no field: the owner labelled them from the employer or general knowledge, which a title
        // rule cannot reproduce without misfiring elsewhere (employer-name matching was tried and dropped, 2026-10-05)
        for (String row : List.of("i072", "i073", "i078", "i082", "i086", "i089", "i090", "i098", "m000", "m001", "m006",
                "m009", "m012", "m025", "m030", "m044", "m056", "m059", "m062", "m083", "m090"))
            KNOWN.put(row, "title names no field");
    }

    @Test
    void rulesAgreeWithTheLabels() throws Exception {
        JsonNode doc;
        try (var in = getClass().getResourceAsStream("/labelled-sample.json")) { doc = new ObjectMapper().readTree(in); }
        ObjectMapper m = new ObjectMapper();
        Map<String, String> wrong = new TreeMap<>();
        List<String> fromDescription = new ArrayList<>();
        Map<String, int[]> byStratum = new TreeMap<>();   // stratum -> {rows, filter wrong, major checked, major wrong}
        for (JsonNode r : doc.path("rows")) {
            Posting p = m.treeToValue(r.path("posting"), Posting.class);
            String row = r.path("row").asText();
            int[] c = byStratum.computeIfAbsent(r.path("stratum").asText(), k -> new int[4]);
            c[0]++;
            String reason = Rules.reject(p);
            boolean keep = r.path("keep").asBoolean();
            if ((reason == null) != keep) {
                c[1]++;
                wrong.put(row, (keep ? "wrongly rejected (" + reason + "): " : "wrongly kept: ") + p.title());
            } else if (keep && !r.path("major").isNull()) {
                c[2]++;
                String major = Rules.major(p).value();
                if (!major.equals(r.path("major").asText())) {
                    // only the description settles these; the rules read title, department and employer, so not expected
                    if (r.path("majorFrom").asText().equals("description")) {
                        fromDescription.add(row + " major " + major + ", labelled " + r.path("major").asText() + ": " + p.title());
                        continue;
                    }
                    c[3]++;
                    wrong.put(row, "major " + major + ", labelled " + r.path("major").asText() + ": " + p.title());
                }
            }
        }
        byStratum.forEach((s, c) -> System.out.printf("%-45s rows %3d  filter wrong %3d  major wrong %3d/%d%n", s, c[0], c[1], c[3], c[2]));
        wrong.forEach((row, w) -> System.out.println("  " + row + (KNOWN.containsKey(row) ? " (known) " : " NEW     ") + w));
        System.out.println("major settled only by the description, not counted (" + fromDescription.size() + "):");
        fromDescription.forEach(w -> System.out.println("  " + w));

        Set<String> unexpected = new TreeSet<>(wrong.keySet()); unexpected.removeAll(KNOWN.keySet());
        Set<String> fixed = new TreeSet<>(KNOWN.keySet()); fixed.removeAll(wrong.keySet());
        assertTrue(unexpected.isEmpty(), "rules disagree with the labels on " + unexpected);
        assertTrue(fixed.isEmpty(), "now agree with the labels, remove from KNOWN: " + fixed);
    }
}
