import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

public class Notifier {
    static final String LIVE = "https://tilsturm2506-pixel.github.io/Stundenplan.thws/";
    static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd.MM.");

    static String s(String n) { return "(?<" + n + ">(?:[^\"\\\\]|\\\\.)*)"; }
    static String ns(String n) { return "(?:null|\"" + s(n) + "\")"; }
    static final Pattern P = Pattern.compile(
            "\\{\"date\":\"" + s("date") + "\",\"start\":\"" + s("start") + "\",\"end\":\"" + s("end")
                    + "\",\"type\":\"" + s("type") + "\",\"title\":\"" + s("title") + "\",\"group\":" + ns("group")
                    + ",\"lecturer\":" + ns("lec") + ",\"room\":" + ns("room") + ",\"note\":" + ns("note")
                    + ",\"changed\":(?<ch>true|false)\\}");

    record Row(String date, String start, String type, String title, String group, String room) {
        String key() { return date + "|" + start + "|" + type + "|" + title + "|" + (group == null ? "" : group); }
        String text() { return title + " am " + LocalDate.parse(date).format(DM) + " " + start; }
    }

    static String key(Event e) {
        return e.date() + "|" + e.start() + "|" + e.type() + "|" + e.title() + "|" + (e.group() == null ? "" : e.group());
    }

    public static String fetchPrevious() {
        try {
            HttpResponse<String> r = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(LIVE + "stundenplan.json")).timeout(java.time.Duration.ofSeconds(20)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return r.statusCode() == 200 ? r.body() : null;
        } catch (Exception e) { return null; }
    }

    static List<Row> parse(String json) {
        List<Row> out = new ArrayList<>();
        Matcher m = P.matcher(json);
        while (m.find()) out.add(new Row(m.group("date"), m.group("start"), m.group("type"), m.group("title"), m.group("group"), m.group("room")));
        return out;
    }

    public static void run(String prevJson, List<Event> events) {
        String topic = System.getenv("NTFY_TOPIC");
        if (topic == null || topic.isBlank()) return;
        if (!"schedule".equals(System.getenv("EVENT_NAME"))) return;
        LocalDate today = LocalDate.now(BERLIN);

        if (prevJson != null) {
            Map<String, Row> old = new LinkedHashMap<>();
            for (Row r : parse(prevJson)) if (r.date().compareTo(today.toString()) >= 0) old.put(r.key(), r);
            List<String> out = new ArrayList<>();
            for (Event e : events) {
                if (e.date().isBefore(today)) continue;
                Row r = old.remove(key(e));
                String t = e.title() + " am " + e.date().format(DM) + " " + e.start();
                if (r == null) out.add("Neu: " + t + (e.room() != null ? " (" + e.room() + ")" : ""));
                else if (!Objects.equals(r.room(), e.room()))
                    out.add("Raum: " + t + ": " + (r.room() == null ? "-" : r.room()) + " -> " + (e.room() == null ? "-" : e.room()));
            }
            for (Row r : old.values()) out.add("Entfällt: " + r.text());
            if (!out.isEmpty()) {
                if (out.size() > 8) { int n = out.size() - 8; out = new ArrayList<>(out.subList(0, 8)); out.add("… und " + n + " weitere"); }
                send(topic, "Änderungen im Stundenplan\n" + String.join("\n", out));
            }
        }

        if ("30 4,5 * * *".equals(System.getenv("SCHEDULE")) && ZonedDateTime.now(BERLIN).getHour() == 6) {
            List<Event> td = events.stream().filter(e -> e.date().equals(today)).toList();
            if (!td.isEmpty()) {
                StringBuilder sb = new StringBuilder("Heute: " + td.size() + " Termine · "
                        + td.get(0).start() + "–" + td.get(td.size() - 1).end() + " Uhr");
                for (Event e : td)
                    sb.append("\n").append(e.start()).append("–").append(e.end()).append(" ")
                            .append(e.type()).append(" ").append(e.title())
                            .append(e.room() != null ? " (" + e.room() + ")" : "");
                send(topic, sb.toString());
            }
        }
    }

    static void send(String topic, String body) {
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create("https://ntfy.sh/" + topic))
                    .header("Title", "Stundenplan").header("Tags", "calendar").header("Click", LIVE)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpClient.newHttpClient().send(r, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) { System.err.println("ntfy: " + e); }
    }
}
