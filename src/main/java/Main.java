import java.nio.file.Path;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        List<Event> events = EventFilter.apply(ScheduleParser.load()).stream()
                .sorted(Comparator.comparing(Event::date).thenComparing(Event::start))
                .toList();
        String previous = Notifier.fetchPrevious();
        IcsWriter.write(events, Path.of("stundenplan.ics"));
        JsonWriter.write(events, Path.of("stundenplan.json"));
        Notifier.run(previous, events);
        System.out.println(events.size() + " Termine geschrieben");
    }
}
