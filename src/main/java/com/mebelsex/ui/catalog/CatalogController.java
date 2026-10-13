package com.mebelsex.ui.catalog;

import com.mebelsex.common.BusinessException;
import com.mebelsex.employees.PositionRow;
import com.mebelsex.employees.PositionService;
import com.mebelsex.expenses.CategoryRow;
import com.mebelsex.expenses.ExpenseCatalogService;
import com.mebelsex.expenses.ItemRow;
import com.mebelsex.ui.AlertHelper;
import com.mebelsex.ui.TaskRunner;
import com.mebelsex.ui.components.MoneyParser;
import com.mebelsex.ui.components.SearchField;
import com.mebelsex.work.OperationTypeRow;
import com.mebelsex.work.OperationTypeService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.ResourceBundle;

/** Katalog: expense categories (left) and their items (right). */
@Controller
@Scope("prototype")
public class CatalogController {

    private final ExpenseCatalogService catalogService;
    private final OperationTypeService operationTypeService;
    private final PositionService positionService;
    private final TaskRunner taskRunner;
    private final ApplicationContext context;
    private final ResourceBundle bundle;

    @FXML
    private TabPane tabs;
    @FXML
    private ListView<CategoryRow> categoryList;
    @FXML
    private CheckBox showInactiveCategories;
    @FXML
    private Button newCategoryButton;
    @FXML
    private Button renameCategoryButton;
    @FXML
    private Button toggleCategoryButton;
    @FXML
    private SearchField itemSearchField;
    @FXML
    private TableView<ItemRow> itemTable;
    @FXML
    private TableColumn<ItemRow, String> colItemName;
    @FXML
    private TableColumn<ItemRow, String> colItemUnit;
    @FXML
    private TableColumn<ItemRow, String> colItemPrice;
    @FXML
    private TableColumn<ItemRow, String> colItemStatus;
    @FXML
    private Button newItemButton;
    @FXML
    private Button editItemButton;
    @FXML
    private Button toggleItemButton;

    @FXML
    private SearchField operationSearchField;
    @FXML
    private CheckBox showInactiveOperations;
    @FXML
    private TableView<OperationTypeRow> operationTable;
    @FXML
    private TableColumn<OperationTypeRow, String> colOperationName;
    @FXML
    private TableColumn<OperationTypeRow, String> colOperationUnit;
    @FXML
    private TableColumn<OperationTypeRow, String> colOperationPrice;
    @FXML
    private TableColumn<OperationTypeRow, String> colOperationStatus;
    @FXML
    private Button newOperationButton;
    @FXML
    private Button editOperationButton;
    @FXML
    private Button toggleOperationButton;

    @FXML
    private SearchField positionSearchField;
    @FXML
    private CheckBox showInactivePositions;
    @FXML
    private TableView<PositionRow> positionTable;
    @FXML
    private TableColumn<PositionRow, String> colPositionName;
    @FXML
    private TableColumn<PositionRow, String> colPositionCount;
    @FXML
    private TableColumn<PositionRow, String> colPositionStatus;
    @FXML
    private Button newPositionButton;
    @FXML
    private Button renamePositionButton;
    @FXML
    private Button togglePositionButton;

    private Long selectedCategoryId;

