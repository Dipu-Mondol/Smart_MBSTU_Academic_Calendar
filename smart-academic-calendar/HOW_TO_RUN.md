# How to run

Requirements: JDK 17 or newer, Maven 3.8 or newer, internet for the first build.

    mvn clean javafx:run        # start the app
    mvn test                    # run the unit tests
    mvn clean package           # build target/smart-academic-calendar-1.0.0.jar
    java -jar target/smart-academic-calendar-1.0.0.jar

First start: there are no accounts yet. The login page opens on "Admin - Sign up".
The university office creates the ONE admin account there (a second admin sign-up is refused).
After that, students use the Student tab and Sign up (department ICT or CSE, year, semester).
Students can add only personal events (visible only to them). Class tests, exams, assignments
and holidays are added by the admin.

AI keys (optional): copy config/app.properties.example to config/app.properties
and set ai.provider plus the matching key. Default is offline mode, no key needed.

To reset the database: close the app and delete the data/ folder.

Calendar facts (taken from the printed MBSTU 2026 calendar):
- Weekly off days: Thursday and Friday. The week starts on Saturday.
- All 25 holidays and vacations in src/main/resources/seed/holidays_2026.json match the two tables printed at the bottom of the calendar (class off / office off, dates and number of days).
- Bangla and Hijri day numbers are computed by the program. Hijri days can differ by one day from the print (local moon sighting). Change hijri.offset.days in config/app.properties if needed.

If you ran an older version: the old database file is renamed to calendar-old-....db automatically
and a fresh one is created (you do not need to delete anything).
