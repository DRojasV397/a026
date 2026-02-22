package com.app.ui.admin;

import com.app.model.UserDTO;
import com.app.service.storage.AvatarStorageService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;



public class AdminController {

    // ── FXML ──────────────────────────────────────────────────────────────────
    @FXML private Label kpiActiveUsers;
    @FXML private Label kpiFullAccess;
    @FXML private Label kpiOnlineToday;
    @FXML private Label kpiPendingVerify;

    @FXML private TextField searchField;
    @FXML private VBox      usersContainer;

    @FXML private Button btnPrevPage;
    @FXML private Button btnNextPage;
    @FXML private Label  pageLabel;

    // ── Estado ────────────────────────────────────────────────────────────────
    private static final int PAGE_SIZE = 4;
    private int currentPage = 0;

    private List<UserDTO> allUsers   = new ArrayList<>();
    private List<UserDTO> filtered   = new ArrayList<>();

    private static final DateTimeFormatter FMT_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_DATETIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");


    // ── Constraints compartidos: úsalos en header y en cada fila ──
    private static final double COL_USER_MIN   = 140;
    private static final double COL_USER_MAX   = 9999;
    private static final double COL_ROLE_MIN   = 110;   // mínimo
    private static final double COL_ROLE_MAX     = 140;
    private static final double COL_STATUS_MIN = 99;    // mínimo
    private static final double COL_STATUS_MAX = 140;
    private static final double COL_LASTACCESS = 145;
    private static final double COL_REGDATE    = 115;
    private static final double COL_ACTIONS    = 44;

    // ── Init ──────────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        allUsers = getMockUsers();
        filtered = new ArrayList<>(allUsers);

        usersContainer.getChildren().addFirst(buildTableHeader());

        updateKpis();
        renderPage();

        // Filtrado reactivo
        searchField.textProperty().addListener((obs, o, v) -> {
            applyFilter(v);
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  KPIs
    // ═════════════════════════════════════════════════════════════════════════

    private void updateKpis() {
        long active         = allUsers.stream().filter(this::isActive).count();
        long fullAccess     = allUsers.stream().filter(this::isAdmin).count();
        long onlineToday    = allUsers.stream().filter(this::accessedToday).count();
        long pendingVerify  = allUsers.stream().filter(u -> !u.isVerified()).count();

        kpiActiveUsers.setText(String.valueOf(active));
        kpiFullAccess.setText(String.valueOf(fullAccess));
        kpiOnlineToday.setText(String.valueOf(onlineToday));
        kpiPendingVerify.setText(String.valueOf(pendingVerify));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  FILTRO Y PAGINACIÓN
    // ═════════════════════════════════════════════════════════════════════════

    private void applyFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isBlank()) {
            filtered = new ArrayList<>(allUsers);
        } else {
            filtered = allUsers.stream().filter(u ->
                    (u.fullName()  != null && u.fullName().toLowerCase().contains(q)) ||
                            (u.email()     != null && u.email().toLowerCase().contains(q))    ||
                            (u.username()  != null && u.username().toLowerCase().contains(q))
            ).collect(Collectors.toList());
        }
        currentPage = 0;
        renderPage();
    }

    private void renderPage() {
        // Conserva el header (índice 0), borra solo las filas
        if (!usersContainer.getChildren().isEmpty()) {
            usersContainer.getChildren().remove(1, usersContainer.getChildren().size());
        }

        int total = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
        currentPage = Math.min(currentPage, totalPages - 1);

        int from = currentPage * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, total);

        if (total == 0) {
            VBox empty = new VBox(8);
            empty.getStyleClass().add("empty-state");
            empty.setAlignment(Pos.CENTER);
            Label icon = new Label("🔍");
            icon.getStyleClass().add("empty-state-icon");
            Label msg = new Label("No se encontraron usuarios");
            msg.getStyleClass().add("empty-state-text");
            empty.getChildren().addAll(icon, msg);
            usersContainer.getChildren().add(empty);
        } else {
            for (int i = from; i < to; i++) {
                usersContainer.getChildren().add(buildUserRow(filtered.get(i)));
            }
        }

        pageLabel.setText("Página " + (currentPage + 1) + " de " + totalPages);
        btnPrevPage.setDisable(currentPage == 0);
        btnNextPage.setDisable(currentPage >= totalPages - 1);
    }