    public CatalogController(ExpenseCatalogService catalogService, OperationTypeService operationTypeService,
            PositionService positionService, TaskRunner taskRunner, ApplicationContext context) {
        this.catalogService = catalogService;
        this.operationTypeService = operationTypeService;
        this.positionService = positionService;
        this.taskRunner = taskRunner;
        this.context = context;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    @FXML
    public void initialize() {
        setupCategoryList();
        setupItemTable();

        showInactiveCategories.selectedProperty().addListener((o, ov, nv) -> loadCategories());
        itemSearchField.setOnSearchAction(this::loadItems);

        itemTable.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> updateButtons());
        categoryList.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            selectedCategoryId = nv == null ? null : nv.id();
            updateButtons();
            loadItems();
        });

        updateButtons();
        loadCategories();

        setupOperationTable();
        setupPositionTable();
        showInactiveOperations.selectedProperty().addListener((o, ov, nv) -> loadOperations());
        operationSearchField.setOnSearchAction(this::loadOperations);
        operationTable.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> updateOperationButtons());

        showInactivePositions.selectedProperty().addListener((o, ov, nv) -> loadPositions());
        positionSearchField.setOnSearchAction(this::loadPositions);
        positionTable.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> updatePositionButtons());

        loadOperations();
        loadPositions();
    }

    private void setupCategoryList() {
        categoryList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CategoryRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String text = item.name() + " (" + item.itemCount() + ")";
                    if (!item.active()) {
                        text = text + " " + bundle.getString("expense.catalog.inactive_suffix");
                    }
                    setText(text);
                }
            }
        });
    }

    private void setupItemTable() {
        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colItemName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name()));
        colItemUnit.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().unit() == null ? "" : d.getValue().unit()));
        colItemPrice.setCellValueFactory(d -> new SimpleStringProperty(MoneyParser.format(d.getValue().defaultPrice())));
        colItemStatus.setCellValueFactory(d -> new SimpleStringProperty(
                bundle.getString(d.getValue().active() ? "expense.catalog.active" : "expense.catalog.inactive")));

        itemTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(ItemRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("payment-voided");
                if (item != null && !empty && !item.active()) {
                    getStyleClass().add("payment-voided");
                }
            }
        });
    }

    private void updateButtons() {
        CategoryRow category = categoryList.getSelectionModel().getSelectedItem();
        ItemRow item = itemTable.getSelectionModel().getSelectedItem();

        renameCategoryButton.setDisable(category == null);
        if (category != null) {
            toggleCategoryButton.setText(bundle.getString(
                    category.active() ? "expense.catalog.deactivate" : "expense.catalog.activate"));
        }
        toggleCategoryButton.setDisable(category == null);

        newItemButton.setDisable(selectedCategoryId == null);
        editItemButton.setDisable(item == null);
        if (item != null) {
            toggleItemButton.setText(bundle.getString(
                    item.active() ? "expense.catalog.deactivate" : "expense.catalog.activate"));
        }
        toggleItemButton.setDisable(item == null);
    }

    private void loadCategories() {
        Long keep = selectedCategoryId;
        boolean includeInactive = showInactiveCategories.isSelected();
        Task<List<CategoryRow>> task = new Task<>() {
            @Override
            protected List<CategoryRow> call() {
                return catalogService.listCategories(includeInactive);
            }
        };
        taskRunner.run(task, rows -> {
            categoryList.setItems(FXCollections.observableArrayList(rows));
            if (keep != null) {
                for (int i = 0; i < rows.size(); i++) {
                    if (rows.get(i).id().equals(keep)) {
                        categoryList.getSelectionModel().select(i);
                        return;
                    }
                }
            }
            if (!rows.isEmpty()) {
                categoryList.getSelectionModel().selectFirst();
            }
        });
    }

    private void loadItems() {
        if (selectedCategoryId == null) {
            itemTable.setItems(FXCollections.observableArrayList());
            return;
        }
        Long categoryId = selectedCategoryId;
        String query = itemSearchField.getText();
        Task<List<ItemRow>> task = new Task<>() {
            @Override
            protected List<ItemRow> call() {
                return catalogService.listItems(categoryId, query, true);
            }
        };
        taskRunner.run(task, rows -> itemTable.setItems(FXCollections.observableArrayList(rows)));
    }

    @FXML
    public void handleNewCategory() {
        openCategoryForm(null);
    }

    @FXML
    public void handleRenameCategory() {
        CategoryRow selected = categoryList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openCategoryForm(selected);
        }
    }

    @FXML
    public void handleToggleCategory() {
        CategoryRow selected = categoryList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        String message = bundle.getString(selected.active() ? "expense.catalog.deactivate" : "expense.catalog.activate");
        if (!AlertHelper.confirm(bundle.getString("ui.confirm"), selected.name() + ": " + message + "?")) {
            return;
        }
        try {
            catalogService.setCategoryActive(selected.id(), !selected.active());
            loadCategories();
        } catch (BusinessException e) {
            AlertHelper.error(bundle.getString("ui.error"), bundle.getString(e.getMessageKey()));
        }
    }

    @FXML
    public void handleNewItem() {
        if (selectedCategoryId != null) {
            openItemForm(null);
        }
    }

    @FXML
    public void handleEditItem() {
        ItemRow selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openItemForm(selected);
        }
    }

    @FXML
    public void handleToggleItem() {
        ItemRow selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            catalogService.setItemActive(selected.id(), !selected.active());
            loadItems();
        } catch (BusinessException e) {
            AlertHelper.error(bundle.getString("ui.error"), bundle.getString(e.getMessageKey()));
        }
    }

    private void openCategoryForm(CategoryRow category) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/catalog/category_form.fxml"), bundle);
            loader.setControllerFactory(context::getBean);
            Parent root = loader.load();

            CategoryFormController controller = loader.getController();
            controller.setCategory(category);

            Stage stage = buildDialog(root, category == null ? bundle.getString("expense.category.new")
                    : bundle.getString("expense.category.edit"));
            controller.setStage(stage);
            stage.showAndWait();

            if (controller.isSaved()) {
                loadCategories();
            }
        } catch (Throwable e) {
            showFailure(e);
        }
    }

    private void openItemForm(ItemRow item) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/catalog/item_form.fxml"), bundle);
            loader.setControllerFactory(context::getBean);
            Parent root = loader.load();

            ItemFormController controller = loader.getController();
            controller.setItem(item, selectedCategoryId);

            Stage stage = buildDialog(root, item == null ? bundle.getString("expense.item.new")
                    : bundle.getString("expense.item.edit"));
            controller.setStage(stage);
            stage.showAndWait();

            if (controller.isSaved()) {
                loadItems();
                loadCategories();
            }
        } catch (Throwable e) {
            showFailure(e);
        }
    }

    private Stage buildDialog(Parent root, String title) {
        Stage stage = new Stage();
        stage.initOwner(tabs.getScene().getWindow());
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(title);
        Scene scene = new Scene(root);
        com.mebelsex.ui.ThemeManager theme = com.mebelsex.ui.ThemeManager.getInstance();
        if (theme != null) {
            theme.install(scene);
        } else {
            scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
        }
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });
        stage.setScene(scene);
        stage.sizeToScene();
        stage.setMinWidth(480);
        return stage;
    }

    private void showFailure(Throwable e) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("UI error", e);
        AlertHelper.error(bundle.getString("ui.error"), e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    // ---------------- operation types (Ish turlari) ----------------

    private void setupOperationTable() {
        operationTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colOperationName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name()));
        colOperationUnit.setCellValueFactory(
                d -> new SimpleStringProperty(d.getValue().unit() == null ? "" : d.getValue().unit()));
        colOperationPrice
                .setCellValueFactory(d -> new SimpleStringProperty(MoneyParser.format(d.getValue().defaultPrice())));
        colOperationStatus.setCellValueFactory(d -> new SimpleStringProperty(
                bundle.getString(d.getValue().active() ? "expense.catalog.active" : "expense.catalog.inactive")));

        operationTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(OperationTypeRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("payment-voided");
                if (item != null && !empty && !item.active()) {
                    getStyleClass().add("payment-voided");
                }
            }
        });
    }

    private void updateOperationButtons() {
        OperationTypeRow operation = operationTable.getSelectionModel().getSelectedItem();
        editOperationButton.setDisable(operation == null);
        if (operation != null) {
            toggleOperationButton.setText(bundle.getString(
                    operation.active() ? "expense.catalog.deactivate" : "expense.catalog.activate"));
        }
        toggleOperationButton.setDisable(operation == null);
    }

    private void loadOperations() {
        boolean includeInactive = showInactiveOperations.isSelected();
        String query = operationSearchField.getText();
        Task<List<OperationTypeRow>> task = new Task<>() {
            @Override
            protected List<OperationTypeRow> call() {
                return operationTypeService.list(query, includeInactive);
            }
        };
        taskRunner.run(task, rows -> {
            operationTable.setItems(FXCollections.observableArrayList(rows));
            updateOperationButtons();
        });
    }

    @FXML
    public void handleNewOperation() {
        openOperationForm(null);
    }

    @FXML
    public void handleEditOperation() {
        OperationTypeRow selected = operationTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openOperationForm(selected);
        }
    }

    @FXML
    public void handleToggleOperation() {
        OperationTypeRow selected = operationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        String message = bundle.getString(selected.active() ? "expense.catalog.deactivate" : "expense.catalog.activate");
        if (!AlertHelper.confirm(bundle.getString("ui.confirm"), selected.name() + ": " + message + "?")) {
            return;
        }
        try {
            operationTypeService.setActive(selected.id(), !selected.active());
            loadOperations();
        } catch (BusinessException e) {
            AlertHelper.error(bundle.getString("ui.error"), bundle.getString(e.getMessageKey()));
        }
    }

    private void openOperationForm(OperationTypeRow operation) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/catalog/operation_form.fxml"), bundle);
            loader.setControllerFactory(context::getBean);
            Parent root = loader.load();

            OperationFormController controller = loader.getController();
            controller.setOperation(operation);

            Stage stage = buildDialog(root, operation == null ? bundle.getString("operation.new")
                    : bundle.getString("operation.edit"));
            controller.setStage(stage);
            stage.showAndWait();

            if (controller.isSaved()) {
                loadOperations();
            }
        } catch (Throwable e) {
            showFailure(e);
        }
    }

    // ---------------- positions (Lavozimlar) ----------------

    private void setupPositionTable() {
        positionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colPositionName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name()));
        colPositionCount.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().employeeCount())));
        colPositionStatus.setCellValueFactory(d -> new SimpleStringProperty(
                bundle.getString(d.getValue().active() ? "expense.catalog.active" : "expense.catalog.inactive")));

        positionTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(PositionRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("payment-voided");
                if (item != null && !empty && !item.active()) {
                    getStyleClass().add("payment-voided");
                }
            }
        });
    }

    private void updatePositionButtons() {
        PositionRow position = positionTable.getSelectionModel().getSelectedItem();
        renamePositionButton.setDisable(position == null);
        if (position != null) {
            togglePositionButton.setText(bundle.getString(
                    position.active() ? "expense.catalog.deactivate" : "expense.catalog.activate"));
        }
        togglePositionButton.setDisable(position == null);
    }

    private void loadPositions() {
        boolean includeInactive = showInactivePositions.isSelected();
        String query = positionSearchField.getText();
        Task<List<PositionRow>> task = new Task<>() {
            @Override
            protected List<PositionRow> call() {
                List<PositionRow> rows = positionService.list(includeInactive);
                if (query != null && !query.isBlank()) {
                    String q = query.trim().toLowerCase();
                    rows = rows.stream().filter(r -> r.name().toLowerCase().contains(q))
                            .collect(java.util.stream.Collectors.toList());
                }
                return rows;
            }
        };
        taskRunner.run(task, rows -> {
            positionTable.setItems(FXCollections.observableArrayList(rows));
            updatePositionButtons();
        });
    }

    @FXML
    public void handleNewPosition() {
        openPositionForm(null);
    }

    @FXML
    public void handleRenamePosition() {
        PositionRow selected = positionTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openPositionForm(selected);
        }
    }

    @FXML
    public void handleTogglePosition() {
        PositionRow selected = positionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        String message = bundle.getString(selected.active() ? "expense.catalog.deactivate" : "expense.catalog.activate");
        if (!AlertHelper.confirm(bundle.getString("ui.confirm"), selected.name() + ": " + message + "?")) {
            return;
        }
        try {
            positionService.setActive(selected.id(), !selected.active());
            loadPositions();
        } catch (BusinessException e) {
            AlertHelper.error(bundle.getString("ui.error"), bundle.getString(e.getMessageKey()));
        }
    }

    private void openPositionForm(PositionRow position) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/catalog/position_form.fxml"), bundle);
            loader.setControllerFactory(context::getBean);
            Parent root = loader.load();

            PositionFormController controller = loader.getController();
            controller.setPosition(position);

            Stage stage = buildDialog(root, position == null ? bundle.getString("position.new")
                    : bundle.getString("position.edit"));
            controller.setStage(stage);
            stage.showAndWait();

            if (controller.isSaved()) {
                loadPositions();
            }
        } catch (Throwable e) {
            showFailure(e);
        }
    }
}
