package com.mebelsex.ui.employees;

import com.mebelsex.common.BusinessException;
import com.mebelsex.common.Formatters;
import com.mebelsex.common.RecordStatus;
import com.mebelsex.employees.EmployeeBalanceRow;
import com.mebelsex.employees.EmployeeBalanceService;
import com.mebelsex.employees.EmployeeRow;
import com.mebelsex.employees.EmployeeService;
import com.mebelsex.ui.AlertHelper;
import com.mebelsex.ui.EnumLabels;
import com.mebelsex.ui.MainController;
import com.mebelsex.ui.TaskRunner;
import com.mebelsex.ui.components.MoneyField;
import com.mebelsex.ui.components.MoneyParser;
import com.mebelsex.ui.components.QuantityParser;
import com.mebelsex.ui.components.UzDatePicker;
import com.mebelsex.ui.orders.OrderCards;
import com.mebelsex.work.SalaryPaymentRow;
import com.mebelsex.work.SalaryPaymentService;
import com.mebelsex.work.WorkLogRow;
import com.mebelsex.work.WorkLogService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/** Employee card: balance as of a date, work logs and wage payments. */
@Controller
@Scope("prototype")
public class EmployeeCardController {

    private final EmployeeService employeeService;
    private final EmployeeBalanceService balanceService;
    private final WorkLogService workLogService;
    private final SalaryPaymentService salaryPaymentService;
    private final TaskRunner taskRunner;
    private final MainController mainController;
    private final OrderCards orderCards;
    private final EnumLabels enumLabels;
    private final ResourceBundle bundle;

    @FXML
    private Label nameLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label phoneLabel;
    @FXML
    private Label positionLabel;
    @FXML
    private UzDatePicker asOfPicker;

    @FXML
    private Label earnedLabel;
    @FXML
    private Label paidLabel;
    @FXML
    private Label balanceLabel;

    @FXML
    private UzDatePicker fromPicker;
    @FXML
    private UzDatePicker toPicker;
    @FXML
    private Button voidWorkButton;
    @FXML
    private TableView<WorkLogRow> worksTable;
    @FXML
    private TableColumn<WorkLogRow, String> colWorkDate;
    @FXML
    private TableColumn<WorkLogRow, String> colWorkOrder;
    @FXML
    private TableColumn<WorkLogRow, String> colWorkOperation;
    @FXML
    private TableColumn<WorkLogRow, String> colWorkQuantity;
    @FXML
    private TableColumn<WorkLogRow, String> colWorkPrice;
    @FXML
    private TableColumn<WorkLogRow, String> colWorkAmount;
    @FXML
    private TableColumn<WorkLogRow, String> colWorkStatus;

    @FXML
    private TableView<SalaryPaymentRow> paymentsTable;
    @FXML
    private TableColumn<SalaryPaymentRow, String> colPayDate;
    @FXML
    private TableColumn<SalaryPaymentRow, String> colPayAmount;
    @FXML
    private TableColumn<SalaryPaymentRow, String> colPayComment;
    @FXML
    private TableColumn<SalaryPaymentRow, String> colPayStatus;
    @FXML
    private javafx.scene.layout.VBox paymentQuickAdd;
    @FXML
    private MoneyField amountField;
    @FXML
    private UzDatePicker paymentDateField;
    @FXML
    private TextField commentField;
    @FXML
    private Button addPaymentButton;
    @FXML
    private Label afterBalanceLabel;
    @FXML
    private Label warningLabel;
    @FXML
    private Button voidPaymentButton;
    @FXML
    private Label paymentErrorLabel;

    private Long employeeId;
    private EmployeeRow employee;
    private BigDecimal currentBalance = BigDecimal.ZERO;

    public EmployeeCardController(EmployeeService employeeService, EmployeeBalanceService balanceService,
            WorkLogService workLogService, SalaryPaymentService salaryPaymentService, TaskRunner taskRunner,
            MainController mainController, OrderCards orderCards, EnumLabels enumLabels) {
        this.employeeService = employeeService;
        this.balanceService = balanceService;
        this.workLogService = workLogService;
        this.salaryPaymentService = salaryPaymentService;
        this.taskRunner = taskRunner;
        this.mainController = mainController;
        this.orderCards = orderCards;
        this.enumLabels = enumLabels;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    @FXML
    public void initialize() {
        asOfPicker.setValue(LocalDate.now());
        paymentDateField.setValue(LocalDate.now());
        setupWorkColumns();
        setupPaymentColumns();

        asOfPicker.valueProperty().addListener((o, ov, nv) -> reload());
        fromPicker.valueProperty().addListener((o, ov, nv) -> loadWorks());
        toPicker.valueProperty().addListener((o, ov, nv) -> loadWorks());

        voidWorkButton.setDisable(true);
        worksTable.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> updateVoidButtons());
        paymentsTable.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> updateVoidButtons());
        worksTable.setOnMouseClicked(event -> {
            WorkLogRow selected = worksTable.getSelectionModel().getSelectedItem();
            if (event.getClickCount() == 2 && selected != null) {
                orderCards.open(selected.orderId());
            }
        });

