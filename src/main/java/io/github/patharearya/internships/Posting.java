package io.github.patharearya.internships;

import java.util.List;

/**
 * The common record every source is converted into. Raw source values only:
 * classification (major, level, arrangement, state) happens later, from these.
 * Dates are ISO yyyy-MM-dd or null; a null means the source did not say.
 */
public record Posting(
        String source,        // greenhouse, lever, ashby, workday, usajobs, nsf, nih
        String board,         // board / tenant site; identity is (source, board, id)
        String id,            // the source's own posting ID
        String title,
        String org,
        String url,
        List<String> locations,
        String country,       // when the source gives it separately from locations
        String category,      // department, USAJOBS series code, NSF directorate, ...
        String workplace,     // source's remote/hybrid/on-site wording
        String employment,    // source's commitment / employment type
        String eligibility,   // USAJOBS hiring paths; null elsewhere
        String posted,
        String deadline,
        String description) {
}
