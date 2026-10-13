package com.mebelsex.ui.orders;

import com.mebelsex.orders.OrderListItem;
import com.mebelsex.orders.OrderService;
import com.mebelsex.orders.OrderStatus;
import com.mebelsex.ui.TaskRunner;
import com.mebelsex.ui.components.EnumLabels;
import com.mebelsex.ui.components.MoneyParser;
import com.mebelsex.ui.components.SearchField;
import com.mebelsex.ui.components.StatusBadge;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class OrdersController {

    private final OrderService orderService;
    private final TaskRunner taskRunner;
    private final OrderDialogs orderDialogs;
    private final OrderCards orderCards;

    @FXML
    private ComboBox<String> statusFilter;
    @FXML
    private CheckBox includeClosedCheck;
    @FXML
    private SearchField searchField;

    @FXML
    private TableView<OrderListItem> table;
    @FXML
    private TableColumn<OrderListItem, String> colNumber;
    @FXML
    private TableColumn<OrderListItem, String> colOrderDate;
    @FXML
    private TableColumn<OrderListItem, String> colDueDate;
    @FXML
    private TableColumn<OrderListItem, String> colCustomer;
    @FXML
    private TableColumn<OrderListItem, String> colProduct;
    @FXML
    private TableColumn<OrderListItem, String> colAgreedPrice;
    @FXML
    private TableColumn<OrderListItem, String> colPaid;
    @FXML
    private TableColumn<OrderListItem, String> colDebt;
    @FXML
    private TableColumn<OrderListItem, OrderStatus> colStatus;

    @FXML
    private Button editButton;
    @FXML
    private Button statusChangeButton;
    @FXML
    private Label totalDebtLabel;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public OrdersController(OrderService orderService, TaskRunner taskRunner, OrderDialogs orderDialogs,
            OrderCards orderCards) {
        this.orderService = orderService;
        this.taskRunner = taskRunner;
        this.orderDialogs = orderDialogs;
        this.orderCards = orderCards;
    }

    @FXML
    public void initialize() {
        setupFilter();
        setupTableColumns();
        setupRowFactory();

        editButton.setDisable(true);
        statusChangeButton.setDisable(true);

        table.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> {
            boolean hasSeg = nv != null;
            editButton.setDisable(!hasSeg);
            statusChangeButton.setDisable(!hasSeg);
        });

        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                orderCards.open(table.getSelectionModel().getSelectedItem().id());
            }
        });

        searchField.setOnSearchAction(this::loadData);
        includeClosedCheck.selectedProperty().addListener((obs, ov, nv) -> loadData());

        loadData();
    }

    private void setupFilter() {
        statusFilter.getItems().add(com.mebelsex.common.Messages.get("orders.filter.all"));
        for (OrderStatus status : OrderStatus.values()) {
            statusFilter.getItems().add(EnumLabels.get(status));
        }
        statusFilter.getSelectionModel().select(0);
        statusFilter.valueProperty().addListener((obs, ov, nv) -> loadData());
    }

    private OrderStatus getSelectedStatus() {
        int idx = statusFilter.getSelectionModel().getSelectedIndex();
        if (idx <= 0)
            return null;
        return OrderStatus.values()[idx - 1]; // First item is 'Hammasi'
    }

    private void setupTableColumns() {
        colNumber.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().orderNumber()));
        colOrderDate.setCellValueFactory(data -> {
            var date = data.getValue().orderDate();
            return new SimpleStringProperty(date != null ? DATE_FORMATTER.format(date) : "");
        });
        colCustomer.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().customerName()));
        colProduct.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().productDescription()));

        colDueDate.getStyleClass().add("due-date-cell");
        colDueDate.setCellValueFactory(data -> {
            var date = data.getValue().dueDate();
            return new SimpleStringProperty(date != null ? DATE_FORMATTER.format(date) : "");
        });

        colAgreedPrice.setCellValueFactory(
                data -> new SimpleStringProperty(MoneyParser.format(data.getValue().agreedPrice())));
        colPaid.setCellValueFactory(data -> new SimpleStringProperty(MoneyParser.format(data.getValue().paid())));
        colDebt.setCellValueFactory(data -> new SimpleStringProperty(MoneyParser.format(data.getValue().debt())));

        // Set status badge cell factory
        colStatus.setCellValueFactory(
                data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().status()));
        colStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(OrderStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                } else {
                    setGraphic(new StatusBadge(status));
                }
            }
        });
    }

    private void setupRowFactory() {
        table.setRowFactory(tv -> new TableRow<OrderListItem>() {
            @Override
            protected void updateItem(OrderListItem item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("overdue");
                if (item != null && !empty) {
                    if (item.overdue()) {
                        getStyleClass().add("overdue");
                    }
                }
            }
        });
    }

    private void loadData() {
        OrderStatus status = getSelectedStatus();
        boolean includeClosed = includeClosedCheck.isSelected();
        String query = searchField.getText();

        Task<List<OrderListItem>> task = new Task<>() {
            @Override
            protected List<OrderListItem> call() throws Exception {
                return orderService.listItems(status, includeClosed, query, null);
            }
        };

        taskRunner.run(task, result -> {
            table.setItems(FXCollections.observableArrayList(result));
            updateFooter(result);
        });
    }

    private void updateFooter(List<OrderListItem> items) {
        BigDecimal totalDebt = BigDecimal.ZERO;
        for (OrderListItem item : items) {
            totalDebt = totalDebt.add(item.debt());
        }
        totalDebtLabel.setText(com.mebelsex.common.Messages.format("orders.total_debt",
                MoneyParser.format(totalDebt)));
    }

    @FXML
    public void handleNew() {
        orderDialogs.openOrderForm(null, this::loadData);
    }

    @FXML
    public void handleEdit() {
        OrderListItem selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            orderDialogs.openOrderForm(selected.id(), this::loadData);
        }
    }

    @FXML
    public void handleStatusChange() {
        OrderListItem selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            orderDialogs.openStatusDialog(selected, this::loadData);
        }
    }
}
