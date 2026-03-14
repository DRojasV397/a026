package com.app.ui.profit;

import com.app.model.profitability.CategoryProfitDTO;
import com.app.model.profitability.IndicatorsResponseDTO;
import com.app.model.profitability.ProductProfitDTO;
import com.app.service.profitability.ProfitabilityService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.beans.property.SimpleStringProperty;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BiConsumer;

public class ProfitController {

    private final ProfitabilityService profitabilityService = new ProfitabilityService();

    // Vistas principales
    @FXML private VBox indicatorsView;
    @FXML private VBox productView;
    @FXML private VBox categoryView;
    @FXML private VBox projectionView;

    // Botones de navegación
    @FXML private Button btnIndicators;
    @FXML private Button btnProduct;
    @FXML private Button btnCategory;
    @FXML private Button btnProjection;

    private DecimalFormat df = new DecimalFormat("#,##0.00");
    private DecimalFormat pct = new DecimalFormat("0.0");

    // ✅ Configuración de paginación
    private static final int ROWS_PER_PAGE = 10;

    @FXML
    private void initialize() {
        showIndicators();
    }

    /* =========================
       NAVEGACIÓN
       ========================= */

    @FXML
    private void showIndicators() {
        hideAll();
        indicatorsView.setVisible(true);
        indicatorsView.setManaged(true);
        setActiveButton(btnIndicators);

        if (indicatorsView.getChildren().isEmpty()) {
            buildIndicatorsUI();
        }
    }

    @FXML
    private void showByProduct() {
        hideAll();
        productView.setVisible(true);
        productView.setManaged(true);
        setActiveButton(btnProduct);

        if (productView.getChildren().isEmpty()) {
            buildProductProfitUI();
        }
    }

    @FXML
    private void showByCategory() {
        hideAll();
        categoryView.setVisible(true);
        categoryView.setManaged(true);
        setActiveButton(btnCategory);

        if (categoryView.getChildren().isEmpty()) {
            buildCategoryProfitUI();
        }
    }

    @FXML
    private void showProjection() {
        hideAll();
        projectionView.setVisible(true);
        projectionView.setManaged(true);
        setActiveButton(btnProjection);

        if (projectionView.getChildren().isEmpty()) {
            buildProjectionUI();
        }
    }

    private void hideAll() {
        for (VBox v : List.of(indicatorsView, productView, categoryView, projectionView)) {
            v.setVisible(false);
            v.setManaged(false);
        }
    }

    private void setActiveButton(Button activeBtn) {
        for (Button btn : List.of(btnIndicators, btnProduct, btnCategory, btnProjection)) {
            btn.getStyleClass().remove("nav-active");
        }
        if (!activeBtn.getStyleClass().contains("nav-active")) {
            activeBtn.getStyleClass().add("nav-active");
        }
    }

    /* =========================
       CU-29: INDICADORES FINANCIEROS
       ========================= */

    private void buildIndicatorsUI() {
        VBox kpiContainer = new VBox(16);

        HBox periodRow = buildPeriodSelector(0,
                (from, to) -> loadIndicatorsForPeriod(kpiContainer, from, to));

        Label roaRoeTitle = new Label("Indicadores Avanzados");
        roaRoeTitle.getStyleClass().add("card-title");
        roaRoeTitle.setStyle("-fx-padding: 16 0 0 0;");

        Label roaRoeNote = new Label(
                "Ingresa activos totales y patrimonio para calcular ROA y ROE");
        roaRoeNote.getStyleClass().add("main-subtitle");

        FlowPane roaRoeGrid = new FlowPane(20, 20);
        roaRoeGrid.getStyleClass().add("indicators-grid");
        roaRoeGrid.getChildren().addAll(
            createIndicatorCard("📊", "Retorno sobre Activos (ROA)",
                    "(Utilidad Neta / Activos Totales) × 100",
                    List.of("Utilidad Neta ($)", "Activos Totales ($)"), "ROA"),
            createIndicatorCard("📈", "Rendimiento sobre Patrimonio (ROE)",
                    "(Utilidad Neta / Patrimonio) × 100",
                    List.of("Utilidad Neta ($)", "Patrimonio ($)"), "ROE")
        );

        indicatorsView.getChildren().addAll(
                periodRow, kpiContainer, roaRoeTitle, roaRoeNote, roaRoeGrid);

        // Carga inicial: último mes (índice 0)
        LocalDate to = LocalDate.now();
        loadIndicatorsForPeriod(kpiContainer, to.minusDays(30), to);
    }

    private void loadIndicatorsForPeriod(VBox kpiContainer, LocalDate from, LocalDate to) {
        kpiContainer.getChildren().clear();
        Label loading = new Label("Cargando indicadores...");
        loading.getStyleClass().add("main-subtitle");
        kpiContainer.getChildren().add(loading);

        profitabilityService.calculateIndicators(from, to, null, null).thenAccept(response -> {
            Platform.runLater(() -> {
                kpiContainer.getChildren().clear();

                if (response == null) {
                    Label err = new Label("⚠ Error de conexión con el servidor.");
                    err.getStyleClass().add("main-subtitle");
                    kpiContainer.getChildren().add(err);
                    return;
                }

                if (!response.isSuccess()) {
                    Label noData = new Label("Sin datos disponibles para el período seleccionado.");
                    noData.getStyleClass().add("main-subtitle");
                    kpiContainer.getChildren().add(noData);
                    return;
                }

                IndicatorsResponseDTO.IndicatorsData ind = response.getIndicators();
                IndicatorsResponseDTO.SummaryData sum = response.getSummary();

                // Estado vacío: sin ventas en el período
                if (sum != null && sum.getTotalVentas() == 0) {
                    Label noData = new Label("Sin ventas registradas para el período seleccionado.");
                    noData.getStyleClass().add("main-subtitle");
                    kpiContainer.getChildren().add(noData);
                    return;
                }

                if (sum != null && sum.getPeriodo() != null) {
                    Label period = new Label("Período: " + sum.getPeriodo()
                            + "  ·  " + sum.getTotalVentas() + " ventas registradas");
                    period.getStyleClass().add("main-subtitle");
                    kpiContainer.getChildren().add(period);
                }

                FlowPane kpiGrid = new FlowPane(16, 16);
                kpiGrid.getStyleClass().add("indicators-grid");
                kpiGrid.getChildren().addAll(
                    buildKpiCard("Ingresos Totales", fmtMoney(ind.getIngresosTotales()),
                            "Total ventas del período", "#10B981"),
                    buildKpiCard("Costos Totales", fmtMoney(ind.getCostosTotales()),
                            "Costo de mercancía vendida", "#EF4444"),
                    buildKpiCard("Utilidad Bruta", fmtMoney(ind.getUtilidadBruta()),
                            marginPill(ind.getMargenBruto()), marginColor(ind.getMargenBruto())),
                    buildKpiCard("Margen Bruto", pct.format(ind.getMargenBruto()) + "%",
                            marginPill(ind.getMargenBruto()), marginColor(ind.getMargenBruto())),
                    buildKpiCard("Utilidad Neta", fmtMoney(ind.getUtilidadNeta()),
                            marginPill(ind.getMargenNeto()), marginColor(ind.getMargenNeto())),
                    buildKpiCard("Margen Neto", pct.format(ind.getMargenNeto()) + "%",
                            marginPill(ind.getMargenNeto()), marginColor(ind.getMargenNeto()))
                );
                kpiContainer.getChildren().add(kpiGrid);
            });
        });
    }

