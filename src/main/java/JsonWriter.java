import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public class JsonWriter {
    public static void write(List<Event> events, Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"generated\":")
                .append(q(OffsetDateTime.now(ZoneId.of("Europe/Berlin")).toString()))
                .append(",\"events\":[");
        for (int i = 0; i < events.size(); i++) {
            Event e = events.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"date\":").append(q(e.date().toString()))
                    .append(",\"start\":").append(q(e.start().toString()))
                    .append(",\"end\":").append(q(e.end().toString()))
                    .append(",\"type\":").append(q(e.type()))
                    .append(",\"title\":").append(q(e.title()))
                    .append(",\"group\":").append(q(e.group()))
                    .append(",\"lecturer\":").append(q(e.lecturer()))
                    .append(",\"room\":").append(q(e.room()))
                    .append(",\"note\":").append(q(e.note()))
                    .append(",\"changed\":").append(e.changed())
                    .append('}');
        }
        sb.append("]}");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    static String q(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
}