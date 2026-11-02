package bd.ac.mbstu.calendar.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Date helper methods used by services and tests.
 */
public final class DateUtil {

    private DateUtil() {
    }

    /** Every date from start to end, both included. */
    public static List<LocalDate> eachDay(LocalDate start, LocalDate end) {
        Validator.requireDateRange(start, end);
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            days.add(d);
        }
        return days;
    }

    /** Number of days in a range, both ends included (1 Jan to 1 Jan = 1). */
    public static long inclusiveDays(LocalDate start, LocalDate end) {
        Validator.requireDateRange(start, end);
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    /** True if range A and range B share at least one day. */
    public static boolean rangesOverlap(LocalDate aStart, LocalDate aEnd,
                                        LocalDate bStart, LocalDate bEnd) {
        return !aStart.isAfter(bEnd) && !bStart.isAfter(aEnd);
    }

    /** First day of the week containing the given date. The MBSTU calendar week starts on SATURDAY. */
    public static LocalDate startOfWeek(LocalDate date) {
        int daysFromSaturday = (date.getDayOfWeek().getValue() + 1) % 7; // Sat=6 -> 0, Sun=7 -> 1, Fri=5 -> 6
        return date.minusDays(daysFromSaturday);
    }
}
