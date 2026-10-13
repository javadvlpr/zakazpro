package com.mebelsex.ui;

import com.mebelsex.backup.BackupService;
import com.mebelsex.backup.BackupStatus;
import com.mebelsex.common.Formatters;
import com.mebelsex.orders.DashboardService;
import com.mebelsex.orders.DashboardSummary;
import com.mebelsex.orders.OrderListItem;
import com.mebelsex.ui.components.MoneyParser;
import com.mebelsex.ui.orders.OrderCards;
import com.mebelsex.ui.orders.OrderDialogs;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

/** Bosh sahifa: real stat cards and the due-soon orders table. */
@Controller
@Scope("prototype")
public class DashboardController {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final DashboardService dashboardService;
    private final TaskRunner taskRunner;
    private final OrderDialogs orderDialogs;
    private final OrderCards orderCards;
    private final BackupService backupService;
    private final ResourceBundle bundle;

    @FXML
    private Label activeOrdersLabel;
    @FXML
    private Label debtLabel;
    @FXML
    private Label overdueLabel;
    @FXML
    private Label incomeLabel;
    @FXML
    private Label workersOwedLabel;
    @FXML
    private Label monthProfitLabel;
    @FXML
    private Label backupLabel;

    @FXML
    private TableView<OrderListItem> dueSoonTable;
    @FXML
    private TableColumn<OrderListItem, String> colNo;
    @FXML
    private TableColumn<OrderListItem, String> colCustomer;
    @FXML
    private TableColumn<OrderListItem, String> colProduct;
    @FXML
    private TableColumn<OrderListItem, String> colDue;
    @FXML
    private TableColumn<OrderListItem, String> colDebt;

    public DashboardController(DashboardService dashboardService, TaskRunner taskRunner, OrderDialogs orderDialogs,
            OrderCards orderCards, BackupService backupService) {
        this.dashboardService = dashboardService;
        this.taskRunner = taskRunner;
        this.orderDialogs = orderDialogs;
        this.orderCards = orderCards;
        this.backupService = backupService;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    @FXML
    public void initialize() {
        dueSoonTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colNo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().orderNumber()));
        colCustomer.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().customerName()));
        colProduct.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().productDescription()));
        colDue.setCellValueFactory(d -> new SimpleStringProperty(Formatters.formatDate(d.getValue().dueDate())));
        colDebt.setCellValueFactory(d -> new SimpleStringProperty(MoneyParser.format(d.getValue().debt())));

        dueSoonTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && dueSoonTable.getSelectionModel().getSelectedItem() != null) {
                orderCards.open(dueSoonTable.getSelectionModel().getSelectedItem().id());
            }
        });

        load();
        loadBackupStatus();
    }

    private void loadBackupStatus() {
        BackupStatus status = backupService.status();
        String text;
        String style = null;
        if (status.neverRun()) {
            text = bundle.getString("settings.backup.never");
            style = "stat-card-debt";
        } else if (status.ageDays() != null && status.ageDays() == 0) {
            text = new MessageFormat(bundle.getString("settings.backup.today"))
                    .format(new Object[] { HH_MM.format(status.lastSuccess().toLocalTime()) });
            style = "stat-card-profit";
        } else {
            text = new MessageFormat(bundle.getString("settings.backup.days_ago"))
                    .format(new Object[] { String.valueOf(status.ageDays()) });
            if (status.ageDays() != null && status.ageDays() > 2) {
                style = "stat-card-debt";
            }
        }
        backupLabel.setText(text);
        backupLabel.getStyleClass().removeAll("stat-card-profit", "stat-card-debt");
        if (style != null) {
            backupLabel.getStyleClass().add(style);
        }
    }

    @FXML
    public void handleNewOrder() {
        orderDialogs.openOrderForm(null, this::load);
    }

    public void load() {
        Task<DashboardSummary> task = new Task<>() {
            @Override
            protected DashboardSummary call() {
                return dashboardService.getSummary(LocalDate.now());
            }
        };
        taskRunner.run(task, this::apply);
    }

    private void apply(DashboardSummary summary) {
        activeOrdersLabel.setText(String.valueOf(summary.activeOrders()));

        debtLabel.setText(Formatters.formatMoney(summary.totalDebt()));
        applyAlertStyle(debtLabel, summary.totalDebt().compareTo(BigDecimal.ZERO) > 0);

        overdueLabel.setText(String.valueOf(summary.overdueCount()));
        applyAlertStyle(overdueLabel, summary.overdueCount() > 0);

        incomeLabel.setText(Formatters.formatMoney(summary.monthIncome()));

        workersOwedLabel.setText(Formatters.formatMoney(summary.workersOwed()));
        workersOwedLabel.getStyleClass().removeAll("stat-card-debt", "stat-card-profit");
        workersOwedLabel.getStyleClass().add(
                summary.workersOwed().compareTo(BigDecimal.ZERO) > 0 ? "stat-card-debt" : "stat-card-profit");

        monthProfitLabel.setText(Formatters.formatMoney(summary.monthNetProfit()));
        monthProfitLabel.getStyleClass().removeAll("stat-card-debt", "stat-card-profit");
        monthProfitLabel.getStyleClass().add(
                summary.monthNetProfit().compareTo(BigDecimal.ZERO) < 0 ? "stat-card-debt" : "stat-card-profit");

        dueSoonTable.setItems(FXCollections.observableArrayList(summary.dueSoon()));
    }

    private void applyAlertStyle(Label label, boolean alert) {
        label.getStyleClass().remove("stat-card-debt");
        if (alert) {
            label.getStyleClass().add("stat-card-debt");
        }
    }
}
