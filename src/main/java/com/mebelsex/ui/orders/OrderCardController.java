package com.mebelsex.ui.orders;

import com.mebelsex.common.BusinessException;
import com.mebelsex.common.Formatters;
import com.mebelsex.common.RecordStatus;
import com.mebelsex.employees.EmployeeRow;
import com.mebelsex.employees.EmployeeService;
import com.mebelsex.expenses.CategoryRow;
import com.mebelsex.expenses.ExpenseCatalogService;
import com.mebelsex.expenses.ExpenseRow;
import com.mebelsex.expenses.ExpenseService;
import com.mebelsex.expenses.ItemRow;
import com.mebelsex.orders.ExpenseCategoryTotal;
import com.mebelsex.orders.OrderCardDto;
import com.mebelsex.orders.OrderListItem;
import com.mebelsex.orders.OrderService;
import com.mebelsex.orders.OrderStatus;
import com.mebelsex.payments.PaymentKind;
import com.mebelsex.payments.PaymentMethod;
import com.mebelsex.payments.PaymentRow;
import com.mebelsex.payments.PaymentService;
import com.mebelsex.work.OperationTypeRow;
import com.mebelsex.work.OperationTypeService;
import com.mebelsex.work.WorkLogRow;
import com.mebelsex.work.WorkLogService;
import com.mebelsex.ui.AlertHelper;
import com.mebelsex.ui.EnumLabels;
import com.mebelsex.ui.MainController;
import com.mebelsex.ui.TaskRunner;
import com.mebelsex.ui.components.MoneyField;
import com.mebelsex.ui.components.MoneyParser;
import com.mebelsex.ui.components.QuantityField;
import com.mebelsex.ui.components.QuantityParser;
import com.mebelsex.ui.components.StatusBadge;
import com.mebelsex.ui.components.UzDatePicker;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/** Order card (To'lovlar + Xulosa). Shown inside the content area, not as a modal. */
@Controller
@Scope("prototype")
public class OrderCardController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final ExpenseService expenseService;
    private final ExpenseCatalogService expenseCatalogService;
    private final WorkLogService workLogService;
    private final EmployeeService employeeService;
    private final OperationTypeService operationTypeService;
    private final TaskRunner taskRunner;
    private final OrderDialogs orderDialogs;
    private final MainController mainController;
    private final EnumLabels enumLabels;
    private final ResourceBundle bundle;

    @FXML
    private Label titleLabel;
    @FXML
    private javafx.scene.layout.HBox statusBox;
    @FXML
    private Label customerLabel;
    @FXML
    private Label phoneLabel;
    @FXML
    private Label productLabel;
    @FXML
    private Label orderDateLabel;
    @FXML
    private Label dueDateLabel;
    @FXML
    private Label priceValueLabel;
    @FXML
    private Label paidValueLabel;
    @FXML
    private Label debtValueLabel;

    @FXML
    private TableView<PaymentRow> paymentsTable;
    @FXML
    private TableColumn<PaymentRow, String> colDate;
    @FXML
    private TableColumn<PaymentRow, String> colKind;
    @FXML
    private TableColumn<PaymentRow, String> colMethod;
    @FXML
    private TableColumn<PaymentRow, String> colAmount;
    @FXML
    private TableColumn<PaymentRow, String> colComment;
    @FXML
    private TableColumn<PaymentRow, String> colStatus;

    @FXML
    private javafx.scene.layout.VBox quickAddRow;
    @FXML
    private MoneyField amountField;
    @FXML
    private ComboBox<PaymentKind> kindCombo;
    @FXML
    private ComboBox<PaymentMethod> methodCombo;
    @FXML
    private UzDatePicker dateField;
    @FXML
    private TextField commentField;
    @FXML
    private Button addButton;
    @FXML
    private Label remainingDebtLabel;
    @FXML
    private Button voidPaymentButton;
    @FXML
    private Label cancelledNote;
    @FXML
    private Label paymentErrorLabel;

    @FXML
    private Label summaryPriceLabel;
    @FXML
    private Label summaryPaidLabel;
    @FXML
    private Label summaryDebtLabel;
    @FXML
    private Label summaryExpensesLabel;
    @FXML
    private Label summaryProfitBeforeLaborLabel;
    @FXML
    private javafx.scene.layout.VBox summaryCategoryBox;

    @FXML
    private TableView<ExpenseRow> expensesTable;
    @FXML
    private TableColumn<ExpenseRow, String> colExpDate;
    @FXML
    private TableColumn<ExpenseRow, String> colExpCategory;
    @FXML
    private TableColumn<ExpenseRow, String> colExpItem;
    @FXML
    private TableColumn<ExpenseRow, String> colExpQuantity;
    @FXML
    private TableColumn<ExpenseRow, String> colExpPrice;
    @FXML
    private TableColumn<ExpenseRow, String> colExpAmount;
    @FXML
    private TableColumn<ExpenseRow, String> colExpStatus;
    @FXML
    private javafx.scene.layout.VBox expenseQuickAdd;
    @FXML
    private ComboBox<CategoryRow> expenseCategoryCombo;
    @FXML
    private ComboBox<ItemRow> expenseItemCombo;
    @FXML
    private QuantityField expenseQuantityField;
    @FXML
    private MoneyField expensePriceField;
    @FXML
    private UzDatePicker expenseDateField;
    @FXML
    private TextField expenseDescriptionField;
    @FXML
    private Label expenseSumLabel;
    @FXML
    private Button addExpenseButton;
    @FXML
    private Button voidExpenseButton;
    @FXML
    private Label expenseCancelledNote;
    @FXML
    private Label expenseErrorLabel;

    @FXML
    private TableView<WorkLogRow> worksTable;
    @FXML
    private TableColumn<WorkLogRow, String> colWorkDate;
    @FXML
    private TableColumn<WorkLogRow, String> colWorkEmployee;
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
    private javafx.scene.layout.VBox workQuickAdd;
    @FXML
    private ComboBox<EmployeeRow> workEmployeeCombo;
    @FXML
    private ComboBox<OperationTypeRow> workOperationCombo;
    @FXML
    private QuantityField workQuantityField;
    @FXML
    private MoneyField workPriceField;
    @FXML
    private UzDatePicker workDateField;
    @FXML
    private TextField workCommentField;
    @FXML
    private Label workSumLabel;
    @FXML
    private Button addWorkButton;
    @FXML
    private Button voidWorkButton;
    @FXML
    private Label workCancelledNote;
    @FXML
    private Label workErrorLabel;

    @FXML
    private Label summaryLaborLabel;
    @FXML
    private javafx.scene.layout.VBox summaryLaborEmployeesBox;
    @FXML
    private Label summaryNetProfitLabel;

    private Long orderId;
    private OrderCardDto card;

    public OrderCardController(OrderService orderService, PaymentService paymentService, ExpenseService expenseService,
            ExpenseCatalogService expenseCatalogService, WorkLogService workLogService, EmployeeService employeeService,
            OperationTypeService operationTypeService, TaskRunner taskRunner, OrderDialogs orderDialogs,
            MainController mainController, EnumLabels enumLabels) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.expenseService = expenseService;
        this.expenseCatalogService = expenseCatalogService;
        this.workLogService = workLogService;
        this.employeeService = employeeService;
        this.operationTypeService = operationTypeService;
        this.taskRunner = taskRunner;
        this.orderDialogs = orderDialogs;
        this.mainController = mainController;
        this.enumLabels = enumLabels;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    @FXML
    public void initialize() {
        kindCombo.setConverter(enumConverter());
        kindCombo.setItems(FXCollections.observableArrayList(PaymentKind.values()));
        kindCombo.getSelectionModel().select(PaymentKind.REGULAR);

        methodCombo.setConverter(methodConverter());
        methodCombo.setItems(FXCollections.observableArrayList(PaymentMethod.values()));
        methodCombo.getSelectionModel().select(PaymentMethod.CASH);

        dateField.setValue(LocalDate.now());

        paymentsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        setupColumns();
        setupRowFactory();

        voidPaymentButton.setDisable(true);
        paymentsTable.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> updateVoidButton());

        amountField.textProperty().addListener((o, ov, nv) -> updateRemaining());
        quickAddRow.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleAddPayment();
                event.consume();
            }
        });

        setupExpenseColumns();
        setupExpenseCombo();
        loadExpenseCategories();
        expenseCancelledNote.setManaged(false);
        expenseQuickAdd.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleAddExpense();
                event.consume();
            }
        });

        setupWorkColumns();
        setupWorkCombo();
        loadWorkPickers();
        workCancelledNote.setManaged(false);
        workQuickAdd.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleAddWork();
                event.consume();
            }
        });
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
        reload();
    }

    public void reload() {
        loadCard();
        loadPayments();
        loadExpenses();
        loadWorks();
    }

    private void loadCard() {
        Task<OrderCardDto> task = new Task<>() {
            @Override
            protected OrderCardDto call() {
                return orderService.getCard(orderId);
            }
        };
        taskRunner.run(task, this::applyCard);
    }

    private void loadPayments() {
        Task<List<PaymentRow>> task = new Task<>() {
            @Override
            protected List<PaymentRow> call() {
                return paymentService.listRows(orderId);
            }
        };
        taskRunner.run(task, rows -> paymentsTable.setItems(FXCollections.observableArrayList(rows)));
    }

    private void applyCard(OrderCardDto card) {
        this.card = card;

        titleLabel.setText(MessageFormat.format(bundle.getString("order.card.title"), card.orderNumber()));
        statusBox.getChildren().setAll(new StatusBadge(card.status()));

        customerLabel.setText(card.customerName());
        phoneLabel.setText(card.customerPhone() == null ? "" : card.customerPhone());
        productLabel.setText(card.productDescription());
        orderDateLabel.setText(bundle.getString("order.orderDate") + ": " + Formatters.formatDate(card.orderDate()));
        dueDateLabel.setText(bundle.getString("order.dueDate") + ": " + Formatters.formatDate(card.dueDate()));
        dueDateLabel.getStyleClass().remove("overdue-text");
        if (card.overdue()) {
            dueDateLabel.getStyleClass().add("overdue-text");
        }

        priceValueLabel.setText(Formatters.formatMoney(card.agreedPrice()));
        paidValueLabel.setText(Formatters.formatMoney(card.paid()));
        debtValueLabel.setText(Formatters.formatMoney(card.debt()));
        applyDebtStyle(debtValueLabel, card.debt());

        summaryPriceLabel.setText(Formatters.formatMoney(card.agreedPrice()));
        summaryPaidLabel.setText(Formatters.formatMoney(card.paid()));
        summaryDebtLabel.setText(Formatters.formatMoney(card.debt()));
        applyDebtStyle(summaryDebtLabel, card.debt());

        boolean cancelled = card.status() == OrderStatus.CANCELLED;
        quickAddRow.setDisable(cancelled);
        addButton.setDisable(cancelled);
        cancelledNote.setVisible(cancelled);
        cancelledNote.setManaged(cancelled);

        expenseQuickAdd.setDisable(cancelled);
        addExpenseButton.setDisable(cancelled);
        expenseCancelledNote.setVisible(cancelled);
        expenseCancelledNote.setManaged(cancelled);

        workQuickAdd.setDisable(cancelled);
        addWorkButton.setDisable(cancelled);
        workCancelledNote.setVisible(cancelled);
        workCancelledNote.setManaged(cancelled);

        applyExpenseSummary(card);
        applyLaborSummary(card);

        updateRemaining();
        updateVoidButton();
    }

    private void applyDebtStyle(Label label, BigDecimal debt) {
        label.getStyleClass().removeAll("stat-card-debt", "stat-card-profit");
        label.getStyleClass().add(debt.compareTo(BigDecimal.ZERO) > 0 ? "stat-card-debt" : "stat-card-profit");
    }

    private void setupColumns() {
        colDate.setCellValueFactory(d -> new SimpleStringProperty(Formatters.formatDate(d.getValue().date())));
        colKind.setCellValueFactory(d -> new SimpleStringProperty(enumLabels.getLabel(d.getValue().kind())));
        colMethod.setCellValueFactory(d -> new SimpleStringProperty(enumLabels.getLabel(d.getValue().method())));
        colAmount.setCellValueFactory(d -> new SimpleStringProperty(MoneyParser.format(d.getValue().amount())));
        colComment.setCellValueFactory(
                d -> new SimpleStringProperty(d.getValue().comment() == null ? "" : d.getValue().comment()));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(enumLabels.getLabel(d.getValue().status())));
    }

    private void setupRowFactory() {
        paymentsTable.setRowFactory(tv -> new TableRow<PaymentRow>() {
            @Override
            protected void updateItem(PaymentRow item, boolean empty) {
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

    private void updateRemaining() {
        if (card == null) {
            remainingDebtLabel.setText("");
            return;
        }
        BigDecimal remaining = card.debt();
        BigDecimal amount = amountField.getAmount();
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            remaining = remaining.subtract(amount);
        }
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO;
        }
        remainingDebtLabel.setText(bundle.getString("order.card.remaining") + ": " + Formatters.formatMoney(remaining));
    }

    private void updateVoidButton() {
        PaymentRow selected = paymentsTable.getSelectionModel().getSelectedItem();
        voidPaymentButton.setDisable(selected == null || selected.status() != RecordStatus.ACTIVE);
    }

    @FXML
    public void handleAddPayment() {
        if (orderId == null) {
            return;
        }
        BigDecimal amount = amountField.getAmount();
        PaymentKind kind = kindCombo.getValue();
        PaymentMethod method = methodCombo.getValue();
        LocalDate date = dateField.getValue();
        String comment = commentField.getText();
        try {
            paymentService.addPayment(orderId, amount, kind, method, date,
                    comment == null || comment.isBlank() ? null : comment.trim());
            clearInputs();
            hideError();
            reload();
        } catch (BusinessException e) {
            showError(bundle.getString(e.getMessageKey()));
        }
    }

    @FXML
    public void handleVoidPayment() {
        PaymentRow selected = paymentsTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.status() != RecordStatus.ACTIVE) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(bundle.getString("order.card.void.title"));
        dialog.setHeaderText(bundle.getString("order.card.void.header"));
        dialog.getEditor().setPromptText(bundle.getString("order.card.void.prompt"));
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
            return;
        }
        String reason = result.get().trim();
        if (reason.isEmpty()) {
            showError(bundle.getString("payment.error.void_reason_required"));
            return;
        }
        try {
            paymentService.voidPayment(selected.id(), reason);
            hideError();
            reload();
        } catch (BusinessException e) {
            showError(bundle.getString(e.getMessageKey()));
        }
    }

    @FXML
    public void handleEdit() {
        if (orderId != null) {
            orderDialogs.openOrderForm(orderId, this::reload);
        }
    }

    @FXML
    public void handleStatusChange() {
        if (card == null) {
            return;
        }
        OrderListItem item = new OrderListItem(
                card.id(), card.orderNumber(), card.customerName(), card.productDescription(),
                card.agreedPrice(), card.paid(), card.debt(), card.orderDate(), card.dueDate(), card.status(), card.overdue());
        orderDialogs.openStatusDialog(item, this::reload);
    }

    @FXML
    public void handleBack() {
        mainController.showOrders();
    }

    private void clearInputs() {
        amountField.clear();
        commentField.clear();
        kindCombo.getSelectionModel().select(PaymentKind.REGULAR);
        methodCombo.getSelectionModel().select(PaymentMethod.CASH);
        dateField.setValue(LocalDate.now());
        updateRemaining();
    }

    // ---------------- expenses ----------------

    private void setupExpenseColumns() {
        expensesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colExpDate.setCellValueFactory(d -> new SimpleStringProperty(Formatters.formatDate(d.getValue().date())));
        colExpCategory.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().categoryName()));
        colExpItem.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().itemName() == null ? "" : d.getValue().itemName()));
        colExpQuantity.setCellValueFactory(d -> new SimpleStringProperty(QuantityParser.format(d.getValue().quantity())));
        colExpPrice.setCellValueFactory(d -> new SimpleStringProperty(MoneyParser.format(d.getValue().unitPrice())));
        colExpAmount.setCellValueFactory(d -> new SimpleStringProperty(MoneyParser.format(d.getValue().amount())));
        colExpStatus.setCellValueFactory(d -> new SimpleStringProperty(enumLabels.getLabel(d.getValue().status())));

        expensesTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(ExpenseRow item, boolean empty) {
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
        expensesTable.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> updateVoidExpenseButton());
    }

    private void setupExpenseCombo() {
        expenseCategoryCombo.setConverter(new StringConverter<CategoryRow>() {
            @Override
            public String toString(CategoryRow row) {
                return row == null ? "" : row.name();
            }

            @Override
            public CategoryRow fromString(String s) {
                return null;
            }
        });
        expenseItemCombo.setConverter(new StringConverter<ItemRow>() {
            @Override
            public String toString(ItemRow row) {
                return row == null ? "" : row.name();
            }

            @Override
            public ItemRow fromString(String s) {
                return null;
            }
        });

        expenseCategoryCombo.valueProperty().addListener((o, ov, nv) -> loadActiveExpenseItems());
        expenseItemCombo.valueProperty().addListener((o, ov, nv) -> applyItemSelection(nv));
        expenseQuantityField.textProperty().addListener((o, ov, nv) -> updateExpenseSum());
        expensePriceField.textProperty().addListener((o, ov, nv) -> updateExpenseSum());
        expenseDateField.setValue(LocalDate.now());
    }

    private void loadExpenseCategories() {
        Task<List<CategoryRow>> task = new Task<>() {
            @Override
            protected List<CategoryRow> call() {
                return expenseCatalogService.listActiveCategories();
            }
        };
        taskRunner.run(task, rows -> expenseCategoryCombo.setItems(FXCollections.observableArrayList(rows)));
    }

    private void loadActiveExpenseItems() {
        CategoryRow category = expenseCategoryCombo.getValue();
        if (category == null) {
            expenseItemCombo.setItems(FXCollections.observableArrayList(otherItem()));
            expenseItemCombo.getSelectionModel().selectFirst();
            return;
        }
        Long categoryId = category.id();
        Task<List<ItemRow>> task = new Task<>() {
            @Override
            protected List<ItemRow> call() {
                return expenseCatalogService.listActiveItems(categoryId);
            }
        };
        taskRunner.run(task, rows -> {
            List<ItemRow> options = new ArrayList<>();
            options.add(otherItem());
            options.addAll(rows);
            expenseItemCombo.setItems(FXCollections.observableArrayList(options));
            expenseItemCombo.getSelectionModel().selectFirst();
        });
    }

    private ItemRow otherItem() {
        return new ItemRow(null, null, null, bundle.getString("order.card.expenses.other_item"), null, null, true);
    }

    private void applyItemSelection(ItemRow item) {
        if (item != null && item.id() != null) {
            if (item.defaultPrice() != null) {
                expensePriceField.setText(MoneyParser.format(item.defaultPrice()));
            }
            expenseQuantityField.setPromptText(item.unit() == null ? "" : item.unit());
        }
        updateExpenseSum();
    }

    private void updateExpenseSum() {
        BigDecimal quantity = expenseQuantityField.getQuantity();
        BigDecimal price = expensePriceField.getAmount();
        BigDecimal sum = (quantity != null && price != null)
                ? quantity.multiply(price).setScale(0, RoundingMode.HALF_UP)
                : null;
        expenseSumLabel.setText(MessageFormat.format(
                bundle.getString("order.card.expenses.sum"), Formatters.formatMoney(sum)));
    }

    private void updateVoidExpenseButton() {
        ExpenseRow selected = expensesTable.getSelectionModel().getSelectedItem();
        voidExpenseButton.setDisable(selected == null || selected.status() != RecordStatus.ACTIVE);
    }

    private void loadExpenses() {
        Task<List<ExpenseRow>> task = new Task<>() {
            @Override
            protected List<ExpenseRow> call() {
                return expenseService.listRows(orderId);
            }
        };
        taskRunner.run(task, rows -> expensesTable.setItems(FXCollections.observableArrayList(rows)));
    }

    @FXML
    public void handleAddExpense() {
        if (orderId == null) {
            return;
        }
        CategoryRow category = expenseCategoryCombo.getValue();
        if (category == null) {
            showExpenseError(bundle.getString("expense.error.category_required"));
            return;
        }
        ItemRow item = expenseItemCombo.getValue();
        Long itemId = (item == null || item.id() == null) ? null : item.id();
        try {
            expenseService.addExpense(orderId, category.id(), itemId, expenseDescriptionField.getText(),
                    expenseQuantityField.getQuantity(), expensePriceField.getAmount(), expenseDateField.getValue());
            clearExpenseInputs();
            hideExpenseError();
            reload();
        } catch (BusinessException e) {
            showExpenseError(bundle.getString(e.getMessageKey()));
        }
    }

    @FXML
    public void handleVoidExpense() {
        ExpenseRow selected = expensesTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.status() != RecordStatus.ACTIVE) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(bundle.getString("expense.void.title"));
        dialog.setHeaderText(bundle.getString("expense.void.header"));
        dialog.getEditor().setPromptText(bundle.getString("expense.void.prompt"));
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
            return;
        }
        String reason = result.get().trim();
        if (reason.isEmpty()) {
            showExpenseError(bundle.getString("expense.error.void_reason_required"));
            return;
        }
        try {
            expenseService.voidExpense(selected.id(), reason);
            hideExpenseError();
            reload();
        } catch (BusinessException e) {
            showExpenseError(bundle.getString(e.getMessageKey()));
        }
    }

    private void clearExpenseInputs() {
        expenseQuantityField.clear();
        expensePriceField.clear();
        expenseDescriptionField.clear();
        expenseQuantityField.setPromptText("");
        if (!expenseItemCombo.getItems().isEmpty()) {
            expenseItemCombo.getSelectionModel().selectFirst();
        }
        expenseDateField.setValue(LocalDate.now());
        updateExpenseSum();
    }

    private void applyExpenseSummary(OrderCardDto card) {
        summaryExpensesLabel.setText(Formatters.formatMoney(card.expensesTotal()));

        String profitText = Formatters.formatMoney(card.profitBeforeLabor());
        if (card.profitBeforeLaborPercent() != null) {
            profitText = profitText + " (" + card.profitBeforeLaborPercent().toPlainString() + "%)";
        }
        summaryProfitBeforeLaborLabel.setText(profitText);
        summaryProfitBeforeLaborLabel.getStyleClass().removeAll("stat-card-profit", "stat-card-debt");
        summaryProfitBeforeLaborLabel.getStyleClass().add(
                card.profitBeforeLabor().compareTo(BigDecimal.ZERO) >= 0 ? "stat-card-profit" : "stat-card-debt");

        summaryCategoryBox.getChildren().clear();
        for (ExpenseCategoryTotal total : card.expenseCategories()) {
            Label line = new Label(total.categoryName() + ": " + Formatters.formatMoney(total.total()));
            line.getStyleClass().add("muted-text");
            summaryCategoryBox.getChildren().add(line);
        }
    }

    private void applyLaborSummary(OrderCardDto card) {
        summaryLaborLabel.setText(Formatters.formatMoney(card.laborTotal()));

        summaryLaborEmployeesBox.getChildren().clear();
        for (com.mebelsex.orders.LaborEmployeeTotal total : card.laborEmployees()) {
            Label line = new Label(total.employeeName() + ": " + Formatters.formatMoney(total.total()));
            line.getStyleClass().add("muted-text");
            summaryLaborEmployeesBox.getChildren().add(line);
        }

        String profitText = Formatters.formatMoney(card.netProfit());
        if (card.netProfitPercent() != null) {
            profitText = profitText + " (" + card.netProfitPercent().toPlainString() + "%)";
        } else {
            profitText = profitText + " (\u2014)";
        }
        summaryNetProfitLabel.setText(profitText);
        summaryNetProfitLabel.getStyleClass().removeAll("stat-card-profit", "stat-card-debt");
        summaryNetProfitLabel.getStyleClass().add(
                card.netProfit().compareTo(BigDecimal.ZERO) >= 0 ? "stat-card-profit" : "stat-card-debt");
    }

    // ---------------- work logs (Ishlar) ----------------

    private void setupWorkColumns() {
        worksTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colWorkDate.setCellValueFactory(d -> new SimpleStringProperty(Formatters.formatDate(d.getValue().date())));
        colWorkEmployee.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().employeeName()));
        colWorkOperation.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().operationName()));
        colWorkQuantity
                .setCellValueFactory(d -> new SimpleStringProperty(QuantityParser.format(d.getValue().quantity())));
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
        worksTable.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> updateVoidWorkButton());
    }

    private void setupWorkCombo() {
        workEmployeeCombo.setConverter(new javafx.util.StringConverter<EmployeeRow>() {
            @Override
            public String toString(EmployeeRow row) {
                return row == null ? "" : row.fullName();
            }

            @Override
            public EmployeeRow fromString(String s) {
                return null;
            }
        });
        workOperationCombo.setConverter(new javafx.util.StringConverter<OperationTypeRow>() {
            @Override
            public String toString(OperationTypeRow row) {
                if (row == null) {
                    return "";
                }
                return row.unit() == null ? row.name() : row.name() + " (" + row.unit() + ")";
            }

            @Override
            public OperationTypeRow fromString(String s) {
                return null;
            }
        });
        workOperationCombo.valueProperty().addListener((o, ov, nv) -> applyOperationSelection(nv));
        workQuantityField.textProperty().addListener((o, ov, nv) -> updateWorkSum());
        workPriceField.textProperty().addListener((o, ov, nv) -> updateWorkSum());
        workDateField.setValue(LocalDate.now());
    }

    private void loadWorkPickers() {
        Task<List<EmployeeRow>> employeeTask = new Task<>() {
            @Override
            protected List<EmployeeRow> call() {
                return employeeService.listActive();
            }
        };
        taskRunner.run(employeeTask, rows -> workEmployeeCombo.setItems(FXCollections.observableArrayList(rows)));

        Task<List<OperationTypeRow>> operationTask = new Task<>() {
            @Override
            protected List<OperationTypeRow> call() {
                return operationTypeService.listActive();
            }
        };
        taskRunner.run(operationTask, rows -> {
            workOperationCombo.setItems(FXCollections.observableArrayList(rows));
            workOperationCombo.getSelectionModel().selectFirst();
        });
    }

    private void applyOperationSelection(OperationTypeRow operation) {
        if (operation != null && operation.defaultPrice() != null) {
            workPriceField.setText(MoneyParser.format(operation.defaultPrice()));
        }
        if (operation != null) {
            workQuantityField.setPromptText(operation.unit() == null ? "" : operation.unit());
        }
        updateWorkSum();
    }

    private void updateWorkSum() {
        BigDecimal quantity = workQuantityField.getQuantity();
        BigDecimal price = workPriceField.getAmount();
        BigDecimal sum = (quantity != null && price != null)
                ? quantity.multiply(price).setScale(0, RoundingMode.HALF_UP)
                : null;
        workSumLabel.setText(MessageFormat.format(bundle.getString("order.card.works.sum"),
                Formatters.formatMoney(sum)));
    }

    private void updateVoidWorkButton() {
        WorkLogRow selected = worksTable.getSelectionModel().getSelectedItem();
        voidWorkButton.setDisable(selected == null || selected.status() != RecordStatus.ACTIVE);
    }

    private void loadWorks() {
        Task<List<WorkLogRow>> task = new Task<>() {
            @Override
            protected List<WorkLogRow> call() {
                return workLogService.listForOrder(orderId);
            }
        };
        taskRunner.run(task, rows -> {
            worksTable.setItems(FXCollections.observableArrayList(rows));
            updateVoidWorkButton();
        });
    }

    @FXML
    public void handleAddWork() {
        if (orderId == null) {
            return;
        }
        EmployeeRow employee = workEmployeeCombo.getValue();
        if (employee == null) {
            showWorkError(bundle.getString("order.card.works.employee_required"));
            return;
        }
        OperationTypeRow operation = workOperationCombo.getValue();
        if (operation == null) {
            showWorkError(bundle.getString("order.card.works.operation_required"));
            return;
        }
        try {
            workLogService.addWorkLog(orderId, employee.id(), operation.id(), workDateField.getValue(),
                    workQuantityField.getQuantity(), workPriceField.getAmount(), workCommentField.getText());
            clearWorkInputs();
            hideWorkError();
            reload();
        } catch (BusinessException e) {
            showWorkError(bundle.getString(e.getMessageKey()));
        }
    }

    @FXML
    public void handleVoidWork() {
        WorkLogRow selected = worksTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.status() != RecordStatus.ACTIVE) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(bundle.getString("work.void.title"));
        dialog.setHeaderText(bundle.getString("work.void.header"));
        dialog.getEditor().setPromptText(bundle.getString("work.void.prompt"));
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
            return;
        }
        String reason = result.get().trim();
        if (reason.isEmpty()) {
            showWorkError(bundle.getString("work.error.void_reason_required"));
            return;
        }
        try {
            workLogService.voidWorkLog(selected.id(), reason);
            hideWorkError();
            reload();
        } catch (BusinessException e) {
            showWorkError(bundle.getString(e.getMessageKey()));
        }
    }

    private void clearWorkInputs() {
        workQuantityField.clear();
        workPriceField.clear();
        workCommentField.clear();
        workQuantityField.setPromptText("");
        if (!workEmployeeCombo.getItems().isEmpty()) {
            workEmployeeCombo.getSelectionModel().selectFirst();
        }
        if (!workOperationCombo.getItems().isEmpty()) {
            workOperationCombo.getSelectionModel().selectFirst();
        }
        workDateField.setValue(LocalDate.now());
        updateWorkSum();
    }

    private void showWorkError(String message) {
        workErrorLabel.setText(message);
        workErrorLabel.setVisible(true);
        workErrorLabel.setManaged(true);
    }

    private void hideWorkError() {
        workErrorLabel.setVisible(false);
        workErrorLabel.setManaged(false);
    }

    private void showExpenseError(String message) {
        expenseErrorLabel.setText(message);
        expenseErrorLabel.setVisible(true);
        expenseErrorLabel.setManaged(true);
    }

    private void hideExpenseError() {
        expenseErrorLabel.setVisible(false);
        expenseErrorLabel.setManaged(false);
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

    private StringConverter<PaymentKind> enumConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(PaymentKind value) {
                return value == null ? "" : enumLabels.getLabel(value);
            }

            @Override
            public PaymentKind fromString(String string) {
                return null;
            }
        };
    }

    private StringConverter<PaymentMethod> methodConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(PaymentMethod value) {
                return value == null ? "" : enumLabels.getLabel(value);
            }

            @Override
            public PaymentMethod fromString(String string) {
                return null;
            }
        };
    }
}