        amountField.textProperty().addListener((o, ov, nv) -> updateAfterBalance());
        paymentQuickAdd.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleAddPayment();
                event.consume();
            }
        });
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
        reload();
    }

    public void reload() {
        loadMetrics();
        loadWorks();
        loadPayments();
    }

    private void loadMetrics() {
        if (employeeId == null) {
            return;
        }
        Task<EmployeeBalanceRow> task = new Task<>() {
            @Override
            protected EmployeeBalanceRow call() {
                employee = employeeService.findForEdit(employeeId);
                LocalDate date = asOfPicker.getValue() == null ? LocalDate.now() : asOfPicker.getValue();
                return balanceService.balancesAsOf(date, true).stream()
                        .filter(r -> r.employeeId().equals(employeeId))
                        .findFirst().orElse(null);
            }
        };
        taskRunner.run(task, this::applyMetrics);
    }

    private void applyMetrics(EmployeeBalanceRow row) {
        if (employee != null) {
            nameLabel.setText(employee.fullName());
            phoneLabel.setText(employee.phone() == null ? "" : employee.phone());
            positionLabel.setText(employee.positionName() == null ? "" : employee.positionName());
            statusLabel.setText(bundle.getString(employee.active() ? "employee.card.status.active"
                    : "employee.card.status.inactive"));
            statusLabel.getStyleClass().removeAll("muted-text");
            if (!employee.active()) {
                statusLabel.getStyleClass().add("muted-text");
            }
        }
        if (row == null) {
            return;
        }
        earnedLabel.setText(Formatters.formatMoney(row.earned()));
        paidLabel.setText(Formatters.formatMoney(row.paid()));
        balanceLabel.setText(Formatters.formatMoney(row.balance()));
        currentBalance = row.balance();
        applyBalanceStyle(balanceLabel, row.balance());
        updateAfterBalance();
    }

    private void applyBalanceStyle(Label label, BigDecimal balance) {
        label.getStyleClass().removeAll("stat-card-profit", "stat-card-debt");
        label.getStyleClass().add(balance.compareTo(BigDecimal.ZERO) >= 0 ? "stat-card-profit" : "stat-card-debt");
    }

    private void setupWorkColumns() {
        worksTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colWorkDate.setCellValueFactory(d -> new SimpleStringProperty(Formatters.formatDate(d.getValue().date())));
        colWorkOrder.setCellValueFactory(
                d -> new SimpleStringProperty(d.getValue().orderNumber() == null ? "" : d.getValue().orderNumber()));
        colWorkOperation.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().operationName()));
        colWorkQuantity.setCellValueFactory(
                d -> new SimpleStringProperty(QuantityParser.format(d.getValue().quantity())));
        colWorkPrice.setCellValueFactory(d -> new SimpleStringProperty(MoneyParser.format(d.getValue().unitPrice())));
        colWorkAmount.setCellValueFactory(d -> new SimpleStringProperty(MoneyParser.format(d.getValue().amount())));
        colWorkStatus.setCellValueFactory(d -> new SimpleStringProperty(enumLabels.getLabel(d.getValue().status())));

        worksTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(WorkLogRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("payment-voided");
                setTooltip(null);
                if (item != null && !empty && item.status() == RecordStatus.VOIDED) {
                    getStyleClass().add("payment-voided");
                    if (item.voidReason() != null && !item.voidReason().isBlank()) {
                        setTooltip(new Tooltip(item.voidReason()));
                    }
                }
            }
        });
    }

    private void setupPaymentColumns() {
        paymentsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colPayDate.setCellValueFactory(d -> new SimpleStringProperty(Formatters.formatDate(d.getValue().date())));
        colPayAmount.setCellValueFactory(d -> new SimpleStringProperty(MoneyParser.format(d.getValue().amount())));
        colPayComment.setCellValueFactory(
                d -> new SimpleStringProperty(d.getValue().comment() == null ? "" : d.getValue().comment()));
        colPayStatus.setCellValueFactory(d -> new SimpleStringProperty(enumLabels.getLabel(d.getValue().status())));

        paymentsTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(SalaryPaymentRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("payment-voided");
                setTooltip(null);
                if (item != null && !empty && item.status() == RecordStatus.VOIDED) {
                    getStyleClass().add("payment-voided");
                    if (item.voidReason() != null && !item.voidReason().isBlank()) {
                        setTooltip(new Tooltip(item.voidReason()));
                    }
                }
            }
        });
    }

    private void loadWorks() {
        if (employeeId == null) {
            return;
        }
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        Task<List<WorkLogRow>> task = new Task<>() {
            @Override
            protected List<WorkLogRow> call() {
                return workLogService.listForEmployee(employeeId, from, to);
            }
        };
        taskRunner.run(task, rows -> {
            worksTable.setItems(FXCollections.observableArrayList(rows));
            updateVoidButtons();
        });
    }

    private void loadPayments() {
        if (employeeId == null) {
            return;
        }
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        Task<List<SalaryPaymentRow>> task = new Task<>() {
            @Override
            protected List<SalaryPaymentRow> call() {
                return salaryPaymentService.listForEmployee(employeeId, from, to);
            }
        };
        taskRunner.run(task, rows -> {
            paymentsTable.setItems(FXCollections.observableArrayList(rows));
            updateVoidButtons();
        });
    }

    private void updateVoidButtons() {
        WorkLogRow work = worksTable.getSelectionModel().getSelectedItem();
        voidWorkButton.setDisable(work == null || work.status() != RecordStatus.ACTIVE);
        SalaryPaymentRow payment = paymentsTable.getSelectionModel().getSelectedItem();
        voidPaymentButton.setDisable(payment == null || payment.status() != RecordStatus.ACTIVE);
    }

    private void updateAfterBalance() {
        BigDecimal amount = amountField.getAmount();
        BigDecimal after = currentBalance;
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            after = currentBalance.subtract(amount);
        }
        afterBalanceLabel.setText(MessageFormat.format(bundle.getString("employee.card.pay.remaining"),
                Formatters.formatMoney(after)));
        boolean negative = after.compareTo(BigDecimal.ZERO) < 0;
        warningLabel.setVisible(negative);
        warningLabel.setManaged(negative);
    }

    @FXML
    public void handleAddPayment() {
        if (employeeId == null) {
            return;
        }
        try {
            salaryPaymentService.addPayment(employeeId, amountField.getAmount(), paymentDateField.getValue(),
                    commentField.getText());
            amountField.clear();
            commentField.clear();
            paymentDateField.setValue(LocalDate.now());
            hideError();
            reload();
        } catch (BusinessException e) {
            showError(bundle.getString(e.getMessageKey()));
        }
    }

    @FXML
    public void handleVoidWork() {
        WorkLogRow selected = worksTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.status() != RecordStatus.ACTIVE) {
            return;
        }
        String reason = askReason("work.void.title", "work.void.header", "work.void.prompt");
        if (reason == null) {
            return;
        }
        try {
            workLogService.voidWorkLog(selected.id(), reason);
            hideError();
            reload();
        } catch (BusinessException e) {
            showError(bundle.getString(e.getMessageKey()));
        }
    }

    @FXML
    public void handleVoidPayment() {
        SalaryPaymentRow selected = paymentsTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.status() != RecordStatus.ACTIVE) {
            return;
        }
        String reason = askReason("salary.void.title", "salary.void.header", "salary.void.prompt");
        if (reason == null) {
            return;
        }
        try {
            salaryPaymentService.voidPayment(selected.id(), reason);
            hideError();
            reload();
        } catch (BusinessException e) {
            showError(bundle.getString(e.getMessageKey()));
        }
    }

    @FXML
    public void handleBack() {
        mainController.showEmployees();
    }

    private String askReason(String titleKey, String headerKey, String promptKey) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(bundle.getString(titleKey));
        dialog.setHeaderText(bundle.getString(headerKey));
        dialog.getEditor().setPromptText(bundle.getString(promptKey));
        com.mebelsex.ui.ThemeManager theme = com.mebelsex.ui.ThemeManager.getInstance();
        if (theme != null) {
            theme.install(dialog.getDialogPane());
        }
        Window owner = AlertHelper.getActiveWindow(Window.getWindows());
        if (owner != null) {
            dialog.initOwner(owner);
        }
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return null;
        }
        String reason = result.get().trim();
        if (reason.isEmpty()) {
            return null;
        }
        return reason;
    }

    private void showError(String message) {
        paymentErrorLabel.setText(message);
        paymentErrorLabel.setVisible(true);
        paymentErrorLabel.setManaged(true);
    }

    private void hideError() {
        paymentErrorLabel.setVisible(false);
        paymentErrorLabel.setManaged(false);
    }
}
