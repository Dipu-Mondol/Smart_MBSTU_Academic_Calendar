package bd.ac.mbstu.calendar.controller;

import bd.ac.mbstu.calendar.dao.DepartmentDao;
import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.model.Role;
import bd.ac.mbstu.calendar.util.Async;
import bd.ac.mbstu.calendar.util.I18n;
import bd.ac.mbstu.calendar.util.Theme;
import bd.ac.mbstu.calendar.view.Ui;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.net.URL;
import java.util.List;

/**
 * The first screen. Two kinds of people use it:
 *   Student : Sign in / Sign up (department, year, semester)
 *   Admin   : Sign in / Sign up. The admin account can be created only ONCE (university office).
 * Passwords are checked with BCrypt on a background thread so the window never freezes.
 */
public class LoginController {

    private enum Mode { SIGN_IN, SIGN_UP }

    private final AppShell shell;
    private final StackPane root = new StackPane();
    private final VBox formBox = new VBox(10);
    private final Label messageLabel = new Label();
    private final List<Department> departments = new DepartmentDao().findAll();

    private final ToggleButton studentTab = new ToggleButton();
    private final ToggleButton adminTab = new ToggleButton();
    private final ToggleButton signInTab = new ToggleButton();
    private final ToggleButton signUpTab = new ToggleButton();

    private Role role = Role.STUDENT;
    private Mode mode = Mode.SIGN_IN;
    private String prefillUsername = "";
    private boolean syncing;

    public LoginController(AppShell shell) {
        this.shell = shell;
        root.getStyleClass().add("login-root");

        Region overlay = new Region();
        overlay.getStyleClass().add("login-overlay");

        Label title = new Label(I18n.t("app_title"));
        title.getStyleClass().add("login-title");
        Label university = new Label(I18n.t("university"));
        university.getStyleClass().add("login-university");
        university.setWrapText(true);
        university.setTextAlignment(TextAlignment.CENTER);

        VBox card = new VBox(12, Ui.logo(96), title, university,
                buildTabs(), formBox, messageLabel, buildHint(), buildToggles());
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(440);
        card.setPrefWidth(440);

        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(Double.MAX_VALUE);

        StackPane centre = new StackPane(card);
        centre.setPadding(new Insets(24));
        ScrollPane scroll = new ScrollPane(centre);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);

        root.getChildren().addAll(buildBackground(), overlay, scroll);

