package com.mebelsex.ui.employees;

import com.mebelsex.common.Formatters;
import com.mebelsex.employees.EmployeeBalanceRow;
import com.mebelsex.employees.EmployeeBalanceService;
import com.mebelsex.ui.AlertHelper;
import com.mebelsex.ui.TaskRunner;
import com.mebelsex.ui.components.SearchField;
import com.mebelsex.ui.components.UzDatePicker;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

/** Ishchilar: balances as of a date; double-click opens the employee card. */
@Controller
@Scope("prototype")
public class EmployeesController {

    private final EmployeeBalanceService balanceService;
    private final TaskRunner taskRunner;
    private final ApplicationContext context;
    private final EmployeeCards employeeCards;
    private final ResourceBundle bundle;

    @FXML
    private UzDatePicker asOfPicker;
    @FXML
    private SearchField searchField;
    @FXML
    private CheckBox showInactive;
    @FXML
    private Button newEmployeeButton;
    @FXML
    private TableView<EmployeeBalanceRow> employeeTable;
    @FXML
    private TableColumn<EmployeeBalanceRow, String> colName;
    @FXML
    private TableColumn<EmployeeBalanceRow, String> colPosition;
    @FXML
    private TableColumn<EmployeeBalanceRow, String> colOpening;
    @FXML
    private TableColumn<EmployeeBalanceRow, String> colEarned;
    @FXML
    private TableColumn<EmployeeBalanceRow, String> colPaid;
    @FXML
    private TableColumn<EmployeeBalanceRow, String> colBalance;
    @FXML
    private Label owedTotalLabel;

    public EmployeesController(EmployeeBalanceService balanceService, TaskRunner taskRunner,
            ApplicationContext context, EmployeeCards employeeCards) {
        this.balanceService = balanceService;
        this.taskRunner = taskRunner;
        this.context = context;
        this.employeeCards = employeeCards;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    @FXML
    public void initialize() {
        asOfPicker.setValue(LocalDate.now());
        setupTable();

        asOfPicker.valueProperty().addListener((o, ov, nv) -> load());
        showInactive.selectedProperty().addListener((o, ov, nv) -> load());
        searchField.setOnSearchAction(this::load);

        employeeTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && employeeTable.getSelectionModel().getSelectedItem() != null) {
                employeeCards.open(employeeTable.getSelectionModel().getSelectedItem().employeeId());
            }
        });

        load();
    }

    private void setupTable() {
        employeeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().fullName()));
        colPosition.setCellValueFactory(
                d -> new SimpleStringProperty(d.getValue().positionName() == null ? "" : d.getValue().positionName()));
        colOpening.setCellValueFactory(d -> new SimpleStringProperty(Formatters.formatMoney(d.getValue().openingBalance())));
        colEarned.setCellValueFactory(d -> new SimpleStringProperty(Formatters.formatMoney(d.getValue().earned())));
        colPaid.setCellValueFactory(d -> new SimpleStringProperty(Formatters.formatMoney(d.getValue().paid())));
        colBalance.setCellValueFactory(d -> new SimpleStringProperty(Formatters.formatMoney(d.getValue().balance())));
        colBalance.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                getStyleClass().removeAll("stat-card-profit", "stat-card-debt");
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    return;
                }
                EmployeeBalanceRow row = getTableRow().getItem();
                setText(value);
                getStyleClass().add(row.balance().compareTo(BigDecimal.ZERO) >= 0 ? "stat-card-profit"
                        : "stat-card-debt");
            }
        });

        employeeTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(EmployeeBalanceRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("payment-voided");
                if (item != null && !empty && !item.active()) {
                    getStyleClass().add("payment-voided");
                }
            }
        });
    }

    private void load() {
        LocalDate date = asOfPicker.getValue() == null ? LocalDate.now() : asOfPicker.getValue();
        boolean includeInactive = showInactive.isSelected();
        String query = searchField.getText();
        Task<List<EmployeeBalanceRow>> task = new Task<>() {
            @Override
            protected List<EmployeeBalanceRow> call() {
                List<EmployeeBalanceRow> rows = balanceService.balancesAsOf(date, includeInactive);
                if (query != null && !query.isBlank()) {
                    String q = query.trim().toLowerCase();
                    rows = rows.stream().filter(r -> r.fullName().toLowerCase().contains(q))
                            .collect(java.util.stream.Collectors.toList());
                }
                return rows;
            }
        };
        taskRunner.run(task, this::apply);
    }

    private void apply(List<EmployeeBalanceRow> rows) {
        employeeTable.setItems(FXCollections.observableArrayList(rows));
        BigDecimal owed = BigDecimal.ZERO;
        for (EmployeeBalanceRow row : rows) {
            if (row.balance().compareTo(BigDecimal.ZERO) > 0) {
                owed = owed.add(row.balance());
            }
        }
        owedTotalLabel.setText(java.text.MessageFormat.format(bundle.getString("employee.owed_total"),
                Formatters.formatMoney(owed)));
    }

    @FXML
    public void handleNewEmployee() {
        openForm(null);
    }

    private void openForm(EmployeeBalanceRow row) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/employees_form.fxml"), bundle);
            loader.setControllerFactory(context::getBean);
            Parent root = loader.load();

            EmployeeFormController controller = loader.getController();
            controller.setEmployee(row == null ? null : employeeService().findForEdit(row.employeeId()));

            Stage stage = new Stage();
            Window owner = AlertHelper.getActiveWindow(Window.getWindows());
            if (owner != null) {
                stage.initOwner(owner);
            }
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(row == null ? bundle.getString("employee.form.new") : bundle.getString("employee.form.edit"));

            Scene scene = new Scene(root);
            com.mebelsex.ui.ThemeManager theme = com.mebelsex.ui.ThemeManager.getInstance();
            if (theme != null) {
                theme.install(scene);
            } else {
                scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
            }
            scene.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    stage.close();
                }
            });
            stage.setScene(scene);
            stage.sizeToScene();
            stage.setMinWidth(520);

            controller.setStage(stage);
            stage.showAndWait();

            if (controller.isSaved()) {
                load();
            }
        } catch (Throwable e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error("UI error", e);
            AlertHelper.error(bundle.getString("ui.error"), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private com.mebelsex.employees.EmployeeService employeeService() {
        return context.getBean(com.mebelsex.employees.EmployeeService.class);
    }
}