    @FXML
    private void onPrevPage() {
        if (currentPage > 0) { currentPage--; renderPage(); }
    }

    @FXML
    private void onNextPage() {
        int totalPages = (int) Math.ceil((double) filtered.size() / PAGE_SIZE);
        if (currentPage < totalPages - 1) { currentPage++; renderPage(); }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  FILA DE USUARIO
    // ═════════════════════════════════════════════════════════════════════════


    private void applyColumnConstraints(GridPane grid) {
        // Col 0: Usuario — crece con el espacio disponible
        ColumnConstraints colUser = new ColumnConstraints();
        colUser.setMinWidth(COL_USER_MIN);
        colUser.setMaxWidth(COL_USER_MAX);
        colUser.setHgrow(Priority.ALWAYS);

        // Col 1–5: fijas
        ColumnConstraints colRole   = flexible(COL_ROLE_MIN,   COL_ROLE_MAX);
        ColumnConstraints colStatus = flexible(COL_STATUS_MIN, COL_STATUS_MAX);
        ColumnConstraints colLast   = fixed(COL_LASTACCESS);
        ColumnConstraints colReg    = fixed(COL_REGDATE);
        ColumnConstraints colAct    = fixed(COL_ACTIONS);

        grid.getColumnConstraints().addAll(
                colUser, colRole, colStatus, colLast, colReg, colAct
        );
    }

    private ColumnConstraints fixed(double width) {
        ColumnConstraints c = new ColumnConstraints();
        c.setMinWidth(width);
        c.setMaxWidth(width);
        c.setPrefWidth(width);
        c.setHgrow(Priority.NEVER);
        return c;
    }

    /** Columna que tiene un mínimo pero puede crecer hasta max */
    private ColumnConstraints flexible(double min, double max) {
        ColumnConstraints c = new ColumnConstraints();
        c.setMinWidth(min);
        c.setMaxWidth(max);
        c.setPrefWidth(min);      // arranca en el mínimo
        c.setHgrow(Priority.ALWAYS);  // crece si hay espacio sobrante
        return c;
    }

    /** Header de la tabla — mismo GridPane, mismos constraints */
    private GridPane buildTableHeader() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("table-header");
        applyColumnConstraints(grid);

        Label roleTh = styledHeader("Rol");
        roleTh.setAlignment(Pos.CENTER);
        roleTh.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Label edoTh = styledHeader("Estado");
        edoTh.setAlignment(Pos.CENTER);
        edoTh.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);


        grid.add(styledHeader("Usuario"),        0, 0);
        grid.add(roleTh, 1, 0);
        grid.add(edoTh,  2, 0);
        grid.add(styledHeader("Último acceso"),  3, 0);
        grid.add(styledHeader("Registro"),       4, 0);
        grid.add(styledHeader(""),               5, 0);

