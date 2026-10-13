package com.mebelsex.ui.orders;

import com.mebelsex.common.BusinessException;
import com.mebelsex.common.Formatters;
import com.mebelsex.customers.Customer;
import com.mebelsex.customers.CustomerService;
import com.mebelsex.orders.OrderEditDto;
import com.mebelsex.orders.OrderService;
import com.mebelsex.payments.PaymentMethod;
import com.mebelsex.ui.AlertHelper;
import com.mebelsex.ui.TaskRunner;
import com.mebelsex.ui.components.MoneyField;
import com.mebelsex.ui.components.UzDatePicker;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.springframework.stereotype.Controller;

import static javafx.application.Platform.runLater;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

@Controller
public class OrderFormController {

    private final OrderService orderService;
    private final CustomerService customerService;
    private final TaskRunner taskRunner;
    private final ResourceBundle bundle;

    @FXML
    private Label titleLabel;
    @FXML
    private Label paidInfoLabel;
    @FXML
    private ComboBox<Customer> customerCombo;
    @FXML
    private TextField productField;
    @FXML
    private MoneyField priceField;
    @FXML
    private UzDatePicker orderDateField;
    @FXML
    private UzDatePicker dueDateField;
    @FXML
    private TextArea notesField;

    @FXML
    private VBox advanceSection;
    @FXML
    private CheckBox advanceCheck;
    @FXML
    private GridPane advanceGrid;
    @FXML
    private MoneyField advanceAmountField;
    @FXML
    private ComboBox<PaymentMethod> advanceMethodCombo;
    @FXML
    private UzDatePicker advanceDateField;

    private Stage stage;
    private Long orderIdToEdit;
    private boolean saved = false;
    private boolean editMode = false;

    /** Carries both read-models out of the background Task without touching a lazy entity. */
    private record EditData(OrderEditDto dto, Customer customer) {
    }

    public OrderFormController(OrderService orderService, CustomerService customerService, TaskRunner taskRunner) {
        this.orderService = orderService;
        this.customerService = customerService;
        this.taskRunner = taskRunner;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    @FXML
    public void initialize() {
        setupCustomerCombo();

        advanceMethodCombo.setConverter(methodConverter());
        advanceMethodCombo.setItems(FXCollections.observableArrayList(PaymentMethod.values()));
        advanceMethodCombo.getSelectionModel().select(PaymentMethod.CASH);
        advanceDateField.setValue(LocalDate.now());
        orderDateField.setValue(LocalDate.now());

        advanceCheck.selectedProperty().addListener((obs, ov, nv) -> {
            advanceGrid.setVisible(nv);
            advanceGrid.setManaged(nv);
            if (stage != null) {
                runLater(stage::sizeToScene);
            }
        });

        loadCustomers();
    }

    private void setupCustomerCombo() {
        customerCombo.setConverter(new StringConverter<Customer>() {
            @Override
            public String toString(Customer c) {
                if (c == null)
                    return "";
                return c.getFullName() + " (" + c.getPhone() + ")";
            }

            @Override
            public Customer fromString(String string) {
                return null;
            }
        });
    }

    private void loadCustomers() {
        Task<List<Customer>> task = new Task<>() {
            @Override
            protected List<Customer> call() throws Exception {
                return customerService.search("");
            }
        };

        taskRunner.run(task, customers -> {
            if (editMode) {
                // The edit form shows a single, pre-selected customer; do not overwrite it.
                return;
            }
            customerCombo.setItems(FXCollections.observableArrayList(customers));
        });
    }

    public void setOrder(Long orderId) {
        this.orderIdToEdit = orderId;
        if (orderId != null) {
            editMode = true;
            titleLabel.setText(bundle.getString("action.edit"));
            advanceSection.setVisible(false);
            advanceSection.setManaged(false);
            customerCombo.setDisable(true); // Can't change customer on edit

            Task<EditData> task = new Task<>() {
                @Override
                protected EditData call() {
                    OrderEditDto dto = orderService.getForEdit(orderId);
                    Customer customer = customerService.findById(dto.customerId())
                            .orElseThrow(() -> new BusinessException("customer.error.notfound"));
                    return new EditData(dto, customer);
                }
            };
            taskRunner.run(task, data -> {
                OrderEditDto dto = data.dto();
                customerCombo.setItems(FXCollections.observableArrayList(data.customer()));
                customerCombo.getSelectionModel().select(data.customer());
                productField.setText(dto.productDescription());
                priceField.setText(com.mebelsex.ui.components.MoneyParser.format(dto.agreedPrice()));
                orderDateField.setValue(dto.orderDate() != null ? dto.orderDate() : LocalDate.now());
                dueDateField.setValue(dto.dueDate());
                notesField.setText(dto.notes());
                paidInfoLabel.setText(bundle.getString("order.paid") + ": " + Formatters.formatMoney(dto.paid()));
                paidInfoLabel.setVisible(true);
                paidInfoLabel.setManaged(true);
            });
        }
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public boolean isSaved() {
        return saved;
    }

    @FXML
    public void handleSave() {
        Customer customer = customerCombo.getValue();
        String product = productField.getText();
        BigDecimal price = priceField.getAmount();
        LocalDate due = dueDateField.getValue();
        LocalDate orderDate = orderDateField.getValue();
        String notes = notesField.getText();

        if (customer == null || product == null || product.trim().isEmpty() || price == null || due == null
                || orderDate == null) {
            AlertHelper.error(bundle.getString("ui.error"), bundle.getString("validation.required_fields"));
            return;
        }

        try {
            if (orderIdToEdit == null) {
                BigDecimal advAmt = advanceCheck.isSelected() ? advanceAmountField.getAmount() : null;
                PaymentMethod advMethod = advanceCheck.isSelected() ? advanceMethodCombo.getValue() : null;
                LocalDate advDate = advanceCheck.isSelected() ? advanceDateField.getValue() : null;

                orderService.create(customer.getId(), product.trim(), price, due, orderDate, notes, advAmt, advMethod,
                        advDate);
            } else {
                orderService.update(orderIdToEdit, price, due, orderDate, product.trim(), notes);
            }
            saved = true;
            stage.close();
        } catch (BusinessException e) {
            String error = e.getMessageKey();
            try {
                error = bundle.getString(e.getMessageKey());
            } catch (Exception ignored) {
            }
            AlertHelper.error(bundle.getString("ui.error"), error);
        }
    }

    @FXML
    public void handleCancel() {
        stage.close();
    }

    private StringConverter<PaymentMethod> methodConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(PaymentMethod value) {
                if (value == null)
                    return "";
                try {
                    return bundle.getString("PaymentMethod." + value.name());
                } catch (Exception e) {
                    return value.name();
                }
            }

            @Override
            public PaymentMethod fromString(String string) {
                return null;
            }
        };
    }
}
