package io.github.patharearya.internships;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keyword rules. Each value carries the rule and matched text that produced it ("why"), so any label can be
 * checked against the posting. No match means unknown / not stated / Other, never a guessed default.
 */
public final class Rules {
    private Rules() {}

    /** A labelled value and why. */
    public record Label(String value, String why) {}

    /** Everything derived from a kept posting. {@code states} empty means state unknown. */
    public record Labels(String type, Label major, Label level, Label arrangement, List<String> states, boolean remoteUs) {}

    // ---------- internship filter ----------

    // "summer 2027" dates a term, which a regular job never has ("Summer 2027 Analyst", "Associate Engineer (Summer 2027)");
    // "starting summer 2027" is a full-time start date
    private static final Pattern KEEP = Pattern.compile(
            "\\b(interns?|internships?|co-?ops?|apprentices?|apprenticeships?|fellows?|fellowships?"
                    + "|student trainee|student volunteer|summer (analyst|associate|scholar)s?|(?<!start(ing)? )summer 20\\d\\d|20\\d\\d summer)\\b",
            Pattern.CASE_INSENSITIVE);
    /** "Early Career Intern" is an internship; "early career" alone is a new-grad job. */
    private static final Pattern NOT_STUDENT = Pattern.compile(
            "\\b(new grad(uate)?s?|university grad(uate)?s?|recent grad(uate)?s?|entry[- ]level|early career(?! intern)"
                    + "|post-?doc(toral)?|distinguished fellow|senior fellow|clinical fellow|medical fellow"
                    // DoD SkillBridge internships are for transitioning service members only (labelled sample, 2026-10-05)
                    + "|skill ?bridge)\\b", Pattern.CASE_INSENSITIVE);
    /**
     * Jobs running an internship programme, e.g. "Internship Program Manager", "Recruiter, Early Talent & Interns".
     * "Product Manager, Intern" is an intern: after the role, a bare "intern" names the job's level, not its subject.
     */
    private static final Pattern RUNS_PROGRAMME = Pattern.compile(
            "\\b(internships?|interns?) (program(me)?s? )?(manager|coordinator|director|recruiter|lead|partner)\\b"
                    + "|\\b(manager|coordinator|director|recruiter|lead|partner)(,| of| for| -) (the )?(early talent|university|campus"
                    + "|(internships?|interns?) program(me)?s?)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Titles as words: "_" is a word character to regex, so "Co-op_Spring 2027" had no "co-op" (labelled sample). */
    static String words(String title) { return title.replace('_', ' '); }

    /** Why a posting is rejected, or null to keep it. */
    public static String reject(Posting p) {
        if (isResearch(p)) return outsideUs(p) ? "outside US" : null;
        String t = words(p.title());
        if (!KEEP.matcher(t).find()) return "no internship keyword in title";
        Matcher m = NOT_STUDENT.matcher(t);
        if (m.find()) return "not a student role: \"" + m.group() + "\"";
        m = RUNS_PROGRAMME.matcher(t);
        if (m.find()) return "runs the programme: \"" + m.group() + "\"";
        if (p.eligibility() != null && !p.eligibility().matches(".*\\b(public|student|graduates)\\b.*"))
            return "not open to students (USAJOBS hiring paths: " + p.eligibility() + ")";
        if (outsideUs(p)) return "outside US";
        return null;
    }

    static boolean isResearch(Posting p) { return Parse.RESEARCH.equals(p.employment()); }

    public static Labels label(Posting p) {
        Where w = where(p);
        return new Labels(type(p), major(p), level(p), arrangement(p), w.states, w.remoteUs);
    }

    static String type(Posting p) {
        if (isResearch(p)) return "research programme";
        String t = words(p.title()).toLowerCase(Locale.ROOT);
        if (t.matches(".*\\bapprentice(ship)?s?\\b.*")) return "apprenticeship";
        if (t.matches(".*\\bfellow(ship)?s?\\b.*")) return "fellowship";
        if (t.matches(".*\\bco-?ops?\\b.*")) return "co-op";
        return "internship";
    }

    // ---------- major ----------

    public static final List<String> MAJORS = List.of(
            "Computer Science & IT", "Engineering", "Data & Mathematics", "Business & Management", "Finance & Accounting",
            "Marketing & Communications", "Life Sciences & Health", "Physical Sciences", "Social Sciences & Psychology",
            "Government, Law & Policy", "Education", "Arts, Design & Media", "Other");

    /**
     * Checked in this order; first match wins. Specific before general (e.g. "software engineer" is CS, not Engineering).
     * The first rule holds phrases Data's "ai"/"machine learning" would otherwise take ("AI Engineer" is CS, owner's
     * label). Keywords added 2026-10-05 come from the owner's major labels.
     */
    private static final List<Map.Entry<String, Pattern>> MAJOR_RULES = List.of(
            rule("Computer Science & IT", "\\b(ai|ml|machine learning) engineer|deep learning|reinforcement learning|\\bllms?\\b|application development"),
            rule("Data & Mathematics", "data scien|data analy|data engineer|machine learning|\\bml\\b|\\bai\\b|artificial intelligence|statistic|mathemat|\\bmath\\b|analytics|quantitative research|actuar"),
            rule("Computer Science & IT", "software|developer|programmer|computer|\\bit\\b|information technology|information systems|cyber|security engineer|devops|\\bcloud|network|\\bweb\\b|front-?end|back-?end|full[- ]stack|\\bsre\\b|database"),
            rule("Engineering", "engineer|mechanical|electrical|civil|hardware|manufactur|aerospace|industrial|robotic|embedded|semiconductor|\\brf\\b|process tech"
                    + "|concrete|installation|public works|machinist|machining|machine assembly|fabricat|\\bsystems? test|technical staff|\\bthermo|thermal"
                    + "|power electronics|avionics|propulsion|\\bfpga\\b|\\basic\\b|systems of systems|maintenance|land survey|surveying"),
            // "\\bquant" also matched "Quantum" (devlog 2026-10-05)
            rule("Finance & Accounting", "accounting|accountant|\\baudit|\\btax|financ|investment|banking|treasury|wealth|credit|trading|trader|\\bquants?\\b|asset management|private equity|capital markets|underwrit|insurance"
                    + "|\\bclaims?\\b|crypto|equity research|\\bloans?\\b|lending|controller|market risk|risk asset|risk management|reconcil|fp&a"),
            rule("Marketing & Communications", "marketing|\\bbrand|communications|public relations|\\bpr\\b|social media|content|journalis|advertis|copywrit|editorial|\\bwriter|public affairs"
                    + "|merchandis|broadcast|multimedia|consumer insights|market research|community (partnerships?|relations|engagement)"),
            rule("Arts, Design & Media", "design|graphic|\\bux\\b|\\bui\\b|\\bart\\b|\\barts\\b|video|\\bfilm|photograph|music|creative|animat|illustrat|media production|\\baudio\\b|podcast|sculpt|ceramic"),
            rule("Life Sciences & Health", "biolog|biotech|bioinformatic|clinical|health|medical|medicine|nurs|pharma|patient|life science|neuro|genetic|genomic|\\bdental|therap|veterinar|microbio|immunolog|cancer|physician|biomedical"
                    + "|protein|casework|\\b(ehs|hse|hes)\\b|\\bfood (safety|quality|science)|dietitian|dietetic"),
            rule("Physical Sciences", "chemist|chemical|physic|environment|geolog|geoscien|\\bearth|climate|meteorolog|forecast|materials scien|astronom|ocean|atmospher|energy science"
                    + "|polymer|plasma (physics|research|science)|quantum|air quality|water quality"),
            rule("Social Sciences & Psychology", "psycholog|sociolog|economic|economist|anthropolog|archaeolog|social work|social science|behavioral|human services|criminolog"),
            rule("Government, Law & Policy", "legal|\\blaw\\b|paralegal|policy|government|legislat|political|regulatory|compliance|intelligence analyst|public service|diplomat"
                    + "|clearance|forensic|urban planning|city planning|land use|landscape architecture|site development"),
            rule("Education", "teach|education|tutor|curriculum|instruction|\\bschool|academic|admissions|learning (and|&) development|training (and|&) development"),
            rule("Business & Management", "business|operations|strategy|consult|management|manager|product|project|supply chain|logistic|procurement|purchasing|human resources|\\bhr\\b|people|talent|recruit|sales|customer|account executive|real estate|administrat|entrepreneur"
                    + "|sourcing|labor relations|employee relations|organizational|landman|portfolio executive|corporate functions"));

    /**
     * Generic words that name a field only when nothing else does: tried after the title's subject words and the
     * source's category. As ordinary rules they took "Air Quality", "Structural Biology",
     * "Sports Medicine", an astrophysics REU at a museum and the CURATE addiction programme (replay, 2026-10-05).
     */
    private static final List<Map.Entry<String, Pattern>> WEAK_RULES = List.of(
            rule("Engineering", "\\bquality\\b|structural|construction"),
            rule("Data & Mathematics", "\\banalytical\\b"),
            rule("Finance & Accounting", "\\bquantitative\\b"),   // "Quantitative Intern" at a bank; "Quantitative Biology" is Life Sciences
            rule("Marketing & Communications", "\\bsports\\b"),
            rule("Arts, Design & Media", "\\bcurat|museum"),
            rule("Computer Science & IT", "\\btechnology\\b"));

    /** USAJOBS occupational series by group (first two digits), from OPM's handbook of occupational groups. */
    private static final Map<String, String> SERIES = Map.ofEntries(
            Map.entry("01", "Social Sciences & Psychology"), Map.entry("02", "Business & Management"),
            Map.entry("03", "Business & Management"), Map.entry("04", "Life Sciences & Health"),
            Map.entry("05", "Finance & Accounting"), Map.entry("06", "Life Sciences & Health"),
            Map.entry("07", "Life Sciences & Health"), Map.entry("08", "Engineering"),
            Map.entry("09", "Government, Law & Policy"), Map.entry("10", "Arts, Design & Media"),
            Map.entry("11", "Business & Management"), Map.entry("12", "Government, Law & Policy"),
            Map.entry("13", "Physical Sciences"), Map.entry("15", "Data & Mathematics"),
            Map.entry("17", "Education"), Map.entry("18", "Government, Law & Policy"),
            Map.entry("20", "Business & Management"), Map.entry("21", "Business & Management"),
            Map.entry("22", "Computer Science & IT"));
    private static final Map<String, String> NSF_DIRECTORATE = Map.of(
            // "CSE" is the API's dirAbbr for CISE; "CISE" never occurs (42 awards were Other, devlog 2026-10-05)
            "CSE", "Computer Science & IT", "ENG", "Engineering", "TIP", "Engineering", "MPS", "Physical Sciences",
            "BIO", "Life Sciences & Health", "GEO", "Physical Sciences", "SBE", "Social Sciences & Psychology",
            "EDU", "Education", "EHR", "Education");

    /** Title first, then the source's own category (department, series code, directorate), then the title's generic words, then Other. */
    static Label major(Posting p) {
        Label byTitle = matchMajor(MAJOR_RULES, words(p.title()), "title");
        if (byTitle != null) return byTitle;
        String c = p.category();
        if (c != null) {
            String mapped = switch (p.source()) {
                case "usajobs" -> c.equals("1550") ? "Computer Science & IT" : c.length() == 4 ? SERIES.get(c.substring(0, 2)) : null;
                case "nsf" -> NSF_DIRECTORATE.get(c);
                case "nih" -> "Life Sciences & Health";
                default -> null;
            };
            if (mapped != null) return new Label(mapped, p.source() + " category " + c);
            Label byCategory = matchMajor(MAJOR_RULES, c, "category");
            if (byCategory != null) return byCategory;
        }
        // no employer-name fallback: tried 2026-10-05, it decided 22 postings and about half wrongly
        // ("Conagra Brands" -> Marketing, "Cadence Design" -> Arts); employer names are brands, not fields (devlog)
        Label weak = matchMajor(WEAK_RULES, words(p.title()), "title");
        return weak != null ? weak : new Label("Other", "no rule matched");
    }

    private static Label matchMajor(List<Map.Entry<String, Pattern>> rules, String text, String field) {
        for (var r : rules) {
            Matcher m = r.getValue().matcher(text);
            if (m.find()) return new Label(r.getKey(), field + " \"" + m.group() + "\"");
        }
        return null;
    }

    private static Map.Entry<String, Pattern> rule(String major, String regex) {
        return Map.entry(major, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    // ---------- level ----------

    private static final Pattern GRAD = Pattern.compile(
            "\\bph\\.?d\\b|doctoral|master['’]?s|\\bm\\.?s\\.? (student|degree|candidate)|\\bmba\\b|graduate student|graduate degree"
                    + "|graduate program|graduate school|pursuing an? (advanced|graduate)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNDERGRAD = Pattern.compile(
            "undergrad|bachelor['’]?s|\\bb\\.?[sa]\\.? (student|degree|candidate)|\\bbs/ms\\b|rising (freshman|sophomore|junior|senior)"
                    + "|\\bfreshman\\b|\\bsophomore\\b|(4|four)[- ]year (college|university|degree|program)|associate['’]?s degree|community college",
            Pattern.CASE_INSENSITIVE);

    /** undergrad / grad / both / unknown. Never bare "graduate": "graduating in 2027" describes a senior. */
    static Label level(Posting p) {
        String text = words(p.title()) + "\n" + Objects.toString(p.description(), "");
        Matcher g = GRAD.matcher(text), u = UNDERGRAD.matcher(text);
        boolean isG = g.find(), isU = u.find();
        if (isG && isU) return new Label("both", "\"" + u.group() + "\" and \"" + g.group() + "\"");
        if (isG) return new Label("grad", "\"" + g.group() + "\"");
        if (isU) return new Label("undergrad", "\"" + u.group() + "\"");
        return new Label("unknown", "no level keyword");
    }

    // ---------- arrangement ----------

    private static final Pattern HYBRID = Pattern.compile("\\bhybrid\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMOTE = Pattern.compile("\\bremote\\b|work from home|\\bwfh\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ONSITE = Pattern.compile("\\bon-?site\\b|\\bon site\\b|\\bin[- ]office\\b|\\bin[- ]person\\b", Pattern.CASE_INSENSITIVE);
    /** Only explicit statements in a description count; boilerplate like "our remote work policy" does not. */
    private static final Pattern DESC_HYBRID = Pattern.compile(
            "\\bhybrid (work|schedule|role|position|model|internship)|\\b\\d days? (a|per) week (in|at) (the|our) office", Pattern.CASE_INSENSITIVE);
    private static final Pattern DESC_REMOTE = Pattern.compile(
            "\\b(this|the) (role|position|internship|job) is (fully |100% )?remote\\b|\\bfully remote\\b|\\b100% remote\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DESC_ONSITE = Pattern.compile(
            "\\b(this|the) (role|position|internship|job) is (based )?(on-?site|in[- ]person|in[- ]office)\\b|\\b(on-?site|in[- ]person) (role|position|internship)\\b",
            Pattern.CASE_INSENSITIVE);

    static Label arrangement(Posting p) {
        String w = p.workplace();
        if (w != null) {
            String l = w.toLowerCase(Locale.ROOT);
            if (l.contains("hybrid") || l.contains("telework")) return new Label("hybrid", "source field \"" + w + "\"");
            if (l.contains("remote")) return new Label("remote", "source field \"" + w + "\"");
            if (l.contains("onsite") || l.contains("on-site") || l.contains("office")) return new Label("in-person", "source field \"" + w + "\"");
        }
        String head = words(p.title()) + " | " + String.join(" | ", p.locations());
        Label l = firstOf(head, "title/location", HYBRID, REMOTE, ONSITE);
        if (l != null) return l;
        l = firstOf(Objects.toString(p.description(), ""), "description", DESC_HYBRID, DESC_REMOTE, DESC_ONSITE);
        return l != null ? l : new Label("not stated", "no arrangement keyword");
    }

    private static Label firstOf(String text, String field, Pattern hybrid, Pattern remote, Pattern onsite) {
        String[] names = {"hybrid", "remote", "in-person"};
        Pattern[] ps = {hybrid, remote, onsite};
        for (int i = 0; i < ps.length; i++) {
            Matcher m = ps[i].matcher(text);
            if (m.find()) return new Label(names[i], field + " \"" + m.group() + "\"");
        }
        return null;
    }

    // ---------- location ----------

    private static final Map<String, String> STATES = new LinkedHashMap<>();
    static {
        String[] s = {"Alabama", "AL", "Alaska", "AK", "Arizona", "AZ", "Arkansas", "AR", "California", "CA", "Colorado", "CO",
                "Connecticut", "CT", "Delaware", "DE", "District of Columbia", "DC", "Florida", "FL", "Georgia", "GA", "Hawaii", "HI",
                "Idaho", "ID", "Illinois", "IL", "Indiana", "IN", "Iowa", "IA", "Kansas", "KS", "Kentucky", "KY", "Louisiana", "LA",
                "Maine", "ME", "Maryland", "MD", "Massachusetts", "MA", "Michigan", "MI", "Minnesota", "MN", "Mississippi", "MS",
                "Missouri", "MO", "Montana", "MT", "Nebraska", "NE", "Nevada", "NV", "New Hampshire", "NH", "New Jersey", "NJ",
                "New Mexico", "NM", "New York", "NY", "North Carolina", "NC", "North Dakota", "ND", "Ohio", "OH", "Oklahoma", "OK",
                "Oregon", "OR", "Pennsylvania", "PA", "Rhode Island", "RI", "South Carolina", "SC", "South Dakota", "SD",
                "Tennessee", "TN", "Texas", "TX", "Utah", "UT", "Vermont", "VT", "Virginia", "VA", "Washington", "WA",
                "West Virginia", "WV", "Wisconsin", "WI", "Wyoming", "WY", "Puerto Rico", "PR"};
        for (int i = 0; i < s.length; i += 2) STATES.put(s[i], s[i + 1]);
    }
    /** Longest names first, so "West Virginia" is consumed before "Virginia" can match inside it. */
    private static final List<String> NAMES_LONGEST_FIRST =
            STATES.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
    /** Two-letter code only in address positions: ", NC", "US-NC-", "- NC", "(NC)", or at the end. Avoids words like IT, OR, IN. */
    private static final Pattern CODE = Pattern.compile("(?:,\\s*|\\bUSA?[-\\s]+|-\\s*|\\()([A-Z]{2})(?=\\s*$|[\\s,)\\-/|;])");
    private static final Pattern US = Pattern.compile("united states|\\bUSA?\\b|\\bU\\.S\\.", Pattern.CASE_INSENSITIVE);
    // ponytail: short list of the countries and hubs seen in feeds; add names when "unknown" locations show up abroad
    private static final Pattern FOREIGN = Pattern.compile(
            "\\b(canada|mexico|united kingdom|\\bUK\\b|england|scotland|ireland|germany|france|spain|italy|netherlands|belgium|poland"
                    + "|switzerland|austria|sweden|denmark|norway|finland|portugal|romania|czech|hungary|greece|turkey|israel"
                    + "|india|china|japan|korea|taiwan|hong kong|singapore|malaysia|philippines|vietnam|thailand|indonesia"
                    + "|australia|new zealand|brazil|argentina|colombia|chile|peru|costa rica|south africa|nigeria|kenya|egypt"
                    + "|morocco|united arab emirates|\\bUAE\\b|saudi|qatar|europe|\\bEMEA\\b|\\bAPAC\\b|\\bLATAM\\b"
                    + "|toronto|vancouver|montreal|london|dublin|berlin|munich|paris|amsterdam|zurich|madrid|barcelona|warsaw"
                    + "|bangalore|bengaluru|hyderabad|mumbai|pune|chennai|delhi|gurgaon|shanghai|beijing|shenzhen|tokyo|seoul"
                    + "|sydney|melbourne|manila|tel aviv|são paulo|sao paulo|mexico city"
                    // seen as state-less "US" locations on 2026-10-05
                    + "|auckland|wellington|\\bnz\\b|stockholm|cape town|sofia|belgrade|serbia|bulgaria|cologne|köln|dusseldorf|düsseldorf"
                    + "|münchen|stuttgart|frankfurt|hamburg|courbevoie|wroclaw|wrocław|krakow|kraków|dubai|abu dhabi|calgary|ottawa"
                    + "|edmonton|ontario|surrey|kuala lumpur|\\bmys\\b|latin america|santiago)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNTRY_US = Pattern.compile("us|usa|united states.*", Pattern.CASE_INSENSITIVE);

    record Where(List<String> states, boolean remoteUs, boolean anyUs, boolean allForeign) {}

    static Where where(Posting p) {
        if (p.country() != null && !COUNTRY_US.matcher(p.country().strip()).matches())
            return new Where(List.of(), false, false, true);
        Set<String> states = new TreeSet<>();
        boolean remoteUs = false, anyUs = p.country() != null, allForeign = !p.locations().isEmpty();
        for (String loc : p.locations()) {
            Set<String> found = statesIn(loc);
            boolean foreign = found.isEmpty() && FOREIGN.matcher(loc).find() && !US.matcher(loc).find();
            if (!foreign) allForeign = false;
            if (foreign) continue;
            states.addAll(found);
            if (!found.isEmpty() || US.matcher(loc).find()) anyUs = true;
            if (REMOTE.matcher(loc).find()) remoteUs = true;
        }
        return new Where(List.copyOf(states), remoteUs, anyUs, allForeign);
    }

    static boolean outsideUs(Posting p) { return where(p).allForeign(); }

    static Set<String> statesIn(String loc) {
        Set<String> out = new TreeSet<>();
        String rest = loc;
        if (rest.matches("(?i).*\\b(washington,?\\s*(d\\.?c\\.?|district of columbia))\\b.*")) {
            out.add("DC");
            rest = rest.replaceAll("(?i)washington,?\\s*(d\\.?c\\.?|district of columbia)", " ");
        }
        for (String name : NAMES_LONGEST_FIRST) {
            // "Kansas City" is mostly Missouri; "Kansas City, KS" still gets KS from its code
            String after = name.equals("Kansas") ? "\\b(?! city)" : "\\b";
            Matcher m = Pattern.compile("\\b" + Pattern.quote(name) + after, Pattern.CASE_INSENSITIVE).matcher(rest);
            if (m.find()) {
                out.add(STATES.get(name));
                rest = m.replaceAll(" ");
            }
        }
        Matcher c = CODE.matcher(rest);
        while (c.find()) if (STATES.containsValue(c.group(1))) out.add(c.group(1));
        for (Pattern p : List.of(CODE_MORE, CODE_LEADING, CODE_TRAILING)) {
            Matcher m = p.matcher(rest);
            while (m.find()) if (STATES.containsValue(m.group(1)) && !(p == CODE_LEADING && m.group(1).equals("LA"))) out.add(m.group(1));
        }
        if (out.isEmpty() && !FOREIGN.matcher(rest).find()) {   // "San José, Costa Rica" is not San Jose, CA
            Matcher m = CITY.matcher(rest);
            while (m.find()) out.add(CITIES.get(m.group(1).toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    // Code positions CODE misses, seen in 1,931 state-less postings on 2026-10-05; kept apart so CODE's matches never change.
    /** "TX-Dallas", "TX - Dallas", "VA, Portsmouth", a bare "FL", "MN-Mankato; WI-Baldwin", "US.CO.Denver", "USA_NC_Holly Springs". */
    private static final Pattern CODE_MORE = Pattern.compile("(?:^|;\\s*|(?<![A-Za-z])USA?[._]|[._])([A-Z]{2})(?=\\s*$|\\s*[-,;.]|_)");
    /** "WI Madison", "MO St Louis"; not "UT MAIN CAMPUS" (next word in capitals) and not "LA" (Los Angeles as often as Louisiana). */
    private static final Pattern CODE_LEADING = Pattern.compile("^([A-Z]{2})\\s+(?=[A-Z][a-z])");
    /** "Atlanta GA", "Indianapolis IN USA", "Omaha NE-6750": after a lower-case word, at the end or before USA or a number. */
    private static final Pattern CODE_TRAILING = Pattern.compile("(?<=[a-z.]\\s)([A-Z]{2})(?=\\s*$|\\s+USA?\\b|-\\d)");

    /**
     * US cities that name one state, tried only when no state name or code was found. Names shared with another state
     * or country are left out on purpose (Portland, Columbus, Rochester, Cambridge, Birmingham, Kingston, Manhattan).
     */
    // ponytail: hand list from the 2026-10-05 state-less locations; add names when "unknown" counts show them
    private static final Map<String, String> CITIES = new HashMap<>();
    static {
        String[][] byState = {
                {"CA", "san francisco", "south san francisco", "sf", "bay area", "silicon valley", "palo alto", "mountain view",
                        "sunnyvale", "san jose", "santa clara", "redwood city", "los altos", "berkeley", "oakland", "emeryville",
                        "fremont", "menlo park", "irvine", "long beach", "los angeles", "el segundo", "santa monica", "culver city",
                        "torrance", "san diego", "la jolla", "oxnard", "newport beach", "scotts valley", "sacramento"},
                {"IL", "chicago", "naperville", "downers grove", "rosemont", "northbrook", "schaumburg", "westmont"},
                {"MA", "boston", "braintree", "waltham", "woburn"},
                {"TX", "austin", "dallas", "houston", "plano", "san antonio", "richardson", "mckinney", "fort worth", "irving", "dfw"},
                {"WA", "seattle", "bothell", "redmond"},
                {"NY", "new york city", "nyc", "brooklyn", "queens", "bronx", "tarrytown", "sleepy hollow", "buffalo", "utica",
                        "binghamton", "poughkeepsie", "fishkill", "long island", "syracuse", "albany"},
                {"GA", "atlanta", "alpharetta", "macon"},
                {"CO", "denver", "boulder"},
                {"AZ", "phoenix", "scottsdale", "chandler", "tempe", "tucson"},
                {"PA", "philadelphia", "pittsburgh", "king of prussia", "canonsburg"},
                {"MN", "minneapolis", "mpls", "st. paul", "saint paul", "chaska", "mankato", "lakeville", "edina", "minnetonka"},
                {"IN", "indianapolis", "fort wayne", "evansville"},
                {"WI", "milwaukee", "sun prairie", "fond du lac"},
                {"MI", "detroit", "dearborn", "ann arbor", "grand rapids"},
                {"FL", "miami", "orlando", "tampa", "jacksonville", "fort lauderdale", "sarasota"},
                {"TN", "nashville", "memphis", "knoxville"},
                {"NC", "charlotte", "raleigh", "chapel hill"},
                {"MD", "baltimore", "bethesda", "hunt valley"},
                {"OH", "cincinnati", "cleveland", "akron", "dayton"},
                {"LA", "new orleans", "baton rouge", "lake charles", "shreveport"},
                {"NE", "omaha"}, {"IA", "des moines", "cedar rapids"}, {"UT", "salt lake city", "lehi"},
                {"NV", "las vegas"}, {"MO", "st. louis", "st louis", "saint louis"}, {"AR", "bentonville", "little rock"},
                {"VA", "ashburn", "reston", "mclean"}, {"NJ", "iselin", "parsippany", "trenton", "jersey city", "princeton"},
                {"KS", "topeka"}, {"ID", "boise"}, {"VT", "essex junction"}, {"CT", "hartford"}, {"KY", "louisville"},
                {"OK", "oklahoma city", "tulsa"}};
        for (String[] s : byState) for (int i = 1; i < s.length; i++) CITIES.put(s[i], s[0]);
    }
    private static final Pattern CITY = Pattern.compile("(?<![\\w.])(" + CITIES.keySet().stream()
            .sorted(Comparator.comparingInt(String::length).reversed()).map(Pattern::quote).collect(java.util.stream.Collectors.joining("|"))
            + ")(?![\\w-])", Pattern.CASE_INSENSITIVE);   // not "SF-57th & I-229" (Sioux Falls)
}