        return grid;
    }

    private Label styledHeader(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("th-label");
        l.setMaxWidth(Double.MAX_VALUE);
        GridPane.setFillWidth(l, true);
        return l;
    }

    private GridPane buildUserRow(UserDTO user) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("user-row");
        grid.setAlignment(Pos.CENTER_LEFT);
        applyColumnConstraints(grid);

        // ── Col 0: Avatar + nombre + email ───────────────────────
        HBox userCell = new HBox(10);
        userCell.setAlignment(Pos.CENTER_LEFT);
        userCell.setMaxWidth(Double.MAX_VALUE);

        StackPane avatarWrap = new StackPane();
        avatarWrap.setMinWidth(36); avatarWrap.setMaxWidth(36);
        avatarWrap.setMinHeight(36); avatarWrap.setMaxHeight(36);
        avatarWrap.getStyleClass().add("user-avatar-wrap");

        ImageView avatarView = new ImageView();
        avatarView.setFitWidth(36); avatarView.setFitHeight(36);
        avatarView.setPreserveRatio(false);
        avatarView.setClip(new Circle(18, 18, 18));
        loadUserAvatar(avatarView, user);
        avatarWrap.getChildren().add(avatarView);

        VBox nameBox = new VBox(2);
        nameBox.setMinWidth(0);
        HBox.setHgrow(nameBox, Priority.ALWAYS);

        Label nameLabel = new Label(user.fullName() != null ? user.fullName() : "—");
        nameLabel.getStyleClass().add("user-name");
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        Label emailLabel = new Label(user.email() != null ? user.email() : "—");
        emailLabel.getStyleClass().add("user-email");
        emailLabel.setMaxWidth(Double.MAX_VALUE);

        nameBox.getChildren().addAll(nameLabel, emailLabel);
        userCell.getChildren().addAll(avatarWrap, nameBox);
        GridPane.setFillWidth(userCell, true);

        // ── Col 1: Rol pill ───────────────────────────────────────
        HBox roleCell = new HBox();
        roleCell.setAlignment(Pos.CENTER);
        roleCell.getChildren().add(
                buildRolePill(user.roleDisplay() != null ? user.roleDisplay() : user.role())
        );

        // ── Col 2: Estado ─────────────────────────────────────────
        HBox statusCell = new HBox();
        statusCell.setAlignment(Pos.CENTER);
        boolean active = isActive(user);
        Label statusLabel = new Label(active ? "● Activo" : "● Inactivo");
        statusLabel.getStyleClass().add(active ? "status-dot-active" : "status-dot-inactive");
        statusCell.getChildren().add(statusLabel);

        // ── Col 3: Último acceso ──────────────────────────────────
        Label lastLabel = new Label(
                user.lastAccess() != null ? user.lastAccess().format(FMT_DATETIME) : "—"
        );
        lastLabel.getStyleClass().add("date-label");

        // ── Col 4: Registro ───────────────────────────────────────
        Label regLabel = new Label(
                user.memberSince() != null ? user.memberSince().format(FMT_DATE) : "—"
        );
        regLabel.getStyleClass().add("date-label");

        // ── Col 5: Botón acciones ─────────────────────────────────
        Button btnActions = new Button("•••");
        btnActions.getStyleClass().add("btn-row-actions");
        btnActions.setMinWidth(COL_ACTIONS); btnActions.setMaxWidth(COL_ACTIONS);
        btnActions.setOnAction(e -> showActionsMenu(btnActions, user));

        grid.add(userCell,    0, 0);
        grid.add(roleCell,    1, 0);
        grid.add(statusCell,  2, 0);
        grid.add(lastLabel,   3, 0);
        grid.add(regLabel,    4, 0);
        grid.add(btnActions,  5, 0);

        return grid;
    }

    private Label buildRolePill(String role) {
        Label pill = new Label(role != null ? role : "—");
        pill.getStyleClass().add("role-pill");
        if (role == null) {
            pill.getStyleClass().add("role-default");
        } else if (role.equalsIgnoreCase("Administrador") || role.equalsIgnoreCase("Admin")) {
            pill.getStyleClass().add("role-admin");
        } else if (role.equalsIgnoreCase("Analista") || role.equalsIgnoreCase("Usuario")) {
            pill.getStyleClass().add("role-analyst");
        } else {
            pill.getStyleClass().add("role-default");
        }
        return pill;
    }

    private void loadUserAvatar(ImageView view, UserDTO user) {
        // Intentar cargar por ID desde AvatarStorageService
        String userId = String.valueOf(
                user.username() != null ? user.username().hashCode() : user.fullName().hashCode()
        );

        Image saved = AvatarStorageService.loadAvatar(userId);
        if (saved != null) {
            view.setImage(saved);
            return;
        }
        // Fallback: default avatar
        try {
            var stream = getClass().getResourceAsStream("/images/default-avatar.png");
            if (stream != null) view.setImage(new Image(stream));
        } catch (Exception e) {
            System.err.println("Avatar no encontrado para: " + user.fullName());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  MENÚ ACCIONES
    // ═════════════════════════════════════════════════════════════════════════

    private void showActionsMenu(Button anchor, UserDTO user) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("actions-context-menu");

        MenuItem editItem = new MenuItem("✏️  Editar");
        editItem.setOnAction(e -> openEditModal(user));

        SeparatorMenuItem sep = new SeparatorMenuItem();

        MenuItem deactivateItem = new MenuItem("🚫  Desactivar");
        deactivateItem.getStyleClass().add("deactivate-menu-item");
        deactivateItem.setOnAction(e -> onDeactivateUser(user));

        menu.getItems().addAll(editItem, sep, deactivateItem);
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    private void onDeactivateUser(UserDTO user) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Desactivar usuario");
        alert.setHeaderText("¿Desactivar a " + user.fullName() + "?");
        alert.setContentText("El usuario perderá acceso al sistema. Podrás reactivarlo más tarde.");

        ButtonType btnConfirm = new ButtonType("Desactivar", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel  = new ButtonType("Cancelar",   ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnConfirm, btnCancel);
        applyAlertStyle(alert);

        alert.showAndWait().ifPresent(r -> {
            if (r == btnConfirm) {
                // TODO: adminApiService.deactivateUser(userId)
                System.out.println("[ADMIN] Desactivar usuario: id=" + user.username()
                        + " | email=" + user.email());
                showInfo("Usuario desactivado",
                        user.fullName() + " ha sido desactivado correctamente.");
                renderPage();
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  MODALES
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    private void onAddUser() {
        openUserFormModal(null);
    }

    private void openEditModal(UserDTO user) {
        openUserFormModal(user);
    }

    private void openUserFormModal(UserDTO userToEdit) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/admin/UserFormView.fxml")
            );
            Parent root = loader.load();
            UserFormController controller = loader.getController();
            controller.initForm(userToEdit, this::onUserSaved);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                    Objects.requireNonNull(
                            getClass().getResource("/styles/admin.css")
                    ).toExternalForm()
            );

            Stage modal = new Stage();
            modal.setTitle(userToEdit == null ? "Nuevo usuario" : "Editar usuario");
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setResizable(false);
            modal.setScene(scene);

            // Icono
            var iconStream = getClass().getResourceAsStream("/images/app-icon.png");
            if (iconStream != null) modal.getIcons().add(new Image(iconStream));

            modal.showAndWait();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Callback cuando el formulario guarda un usuario (nuevo o editado) */
    private void onUserSaved(UserDTO savedUser) {
        // Buscar si existe y reemplazar, o agregar
        boolean found = false;
        for (int i = 0; i < allUsers.size(); i++) {
            if (allUsers.get(i).username().equals(savedUser.username())) {
                allUsers.set(i, savedUser);
                found = true;
                break;
            }
        }
        if (!found) allUsers.add(savedUser);

        applyFilter(searchField.getText());
        updateKpis();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  EXPORTAR
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    private void onExport() {
        // Mapear lista filtrada a arreglo para el backend
        List<java.util.Map<String, Object>> rows = filtered.stream().map(u -> {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("Nombre",         u.fullName());
            row.put("Usuario",        u.username());
            row.put("Email",          u.email());
            row.put("Rol",            u.roleDisplay());
            row.put("Verificado",     u.isVerified() ? "Sí" : "No");
            row.put("Último acceso",  u.lastAccess() != null ? u.lastAccess().format(FMT_DATETIME) : "—");
            row.put("Fecha registro", u.memberSince() != null ? u.memberSince().format(FMT_DATE) : "—");
            return row;
        }).collect(Collectors.toList());

        // TODO: adminApiService.exportUsers(rows)  →  genera Excel en backend
        System.out.println("[ADMIN] Exportar " + rows.size() + " usuarios:");
        rows.forEach(r -> System.out.println("  " + r));

        showInfo("Exportación iniciada",
                "Se están exportando " + rows.size() + " usuarios.");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    private boolean isActive(UserDTO u) {
        // Consideramos activo si tuvo acceso en los últimos 30 días
        return u.lastAccess() != null &&
                u.lastAccess().isAfter(LocalDateTime.now().minusDays(30));
    }

    private boolean isAdmin(UserDTO u) {
        return u.role() != null &&
                (u.role().equalsIgnoreCase("Administrador") ||
                        u.role().equalsIgnoreCase("Admin"));
    }

    private boolean accessedToday(UserDTO u) {
        return u.lastAccess() != null &&
                u.lastAccess().toLocalDate().equals(LocalDate.now());
    }

    private void applyAlertStyle(Alert alert) {
        try {
            alert.getDialogPane().getStylesheets().add(
                    Objects.requireNonNull(
                            getClass().getResource("/styles/admin.css")
                    ).toExternalForm()
            );
        } catch (Exception ignored) {}

        alert.getDialogPane().getScene().windowProperty().addListener((obs, o, w) -> {
            if (w instanceof Stage s) {
                var ico = getClass().getResourceAsStream("/images/app-icon.png");
                if (ico != null) s.getIcons().add(new Image(ico));
            }
        });
    }

    private void showInfo(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        applyAlertStyle(a);
        a.showAndWait();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  MOCK DATA
    // ═════════════════════════════════════════════════════════════════════════

    private List<UserDTO> getMockUsers() {
        return List.of(
                new UserDTO(
                        "Mateo Alexander", "malex_sani",
                        "Administrador", "Administrador",
                        "Business Intelligence",
                        "mateo.alex@sani-bi.com", true, "+52 55 1234 5678",
                        null,
                        LocalDate.of(2023, 3, 15),
                        LocalDateTime.now().minusHours(2),
                        new UserDTO.UserStats(128, 1042, 45)
                ),
                new UserDTO(
                        "Laura Martínez", "lmartinez",
                        "Analista", "Analista",
                        "Ventas",
                        "laura.martinez@sani-bi.com", true, "+52 55 9876 5432",
                        null,
                        LocalDate.of(2023, 7, 20),
                        LocalDateTime.now().minusDays(1),
                        new UserDTO.UserStats(89, 540, 22)
                ),
                new UserDTO(
                        "Carlos Ruiz", "cruiz",
                        "Analista", "Analista",
                        "Operaciones",
                        "carlos.ruiz@sani-bi.com", false, "",
                        null,
                        LocalDate.of(2024, 1, 10),
                        LocalDateTime.now().minusDays(5),
                        new UserDTO.UserStats(45, 210, 8)
                ),
                new UserDTO(
                        "Sofía Hernández", "shernandez",
                        "Administrador", "Administrador",
                        "Dirección",
                        "sofia.hdz@sani-bi.com", true, "+52 55 4567 8901",
                        null,
                        LocalDate.of(2022, 11, 3),
                        LocalDateTime.now().minusMinutes(30),
                        new UserDTO.UserStats(200, 1800, 70)
                ),
                new UserDTO(
                        "Diego López", "dlopez",
                        "Analista", "Analista",
                        "Finanzas",
                        "diego.lopez@sani-bi.com", false, "",
                        null,
                        LocalDate.of(2024, 4, 18),
                        LocalDateTime.now().minusDays(12),
                        new UserDTO.UserStats(30, 98, 3)
                ),
                new UserDTO(
                        "Valentina Torres", "vtorres",
                        "Analista", "Analista",
                        "Marketing",
                        "v.torres@sani-bi.com", true, "+52 55 2345 6789",
                        null,
                        LocalDate.of(2023, 9, 5),
                        LocalDateTime.now().minusDays(2),
                        new UserDTO.UserStats(67, 320, 15)
                )
        );
    }
}