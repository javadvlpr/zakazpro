package com.mebelsex.ui.catalog;

import com.mebelsex.common.BusinessException;
import com.mebelsex.expenses.CategoryRow;
import com.mebelsex.expenses.ExpenseCatalogService;
import com.mebelsex.expenses.ItemRow;
import com.mebelsex.ui.components.MoneyField;
import com.mebelsex.ui.components.MoneyParser;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** New / edit expense catalog item dialog. */
@Controller
@Scope("prototype")
public class ItemFormController {

    private final ExpenseCatalogService catalogService;
    private final ResourceBundle bundle;

    @FXML
    private Label titleLabel;
    @FXML
    private ComboBox<CategoryRow> categoryCombo;
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
    private Long itemId;
    private Long defaultCategoryId;
    private boolean saved = false;

    public ItemFormController(ExpenseCatalogService catalogService) {
        this.catalogService = catalogService;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    @FXML
    public void initialize() {
        saveButton.setDefaultButton(true);
        categoryCombo.setConverter(new StringConverter<CategoryRow>() {
            @Override
            public String toString(CategoryRow row) {
                return row == null ? "" : row.name();
            }

            @Override
            public CategoryRow fromString(String s) {
                return null;
            }
        });
        unitCombo.setItems(FXCollections.observableArrayList(unitSuggestions()));
    }

    public void setItem(ItemRow item, Long defaultCategoryId) {
        this.defaultCategoryId = defaultCategoryId;
        List<CategoryRow> categories = catalogService.listCategories(true);
        categoryCombo.setItems(FXCollections.observableArrayList(categories));

        if (item == null) {
            titleLabel.setText(bundle.getString("expense.item.new"));
            selectCategory(defaultCategoryId);
        } else {
            titleLabel.setText(bundle.getString("expense.item.edit"));
            itemId = item.id();
            nameField.setText(item.name());
            unitCombo.setValue(item.unit());
            priceField.setText(MoneyParser.format(item.defaultPrice()));
            selectCategory(item.categoryId());
        }
    }

    private void selectCategory(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        for (CategoryRow row : categoryCombo.getItems()) {
            if (row.id().equals(categoryId)) {
                categoryCombo.getSelectionModel().select(row);
                return;
            }
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
        CategoryRow category = categoryCombo.getValue();
        if (category == null) {
            showError("expense.item.category.required");
            return;
        }
        String name = nameField.getText();
        String unit = unitCombo.getEditor().getText();
        if ((unit == null || unit.isBlank()) && unitCombo.getValue() != null) {
            unit = unitCombo.getValue();
        }
        try {
            if (itemId == null) {
                catalogService.createItem(category.id(), name, unit, priceField.getAmount());
            } else {
                catalogService.updateItem(itemId, category.id(), name, unit, priceField.getAmount());
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
        String raw = bundle.getString("expense.unit.suggestions");
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
