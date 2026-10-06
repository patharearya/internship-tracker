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
                return "{\"total\":" + (offset == 0 ? 64 : 0) + ",\"jobPostings\":[" + jobs + "]}";
            }
        };
        Board b = new Board("workday", "wiley.wd1.myworkdayjobs.com/wiley_careers", "Wiley",
                "https://wiley.wd1.myworkdayjobs.com/wday/cxs/wiley/Wiley_Careers", "simplify");
        Fetch.Result r = f.board(b, Map.of());
        assertEquals(4, bodies.size(), "pages at offsets 0, 20, 40, 60");
        assertEquals(64, r.rawCount());
        assertEquals(64, r.postings().size());
    }
}