    /**
     * Construye la fila de botones de período reutilizable.
     * @param defaultIdx  índice del botón activo por defecto (0=mes, 1=3m, 2=6m, 3=año)
     * @param onLoad      callback invocado con (from, to) al seleccionar un período
     */
    private HBox buildPeriodSelector(int defaultIdx, BiConsumer<LocalDate, LocalDate> onLoad) {
        String[] labels = {"Último mes", "3 meses", "6 meses", "1 año"};
        int[]    days   = {30, 90, 180, 365};

        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("period-selector-row");

        Button[] btns = new Button[labels.length];
        for (int i = 0; i < labels.length; i++) {
            Button btn = new Button(labels[i]);
            btn.getStyleClass().add("period-btn");
            btns[i] = btn;
            row.getChildren().add(btn);
        }

        for (int i = 0; i < labels.length; i++) {
            final int d = days[i];
            final Button btn = btns[i];
            btn.setOnAction(e -> {
                for (Button b : btns) b.getStyleClass().remove("period-btn-active");
                btn.getStyleClass().add("period-btn-active");
                LocalDate to = LocalDate.now();
                onLoad.accept(to.minusDays(d), to);
            });
        }
        btns[defaultIdx].getStyleClass().add("period-btn-active");
        return row;
    }

    private VBox buildKpiCard(String title, String value, String statusText, String color) {
        VBox card = new VBox(8);
        card.getStyleClass().add("kpi-card");

        Label lbl = new Label(title);
        lbl.getStyleClass().add("kpi-label");

        Label val = new Label(value);
        val.getStyleClass().add("kpi-value");
        val.setStyle("-fx-text-fill: " + color + ";");

        card.getChildren().addAll(lbl, val);

        if (statusText != null && !statusText.isBlank()) {
            Label pill = new Label(statusText);
            pill.getStyleClass().addAll("status-pill", statusPillClass(statusText));
            card.getChildren().add(pill);
        }
        return card;
    }

    private String statusPillClass(String status) {
        return switch (status) {
            case "Excelente" -> "pill-excellent";
            case "Bueno"     -> "pill-good";
            case "Regular"   -> "pill-warning";
            case "Bajo"      -> "pill-low";
            default          -> "pill-negative";
        };
    }

    private String fmtMoney(double v) {
        return (v < 0 ? "-$" : "$") + df.format(Math.abs(v));
    }

    private String marginPill(double pctVal) {
        if (pctVal >= 20) return "Excelente";
        if (pctVal >= 10) return "Bueno";
        if (pctVal >= 5)  return "Regular";
        if (pctVal >= 0)  return "Bajo";
        return "Negativo";
    }

    private String marginColor(double pctVal) {
        if (pctVal >= 20) return "#10B981";
        if (pctVal >= 10) return "#3B82F6";
        if (pctVal >= 5)  return "#F59E0B";
        if (pctVal >= 0)  return "#F97316";
        return "#EF4444";
    }

    private VBox createIndicatorCard(String emoji, String name, String formula,
                                     List<String> inputLabels, String indicatorType) {
        VBox card = new VBox(14);
        card.getStyleClass().add("indicator-card");

        Label icon = new Label(emoji);
        icon.getStyleClass().add("indicator-icon");

        Label titleLabel = new Label(name);
        titleLabel.getStyleClass().add("indicator-title");

        Label formulaLabel = new Label(formula);
        formulaLabel.getStyleClass().add("indicator-formula");
        formulaLabel.setWrapText(true);

        VBox inputsContainer = new VBox(8);
        inputsContainer.getStyleClass().add("indicator-inputs");

        List<TextField> inputs = new ArrayList<>();
        for (String labelText : inputLabels) {
            VBox fieldBox = new VBox(4);

            Label fieldLabel = new Label(labelText);
            fieldLabel.getStyleClass().add("input-label");

            TextField field = new TextField();
            field.setPromptText("0.00");
            field.getStyleClass().add("indicator-input");
            inputs.add(field);

            fieldBox.getChildren().addAll(fieldLabel, field);
            inputsContainer.getChildren().add(fieldBox);
        }

        Button calculateBtn = new Button("Calcular");
        calculateBtn.getStyleClass().add("calculate-btn");
        calculateBtn.setMaxWidth(Double.MAX_VALUE);

        Separator sep = new Separator();
        sep.getStyleClass().add("indicator-separator");

        HBox resultContainer = new HBox(8);
        resultContainer.setAlignment(Pos.CENTER_LEFT);
        resultContainer.getStyleClass().add("result-container");

        Label resultLabel = new Label("—");
        resultLabel.getStyleClass().add("indicator-result");

        Label trendIcon = new Label("");
        trendIcon.getStyleClass().add("trend-icon");
        trendIcon.setVisible(false);

        resultContainer.getChildren().addAll(resultLabel, trendIcon);

        Label statusPill = new Label("Pendiente");
        statusPill.getStyleClass().addAll("status-pill", "pill-neutral");

        Label interpretation = new Label("Ingresa los valores y presiona Calcular");
        interpretation.getStyleClass().add("indicator-interpretation");
        interpretation.setWrapText(true);
        interpretation.setMaxWidth(280);

        calculateBtn.setOnAction(e -> {
            calculateIndicator(inputs, resultLabel, trendIcon, statusPill,
                    interpretation, indicatorType);
        });

        card.getChildren().addAll(
                icon, titleLabel, formulaLabel,
                inputsContainer, calculateBtn, sep,
                resultContainer, statusPill, interpretation
        );

        return card;
    }

