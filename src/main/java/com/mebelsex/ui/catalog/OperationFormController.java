package com.mebelsex.ui.catalog;

import com.mebelsex.common.BusinessException;
import com.mebelsex.ui.components.MoneyParser;
import com.mebelsex.ui.components.MoneyField;
import com.mebelsex.work.OperationTypeRow;
import com.mebelsex.work.OperationTypeService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** New / edit operation type dialog (Katalog > Ish turlari). */
@Controller
@Scope("prototype")
public class OperationFormController {

    private final OperationTypeService operationTypeService;
    private final ResourceBundle bundle;

    @FXML
    private Label titleLabel;
    @FXML
    private TextField nameField;
    @FXML
    private ComboBox<String> unitCombo;
    @FXML
    private MoneyField priceField;
    @FXML
    private Label errorLabel;
    @FXML
    private Button cancelButton;
    @FXML
    private Button saveButton;

    private Stage stage;
    private Long operationId;
    private boolean saved = false;

    public OperationFormController(OperationTypeService operationTypeService) {
        this.operationTypeService = operationTypeService;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    @FXML
    public void initialize() {
        saveButton.setDefaultButton(true);
        unitCombo.setItems(FXCollections.observableArrayList(unitSuggestions()));
    }

    public void setOperation(OperationTypeRow row) {
        if (row == null) {
            titleLabel.setText(bundle.getString("operation.new"));
        } else {
            titleLabel.setText(bundle.getString("operation.edit"));
            operationId = row.id();
            nameField.setText(row.name());
            unitCombo.setValue(row.unit());
            priceField.setText(MoneyParser.format(row.defaultPrice()));
            nameField.selectAll();
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
        String unit = unitCombo.getEditor().getText();
        if ((unit == null || unit.isBlank()) && unitCombo.getValue() != null) {
            unit = unitCombo.getValue();
        }
        try {
            if (operationId == null) {
                operationTypeService.create(nameField.getText(), unit, priceField.getAmount());
            } else {
                operationTypeService.update(operationId, nameField.getText(), unit, priceField.getAmount());
            }
            saved = true;
            stage.close();
        } catch (BusinessException e) {
            showError(e.getMessageKey());
        }
    }

    @FXML
    public void handleCancel() {
        stage.close();
    }

    private List<String> unitSuggestions() {
        List<String> result = new ArrayList<>();
        String raw = bundle.getString("unit.suggestions");
        for (String part : raw.split(",")) {
            String clean = part.trim();
            if (!clean.isEmpty()) {
                result.add(clean);
            }
        }
        return result;
    }

    private void showError(String key) {
        String message;
        try {
            message = bundle.getString(key);
        } catch (MissingResourceException e) {
            message = key;
        }
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
