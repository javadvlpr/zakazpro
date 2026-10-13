package com.mebelsex.ui.customers;

import com.mebelsex.common.BusinessException;
import com.mebelsex.customers.Customer;
import com.mebelsex.customers.CustomerService;
import com.mebelsex.ui.AlertHelper;
import com.mebelsex.ui.TaskRunner;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import com.mebelsex.ui.components.SearchField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javafx.util.Duration;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.ResourceBundle;

@Controller
public class CustomersController {

    private final CustomerService customerService;
    private final TaskRunner taskRunner;
    private final ApplicationContext context;
    private final ResourceBundle bundle;

    @FXML
    private TableView<Customer> table;
    @FXML
    private TableColumn<Customer, String> colName;
    @FXML
    private TableColumn<Customer, String> colPhone;
    @FXML
    private TableColumn<Customer, String> colAddress;
    @FXML
    private SearchField searchField;
    @FXML
    private Button editButton;
    @FXML
    private Button deleteButton;

    private long searchCounter = 0;

    public CustomersController(CustomerService customerService, TaskRunner taskRunner, ApplicationContext context) {
        this.customerService = customerService;
        this.taskRunner = taskRunner;
        this.context = context;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    @FXML
    public void initialize() {
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFullName()));
        colPhone.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPhone()));
        colAddress.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAddress()));

        editButton.setDisable(true);
        deleteButton.setDisable(true);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            boolean hasSelection = newSel != null;
            editButton.setDisable(!hasSelection);
            deleteButton.setDisable(!hasSelection);
        });

        table.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                if (table.getSelectionModel().getSelectedItem() != null) {
                    handleEdit();
                }
            }
        });

        searchField.setOnSearchAction(this::loadData);

        loadData();
    }

    private void loadData() {
        final long currentSearchId = ++searchCounter;
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        Task<List<Customer>> fetchTask = new Task<>() {
            @Override
            protected List<Customer> call() {
                if (query.isEmpty()) {
                    return customerService.search("");
                } else {
                    return customerService.search(query);
                }
            }
        };

        taskRunner.run(fetchTask, result -> {
            if (currentSearchId == searchCounter) {
                table.setItems(FXCollections.observableArrayList(result));
            }
        });
    }

    @FXML
    public void handleNew() {
        openForm(null);
    }

    @FXML
    public void handleEdit() {
        Customer selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openForm(selected);
        }
    }

    @FXML
    public void handleDelete() {
        Customer selected = table.getSelectionModel().getSelectedItem();
        if (selected == null)
            return;

        boolean confirm = AlertHelper.confirm("Tasdiqlash", "Mijoz " + selected.getFullName() + " o'chirilsinmi?");
        if (confirm) {
            try {
                customerService.delete(selected.getId());
                loadData();
            } catch (BusinessException e) {
                String error = e.getMessageKey();
                try {
                    error = bundle.getString(e.getMessageKey());
                } catch (Exception ignored) {
                }
                AlertHelper.error(bundle.getString("ui.error"), error);
            }
        }
    }

    private void openForm(Customer customer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/customers/customers_form.fxml"), bundle);
            loader.setControllerFactory(context::getBean);
            Parent root = loader.load();

            CustomerFormController formController = loader.getController();
            formController.setCustomer(customer);

            Stage stage = new Stage();
            stage.initOwner(this.table.getScene().getWindow());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(bundle.getString(customer == null ? "ui.new_customer" : "ui.customer.edit"));
            Scene scene = new Scene(root);
            com.mebelsex.ui.ThemeManager theme = com.mebelsex.ui.ThemeManager.getInstance();
            if (theme != null) {
                theme.install(scene);
            } else {
                scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
            }
            scene.setOnKeyPressed(event -> {
                if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                    stage.close();
                }
            });
            stage.setScene(scene);
            stage.sizeToScene();
            stage.setMinWidth(560);

            formController.setStage(stage);

            stage.showAndWait();

            if (formController.isSaved()) {
                loadData();
            }

        } catch (Throwable e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error("UI error", e);
            com.mebelsex.ui.AlertHelper.error(bundle.getString("ui.error"),
                    e.getClass().getName() + ": " + e.getMessage());
        }
    }
}
