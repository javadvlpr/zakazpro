package com.mebelsex.ui.catalog;

import com.mebelsex.common.BusinessException;
import com.mebelsex.expenses.CategoryRow;
import com.mebelsex.expenses.ExpenseCatalogService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** New / rename expense category dialog. */
@Controller
@Scope("prototype")
public class CategoryFormController {

    private final ExpenseCatalogService catalogService;
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
    private Long categoryId;
    private boolean saved = false;

    public CategoryFormController(ExpenseCatalogService catalogService) {
        this.catalogService = catalogService;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    @FXML
    public void initialize() {
        saveButton.setDefaultButton(true);
    }

    public void setCategory(CategoryRow category) {
        if (category == null) {
            titleLabel.setText(bundle.getString("expense.category.new"));
        } else {
            titleLabel.setText(bundle.getString("expense.category.edit"));
            categoryId = category.id();
            nameField.setText(category.name());
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
        String name = nameField.getText();
        try {
            if (categoryId == null) {
                catalogService.createCategory(name);
            } else {
                catalogService.renameCategory(categoryId, name);
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
