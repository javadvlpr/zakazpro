package com.mebelsex.ui.orders;

import com.mebelsex.orders.OrderStatus;
import com.mebelsex.orders.OrderService;
import com.mebelsex.ui.AlertHelper;
import com.mebelsex.ui.components.EnumLabels;
import com.mebelsex.ui.components.MoneyParser;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.ResourceBundle;

@Controller
public class StatusDialogController {

    private final OrderService orderService;
    private final ResourceBundle bundle;

    @FXML
    private ComboBox<OrderStatus> statusCombo;
    @FXML
    private Label warningLabel;
    @FXML
    private Button saveButton;

    private Stage stage;
    private Long orderId;
    private OrderStatus originalStatus;
    private BigDecimal paidAmount;
    private boolean saved = false;

    public StatusDialogController(OrderService orderService) {
        this.orderService = orderService;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    @FXML
    public void initialize() {
        statusCombo.setConverter(new StringConverter<OrderStatus>() {
            @Override
            public String toString(OrderStatus status) {
                return EnumLabels.get(status);
            }

            @Override
            public OrderStatus fromString(String s) {
                return null;
            }
        });

        statusCombo.setItems(FXCollections.observableArrayList(OrderStatus.values()));

        saveButton.setDisable(true);
        statusCombo.valueProperty().addListener((obs, ov, nv) -> updateUI(nv));
    }

    public void setInstanceData(Long orderId, OrderStatus currentStatus, BigDecimal paidAmount) {
        this.orderId = orderId;
        this.originalStatus = currentStatus;
        this.paidAmount = paidAmount;
        statusCombo.getSelectionModel().select(currentStatus);
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public boolean isSaved() {
        return saved;
    }

    private void updateUI(OrderStatus newStatus) {
        if (newStatus == null || newStatus == originalStatus) {
            saveButton.setDisable(true);
            warningLabel.setVisible(false);
            warningLabel.setManaged(false);
            return;
        }

        saveButton.setDisable(false);

        if (newStatus == OrderStatus.CANCELLED) {
            if (paidAmount != null && paidAmount.compareTo(BigDecimal.ZERO) > 0) {
                String warningText = String.format(
                        "Zakazda %s so'm to'langan. Bekor qilish to'lovni qaytarmaydi, qaytarishni alohida kiriting.",
                        MoneyParser.format(paidAmount));
                warningLabel.setText(warningText);
                warningLabel.setVisible(true);
                warningLabel.setManaged(true);
            } else {
                warningLabel.setVisible(false);
                warningLabel.setManaged(false);
            }
        } else {
            warningLabel.setVisible(false);
            warningLabel.setManaged(false);
        }
    }

    @FXML
    public void handleSave() {
        OrderStatus newStatus = statusCombo.getValue();
        try {
            orderService.changeStatus(orderId, newStatus);
            saved = true;
            stage.close();
        } catch (Exception e) {
            AlertHelper.error(bundle.getString("ui.error"), e.getMessage());
        }
    }

    @FXML
    public void handleCancel() {
        stage.close();
    }
}
