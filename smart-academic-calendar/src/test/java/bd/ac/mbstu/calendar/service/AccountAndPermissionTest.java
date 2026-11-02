package bd.ac.mbstu.calendar.service;

import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventFilter;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.model.Role;
import bd.ac.mbstu.calendar.model.User;
import bd.ac.mbstu.calendar.support.DatabaseTestBase;
import bd.ac.mbstu.calendar.util.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One admin sign-up, student sign-up, role-separated login, and "students add personal events only". */
class AccountAndPermissionTest extends DatabaseTestBase {

    private AuthService auth;
    private EventService events;
    private static final LocalDate DAY = LocalDate.of(2026, 11, 15);

    @BeforeEach
    void setUp() {
        auth = new AuthService(userDao);
        events = new EventService(eventDao);
    }

    private User student(String username) {
        return auth.registerStudent("Student " + username, username, "secret12", deptId("ICT"), 2, 2);
    }

    private Event personal(String title) {
        Event e = EventFactory.create(EventType.PERSONAL, title, DAY, DAY);
        e.setStartTime(LocalTime.of(17, 40));
        return e;
    }

    @Test
    void onlyIctAndCseExist() {
        assertEquals(List.of("CSE", "ICT"), departmentDao.findAll().stream().map(Department::getCode).toList());
    }

    @Test
    void theAdminAccountCanBeCreatedOnlyOnce() {
        assertFalse(auth.adminExists());
        User admin = auth.registerAdmin("University Office", "Office1", "secret12");
        assertEquals(Role.ADMIN, admin.getRole());
        assertEquals("office1", admin.getUsername());
        assertTrue(auth.adminExists());
        assertThrows(PermissionDeniedException.class, () -> auth.registerAdmin("Second", "other", "secret12"));
    }

    @Test
    void studentsSignUpWithDepartmentYearAndSemester() {
        User s = student("rahim");
        assertEquals(Role.STUDENT, s.getRole());
        assertEquals(Integer.valueOf(2), s.getYear());
        assertThrows(ValidationException.class,
                () -> auth.registerStudent("No Dept", "nodept", "secret12", null, 2, 2));
        assertThrows(ValidationException.class,
                () -> auth.registerStudent("Bad Year", "badyear", "secret12", deptId("ICT"), 5, 2));
        assertThrows(ValidationException.class,
                () -> auth.registerStudent("Short", "shortpw", "123", deptId("ICT"), 2, 2));
        assertThrows(ValidationException.class, () -> student("rahim")); // username taken
    }

    @Test
    void loginIsSeparatedIntoStudentAndAdmin() {
        auth.registerAdmin("University Office", "office1", "secret12");
        student("rahim");
        assertEquals("rahim", auth.login("rahim", "secret12", Role.STUDENT).getUsername());
        assertEquals("office1", auth.login("office1", "secret12", Role.ADMIN).getUsername());
        assertThrows(AuthenticationException.class, () -> auth.login("rahim", "secret12", Role.ADMIN));
        assertThrows(AuthenticationException.class, () -> auth.login("office1", "secret12", Role.STUDENT));
        assertThrows(AuthenticationException.class, () -> auth.login("rahim", "wrongpass", Role.STUDENT));
    }

    @Test
    void studentsCanAddOnlyPersonalEvents() {
        User s = student("rahim");
        Event saved = events.create(s, personal("Playing football"));
        assertTrue(saved.isPersonal());
        assertEquals(EventType.PERSONAL, saved.getType());
        assertEquals(LocalTime.of(17, 40), eventDao.findById(saved.getId()).orElseThrow().getStartTime());

        for (EventType forbidden : new EventType[] {EventType.CLASS_TEST, EventType.EXAM, EventType.HOLIDAY,
                EventType.SEMESTER_BREAK, EventType.ASSIGNMENT_DEADLINE, EventType.DEPARTMENT_EVENT, EventType.NOTICE}) {
            Event e = newEvent(forbidden, "Not allowed", DAY, DAY, "ICT", 2, 2);
            assertThrows(PermissionDeniedException.class, () -> events.create(s, e), forbidden.name());
        }
    }

    @Test
    void aStudentCannotTurnAPersonalEventIntoAnAcademicOne() {
        User s = student("rahim");
        Event saved = events.create(s, personal("Gym"));
        Event sneaky = newEvent(EventType.HOLIDAY, "Gym", DAY, DAY, null, null, null);
        sneaky.setId(saved.getId());
        events.update(s, sneaky);
        Event loaded = eventDao.findById(saved.getId()).orElseThrow();
        assertEquals(EventType.PERSONAL, loaded.getType());
        assertFalse(loaded.isClassOff());
    }

    @Test
    void personalEventsAreVisibleOnlyToTheirOwner() {
        User admin = auth.registerAdmin("University Office", "office1", "secret12");
        User rahim = student("rahim");
        User karim = student("karim");
        events.create(rahim, personal("Playing at 5:40 pm"));

        EventFilter all = new EventFilter();
        assertEquals(1, events.find(rahim, new EventFilter()).size());
        assertTrue(events.find(karim, new EventFilter()).isEmpty());
        assertTrue(events.find(admin, new EventFilter()).isEmpty());
        assertNull(all.getViewerUserId());
    }

    @Test
    void onlyTheAdminAddsOfficialEventsAndEveryoneSeesThem() {
        User admin = auth.registerAdmin("University Office", "office1", "secret12");
        User rahim = student("rahim");
        Event official = events.create(admin, newEvent(EventType.CLASS_TEST, "ICT-2207 Class Test", DAY, DAY, "ICT", 2, 2));
        assertFalse(official.isPersonal());
        assertEquals(1, events.find(rahim, new EventFilter()).size());
        official.setTitle("Hacked");
        assertThrows(PermissionDeniedException.class, () -> events.update(rahim, official));
        assertThrows(PermissionDeniedException.class, () -> events.delete(rahim, official.getId()));
    }
}
