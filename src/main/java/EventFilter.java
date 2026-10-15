import java.util.List;
import java.util.function.Predicate;

record ExcludeRule(String type, String titlePart, String group) {
    boolean matches(Event e) {
        return (type == null || type.equals(e.type()))
                && e.title().contains(titlePart)
                && group.equals(e.group());
    }
}

public class EventFilter {
    static final List<ExcludeRule> RULES = List.of(
            new ExcludeRule("Ü",  "Grundlagen Programmieren", "Gruppe 1"),
            new ExcludeRule("Ü",  "Grundlagen Programmieren", "Gruppe 3"),
            new ExcludeRule("SU", "Diskrete Mathematik",      "Gruppe 2")
    );

    public static List<Event> apply(List<Event> all) {
        Predicate<Event> excluded = e -> RULES.stream().anyMatch(r -> r.matches(e));
        return all.stream().filter(excluded.negate()).toList();
    }
}