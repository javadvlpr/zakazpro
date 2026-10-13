package com.mebelsex.ui.reports;

import com.mebelsex.common.Formatters;
import com.mebelsex.report.Cell;
import com.mebelsex.report.CellType;
import com.mebelsex.report.Column;
import com.mebelsex.report.ExcelExporter;
import com.mebelsex.report.ReportPeriod;
import com.mebelsex.report.ReportService;
import com.mebelsex.report.ReportTable;
import com.mebelsex.report.ReportTables;
import com.mebelsex.report.Row;
import com.mebelsex.report.SummaryItem;
import com.mebelsex.report.Tone;
import com.mebelsex.ui.AlertHelper;
import com.mebelsex.ui.TaskRunner;
import com.mebelsex.ui.components.MoneyParser;
import com.mebelsex.ui.components.QuantityParser;
import com.mebelsex.ui.components.UzDatePicker;
import com.mebelsex.ui.orders.OrderCards;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/** Hisobotlar screen: report type + period filters, a generic table and Excel export. */
@Controller
@Scope("prototype")
public class ReportsController {

    private enum ReportType {
        PERIOD("report.period.title"),
        CASH_FLOW("report.cashflow.title"),
        EXPENSES("report.expenses.title"),
        EMPLOYEES("report.employees.title"),
        CUSTOMERS("report.customers.title");

        private final String titleKey;

        ReportType(String titleKey) {
            this.titleKey = titleKey;
        }
    }

