package com.mebelsex.ui.catalog;

import com.mebelsex.common.BusinessException;
import com.mebelsex.employees.PositionRow;
import com.mebelsex.employees.PositionService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** New / rename position dialog (Katalog > Lavozimlar). */
@Controller
@Scope("prototype")
public class PositionFormController {

    private final PositionService positionService;
    private final ResourceBundle bundle;

    @FXML
    private Label titleLabel;
    @FXML
    private TextField nameField;
    @FXML
    private Label errorLabel;
    @FXML
    private Button cancelButton;
    @FXML
    private Button saveButton;

    private Stage stage;
    private Long positionId;
    private boolean saved = false;

    public PositionFormController(PositionService positionService) {
        this.positionService = positionService;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    @FXML
    public void initialize() {
        saveButton.setDefaultButton(true);
    }

    public void setPosition(PositionRow row) {
        if (row == null) {
            titleLabel.setText(bundle.getString("position.new"));
        } else {
            titleLabel.setText(bundle.getString("position.edit"));
            positionId = row.id();
            nameField.setText(row.name());
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
        try {
            if (positionId == null) {
                positionService.create(nameField.getText());
            } else {
                positionService.rename(positionId, nameField.getText());
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