    private void calculateIndicator(List<TextField> inputs, Label resultLabel,
                                    Label trendIcon, Label statusPill,
                                    Label interpretation, String type) {
        try {
            List<Double> values = new ArrayList<>();
            for (TextField input : inputs) {
                String text = input.getText().trim();
                if (text.isEmpty()) {
                    showError("Por favor completa todos los campos");
                    return;
                }
                values.add(Double.parseDouble(text.replace(",", "")));
            }

            double result = 0;
            String interpText = "";
            String status = "";

            switch (type) {
                case "MARGIN":
                    if (values.get(1) == 0) {
                        showError("Los ingresos no pueden ser cero");
                        return;
                    }
                    result = (values.get(0) / values.get(1)) * 100;
                    interpText = String.format("Por cada $100 en ventas, ganas $%.2f de utilidad",
                            result);

                    if (result >= 20) {
                        status = "Excelente";
                        statusPill.getStyleClass().setAll("status-pill", "pill-excellent");
                    } else if (result >= 10) {
                        status = "Bueno";
                        statusPill.getStyleClass().setAll("status-pill", "pill-good");
                    } else if (result >= 5) {
                        status = "Regular";
                        statusPill.getStyleClass().setAll("status-pill", "pill-warning");
                    } else if (result > 0) {
                        status = "Bajo";
                        statusPill.getStyleClass().setAll("status-pill", "pill-low");
                    } else {
                        status = "Negativo";
                        statusPill.getStyleClass().setAll("status-pill", "pill-negative");
                    }
                    break;

                case "ROA":
                    if (values.get(1) == 0) {
                        showError("Los activos no pueden ser cero");
                        return;
                    }
                    result = (values.get(0) / values.get(1)) * 100;
                    interpText = String.format("Tus activos generan un retorno de %.1f%% anual",
                            result);

                    if (result >= 15) {
                        status = "Excelente";
                        statusPill.getStyleClass().setAll("status-pill", "pill-excellent");
                    } else if (result >= 8) {
                        status = "Bueno";
                        statusPill.getStyleClass().setAll("status-pill", "pill-good");
                    } else if (result >= 3) {
                        status = "Regular";
                        statusPill.getStyleClass().setAll("status-pill", "pill-warning");
                    } else if (result > 0) {
                        status = "Bajo";
                        statusPill.getStyleClass().setAll("status-pill", "pill-low");
                    } else {
                        status = "Negativo";
                        statusPill.getStyleClass().setAll("status-pill", "pill-negative");
                    }
                    break;

                case "ROE":
                    if (values.get(1) == 0) {
                        showError("El patrimonio no puede ser cero");
                        return;
                    }
                    result = (values.get(0) / values.get(1)) * 100;
                    interpText = String.format("El capital invertido genera un retorno de %.1f%% anual",
                            result);

                    if (result >= 20) {
                        status = "Excelente";
                        statusPill.getStyleClass().setAll("status-pill", "pill-excellent");
                    } else if (result >= 12) {
                        status = "Bueno";
                        statusPill.getStyleClass().setAll("status-pill", "pill-good");
                    } else if (result >= 6) {
                        status = "Regular";
                        statusPill.getStyleClass().setAll("status-pill", "pill-warning");
                    } else if (result > 0) {
                        status = "Bajo";
                        statusPill.getStyleClass().setAll("status-pill", "pill-low");
                    } else {
                        status = "Negativo";
                        statusPill.getStyleClass().setAll("status-pill", "pill-negative");
                    }
                    break;
            }

            resultLabel.setText(pct.format(result) + "%");
            statusPill.setText(status);
            interpretation.setText(interpText);

            trendIcon.setVisible(true);
            if (result > 0) {
                trendIcon.setText("↑");
                trendIcon.getStyleClass().setAll("trend-icon", "trend-positive");
            } else {
                trendIcon.setText("↓");
                trendIcon.getStyleClass().setAll("trend-icon", "trend-negative");
            }

        } catch (NumberFormatException ex) {
            showError("Por favor ingresa valores numéricos válidos");
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /* =========================
       CU-30: RENTABILIDAD POR PRODUCTO
       ========================= */

    private void buildProductProfitUI() {
        Label title = new Label("📦 Análisis de Rentabilidad por Producto");
        title.getStyleClass().add("section-title");

        VBox contentContainer = new VBox(16);

        // Default: "1 año" (índice 3) para mostrar todo el histórico
        HBox periodRow = buildPeriodSelector(3,
                (from, to) -> loadProductProfitForPeriod(contentContainer, from, to));

        productView.getChildren().addAll(title, periodRow, contentContainer);

        // Carga inicial: último año
        LocalDate to = LocalDate.now();
        loadProductProfitForPeriod(contentContainer, to.minusDays(365), to);
    }

    private void loadProductProfitForPeriod(VBox container, LocalDate from, LocalDate to) {
        container.getChildren().clear();
        Label loading = new Label("Cargando datos de rentabilidad...");
        loading.getStyleClass().add("main-subtitle");
        container.getChildren().add(loading);

        profitabilityService.getProductProfitability(from, to).thenAccept(response -> {
            Platform.runLater(() -> {
                container.getChildren().clear();

                if (response == null || !response.isSuccess()) {
                    Label err = new Label("⚠ No se pudieron cargar los datos. Verifique la conexión.");
                    err.getStyleClass().add("main-subtitle");
                    container.getChildren().add(err);
                    return;
                }

                // Filtrar solo productos con ventas en el período
                List<com.app.model.profitability.ProductProfitDTO> conVentas =
                        response.getProductos().stream()
                                .filter(p -> p.getIngresos() > 0)
                                .toList();

                if (conVentas.isEmpty()) {
                    Label noData = new Label("Sin ventas registradas para el período seleccionado.");
                    noData.getStyleClass().add("main-subtitle");
                    container.getChildren().add(noData);
                    return;
                }

                List<ProductProfit> productList = mapProductDTOs(conVentas);
                VBox tableCard = createProductTableCardWithData(productList);

                HBox summaryRow = new HBox(20);
                summaryRow.setAlignment(Pos.TOP_LEFT);

                List<TopProduct> topList = conVentas.stream()
                        .sorted((a, b) -> Double.compare(b.getMargen(), a.getMargen()))
                        .limit(4)
                        .map(p -> new TopProduct(p.getNombre(), p.getMargenFormateado()))
                        .toList();

                List<TopProduct> lossList = conVentas.stream()
                        .filter(p -> p.getUtilidad() < 0)
                        .sorted(Comparator.comparingDouble(com.app.model.profitability.ProductProfitDTO::getUtilidad))
                        .limit(4)
                        .map(p -> new TopProduct(p.getNombre(), p.getUtilidadFormateada()))
                        .toList();

                VBox topProducts = createSummaryTable(
                        "🏆 Top 4 Productos Más Rentables", "Margen", topList);
                VBox attentionProducts = createAttentionTable(
                        "⚠ Productos que Requieren Atención", "Pérdida", lossList);

                HBox.setHgrow(topProducts, Priority.ALWAYS);
                HBox.setHgrow(attentionProducts, Priority.ALWAYS);
                summaryRow.getChildren().addAll(topProducts, attentionProducts);

                container.getChildren().addAll(tableCard, summaryRow);
            });
        });
    }

    /** Mapea ProductProfitDTO → ProductProfit (modelo para TableView) */
    private List<ProductProfit> mapProductDTOs(List<com.app.model.profitability.ProductProfitDTO> dtos) {
        List<ProductProfit> list = new ArrayList<>();
        for (var dto : dtos) {
            list.add(new ProductProfit(
                    dto.getNombre(),
                    dto.getIngresosFormateado(),
                    dto.getMargenFormateado(),
                    dto.getUtilidadFormateada(),
                    dto.getEstado()
            ));
        }
        return list;
    }

    /** Mapea CategoryProfitDTO → ProductProfit (reutilizando el mismo POJO) */
    private List<ProductProfit> mapCategoryDTOs(List<com.app.model.profitability.CategoryProfitDTO> dtos) {
        List<ProductProfit> list = new ArrayList<>();
        for (var dto : dtos) {
            list.add(new ProductProfit(
                    dto.getNombre(),
                    dto.getIngresosFormateado(),
                    dto.getMargenFormateado(),
                    dto.getUtilidadFormateada(),
                    dto.getEstado()
            ));
        }
        return list;
    }

    private VBox createProductTableCardWithData(List<ProductProfit> allProducts) {
        return createTableCard("Detalle de Productos", allProducts);
    }

    private VBox createProductTableCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("table-card");

        Label cardTitle = new Label("Detalle de Productos");
        cardTitle.getStyleClass().add("card-title");

        TableView<ProductProfit> table = new TableView<>();
        table.getStyleClass().add("profit-table");
        table.setPrefHeight(650);

        // Columnas con ancho automático (quita espacio vacío)
        TableColumn<ProductProfit, String> nameCol = new TableColumn<>("Producto");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.prefWidthProperty().bind(table.widthProperty().multiply(0.30)); // 30%
        nameCol.setSortable(true); // ✅ Habilitar sorting

        TableColumn<ProductProfit, String> salesCol = new TableColumn<>("Ventas");
        salesCol.setCellValueFactory(new PropertyValueFactory<>("sales"));
        salesCol.prefWidthProperty().bind(table.widthProperty().multiply(0.15)); // 15%
        salesCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        salesCol.setSortable(true); // ✅ Habilitar sorting

        TableColumn<ProductProfit, String> marginCol = new TableColumn<>("Margen (%)");
        marginCol.setCellValueFactory(new PropertyValueFactory<>("margin"));
        marginCol.prefWidthProperty().bind(table.widthProperty().multiply(0.15)); // 15%
        marginCol.setStyle("-fx-alignment: CENTER;");
        marginCol.setSortable(true); // ✅ Habilitar sorting
        // ✅ Aplicar colores en Productos
        marginCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    double value = Double.parseDouble(item.replace("%", ""));
                    if (value >= 20) {
                        setStyle("-fx-text-fill: #10B981; -fx-font-weight: bold;");
                    } else if (value >= 10) {
                        setStyle("-fx-text-fill: #3B82F6; -fx-font-weight: bold;");
                    } else if (value < 0) {
                        setStyle("-fx-text-fill: #EF4444; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #F59E0B;");
                    }
                }
            }
        });

        TableColumn<ProductProfit, String> profitCol = new TableColumn<>("Ganancia/Pérdida");
        profitCol.setCellValueFactory(new PropertyValueFactory<>("profit"));
        profitCol.prefWidthProperty().bind(table.widthProperty().multiply(0.20)); // 20%
        profitCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        profitCol.setSortable(true); // ✅ Habilitar sorting
        profitCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.startsWith("-")) {
                        setStyle("-fx-text-fill: #EF4444; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #10B981; -fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<ProductProfit, String> statusCol = new TableColumn<>("Estado");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.prefWidthProperty().bind(table.widthProperty().multiply(0.20)); // 20%
        statusCol.setStyle("-fx-alignment: CENTER;");
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label pill = new Label(item);
                    pill.getStyleClass().add("status-pill");

                    switch (item) {
                        case "Excelente":
                            pill.getStyleClass().add("pill-excellent");
                            break;
                        case "Bueno":
                            pill.getStyleClass().add("pill-good");
                            break;
                        case "Regular":
                            pill.getStyleClass().add("pill-warning");
                            break;
                        case "Crítico":
                            pill.getStyleClass().add("pill-negative");
                            break;
                    }

                    setGraphic(pill);
                    setText(null);
                }
            }
        });

        table.getColumns().addAll(nameCol, salesCol, marginCol, profitCol, statusCol);

        // Datos completos para paginación
        List<ProductProfit> allProducts = getMockProductData();

        // ✅ Orden inicial A-Z por nombre
        table.getSortOrder().add(nameCol);

        // ✅ LAZY LOADING: Cargar solo la primera página inicialmente
        table.getItems().addAll(allProducts.subList(0, Math.min(ROWS_PER_PAGE, allProducts.size())));

        // ✅ LABEL ESTÁTICO FUERA DEL PAGINATION (sin animación)
        Label pageInfoLabel = new Label(String.format("Mostrando 1-%d de %d registros",
                Math.min(ROWS_PER_PAGE, allProducts.size()), allProducts.size()));
        pageInfoLabel.getStyleClass().add("page-info-label");

        // ✅ Paginación mejorada con soporte de sorting completo
        Pagination pagination = createDynamicPaginationWithSorting(allProducts, table, pageInfoLabel);

        card.getChildren().addAll(cardTitle, table, pageInfoLabel, pagination);
        return card;
    }

    /* =========================
       CU-31: RENTABILIDAD POR CATEGORÍA
       ========================= */

    private void buildCategoryProfitUI() {
        Label title = new Label("🗂️ Análisis de Rentabilidad por Categoría");
        title.getStyleClass().add("section-title");

        VBox contentContainer = new VBox(16);

        HBox periodRow = buildPeriodSelector(3,
                (from, to) -> loadCategoryProfitForPeriod(contentContainer, from, to));

        categoryView.getChildren().addAll(title, periodRow, contentContainer);

        LocalDate to = LocalDate.now();
        loadCategoryProfitForPeriod(contentContainer, to.minusDays(365), to);
    }

    private void loadCategoryProfitForPeriod(VBox container, LocalDate from, LocalDate to) {
        container.getChildren().clear();
        Label loading = new Label("Cargando datos de categorías...");
        loading.getStyleClass().add("main-subtitle");
        container.getChildren().add(loading);

        profitabilityService.getCategoryProfitability(from, to).thenAccept(response -> {
            Platform.runLater(() -> {
                container.getChildren().clear();

                if (response == null || !response.isSuccess()) {
                    Label err = new Label("⚠ No se pudieron cargar los datos. Verifique la conexión.");
                    err.getStyleClass().add("main-subtitle");
                    container.getChildren().add(err);
                    return;
                }

                // Filtrar solo categorías con ventas en el período
                List<com.app.model.profitability.CategoryProfitDTO> conVentas =
                        response.getCategorias().stream()
                                .filter(c -> c.getIngresos() > 0)
                                .toList();

                if (conVentas.isEmpty()) {
                    Label noData = new Label("Sin ventas registradas para el período seleccionado.");
                    noData.getStyleClass().add("main-subtitle");
                    container.getChildren().add(noData);
                    return;
                }

                List<ProductProfit> categoryList = mapCategoryDTOs(conVentas);
                VBox tableCard = createTableCard("Detalle de Categorías", categoryList);

                HBox summaryRow = new HBox(20);
                summaryRow.setAlignment(Pos.TOP_LEFT);

                List<TopProduct> topList = conVentas.stream()
                        .sorted((a, b) -> Double.compare(b.getMargen(), a.getMargen()))
                        .limit(4)
                        .map(c -> new TopProduct(c.getNombre(), c.getMargenFormateado()))
                        .toList();

                List<TopProduct> lossList = conVentas.stream()
                        .filter(c -> c.getUtilidad() < 0)
                        .sorted(Comparator.comparingDouble(com.app.model.profitability.CategoryProfitDTO::getUtilidad))
                        .limit(4)
                        .map(c -> new TopProduct(c.getNombre(), c.getUtilidadFormateada()))
                        .toList();

                VBox topCategories = createSummaryTable(
                        "🏆 Top 4 Categorías Más Rentables", "Margen", topList);
                VBox attentionCategories = createAttentionTable(
                        "⚠ Categorías que Requieren Atención", "Pérdida", lossList);

                HBox.setHgrow(topCategories, Priority.ALWAYS);
                HBox.setHgrow(attentionCategories, Priority.ALWAYS);
                summaryRow.getChildren().addAll(topCategories, attentionCategories);

                container.getChildren().addAll(tableCard, summaryRow);
            });
        });
    }

    private VBox createCategoryTableCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("table-card");

        Label cardTitle = new Label("Detalle de Categorías");
        cardTitle.getStyleClass().add("card-title");

        TableView<ProductProfit> table = new TableView<>();
        table.getStyleClass().add("profit-table");
        table.setPrefHeight(600);


        // Columnas con ancho automático
        TableColumn<ProductProfit, String> nameCol = new TableColumn<>("Categoría");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.prefWidthProperty().bind(table.widthProperty().multiply(0.30));
        nameCol.setSortable(true); // ✅ Habilitar sorting

        TableColumn<ProductProfit, String> salesCol = new TableColumn<>("Ventas");
        salesCol.setCellValueFactory(new PropertyValueFactory<>("sales"));
        salesCol.prefWidthProperty().bind(table.widthProperty().multiply(0.15));
        salesCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        salesCol.setSortable(true); // ✅ Habilitar sorting

        TableColumn<ProductProfit, String> marginCol = new TableColumn<>("Margen (%)");
        marginCol.setCellValueFactory(new PropertyValueFactory<>("margin"));
        marginCol.prefWidthProperty().bind(table.widthProperty().multiply(0.15));
        marginCol.setStyle("-fx-alignment: CENTER;");
        marginCol.setSortable(true); // ✅ Habilitar sorting
        // Aplicar colores en Categorías
        marginCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    double value = Double.parseDouble(item.replace("%", ""));
                    if (value >= 20) {
                        setStyle("-fx-text-fill: #10B981; -fx-font-weight: bold;");
                    } else if (value >= 10) {
                        setStyle("-fx-text-fill: #3B82F6; -fx-font-weight: bold;");
                    } else if (value < 0) {
                        setStyle("-fx-text-fill: #EF4444; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #F59E0B;");
                    }
                }
            }
        });

        TableColumn<ProductProfit, String> profitCol = new TableColumn<>("Ganancia/Pérdida");
        profitCol.setCellValueFactory(new PropertyValueFactory<>("profit"));
        profitCol.prefWidthProperty().bind(table.widthProperty().multiply(0.20));
        profitCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        profitCol.setSortable(true); // ✅ Habilitar sorting
        profitCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.startsWith("-")) {
                        setStyle("-fx-text-fill: #EF4444; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #10B981; -fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<ProductProfit, String> statusCol = new TableColumn<>("Estado");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.prefWidthProperty().bind(table.widthProperty().multiply(0.20));
        statusCol.setStyle("-fx-alignment: CENTER;");
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label pill = new Label(item);
                    pill.getStyleClass().add("status-pill");

                    switch (item) {
                        case "Excelente":
                            pill.getStyleClass().add("pill-excellent");
                            break;
                        case "Bueno":
                            pill.getStyleClass().add("pill-good");
                            break;
                        case "Regular":
                            pill.getStyleClass().add("pill-warning");
                            break;
                        case "Crítico":
                            pill.getStyleClass().add("pill-negative");
                            break;
                    }

                    setGraphic(pill);
                    setText(null);
                }
            }
        });

        table.getColumns().addAll(nameCol, salesCol, marginCol, profitCol, statusCol);

        List<ProductProfit> allCategories = getMockCategoryData();

        // Orden inicial A-Z — ahora sí funciona
        nameCol.setSortType(TableColumn.SortType.ASCENDING);
        table.getSortOrder().add(nameCol);
        table.sort();  //

        // ✅ LAZY LOADING: Cargar solo la primera página inicialmente
        table.getItems().addAll(allCategories.subList(0, Math.min(ROWS_PER_PAGE, allCategories.size())));

        // ✅ LABEL ESTÁTICO FUERA DEL PAGINATION (sin animación)
        Label pageInfoLabel = new Label(String.format("Mostrando 1-%d de %d registros",
                Math.min(ROWS_PER_PAGE, allCategories.size()), allCategories.size()));
        pageInfoLabel.getStyleClass().add("page-info-label");

        // ✅ Paginación mejorada con soporte de sorting completo
        Pagination pagination = createDynamicPaginationWithSorting(allCategories, table, pageInfoLabel);

        card.getChildren().addAll(cardTitle, table, pageInfoLabel, pagination);
        return card;
    }

    /* =========================
       PAGINACIÓN DINÁMICA - CORREGIDA ✅
       ========================= */

    private Pagination createDynamicPagination(List<ProductProfit> allData,
                                               TableView<ProductProfit> table) {
        // Calcular número de páginas dinámicamente
        int pageCount = (int) Math.ceil((double) allData.size() / ROWS_PER_PAGE);

        Pagination pagination = new Pagination(pageCount, 0);
        pagination.getStyleClass().add("profit-pagination");

        // ✅ CRÍTICO: Configurar el número máximo de indicadores de página visibles
        pagination.setMaxPageIndicatorCount(7); // Mostrará hasta 7 botones de página a la vez

        // Solo mostrar si hay más de 1 página
        if (pageCount <= 1) {
            pagination.setVisible(false);
            pagination.setManaged(false);
        }

        pagination.setPageFactory(pageIndex -> {
            int fromIndex = pageIndex * ROWS_PER_PAGE;
            int toIndex = Math.min(fromIndex + ROWS_PER_PAGE, allData.size());

            // Actualizar tabla con los datos de la página actual
            table.getItems().setAll(allData.subList(fromIndex, toIndex));

            // ✅ FIX: Crear contenedor ALINEADO A LA IZQUIERDA
            HBox pageInfoContainer = new HBox();
            pageInfoContainer.setAlignment(Pos.CENTER_LEFT); // ✅ Izquierda fija
            pageInfoContainer.getStyleClass().add("page-info-container");

            Label pageInfo = new Label(String.format("Mostrando %d-%d de %d registros",
                    fromIndex + 1, toIndex, allData.size()));
            pageInfo.getStyleClass().add("page-info-label");

            pageInfoContainer.getChildren().add(pageInfo);

            return pageInfoContainer;
        });

        return pagination;
    }

    /* =========================
       PAGINACIÓN CON SORTING COMPLETO
       ========================= */

    private Pagination createDynamicPaginationWithSorting(List<ProductProfit> allData,
                                                          TableView<ProductProfit> table,
                                                          Label pageInfoLabel) {

        // 1. Lista observable maestra
        ObservableList<ProductProfit> masterData =
                FXCollections.observableArrayList(allData);

        // 2. SortedList vinculada al comparador del table
        SortedList<ProductProfit> sortedData = new SortedList<>(masterData);
        sortedData.comparatorProperty().bind(table.comparatorProperty());

        // 3. CRÍTICO: Usar una ObservableList de "página actual" como items del table
        //    Esta lista NUNCA se reemplaza, solo se actualiza su contenido
        ObservableList<ProductProfit> pageItems = FXCollections.observableArrayList();
        table.setItems(pageItems);  // Solo se llama UNA VEZ

        int pageCount = (int) Math.ceil((double) allData.size() / ROWS_PER_PAGE);
        Pagination pagination = new Pagination(Math.max(pageCount, 1), 0);
        pagination.getStyleClass().add("profit-pagination");
        pagination.setMaxPageIndicatorCount(7);

        if (pageCount <= 1) {
            pagination.setVisible(false);
            pagination.setManaged(false);
        }

        // 4. Función de actualización usando setAll() en lugar de setItems()
        Runnable updatePage = () -> {
            int pageIndex = pagination.getCurrentPageIndex();
            int from = pageIndex * ROWS_PER_PAGE;
            int to = Math.min(from + ROWS_PER_PAGE, sortedData.size());

            pageItems.setAll(sortedData.subList(from, to));  // ← setAll, no setItems

            pageInfoLabel.setText(String.format("Mostrando %d-%d de %d registros",
                    from + 1, to, sortedData.size()));
        };

        // 5. Al cambiar sort → ir a página 0 y refrescar
        table.comparatorProperty().addListener((obs, oldVal, newVal) -> {
            if (pagination.getCurrentPageIndex() == 0) {
                updatePage.run();  // Ya estamos en pág 0, actualizar directamente
            } else {
                pagination.setCurrentPageIndex(0);  // El pageFactory lo actualizará
            }
        });

        // 6. pageFactory retorna un nodo VÁLIDO y VISIBLE (requerido por JavaFX)
        pagination.setPageFactory(pageIndex -> {
            updatePage.run();
            Label dummy = new Label();  // Nodo válido pero sin altura visual
            dummy.setMaxHeight(0);
            dummy.setPrefHeight(0);
            return dummy;
        });

        // Cargar primera página
        updatePage.run();

        return pagination;
    }

    private void updatePage(TableView<ProductProfit> table,
                            SortedList<ProductProfit> sortedData,
                            int pageIndex,
                            Label pageInfoLabel) {

        int fromIndex = pageIndex * ROWS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ROWS_PER_PAGE, sortedData.size());

        ObservableList<ProductProfit> page =
                javafx.collections.FXCollections.observableArrayList(
                        sortedData.subList(fromIndex, toIndex)
                );

        table.setItems(page);

        pageInfoLabel.setText(String.format(
                "Mostrando %d-%d de %d registros",
                fromIndex + 1,
                toIndex,
                sortedData.size()
        ));
    }


    private void prettyPrintList(List<ProductProfit> list) {
        System.out.println("------------------------------------------");
        System.out.printf("%-30s | %-10s | %-8s | %-10s\n",
                "NAME", "SALES", "MARGIN", "PROFIT");
        System.out.println("------------------------------------------");

        for (ProductProfit p : list) {
            System.out.printf("%-30s | %-10s | %-8s | %-10s\n",
                    p.getName(),
                    p.getSales(),
                    p.getMargin(),
                    p.getProfit());
        }

        System.out.println("------------------------------------------\n");
    }



    /* =========================
       COMPARADORES PARA SORTING
       ========================= */

    private int compareMoneyStrings(String a, String b) {
        try {
            // Convertir "$12,345" o "-$123" a números
            double numA = Double.parseDouble(a.replace("$", "").replace(",", ""));
            double numB = Double.parseDouble(b.replace("$", "").replace(",", ""));
            return Double.compare(numA, numB);
        } catch (NumberFormatException e) {
            return a.compareTo(b); // Fallback a comparación de strings
        }
    }

    private int comparePercentageStrings(String a, String b) {
        try {
            // Convertir "25.3%" a números
            double numA = Double.parseDouble(a.replace("%", ""));
            double numB = Double.parseDouble(b.replace("%", ""));
            return Double.compare(numA, numB);
        } catch (NumberFormatException e) {
            return a.compareTo(b); // Fallback a comparación de strings
        }
    }

    /* =========================
       TABLA GENÉRICA (datos reales)
       ========================= */

    /** Crea una TableView con datos ya cargados desde la API */
    private VBox createTableCard(String cardTitle, List<ProductProfit> allData) {
        VBox card = new VBox(12);
        card.getStyleClass().add("table-card");

        Label title = new Label(cardTitle);
        title.getStyleClass().add("card-title");

        TableView<ProductProfit> table = new TableView<>();
        table.getStyleClass().add("profit-table");
        table.setPrefHeight(620);

        TableColumn<ProductProfit, String> nameCol = new TableColumn<>("Nombre");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.prefWidthProperty().bind(table.widthProperty().multiply(0.30));
        nameCol.setSortable(true);

        TableColumn<ProductProfit, String> salesCol = new TableColumn<>("Ingresos");
        salesCol.setCellValueFactory(new PropertyValueFactory<>("sales"));
        salesCol.prefWidthProperty().bind(table.widthProperty().multiply(0.15));
        salesCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        salesCol.setSortable(true);

        TableColumn<ProductProfit, String> marginCol = new TableColumn<>("Margen (%)");
        marginCol.setCellValueFactory(new PropertyValueFactory<>("margin"));
        marginCol.prefWidthProperty().bind(table.widthProperty().multiply(0.15));
        marginCol.setStyle("-fx-alignment: CENTER;");
        marginCol.setSortable(true);
        marginCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                double v = Double.parseDouble(item.replace("%", ""));
                if (v >= 20) setStyle("-fx-text-fill: #10B981; -fx-font-weight: bold;");
                else if (v >= 10) setStyle("-fx-text-fill: #3B82F6; -fx-font-weight: bold;");
                else if (v < 0) setStyle("-fx-text-fill: #EF4444; -fx-font-weight: bold;");
                else setStyle("-fx-text-fill: #F59E0B;");
            }
        });

        TableColumn<ProductProfit, String> profitCol = new TableColumn<>("Ganancia/Pérdida");
        profitCol.setCellValueFactory(new PropertyValueFactory<>("profit"));
        profitCol.prefWidthProperty().bind(table.widthProperty().multiply(0.20));
        profitCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        profitCol.setSortable(true);
        profitCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(item.startsWith("-")
                        ? "-fx-text-fill: #EF4444; -fx-font-weight: bold;"
                        : "-fx-text-fill: #10B981; -fx-font-weight: bold;");
            }
        });

        TableColumn<ProductProfit, String> statusCol = new TableColumn<>("Estado");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.prefWidthProperty().bind(table.widthProperty().multiply(0.20));
        statusCol.setStyle("-fx-alignment: CENTER;");
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                Label pill = new Label(item);
                pill.getStyleClass().add("status-pill");
                pill.getStyleClass().add(switch (item) {
                    case "Excelente" -> "pill-excellent";
                    case "Bueno"     -> "pill-good";
                    case "Regular"   -> "pill-warning";
                    default          -> "pill-negative";
                });
                setGraphic(pill);
                setText(null);
            }
        });

        table.getColumns().addAll(nameCol, salesCol, marginCol, profitCol, statusCol);

        Label pageInfoLabel = new Label(String.format("Mostrando 1-%d de %d registros",
                Math.min(ROWS_PER_PAGE, allData.size()), allData.size()));
        pageInfoLabel.getStyleClass().add("page-info-label");

        Pagination pagination = createDynamicPaginationWithSorting(allData, table, pageInfoLabel);

        card.getChildren().addAll(title, table, pageInfoLabel, pagination);
        return card;
    }

    /** Tabla de resumen genérica (top productos/categorías) */
    private VBox createSummaryTable(String title, String valueColTitle, List<TopProduct> items) {
        VBox card = new VBox(12);
        card.getStyleClass().add("summary-card");

        Label lblTitle = new Label(title);
        lblTitle.getStyleClass().add("summary-card-title");

        TableView<TopProduct> table = new TableView<>();
        table.getStyleClass().add("summary-table");
        table.setPrefHeight(200);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<TopProduct, String> nameCol = new TableColumn<>("Nombre");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setSortable(false);

        TableColumn<TopProduct, String> valueCol = new TableColumn<>(valueColTitle);
        valueCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valueCol.setStyle("-fx-alignment: CENTER;");
        valueCol.setSortable(false);

        table.getColumns().addAll(nameCol, valueCol);
        table.getItems().addAll(items);

        card.getChildren().addAll(lblTitle, table);
        return card;
    }

    /** Tabla de atención genérica (pérdidas) — muestra mensaje vacío si no hay datos */
    private VBox createAttentionTable(String title, String valueColTitle, List<TopProduct> items) {
        VBox card = new VBox(12);
        card.getStyleClass().add("summary-card");

        Label lblTitle = new Label(title);
        lblTitle.getStyleClass().add("summary-card-title");
        card.getChildren().add(lblTitle);

        if (items.isEmpty()) {
            VBox messageBox = new VBox(16);
            messageBox.setAlignment(Pos.CENTER);
            messageBox.getStyleClass().add("no-loss-container");
            messageBox.setPrefHeight(200);

            Label icon = new Label("✅");
            icon.setStyle("-fx-font-size: 48px;");
            Label message = new Label("No hay registros con pérdidas actualmente");
            message.getStyleClass().add("no-loss-message");
            message.setWrapText(true);
            message.setMaxWidth(300);
            message.setAlignment(Pos.CENTER);

            messageBox.getChildren().addAll(icon, message);
            card.getChildren().add(messageBox);
        } else {
            TableView<TopProduct> table = new TableView<>();
            table.getStyleClass().add("summary-table");
            table.setPrefHeight(200);
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

            TableColumn<TopProduct, String> nameCol = new TableColumn<>("Nombre");
            nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
            nameCol.setSortable(false);

            TableColumn<TopProduct, String> valueCol = new TableColumn<>(valueColTitle);
            valueCol.setCellValueFactory(new PropertyValueFactory<>("value"));
            valueCol.setStyle("-fx-alignment: CENTER; -fx-text-fill: #EF4444; -fx-font-weight: bold;");
            valueCol.setSortable(false);

            table.getColumns().addAll(nameCol, valueCol);
            table.getItems().addAll(items);
            card.getChildren().add(table);
        }

        return card;
    }

    /* =========================
       TABLAS DE RESUMEN
       ========================= */

    private VBox createTopProductsTable() {
        VBox card = new VBox(12);
        card.getStyleClass().add("summary-card");

        Label title = new Label("🏆 Top 4 Productos Más Rentables");
        title.getStyleClass().add("summary-card-title");

        TableView<TopProduct> table = new TableView<>();
        table.getStyleClass().add("summary-table");
        table.setPrefHeight(200);
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS
        );


        TableColumn<TopProduct, String> nameCol = new TableColumn<>("Producto");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setSortable(false);

        TableColumn<TopProduct, String> valueCol = new TableColumn<>("Margen");
        valueCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valueCol.setStyle("-fx-alignment: CENTER;");
        valueCol.setSortable(false);

        table.getColumns().addAll(nameCol, valueCol);
        table.getItems().addAll(getMockTopProducts());

        card.getChildren().addAll(title, table);
        return card;
    }

    private VBox createAttentionProductsTable() {
        VBox card = new VBox(12);
        card.getStyleClass().add("summary-card");

        Label title = new Label("⚠ Productos que Requieren Atención");
        title.getStyleClass().add("summary-card-title");

        List<TopProduct> lossProducts = getMockLossProducts();

        if (lossProducts.isEmpty()) {
            // ✅ Mensaje centrado y con mejor diseño
            VBox messageBox = new VBox(16);
            messageBox.setAlignment(Pos.CENTER);
            messageBox.getStyleClass().add("no-loss-container");
            messageBox.setPrefHeight(200);

            Label icon = new Label("✅");
            icon.setStyle("-fx-font-size: 48px;");

            Label message = new Label("No hay productos con pérdidas actualmente");
            message.getStyleClass().add("no-loss-message");
            message.setWrapText(true);
            message.setMaxWidth(300);
            message.setAlignment(Pos.CENTER);

            messageBox.getChildren().addAll(icon, message);
            card.getChildren().addAll(title, messageBox);
        } else {
            TableView<TopProduct> table = new TableView<>();
            table.getStyleClass().add("summary-table");
            table.setPrefHeight(200);
            table.setColumnResizePolicy(
                    TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS
            );

            //table.setPrefHeight(200);

            TableColumn<TopProduct, String> nameCol = new TableColumn<>("Producto");
            nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
            nameCol.setSortable(false);

            TableColumn<TopProduct, String> valueCol = new TableColumn<>("Pérdida");
            valueCol.setCellValueFactory(new PropertyValueFactory<>("value"));
            valueCol.setStyle("-fx-alignment: CENTER; -fx-text-fill: #EF4444; -fx-font-weight: bold;");
            valueCol.setSortable(false);

            table.getColumns().addAll(nameCol, valueCol);
            table.getItems().addAll(lossProducts);

            card.getChildren().addAll(title, table);
        }

        return card;
    }

    private VBox createTopCategoriesTable() {
        VBox card = new VBox(12);
        card.getStyleClass().add("summary-card");

        Label title = new Label("🏆 Top 4 Categorías Más Rentables");
        title.getStyleClass().add("summary-card-title");

        TableView<TopProduct> table = new TableView<>();
        table.getStyleClass().add("summary-table");
        table.setPrefHeight(200);
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS
        );

        TableColumn<TopProduct, String> nameCol = new TableColumn<>("Categoría");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setSortable(false);

        TableColumn<TopProduct, String> valueCol = new TableColumn<>("Margen");
        valueCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valueCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        valueCol.setSortable(false);

        table.getColumns().addAll(nameCol, valueCol);
        table.getItems().addAll(getMockTopCategories());

        card.getChildren().addAll(title, table);
        return card;
    }

    private VBox createAttentionCategoriesTable() {
        VBox card = new VBox(12);
        card.getStyleClass().add("summary-card");

        Label title = new Label("⚠ Categorías que Requieren Atención");
        title.getStyleClass().add("summary-card-title");

        // ✅ Mensaje centrado
        VBox messageBox = new VBox(16);
        messageBox.setAlignment(Pos.CENTER);
        messageBox.getStyleClass().add("no-loss-container");
        messageBox.setPrefHeight(200);

        Label icon = new Label("✅");
        icon.setStyle("-fx-font-size: 48px;");

        Label message = new Label("No hay categorías con pérdidas actualmente");
        message.getStyleClass().add("no-loss-message");
        message.setWrapText(true);
        message.setMaxWidth(300);
        message.setAlignment(Pos.CENTER);

        messageBox.getChildren().addAll(icon, message);
        card.getChildren().addAll(title, messageBox);

        return card;
    }

    /* =========================
       CU-32: PROYECCIÓN FUTURA
       ========================= */

    private void buildProjectionUI() {
        Label title = new Label("📈 Proyección de Rentabilidad Futura");
        title.getStyleClass().add("section-title");

        VBox comingSoon = new VBox(40);
        comingSoon.setAlignment(Pos.CENTER);
        comingSoon.getStyleClass().add("coming-soon-card");

        Label icon = new Label("🚧");
        icon.setStyle("-fx-font-size: 64px;");

        Label message = new Label("Funcionalidad en Desarrollo");
        message.getStyleClass().add("coming-soon-title");

        Label description = new Label("La proyección de rentabilidad futura estará disponible próximamente");
        description.getStyleClass().add("coming-soon-text");
        description.setWrapText(true);
        description.setMaxWidth(400);

        comingSoon.getChildren().addAll(icon, message, description);

        projectionView.getChildren().addAll(title, comingSoon);
    }

    /* =========================
       DATOS MOCK - EXPANDIDOS PARA PAGINACIÓN ✅
       ========================= */

    private List<ProductProfit> getMockProductData() {
        return List.of(
                new ProductProfit("Adaptador HDMI", "$780", "4.1%", "$32", "Regular"),
                new ProductProfit("AirPods Pro", "$18,450", "29.8%", "$5,498", "Excelente"),
                new ProductProfit("Apple Watch Ultra", "$42,560", "25.1%", "$10,683", "Excelente"),
                new ProductProfit("Audio-Technica AT2020", "$6,780", "25.4%", "$1,722", "Excelente"),
                new ProductProfit("Audífonos Sony XM5", "$22,340", "18.9%", "$4,222", "Bueno"),
                new ProductProfit("Auriculares Básicos", "$3,450", "-2.3%", "-$79", "Crítico"),
                new ProductProfit("Beyerdynamic DT 770", "$12,450", "19.1%", "$2,378", "Bueno"),
                new ProductProfit("Blue Yeti Microphone", "$11,230", "20.8%", "$2,336", "Excelente"),
                new ProductProfit("Cable Ethernet", "$450", "2.8%", "$13", "Regular"),
                new ProductProfit("Cable HDMI 2.0", "$2,340", "5.8%", "$136", "Regular"),
                new ProductProfit("Cámara Canon EOS", "$78,450", "16.5%", "$12,944", "Bueno"),
                new ProductProfit("Case Gaming RGB", "$7,560", "12.3%", "$930", "Bueno"),
                new ProductProfit("Cooler Master", "$4,230", "11.8%", "$499", "Bueno"),
                new ProductProfit("Corsair K95", "$16,780", "16.2%", "$2,718", "Bueno"),
                new ProductProfit("Drone DJI Mini", "$34,780", "15.8%", "$5,495", "Bueno"),
                new ProductProfit("Elgato Stream Deck", "$9,560", "23.6%", "$2,256", "Excelente"),
                new ProductProfit("Focusrite Scarlett", "$8,940", "22.1%", "$1,976", "Excelente"),
                new ProductProfit("Fuente Poder 750W", "$9,340", "14.7%", "$1,373", "Bueno"),
                new ProductProfit("Funda Laptop", "$2,100", "1.2%", "$25", "Regular"),
                new ProductProfit("GoPro Hero 12", "$28,920", "14.2%", "$4,107", "Bueno"),
                new ProductProfit("Hub USB 3.0", "$1,450", "6.9%", "$100", "Regular"),
                new ProductProfit("HyperX Cloud II", "$7,890", "19.4%", "$1,531", "Bueno"),
                new ProductProfit("iPad Pro 12.9", "$67,340", "23.4%", "$15,758", "Excelente"),
                new ProductProfit("Kindle Paperwhite", "$8,230", "24.8%", "$2,041", "Excelente"),
                new ProductProfit("Laptop Dell XPS 13", "$45,230", "28.5%", "$12,890", "Excelente"),
                new ProductProfit("Logitech G502", "$4,560", "27.3%", "$1,245", "Excelente"),
                new ProductProfit("MacBook Pro M2", "$125,890", "26.7%", "$33,612", "Excelente"),
                new ProductProfit("Magic Keyboard", "$9,230", "20.3%", "$1,874", "Excelente"),
                new ProductProfit("Monitor LG 27\"", "$23,560", "15.2%", "$3,581", "Bueno"),
                new ProductProfit("Motherboard ASUS", "$28,670", "16.1%", "$4,616", "Bueno"),
                new ProductProfit("Mouse Logitech MX", "$8,450", "32.1%", "$2,713", "Excelente"),
                new ProductProfit("Nintendo Switch OLED", "$19,780", "19.2%", "$3,798", "Bueno"),
                new ProductProfit("Oculus Quest 3", "$29,670", "22.4%", "$6,646", "Excelente"),
                new ProductProfit("Pasta Térmica", "$890", "8.2%", "$73", "Regular"),
                new ProductProfit("PlayStation 5", "$56,340", "17.8%", "$10,028", "Bueno"),
                new ProductProfit("Procesador Intel i7", "$56,230", "17.4%", "$9,784", "Bueno"),
                new ProductProfit("RAM Corsair 16GB", "$14,890", "21.3%", "$3,171", "Excelente"),
                new ProductProfit("Razer BlackWidow", "$11,340", "21.7%", "$2,461", "Excelente"),
                new ProductProfit("Ring Video Doorbell", "$12,560", "13.7%", "$1,721", "Bueno"),
                new ProductProfit("Rode PodMic", "$7,340", "21.9%", "$1,607", "Excelente"),
                new ProductProfit("Samsung Galaxy Tab", "$35,890", "18.6%", "$6,676", "Bueno"),
                new ProductProfit("Sennheiser HD 650", "$19,890", "17.6%", "$3,501", "Bueno"),
                new ProductProfit("Shure SM7B", "$24,560", "18.3%", "$4,494", "Bueno"),
                new ProductProfit("SSD Samsung 1TB", "$34,120", "24.2%", "$8,257", "Excelente"),
                new ProductProfit("Steam Deck", "$32,450", "15.3%", "$4,965", "Bueno"),
                new ProductProfit("SteelSeries Apex", "$13,240", "17.9%", "$2,370", "Bueno"),
                new ProductProfit("Tarjeta Gráfica RTX", "$89,450", "19.8%", "$17,711", "Bueno"),
                new ProductProfit("Teclado Mecánico", "$12,340", "18.7%", "$2,308", "Bueno"),
                new ProductProfit("Webcam HD Pro", "$6,780", "9.4%", "$638", "Regular"),
                new ProductProfit("Xbox Series X", "$48,920", "16.9%", "$8,267", "Bueno")
        );
    }


    private List<TopProduct> getMockTopProducts() {
        return List.of(
                new TopProduct("Mouse Logitech MX", "32.1%"),
                new TopProduct("AirPods Pro", "29.8%"),
                new TopProduct("Laptop Dell XPS 13", "28.5%"),
                new TopProduct("Logitech G502", "27.3%")
        );
    }

    private List<TopProduct> getMockLossProducts() {
        return List.of(
                new TopProduct("Auriculares Básicos", "-$79")
        );
    }

    private List<ProductProfit> getMockCategoryData() {
        return List.of(
                // Página 1
                new ProductProfit("Computadoras", "$156,780", "25.3%", "$39,665", "Excelente"),
                new ProductProfit("Periféricos", "$45,230", "22.8%", "$10,312", "Excelente"),
                new ProductProfit("Monitores", "$67,890", "16.4%", "$11,134", "Bueno"),
                new ProductProfit("Accesorios", "$23,450", "12.1%", "$2,837", "Bueno"),
                new ProductProfit("Cables", "$8,340", "6.2%", "$517", "Regular"),
                new ProductProfit("Almacenamiento", "$98,560", "23.7%", "$23,359", "Excelente"),
                new ProductProfit("Componentes", "$134,220", "18.9%", "$25,368", "Bueno"),
                new ProductProfit("Gaming", "$56,780", "17.2%", "$9,766", "Bueno"),
                new ProductProfit("Refrigeración", "$12,450", "14.3%", "$1,780", "Bueno"),
                new ProductProfit("Conectividad", "$5,670", "7.4%", "$420", "Regular"),

                // Página 2
                new ProductProfit("Audio Profesional", "$78,940", "21.4%", "$16,893", "Excelente"),
                new ProductProfit("Fotografía", "$145,670", "19.7%", "$28,697", "Bueno"),
                new ProductProfit("Video & Streaming", "$89,230", "20.3%", "$18,114", "Excelente"),
                new ProductProfit("Smartphones", "$234,560", "24.8%", "$58,171", "Excelente"),
                new ProductProfit("Tablets", "$112,340", "22.1%", "$24,827", "Excelente"),
                new ProductProfit("Wearables", "$67,890", "26.4%", "$17,923", "Excelente"),
                new ProductProfit("Consolas", "$156,230", "18.6%", "$29,059", "Bueno"),
                new ProductProfit("Realidad Virtual", "$45,670", "23.9%", "$10,915", "Excelente"),
                new ProductProfit("Smart Home", "$34,560", "15.8%", "$5,460", "Bueno"),
                new ProductProfit("Lectura Digital", "$18,920", "27.3%", "$5,165", "Excelente"),

                // Página 3
                new ProductProfit("Redes", "$42,340", "16.7%", "$7,071", "Bueno"),
                new ProductProfit("Seguridad", "$56,780", "19.4%", "$11,015", "Bueno"),
                new ProductProfit("Impresoras", "$38,450", "13.2%", "$5,075", "Bueno"),
                new ProductProfit("Scanners", "$23,670", "14.9%", "$3,527", "Bueno"),
                new ProductProfit("Proyectores", "$45,890", "17.8%", "$8,168", "Bueno"),
                new ProductProfit("UPS & Energía", "$34,230", "15.6%", "$5,340", "Bueno"),
                new ProductProfit("Drones", "$67,340", "20.1%", "$13,535", "Excelente"),
                new ProductProfit("Acción Cams", "$29,450", "21.7%", "$6,391", "Excelente"),
                new ProductProfit("Microfonía", "$41,560", "22.9%", "$9,517", "Excelente"),
                new ProductProfit("Iluminación", "$28,780", "18.3%", "$5,267", "Bueno")
        );
    }

    private List<TopProduct> getMockTopCategories() {
        return List.of(
                new TopProduct("Lectura Digital", "27.3%"),
                new TopProduct("Wearables", "26.4%"),
                new TopProduct("Computadoras", "25.3%"),
                new TopProduct("Smartphones", "24.8%")
        );
    }

    /* =========================
       CLASES AUXILIARES
       ========================= */

    public static class ProductProfit {
        private final SimpleStringProperty name;
        private final SimpleStringProperty sales;
        private final SimpleStringProperty margin;
        private final SimpleStringProperty profit;
        private final SimpleStringProperty status;

        public ProductProfit(String name, String sales, String margin, String profit, String status) {
            this.name = new SimpleStringProperty(name);
            this.sales = new SimpleStringProperty(sales);
            this.margin = new SimpleStringProperty(margin);
            this.profit = new SimpleStringProperty(profit);
            this.status = new SimpleStringProperty(status);
        }

        public String getName() { return name.get(); }
        public String getSales() { return sales.get(); }
        public String getMargin() { return margin.get(); }
        public String getProfit() { return profit.get(); }
        public String getStatus() { return status.get(); }
    }

    public static class TopProduct {
        private final SimpleStringProperty name;
        private final SimpleStringProperty value;

        public TopProduct(String name, String value) {
            this.name = new SimpleStringProperty(name);
            this.value = new SimpleStringProperty(value);
        }

        public String getName() { return name.get(); }
        public String getValue() { return value.get(); }
    }
}