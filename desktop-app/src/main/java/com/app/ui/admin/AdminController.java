package com.app.ui.admin;

import com.app.model.UserDTO;
import com.app.model.admin.AdminUserDTO;
import com.app.service.admin.AdminApiService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private final AdminApiService adminApiService = new AdminApiService();

    private List<AdminUserDTO> allAdminUsers = new ArrayList<>();
    private List<UserDTO> allUsers   = new ArrayList<>();
    private List<UserDTO> filtered   = new ArrayList<>();

    /** Lookup rapido: nombreUsuario -> AdminUserDTO (para acciones) */
    private Map<String, AdminUserDTO> adminUserByUsername = new HashMap<>();

    private static final DateTimeFormatter FMT_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_DATETIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");


    // ── Constraints compartidos: usarlos en header y en cada fila ──
    private static final double COL_USER_MIN   = 140;
    private static final double COL_USER_MAX   = 9999;
    private static final double COL_ROLE_MIN   = 110;
    private static final double COL_ROLE_MAX     = 140;
    private static final double COL_STATUS_MIN = 99;
    private static final double COL_STATUS_MAX = 140;
    private static final double COL_LASTACCESS = 145;
    private static final double COL_REGDATE    = 115;
    private static final double COL_ACTIONS    = 44;

    // ── Init ──────────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        usersContainer.getChildren().addFirst(buildTableHeader());

        // Mostrar estado de carga
        showLoadingState();

        // Cargar usuarios desde la API
        loadUsersFromApi();

        // Filtrado reactivo
        searchField.textProperty().addListener((obs, o, v) -> {
            applyFilter(v);
        });
    }

    private void showLoadingState() {
        // Limpiar filas existentes (conservar header en indice 0)
        if (usersContainer.getChildren().size() > 1) {
            usersContainer.getChildren().remove(1, usersContainer.getChildren().size());
        }

        VBox loading = new VBox(8);
        loading.getStyleClass().add("empty-state");
        loading.setAlignment(Pos.CENTER);
        Label msg = new Label("Cargando usuarios...");
        msg.getStyleClass().add("empty-state-text");
        loading.getChildren().add(msg);
        usersContainer.getChildren().add(loading);

        // KPIs en 0 mientras carga
        kpiActiveUsers.setText("...");
        kpiFullAccess.setText("...");
        kpiOnlineToday.setText("...");
        kpiPendingVerify.setText("...");

        pageLabel.setText("Cargando...");
        btnPrevPage.setDisable(true);
        btnNextPage.setDisable(true);
    }

    private void loadUsersFromApi() {
        adminApiService.getUsuarios().thenAccept(users -> {
            Platform.runLater(() -> {
                if (users == null || users.isEmpty()) {
                    allAdminUsers = new ArrayList<>();
                    allUsers = new ArrayList<>();
                    filtered = new ArrayList<>();
                    adminUserByUsername = new HashMap<>();
                } else {
                    allAdminUsers = new ArrayList<>(users);
                    adminUserByUsername = new HashMap<>();
                    allUsers = new ArrayList<>();

                    for (AdminUserDTO dto : allAdminUsers) {
                        UserDTO userDto = toUserDTO(dto);
                        allUsers.add(userDto);
                        adminUserByUsername.put(dto.getNombreUsuario(), dto);
                    }

                    filtered = new ArrayList<>(allUsers);
                }

                updateKpis();
                renderPage();
            });
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Conversion AdminUserDTO -> UserDTO
    // ═════════════════════════════════════════════════════════════════════════

    private UserDTO toUserDTO(AdminUserDTO dto) {
        String role = dto.isPrincipal() ? "Administrador" : "Secundario";
        String department = dto.isPrincipal()
                ? "Acceso completo"
                : dto.getModulos().size() + " modulos";

        return new UserDTO(
                dto.getNombreCompleto(),
                dto.getNombreUsuario(),
                role,
                role,
                department,
                dto.getEmail(),
                dto.isActivo(),
                "",
                null,
                parseCreadoEn(dto.getCreadoEn()),
                null,
                new UserDTO.UserStats(0, 0, 0)
        );
    }

    private LocalDate parseCreadoEn(String creadoEn) {
        if (creadoEn == null || creadoEn.isBlank()) return null;
        try {
            return LocalDate.parse(creadoEn.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  KPIs
    // ═════════════════════════════════════════════════════════════════════════

    private void updateKpis() {
        long active      = allAdminUsers.stream().filter(AdminUserDTO::isActivo).count();
        long fullAccess  = allAdminUsers.stream().filter(AdminUserDTO::isPrincipal).count();
        long inactive    = allAdminUsers.stream().filter(u -> !u.isActivo()).count();
        long total       = allAdminUsers.size();

        kpiActiveUsers.setText(String.valueOf(active));
        kpiFullAccess.setText(String.valueOf(fullAccess));
        kpiOnlineToday.setText(String.valueOf(total));
        kpiPendingVerify.setText(String.valueOf(inactive));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  FILTRO Y PAGINACION
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
        // Conserva el header (indice 0), borra solo las filas
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
            Label icon = new Label("");
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

        pageLabel.setText("Pagina " + (currentPage + 1) + " de " + totalPages);
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
        ColumnConstraints colUser = new ColumnConstraints();
        colUser.setMinWidth(COL_USER_MIN);
        colUser.setMaxWidth(COL_USER_MAX);
        colUser.setHgrow(Priority.ALWAYS);

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

    private ColumnConstraints flexible(double min, double max) {
        ColumnConstraints c = new ColumnConstraints();
        c.setMinWidth(min);
        c.setMaxWidth(max);
        c.setPrefWidth(min);
        c.setHgrow(Priority.ALWAYS);
        return c;
    }

    private GridPane buildTableHeader() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("table-header");
        applyColumnConstraints(grid);

        Label roleTh = styledHeader("Tipo");
        roleTh.setAlignment(Pos.CENTER);
        roleTh.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Label edoTh = styledHeader("Estado");
        edoTh.setAlignment(Pos.CENTER);
        edoTh.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);


        grid.add(styledHeader("Usuario"),        0, 0);
        grid.add(roleTh, 1, 0);
        grid.add(edoTh,  2, 0);
        grid.add(styledHeader("Modulos"),        3, 0);
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

        Label nameLabel = new Label(user.fullName() != null ? user.fullName() : "--");
        nameLabel.getStyleClass().add("user-name");
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        Label emailLabel = new Label(user.email() != null ? user.email() : "--");
        emailLabel.getStyleClass().add("user-email");
        emailLabel.setMaxWidth(Double.MAX_VALUE);

        nameBox.getChildren().addAll(nameLabel, emailLabel);
        userCell.getChildren().addAll(avatarWrap, nameBox);
        GridPane.setFillWidth(userCell, true);

        // ── Col 1: Tipo pill ───────────────────────────────────────
        HBox roleCell = new HBox();
        roleCell.setAlignment(Pos.CENTER);
        roleCell.getChildren().add(
                buildRolePill(user.roleDisplay() != null ? user.roleDisplay() : user.role())
        );

        // ── Col 2: Estado ─────────────────────────────────────────
        HBox statusCell = new HBox();
        statusCell.setAlignment(Pos.CENTER);
        boolean active = user.isVerified(); // isVerified maps to isActivo
        Label statusLabel = new Label(active ? "Activo" : "Inactivo");
        statusLabel.getStyleClass().add(active ? "status-dot-active" : "status-dot-inactive");
        statusCell.getChildren().add(statusLabel);

        // ── Col 3: Modulos ──────────────────────────────────
        AdminUserDTO adminDto = adminUserByUsername.get(user.username());
        String modulosText = "--";
        if (adminDto != null) {
            if (adminDto.isPrincipal()) {
                modulosText = "Todos";
            } else {
                int count = adminDto.getModulos().size();
                modulosText = count + " de 7";
            }
        }
        Label modulosLabel = new Label(modulosText);
        modulosLabel.getStyleClass().add("date-label");

        // ── Col 4: Registro ───────────────────────────────────────
        Label regLabel = new Label(
                user.memberSince() != null ? user.memberSince().format(FMT_DATE) : "--"
        );
        regLabel.getStyleClass().add("date-label");

        // ── Col 5: Boton acciones ─────────────────────────────────
        Button btnActions = new Button("...");
        btnActions.getStyleClass().add("btn-row-actions");
        btnActions.setMinWidth(COL_ACTIONS); btnActions.setMaxWidth(COL_ACTIONS);
        btnActions.setOnAction(e -> showActionsMenu(btnActions, user));

        grid.add(userCell,      0, 0);
        grid.add(roleCell,      1, 0);
        grid.add(statusCell,    2, 0);
        grid.add(modulosLabel,  3, 0);
        grid.add(regLabel,      4, 0);
        grid.add(btnActions,    5, 0);

        return grid;
    }

    private Label buildRolePill(String role) {
        Label pill = new Label(role != null ? role : "--");
        pill.getStyleClass().add("role-pill");
        if (role == null) {
            pill.getStyleClass().add("role-default");
        } else if (role.equalsIgnoreCase("Administrador") || role.equalsIgnoreCase("Principal")) {
            pill.getStyleClass().add("role-admin");
        } else if (role.equalsIgnoreCase("Secundario") || role.equalsIgnoreCase("Analista")) {
            pill.getStyleClass().add("role-analyst");
        } else {
            pill.getStyleClass().add("role-default");
        }
        return pill;
    }

    private void loadUserAvatar(ImageView view, UserDTO user) {
        String userId = String.valueOf(
                user.username() != null ? user.username().hashCode() : user.fullName().hashCode()
        );

        Image saved = AvatarStorageService.loadAvatar(userId);
        if (saved != null) {
            view.setImage(saved);
            return;
        }
        try {
            var stream = getClass().getResourceAsStream("/images/default-avatar.png");
            if (stream != null) view.setImage(new Image(stream));
        } catch (Exception e) {
            System.err.println("Avatar no encontrado para: " + user.fullName());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  MENU ACCIONES
    // ═════════════════════════════════════════════════════════════════════════

    private void showActionsMenu(Button anchor, UserDTO user) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("actions-context-menu");

        AdminUserDTO adminDto = adminUserByUsername.get(user.username());

        MenuItem editItem = new MenuItem("Editar");
        editItem.setOnAction(e -> {
            if (adminDto != null) {
                openEditModal(adminDto);
            }
        });

        SeparatorMenuItem sep = new SeparatorMenuItem();

        // Mostrar activar o desactivar segun estado actual
        boolean isActive = adminDto != null && adminDto.isActivo();
        MenuItem toggleItem = new MenuItem(isActive ? "Desactivar" : "Activar");
        if (isActive) {
            toggleItem.getStyleClass().add("deactivate-menu-item");
        }
        toggleItem.setOnAction(e -> {
            if (adminDto != null) {
                onToggleUserEstado(adminDto);
            }
        });

        menu.getItems().addAll(editItem, sep, toggleItem);
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    private void onToggleUserEstado(AdminUserDTO user) {
        boolean isActive = user.isActivo();
        String action = isActive ? "Desactivar" : "Activar";
        String newEstado = isActive ? "Inactivo" : "Activo";

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(action + " usuario");
        alert.setHeaderText(action + " a " + user.getNombreCompleto() + "?");
        alert.setContentText(isActive
                ? "El usuario perdera acceso al sistema. Podras reactivarlo mas tarde."
                : "El usuario recuperara acceso al sistema.");

        ButtonType btnConfirm = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel  = new ButtonType("Cancelar",   ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnConfirm, btnCancel);
        applyAlertStyle(alert);

        alert.showAndWait().ifPresent(r -> {
            if (r == btnConfirm) {
                adminApiService.updateEstado(user.getIdUsuario(), newEstado)
                        .thenAccept(success -> Platform.runLater(() -> {
                            if (success) {
                                showInfo("Usuario " + (isActive ? "desactivado" : "activado"),
                                        user.getNombreCompleto() + " ha sido " +
                                                (isActive ? "desactivado" : "activado") + " correctamente.");
                                loadUsersFromApi();
                            } else {
                                showAlert("Error", "No se pudo cambiar el estado del usuario.");
                            }
                        }));
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

    private void openEditModal(AdminUserDTO user) {
        openUserFormModal(user);
    }

    private void openUserFormModal(AdminUserDTO adminUserToEdit) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/admin/UserFormView.fxml")
            );
            Parent root = loader.load();
            UserFormController controller = loader.getController();
            controller.initForm(adminUserToEdit, this::onUserSaved);

            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                    Objects.requireNonNull(
                            getClass().getResource("/styles/admin.css")
                    ).toExternalForm()
            );

            Stage modal = new Stage();
            modal.setTitle(adminUserToEdit == null ? "Nuevo usuario" : "Editar usuario");
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setResizable(false);
            modal.setScene(scene);

            var iconStream = getClass().getResourceAsStream("/images/app-icon.png");
            if (iconStream != null) modal.getIcons().add(new Image(iconStream));

            modal.showAndWait();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Callback cuando el formulario guarda un usuario (nuevo o editado) */
    private void onUserSaved(AdminUserDTO savedUser) {
        // Recargar toda la lista desde la API para mantener consistencia
        loadUsersFromApi();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  EXPORTAR
    // ═════════════════════════════════════════════════════════════════════════

    @FXML
    private void onExport() {
        List<java.util.Map<String, Object>> rows = filtered.stream().map(u -> {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("Nombre",         u.fullName());
            row.put("Usuario",        u.username());
            row.put("Email",          u.email());
            row.put("Tipo",           u.roleDisplay());
            row.put("Estado",         u.isVerified() ? "Activo" : "Inactivo");
            row.put("Fecha registro", u.memberSince() != null ? u.memberSince().format(FMT_DATE) : "--");
            return row;
        }).collect(Collectors.toList());

        System.out.println("[ADMIN] Exportar " + rows.size() + " usuarios:");
        rows.forEach(r -> System.out.println("  " + r));

        showInfo("Exportacion iniciada",
                "Se estan exportando " + rows.size() + " usuarios.");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═════════════════════════════════════════════════════════════════════════

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

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        applyAlertStyle(a);
        a.showAndWait();
    }
}