        // First start: nobody has created the admin account yet, so show that screen first.
        if (!shell.getAuth().adminExists()) {
            role = Role.ADMIN;
            mode = Mode.SIGN_UP;
            show(I18n.t("admin_first_run"), false);
        }
        syncTabs();
        renderForm();
    }

    public Parent getView() {
        return root;
    }

    // ---------------------------------------------------------------
    // Layout pieces
    // ---------------------------------------------------------------

    /** The campus photo, scaled to cover the whole window. */
    private Node buildBackground() {
        Pane pane = new Pane();
        URL url = LoginController.class.getResource("/images/mbstu_campus.jpg");
        if (url != null) {
            Image image = new Image(url.toExternalForm());
            ImageView view = new ImageView(image);
            view.setPreserveRatio(true);
            pane.getChildren().add(view);
            Runnable fit = () -> {
                double w = pane.getWidth();
                double h = pane.getHeight();
                if (w <= 0 || h <= 0) {
                    return;
                }
                double scale = Math.max(w / image.getWidth(), h / image.getHeight());
                view.setFitWidth(image.getWidth() * scale);
                view.setLayoutX((w - image.getWidth() * scale) / 2);
                view.setLayoutY((h - image.getHeight() * scale) / 2);
            };
            pane.widthProperty().addListener((o, a, b) -> fit.run());
            pane.heightProperty().addListener((o, a, b) -> fit.run());
            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(pane.widthProperty());
            clip.heightProperty().bind(pane.heightProperty());
            pane.setClip(clip);
        }
        return pane;
    }

    private Node buildTabs() {
        ToggleGroup roleGroup = new ToggleGroup();
        setupTab(studentTab, I18n.roleLabel(Role.STUDENT), roleGroup);
        setupTab(adminTab, I18n.roleLabel(Role.ADMIN), roleGroup);
        ToggleGroup modeGroup = new ToggleGroup();
        setupTab(signInTab, I18n.t("mode_signin"), modeGroup);
        setupTab(signUpTab, I18n.t("mode_signup"), modeGroup);

        roleGroup.selectedToggleProperty().addListener((o, oldT, newT) -> {
            if (syncing) {
                return;
            }
            if (newT == null) {
                oldT.setSelected(true);
                return;
            }
            role = newT == adminTab ? Role.ADMIN : Role.STUDENT;
            messageLabel.setText("");
            syncTabs();
            renderForm();
        });
        modeGroup.selectedToggleProperty().addListener((o, oldT, newT) -> {
            if (syncing) {
                return;
            }
            if (newT == null) {
                oldT.setSelected(true);
                return;
            }
            mode = newT == signUpTab ? Mode.SIGN_UP : Mode.SIGN_IN;
            messageLabel.setText("");
            syncTabs();
            renderForm();
        });

        HBox roles = new HBox(0, studentTab, adminTab);
        HBox modes = new HBox(0, signInTab, signUpTab);
        roles.setAlignment(Pos.CENTER);
        modes.setAlignment(Pos.CENTER);
        return new VBox(8, roles, modes);
    }

    private void setupTab(ToggleButton tab, String text, ToggleGroup group) {
        tab.setText(text);
        tab.setToggleGroup(group);
        tab.getStyleClass().add("segment-button");
        tab.setMinWidth(130);
    }

    /** Makes the four tab buttons show the current role and mode. */
    private void syncTabs() {
        boolean adminExists = shell.getAuth().adminExists();
        if (role == Role.ADMIN && adminExists && mode == Mode.SIGN_UP) {
            mode = Mode.SIGN_IN; // the admin account exists already: sign-up is closed
        }
        syncing = true;
        try {
            studentTab.setSelected(role == Role.STUDENT);
            adminTab.setSelected(role == Role.ADMIN);
            signInTab.setSelected(mode == Mode.SIGN_IN);
            signUpTab.setSelected(mode == Mode.SIGN_UP);
            signUpTab.setDisable(role == Role.ADMIN && adminExists);
        } finally {
            syncing = false;
        }
    }

    private Node buildHint() {
        Label hint = new Label(I18n.t("login_hint"));
        hint.getStyleClass().add("login-hint");
        hint.setWrapText(true);
        hint.setTextAlignment(TextAlignment.CENTER);
        return hint;
    }

    /** Language and theme buttons, so the user can switch before signing in. */
    private Node buildToggles() {
        Button language = Ui.button(I18n.isBangla() ? "English" : "বাংলা", FontAwesomeSolid.LANGUAGE, "ghost-button");
        language.setTooltip(new Tooltip(I18n.t("switch_language")));
        language.setOnAction(e -> shell.toggleLanguage());

        Button theme = Ui.button("", Theme.isDark() ? FontAwesomeSolid.SUN : FontAwesomeSolid.MOON, "ghost-button");
        theme.setTooltip(new Tooltip(I18n.t("switch_theme")));
        theme.setOnAction(e -> shell.toggleTheme());

        HBox row = new HBox(10, language, theme);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    // ---------------------------------------------------------------
    // The form (changes with the tabs)
    // ---------------------------------------------------------------

    private void renderForm() {
        formBox.getChildren().clear();
        TextField nameField = new TextField();
        TextField usernameField = new TextField(prefillUsername);
        PasswordField passwordField = new PasswordField();
        PasswordField confirmField = new PasswordField();
        nameField.setPromptText(I18n.t("full_name"));
        usernameField.setPromptText(I18n.t("username"));
        passwordField.setPromptText(I18n.t("password"));
        confirmField.setPromptText(I18n.t("confirm_password"));

        ComboBox<Department> deptBox = new ComboBox<>(FXCollections.observableArrayList(departments));
        deptBox.setConverter(Ui.departmentConverter());
        deptBox.setPromptText(I18n.t("pick_department"));
        deptBox.setMaxWidth(Double.MAX_VALUE);
        if (!departments.isEmpty()) {
            deptBox.setValue(departments.get(0));
        }
        ComboBox<Integer> yearBox = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4));
        yearBox.setConverter(Ui.yearConverter());
        yearBox.setPromptText(I18n.t("f_year"));
        yearBox.setValue(1);
        ComboBox<Integer> semesterBox = new ComboBox<>(FXCollections.observableArrayList(1, 2));
        semesterBox.setConverter(Ui.semesterConverter());
        semesterBox.setPromptText(I18n.t("f_semester"));
        semesterBox.setValue(1);
        yearBox.setMaxWidth(Double.MAX_VALUE);
        semesterBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(yearBox, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(semesterBox, javafx.scene.layout.Priority.ALWAYS);

        Button submit = new Button();
        submit.getStyleClass().add("primary-button");
        submit.setMaxWidth(Double.MAX_VALUE);

        if (mode == Mode.SIGN_IN) {
            submit.setText(I18n.t("login"));
            submit.setGraphic(Ui.icon(FontAwesomeSolid.SIGN_IN_ALT, 14));
            formBox.getChildren().addAll(usernameField, passwordField, submit);
            usernameField.setOnAction(e -> passwordField.requestFocus());
            Runnable signIn = () -> signIn(submit, usernameField.getText(), passwordField);
            passwordField.setOnAction(e -> signIn.run());
            submit.setOnAction(e -> signIn.run());
        } else {
            boolean student = role == Role.STUDENT;
            submit.setText(I18n.t(student ? "btn_signup_student" : "btn_signup_admin"));
            submit.setGraphic(Ui.icon(FontAwesomeSolid.USER_PLUS, 14));
            formBox.getChildren().addAll(nameField, usernameField, passwordField, confirmField);
            if (student) {
                formBox.getChildren().addAll(deptBox, new HBox(8, yearBox, semesterBox));
            }
            formBox.getChildren().add(submit);
            submit.setOnAction(e -> signUp(submit, nameField.getText(), usernameField.getText(),
                    passwordField.getText(), confirmField.getText(),
                    deptBox.getValue(), yearBox.getValue(), semesterBox.getValue()));
        }
        Platform.runLater(() -> (mode == Mode.SIGN_UP ? nameField : usernameField).requestFocus());
    }

    // ---------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------

    private void signIn(Button submit, String username, PasswordField passwordField) {
        messageLabel.setText("");
        String password = passwordField.getText();
        submit.setDisable(true);
        Async.run(() -> shell.getAuth().login(username, password, role),
                user -> shell.onLoggedIn(user),
                err -> {
                    submit.setDisable(false);
                    passwordField.clear();
                    show(Ui.userMessage(err), true);
                    passwordField.requestFocus();
                });
    }

    private void signUp(Button submit, String name, String username, String password, String confirm,
                        Department dept, Integer year, Integer semester) {
        messageLabel.setText("");
        if (!password.equals(confirm)) {
            show(I18n.t("passwords_differ"), true);
            return;
        }
        submit.setDisable(true);
        Async.run(() -> role == Role.ADMIN
                        ? shell.getAuth().registerAdmin(name, username, password)
                        : shell.getAuth().registerStudent(name, username, password,
                                dept == null ? null : dept.getId(), year, semester),
                user -> {
                    prefillUsername = user.getUsername();
                    mode = Mode.SIGN_IN;
                    syncTabs();
                    renderForm();
                    show(I18n.t("signup_done"), false);
                },
                err -> {
                    submit.setDisable(false);
                    show(Ui.userMessage(err), true);
                });
    }

    private void show(String text, boolean error) {
        messageLabel.getStyleClass().removeAll("error-label", "info-label");
        messageLabel.getStyleClass().add(error ? "error-label" : "info-label");
        messageLabel.setText(text);
    }
}
