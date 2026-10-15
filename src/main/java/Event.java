import java.time.LocalDate;
import java.time.LocalTime;

public record Event(String id, LocalDate date, LocalTime start, LocalTime end,
                    String type, String title, String group,
                    String lecturer, String room, String note, boolean changed) {}