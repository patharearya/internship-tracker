package io.github.patharearya.internships;

import io.github.patharearya.internships.Discover.Board;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Workday answers "total":0 on every page after the first; that zero once ended paging at page 2 (devlog 2026-10-06). */
class WorkdayPagingTest {

    @Test
    void totalFromTheFirstPageOnly() throws Exception {
        List<String> bodies = new ArrayList<>();
        Fetch f = new Fetch("test@example.com", null) {
            @Override
            String post(String url, String json) {
                bodies.add(json);
                int offset = Integer.parseInt(json.replaceAll(".*\"offset\":(\\d+).*", "$1"));
                // Wiley's offset 20 and 40 answers, 2026-10-06: full pages, total 0; an intern title on each page
                // keeps paging, and "Germany" in the location rejects it before any detail request
                StringBuilder jobs = new StringBuilder();
                for (int i = 0; i < Math.min(20, 64 - offset); i++)
                    jobs.append(i == 0 ? "" : ",").append("{\"title\":\"Intern Editorial Administration\",\"externalPath\":\"/job/x_R").append(offset + i)
                            .append("\",\"locationsText\":\"Weinheim, Germany\",\"bulletFields\":[\"R").append(offset + i).append("\"]}");
                // a student category on the first page is not read: 64 results fit in the intern search
                return "{\"total\":" + (offset == 0 ? 64 : 0) + ",\"jobPostings\":[" + jobs + "]" + (offset == 0
                        ? ",\"facets\":[{\"facetParameter\":\"workerSubType\",\"values\":[{\"descriptor\":\"Intern\",\"id\":\"A\",\"count\":9}]}]" : "") + "}";
            }
        };
        Board b = new Board("workday", "wiley.wd1.myworkdayjobs.com/wiley_careers", "Wiley",
                "https://wiley.wd1.myworkdayjobs.com/wday/cxs/wiley/Wiley_Careers", "simplify");
        Fetch.Result r = f.board(b, Map.of());
        assertEquals(4, bodies.size(), "pages at offsets 0, 20, 40, 60");
        assertEquals(64, r.rawCount());
        assertEquals(64, r.postings().size());
    }

    static String jobs(String prefix, int from, int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < n; i++)
            s.append(i == 0 ? "" : ",").append("{\"title\":\"").append(prefix).append("\",\"externalPath\":\"/job/x_R").append(from + i)
                    .append("\",\"locationsText\":\"Weinheim, Germany\",\"bulletFields\":[\"R").append(from + i).append("\"]}");
        return s.toString();
    }

    @Test
    void beyondWhatTheSearchCanReadStudentCategoriesAreReadInFull() throws Exception {
        // CVS on 2026-10-06: 4,082 "intern" results; 47 corporate internships ranked below 3,285 store pharmacy internships
        List<String> bodies = new ArrayList<>();
        String facets = "\"facets\":[{\"facetParameter\":\"workerSubType\",\"values\":["
                + "{\"descriptor\":\"Intern (Seasonal) (Trainee)\",\"id\":\"A\",\"count\":30},"
                + "{\"descriptor\":\"Pharmacy Intern (Trainee)\",\"id\":\"B\",\"count\":3000}]},"
                + "{\"facetParameter\":\"jobFamilyGroup\",\"values\":[{\"descriptor\":\"Internal Audit\",\"id\":\"C\",\"count\":5},"
                + "{\"descriptor\":\"Interns\",\"id\":\"D\",\"count\":20}]},"   // the same jobs filed a second way
                + "{\"facetParameter\":\"locationMainGroup\",\"values\":[{\"facetParameter\":\"locations\",\"values\":"
                + "[{\"descriptor\":\"Intern Housing Campus\",\"id\":\"L\",\"count\":2}]}]}]";
        Fetch f = new Fetch("test@example.com", null) {
            @Override
            String post(String url, String json) {
                bodies.add(json);
                int offset = Integer.parseInt(json.replaceAll(".*\"offset\":(\\d+).*", "$1"));
                if (json.contains("\"workerSubType\":[\"A\"]"))   // 30 seasonal internships; R0 was already on the first intern page
                    return "{\"total\":30,\"jobPostings\":[" + jobs("Finance Intern", offset == 0 ? 0 : 1000 + offset, Math.min(20, 30 - offset)) + "]}";
                return "{\"total\":" + (offset == 0 ? 4000 : 0) + ",\"jobPostings\":[" + jobs("Pharmacy Intern", offset, 20) + "]"
                        + (offset == 0 ? "," + facets : "") + "}";
            }
        };
        Board b = new Board("workday", "cvshealth.wd1.myworkdayjobs.com/cvs_health_careers", "CVS Health",
                "https://cvshealth.wd1.myworkdayjobs.com/wday/cxs/cvshealth/CVS_Health_Careers", "simplify");
        Fetch.Result r = f.board(b, Map.of());
        assertEquals(Fetch.MAX_WORKDAY_PAGES + 2, bodies.size(), "10 intern pages, then 2 pages of the seasonal category");
        assertTrue(bodies.stream().noneMatch(x -> x.contains("\"B\"") || x.contains("\"C\"") || x.contains("\"D\"") || x.contains("\"L\"")),
                "not the flood, not Internal Audit, not the second facet, not a location");
        assertTrue(bodies.get(Fetch.MAX_WORKDAY_PAGES).contains("\"searchText\":\"\""), "a category is read whole, not searched for \"intern\"");
        assertEquals(200 + 30 - 20, r.postings().size(), "the category's first page repeats R0..R19, read once");
        assertEquals(4000, r.rawCount(), "the drop check still compares the intern search total");
    }
}
