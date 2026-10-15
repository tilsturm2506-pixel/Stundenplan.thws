import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class IcsWriter {
    static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    public static void write(List<Event> events, Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        line(sb, "BEGIN:VCALENDAR");
        line(sb, "VERSION:2.0");
        line(sb, "PRODID:-//Stundenplan BWI1//DE");
        line(sb, "CALSCALE:GREGORIAN");
        line(sb, "X-WR-CALNAME:Stundenplan BWI1");
        line(sb, "REFRESH-INTERVAL;VALUE=DURATION:PT1H");
        line(sb, "X-PUBLISHED-TTL:PT1H");
        String stamp = ZonedDateTime.now(ZoneOffset.UTC).format(UTC);
        for (Event e : events) {
            line(sb, "BEGIN:VEVENT");
            line(sb, "UID:" + uid(e) + "@stundenplan-bwi1");
            line(sb, "DTSTAMP:" + stamp);
            line(sb, "DTSTART:" + utc(e.date(), e.start()));
            line(sb, "DTEND:" + utc(e.date(), e.end()));
            line(sb, "SUMMARY:" + esc((e.changed() ? "⚠ " : "") + e.type() + " " + e.title()));
            if (e.room() != null) line(sb, "LOCATION:" + esc(e.room()));
            line(sb, "DESCRIPTION:" + esc(description(e)));
            line(sb, "END:VEVENT");
        }
        line(sb, "END:VCALENDAR");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    static String description(Event e) {
        List<String> parts = new ArrayList<>();
        if (e.group() != null) parts.add(e.group());
        if (e.lecturer() != null) parts.add("Dozent: " + e.lecturer());
        if (e.note() != null) parts.add(e.note());
        else if (e.changed()) parts.add("Geändert laut Plan");
        return String.join("\n", parts);
    }

    // Stabile UID ohne Raum: eine Raumänderung aktualisiert den Termin statt ihn zu duplizieren
    static String uid(Event e) {
        String key = e.date() + "|" + e.start() + "|" + e.type() + "|" + e.title() + "|" + e.group();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    static String utc(LocalDate d, LocalTime t) {
        return ZonedDateTime.of(d, t, ZONE).withZoneSameInstant(ZoneOffset.UTC).format(UTC);
    }

    static String esc(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;")
                .replace(",", "\\,").replace("\n", "\\n");
    }

    static void line(StringBuilder sb, String s) { sb.append(s).append("\r\n"); }
}