    private final ReportService reportService;
    private final TaskRunner taskRunner;
    private final OrderCards orderCards;
    private final ResourceBundle bundle;
    private final ReportTables reportTables;
    private final ToggleGroup periodGroup = new ToggleGroup();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));

    @FXML
    private ComboBox<String> reportTypeCombo;
    @FXML
    private Button exportButton;
    @FXML
    private ToggleButton periodTodayButton;
    @FXML
    private ToggleButton periodWeekButton;
    @FXML
    private ToggleButton periodMonthButton;
    @FXML
    private ToggleButton periodPrevMonthButton;
    @FXML
    private ToggleButton periodYearButton;
    @FXML
    private ToggleButton periodCustomButton;
    @FXML
    private UzDatePicker fromField;
    @FXML
    private UzDatePicker toField;
    @FXML
    private CheckBox onlyDebtorsCheck;
    @FXML
    private FlowPane summaryBox;
    @FXML
    private TableView<Row> table;
    @FXML
    private Label emptyLabel;

    private ReportType reportType = ReportType.PERIOD;
    private ReportPeriod period = ReportPeriod.thisMonth();
    private ReportTable currentTable;
    private int loadToken = 0;
    private boolean suppressEvents = false;

    public ReportsController(ReportService reportService, TaskRunner taskRunner, OrderCards orderCards) {
        this.reportService = reportService;
        this.taskRunner = taskRunner;
        this.orderCards = orderCards;
        this.bundle = com.mebelsex.common.Messages.bundle();
        this.reportTables = new ReportTables(bundle::getString);
    }

    @FXML
    public void initialize() {
        setupTypeCombo();
        setupPeriodToggles();
        setupDateFields();
        setupTable();

        onlyDebtorsCheck.selectedProperty().addListener((o, ov, nv) -> scheduleLoad());

        debounce.setOnFinished(e -> loadNow());
        applyPeriod(ReportPeriod.thisMonth(), false);
        updateOnlyDebtorsVisibility();
        loadNow();
    }

    private void setupTypeCombo() {
        List<String> labels = new ArrayList<>();
        for (ReportType type : ReportType.values()) {
            labels.add(bundle.getString(type.titleKey));
        }
        reportTypeCombo.setItems(FXCollections.observableArrayList(labels));
        reportTypeCombo.getSelectionModel().select(0);
        reportTypeCombo.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
            if (suppressEvents || nv == null || nv.intValue() < 0) {
                return;
            }
            reportType = ReportType.values()[nv.intValue()];
            updateOnlyDebtorsVisibility();
            scheduleLoad();
        });
    }

    private void setupPeriodToggles() {
        periodGroup.getToggles().setAll(periodTodayButton, periodWeekButton, periodMonthButton,
                periodPrevMonthButton, periodYearButton, periodCustomButton);
        periodTodayButton.setUserData(PeriodKind.TODAY);
        periodWeekButton.setUserData(PeriodKind.WEEK);
        periodMonthButton.setUserData(PeriodKind.MONTH);
        periodPrevMonthButton.setUserData(PeriodKind.PREV_MONTH);
        periodYearButton.setUserData(PeriodKind.YEAR);
        periodCustomButton.setUserData(PeriodKind.CUSTOM);
        periodMonthButton.setSelected(true);

        periodGroup.selectedToggleProperty().addListener((o, ov, nv) -> {
            if (suppressEvents || nv == null) {
                return;
            }
            applyPeriod(periodFor((PeriodKind) nv.getUserData()), nv == periodCustomButton);
            scheduleLoad();
        });
    }

    private void setupDateFields() {
        fromField.valueProperty().addListener((o, ov, nv) -> onDateChanged());
        toField.valueProperty().addListener((o, ov, nv) -> onDateChanged());
    }

    private void setupTable() {
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                Long orderId = table.getSelectionModel().getSelectedItem().orderId();
                if (orderId != null) {
                    orderCards.open(orderId);
                }
            }
        });
    }

    private void onDateChanged() {
        if (suppressEvents || periodCustomButton == null || !periodCustomButton.isSelected()) {
            return;
        }
        LocalDate from = fromField.getValue();
        LocalDate to = toField.getValue();
        if (from == null || to == null || from.isAfter(to)) {
            return;
        }
        period = ReportPeriod.custom(from, to);
        scheduleLoad();
    }

    private void applyPeriod(ReportPeriod newPeriod, boolean custom) {
        suppressEvents = true;
        try {
            period = newPeriod;
            fromField.setValue(newPeriod.from());
            toField.setValue(newPeriod.to());
            fromField.setDisable(!custom);
            toField.setDisable(!custom);
        } finally {
            suppressEvents = false;
        }
    }

    private ReportPeriod periodFor(PeriodKind kind) {
        return switch (kind) {
            case TODAY -> ReportPeriod.today();
            case WEEK -> ReportPeriod.thisWeek();
            case MONTH -> ReportPeriod.thisMonth();
            case PREV_MONTH -> ReportPeriod.previousMonth();
            case YEAR -> ReportPeriod.thisYear();
            case CUSTOM -> fromField.getValue() != null && toField.getValue() != null
                    && !fromField.getValue().isAfter(toField.getValue())
                            ? ReportPeriod.custom(fromField.getValue(), toField.getValue())
                            : period;
        };
    }

    private void updateOnlyDebtorsVisibility() {
        boolean visible = reportType == ReportType.CUSTOMERS;
        onlyDebtorsCheck.setVisible(visible);
        onlyDebtorsCheck.setManaged(visible);
    }

    private void scheduleLoad() {
        debounce.playFromStart();
    }

    private void loadNow() {
        final int token = ++loadToken;
        final ReportType type = reportType;
        final ReportPeriod currentPeriod = period;
        final boolean onlyDebtors = onlyDebtorsCheck.isSelected();

        Task<ReportTable> task = new Task<>() {
            @Override
            protected ReportTable call() {
                return buildTable(type, currentPeriod, onlyDebtors);
            }
        };
        taskRunner.run(task, result -> {
            if (token != loadToken) {
                return; // a newer request is already running
            }
            applyTable(result);
        });
    }

    private ReportTable buildTable(ReportType type, ReportPeriod p, boolean onlyDebtors) {
        LocalDate from = p.from();
        LocalDate to = p.to();
        return switch (type) {
            case PERIOD -> reportTables.periodReport(p, reportService.periodReport(from, to));
            case CASH_FLOW -> reportTables.cashFlow(p, reportService.cashFlow(from, to));
            case EXPENSES -> reportTables.expenseReport(p, reportService.expenseReport(from, to));
            case EMPLOYEES -> reportTables.employeeReport(p, reportService.employeeReport(from, to));
            case CUSTOMERS -> reportTables.customerReport(p,
                    reportService.customerReport(from, to, onlyDebtors));
        };
    }

    private void applyTable(ReportTable result) {
        currentTable = result;
        // Drop the previous report's rows before the columns change, so a stale row
        // is never laid out under new column types (would break the cell formatting).
        table.setItems(FXCollections.observableArrayList());
        buildSummary(result.summary());
        buildColumns(result);

        List<Row> items = new ArrayList<>(result.rows());
        if (result.totals() != null) {
            items.add(result.totals());
        }
        table.setItems(FXCollections.observableArrayList(items));
    }

    private void buildSummary(List<SummaryItem> summary) {
        summaryBox.getChildren().clear();
        for (SummaryItem item : summary) {
            Label title = new Label(item.label());
            title.getStyleClass().add("stat-card-title");
            title.setWrapText(true);

            Label value = new Label(formatValue(item.value()));
            value.getStyleClass().add("stat-card-value");
            value.setWrapText(true);
            applyTone(value, item.tone());

            VBox card = new VBox(6, title, value);
            card.getStyleClass().add("card");
            card.setPrefWidth(185);
            card.setMinWidth(0);
            summaryBox.getChildren().add(card);
        }
    }

    private void applyTone(Label label, Tone tone) {
        label.getStyleClass().removeAll("stat-card-profit", "stat-card-debt");
        if (tone == Tone.POSITIVE) {
            label.getStyleClass().add("stat-card-profit");
        } else if (tone == Tone.NEGATIVE) {
            label.getStyleClass().add("stat-card-debt");
        }
    }

    private void buildColumns(ReportTable result) {
        table.getColumns().clear();
        List<Column> columns = result.columns();
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            final int index = i;
            TableColumn<Row, Row> tableColumn = new TableColumn<>(column.title());
            tableColumn.setPrefWidth(columnWidth(column));
            tableColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
            tableColumn.setCellFactory(c -> new TableCell<>() {
                @Override
                protected void updateItem(Row row, boolean empty) {
                    super.updateItem(row, empty);
                    getStyleClass().remove("report-subtotal-row");
                    if (empty || row == null) {
                        setText(null);
                        return;
                    }
                    Cell cell = index < row.cells().size() ? row.cells().get(index) : Cell.empty();
                    setText(formatCell(column.type(), cell));
                    setAlignment(alignment(column.align()));
                    if (row.subtotal()) {
                        getStyleClass().add("report-subtotal-row");
                    }
                }
            });
            table.getColumns().add(tableColumn);
        }
    }

    /** Preferred width per cell type, so headers and values are not clipped to "...". */
    private double columnWidth(Column column) {
        return switch (column.type()) {
            case TEXT -> Math.max(170, column.title().length() * 9.0);
            case MONEY -> 130;
            case PERCENT -> 90;
            case DATE -> 110;
            case QUANTITY -> 100;
            case INTEGER -> 70;
        };
    }

    private Pos alignment(com.mebelsex.report.Align align) {
        return switch (align) {
            case RIGHT -> Pos.CENTER_RIGHT;
            case CENTER -> Pos.CENTER;
            case LEFT -> Pos.CENTER_LEFT;
        };
    }

    private String formatCell(CellType type, Cell cell) {
        return formatValue(type, cell);
    }

    private String formatValue(Cell cell) {
        if (cell == null || cell.isEmpty()) {
            return "\u2014";
        }
        Object value = cell.value();
        if (value instanceof BigDecimal bd) {
            return MoneyParser.format(bd);
        }
        if (value instanceof LocalDate date) {
            return Formatters.formatDate(date);
        }
        return String.valueOf(value);
    }

    private String formatValue(CellType type, Cell cell) {
        if (cell == null || cell.isEmpty()) {
            return "\u2014";
        }
        Object value = cell.value();
        return switch (type) {
            case MONEY -> value instanceof BigDecimal bd ? MoneyParser.format(bd) : String.valueOf(value);
            case PERCENT -> value instanceof BigDecimal bd ? bd.toPlainString() + "%" : String.valueOf(value);
            case DATE -> value instanceof LocalDate date ? Formatters.formatDate(date) : String.valueOf(value);
            case QUANTITY -> value instanceof BigDecimal bd ? QuantityParser.format(bd) : String.valueOf(value);
            case INTEGER -> value instanceof Number n ? String.valueOf(n.longValue()) : String.valueOf(value);
            case TEXT -> String.valueOf(value);
        };
    }

    @FXML
    public void handleExport() {
        if (currentTable == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(bundle.getString("report.export.title"));
        chooser.getExtensionFilters()
                .add(new FileChooser.ExtensionFilter(bundle.getString("report.export.filter"), "*.xlsx"));
        chooser.setInitialFileName(defaultFileName());
        File documents = new File(System.getProperty("user.home", "."), "Documents");
        if (documents.isDirectory()) {
            chooser.setInitialDirectory(documents);
        }
        Window owner = AlertHelper.getActiveWindow(Window.getWindows());
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return;
        }
        ReportTable snapshot = currentTable;
        taskRunner.run(() -> {
            new ExcelExporter().export(snapshot, file.toPath());
            return null;
        }, v -> AlertHelper.info(bundle.getString("report.export"),
                bundle.getString("report.export.done") + " " + file.getAbsolutePath()));
    }

    private String defaultFileName() {
        return "Hisobot_" + reportType.name() + "_" + period.from() + "_" + period.to() + ".xlsx";
    }

    private enum PeriodKind {
        TODAY,
        WEEK,
        MONTH,
        PREV_MONTH,
        YEAR,
        CUSTOM
    }
}
