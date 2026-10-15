import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

public class ScheduleParser {
    static final String URL = "https://fiw.thws.de/fileadmin/share/vlplan/BaWinf1_2026ws.html";
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");
    static final Pattern DATE_P  = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}");
    static final Pattern TIME_P  = Pattern.compile("(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})");
    static final Pattern TITLE_P = Pattern.compile("^(\\S+)\\s+(.*?)(?:\\s*\\[[^\\]]*\\])?$");

    record DayRange(LocalDate date, int from, int to) {}
    record Line(String text, boolean span) {}

    public static List<Event> load() throws Exception {
        HttpResponse<byte[]> res = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(URL)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        Document doc = Jsoup.parse(new ByteArrayInputStream(res.body()), null, URL);
        List<Event> events = new ArrayList<>();
        for (Element table : doc.select("table:has(td.t)")) {
            events.addAll(parseWeek(table));
        }
        return events;
    }

    static List<Event> parseWeek(Element table) {
        List<DayRange> days = new ArrayList<>();
        List<Event> result = new ArrayList<>();
        Map<Integer, Integer> busyUntil = new HashMap<>();
        int r = 0;
        for (Element tr : table.select("tr")) {
            int col = 0;
            for (Element td : tr.children()) {
                while (busyUntil.getOrDefault(col, 0) > r) col++;
                final int startCol = col;
                int colspan = spanOf(td, "colspan");
                int rowspan = spanOf(td, "rowspan");

                if (td.hasClass("t")) {
                    Matcher m = DATE_P.matcher(td.text());
                    if (m.find()) {
                        days.add(new DayRange(LocalDate.parse(m.group(), DATE), col, col + colspan - 1));
                    }
                } else if (td.hasClass("v")) {
                    days.stream()
                            .filter(d -> startCol >= d.from() && startCol <= d.to())
                            .findFirst()
                            .ifPresent(d -> parseEvent(td, d.date()).ifPresent(result::add));
                }
                for (int c = col; c < col + colspan; c++) busyUntil.put(c, r + rowspan);
                col += colspan;
            }
            r++;
        }
        return result;
    }

    static Optional<Event> parseEvent(Element td, LocalDate date) {
        List<Line> ls = lines(td);
        if (ls.size() < 2) return Optional.empty();
        Matcher tm = TIME_P.matcher(ls.get(0).text());
        Matcher hm = TITLE_P.matcher(ls.get(1).text());
        if (!tm.matches() || !hm.matches()) return Optional.empty();

        String group = null;
        List<String> spans = new ArrayList<>(), notes = new ArrayList<>();
        for (Line l : ls.subList(2, ls.size())) {
            if (l.text().isEmpty()) continue;
            if (l.span()) spans.add(l.text());
            else if (l.text().startsWith("Gruppe")) group = l.text();
            else notes.add(l.text());
        }
        boolean changed = td.attr("style").replace(" ", "").contains("border-color:red");
        return Optional.of(new Event(td.id(), date,
                LocalTime.parse(tm.group(1), TIME), LocalTime.parse(tm.group(2), TIME),
                hm.group(1), hm.group(2), group,
                spans.size() > 0 ? spans.get(0) : null,
                spans.size() > 1 ? spans.get(1) : null,
                notes.isEmpty() ? null : String.join(" | ", notes),
                changed));
    }

    static List<Line> lines(Element td) {
        List<Line> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean span = false;
        for (Node n : td.childNodes()) {
            if (n instanceof Element el && el.tagName().equals("br")) {
                out.add(new Line(clean(sb.toString()), span));
                sb.setLength(0);
                span = false;
            } else if (n instanceof Element el2) {
                sb.append(el2.text());
                span |= el2.tagName().equals("span");
            } else if (n instanceof TextNode tn) {
                sb.append(tn.text());
            }
        }
        if (sb.length() > 0) out.add(new Line(clean(sb.toString()), span));
        return out;
    }

    static String clean(String s) { return s.replace('\u00a0', ' ').strip(); }

    static int spanOf(Element td, String attr) {
        try { return Math.max(1, Integer.parseInt(td.attr(attr))); }
        catch (NumberFormatException e) { return 1; }
    }

    public static void main(String[] args) throws Exception {
        EventFilter.apply(load()).stream()
                .sorted(Comparator.comparing(Event::date).thenComparing(Event::start))
                .forEach(e -> System.out.printf("%s %s-%s %s %s %s %s%s%n",
                        e.date(), e.start(), e.end(), e.type(), e.title(),
                        e.group() == null ? "" : e.group(), e.room(),
                        e.changed() ? "  [GEÄNDERT]" : ""));
    }
}