package com.mebelsex.ui.employees;

import com.mebelsex.common.BusinessException;
import com.mebelsex.employees.EmployeeBalanceService;
import com.mebelsex.employees.EmployeeRow;
import com.mebelsex.employees.EmployeeService;
import com.mebelsex.employees.PositionRow;
import com.mebelsex.employees.PositionService;
import com.mebelsex.ui.AlertHelper;
import com.mebelsex.ui.catalog.PositionFormController;
import com.mebelsex.ui.components.SignedMoneyParser;
import com.mebelsex.ui.components.SignedMoneyField;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** New / edit employee dialog with a small "+" link to create a position. */
@Controller
@Scope("prototype")
public class EmployeeFormController {

    private final EmployeeService employeeService;
    private final EmployeeBalanceService balanceService;
    private final PositionService positionService;
    private final ApplicationContext context;
    private final ResourceBundle bundle;

    @FXML
    private Label titleLabel;
    @FXML
    private TextField nameField;
    @FXML
    private TextField phoneField;
    @FXML
    private ComboBox<PositionRow> positionCombo;
    @FXML
    private Button addPositionButton;
    @FXML
    private SignedMoneyField openingField;
    @FXML
    private CheckBox activeCheck;
    @FXML
    private Label errorLabel;
    @FXML
    private Button cancelButton;
    @FXML
    private Button saveButton;

    private Stage stage;
    private Long employeeId;
    private boolean wasActive = true;
    private boolean saved = false;

    public EmployeeFormController(EmployeeService employeeService, EmployeeBalanceService balanceService,
            PositionService positionService, ApplicationContext context) {
        this.employeeService = employeeService;
        this.balanceService = balanceService;
        this.positionService = positionService;
        this.context = context;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    @FXML
    public void initialize() {
        saveButton.setDefaultButton(true);
        positionCombo.setConverter(new StringConverter<PositionRow>() {
            @Override
            public String toString(PositionRow row) {
                return row == null ? "" : row.name();
            }

            @Override
            public PositionRow fromString(String string) {
                return null;
            }
        });
    }

    public void setEmployee(EmployeeRow employee) {
        loadPositions();
        if (employee == null) {
            titleLabel.setText(bundle.getString("employee.form.new"));
            activeCheck.setVisible(false);
            activeCheck.setManaged(false);
            activeCheck.setSelected(true);
        } else {
            titleLabel.setText(bundle.getString("employee.form.edit"));
            employeeId = employee.id();
            wasActive = employee.active();
            nameField.setText(employee.fullName());
            phoneField.setText(employee.phone());
            openingField.setText(SignedMoneyParser.format(employee.openingBalance()));
            selectPosition(employee.positionId());
            activeCheck.setSelected(employee.active());
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
        PositionRow position = positionCombo.getValue();
        Long positionId = position == null ? null : position.id();
        if (employeeId != null && wasActive && !activeCheck.isSelected()
                && balanceService.balanceAsOf(employeeId, LocalDate.now()).compareTo(BigDecimal.ZERO) != 0) {
            String message = MessageFormat.format(bundle.getString("employee.deactivate.confirm"),
                    nameField.getText());
            if (!AlertHelper.confirm(bundle.getString("ui.confirm"), message)) {
                return;
            }
        }
        try {
            if (employeeId == null) {
                employeeService.create(nameField.getText(), phoneField.getText(), positionId,
                        openingField.getAmount());
            } else {
                employeeService.update(employeeId, nameField.getText(), phoneField.getText(), positionId,
                        openingField.getAmount(), activeCheck.isSelected());
            }
            saved = true;
            stage.close();
        } catch (BusinessException e) {
            showError(e.getMessageKey());
        }
    }

    @FXML
    public void handleAddPosition() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/catalog/position_form.fxml"), bundle);
            loader.setControllerFactory(context::getBean);
            Parent root = loader.load();

            PositionFormController controller = loader.getController();
            controller.setPosition(null);

            Stage dialog = new Stage();
            Window owner = stage == null ? null : stage.getOwner();
            if (owner == null) {
                owner = stage;
            }
            if (owner != null) {
                dialog.initOwner(owner);
            }
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle(bundle.getString("position.new"));
            Scene scene = new Scene(root);
            com.mebelsex.ui.ThemeManager theme = com.mebelsex.ui.ThemeManager.getInstance();
            if (theme != null) {
                theme.install(scene);
            } else {
                scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
            }
            scene.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    dialog.close();
                }
            });
            dialog.setScene(scene);
            dialog.sizeToScene();
            dialog.setMinWidth(480);

            controller.setStage(dialog);
            dialog.showAndWait();

            if (controller.isSaved()) {
                loadPositions();
            }
        } catch (Throwable e) {
            showError(e.getMessage());
        }
    }

    @FXML
    public void handleCancel() {
        stage.close();
    }

    private void loadPositions() {
        List<PositionRow> positions = positionService.listActive();
        positionCombo.setItems(FXCollections.observableArrayList(positions));
        positionCombo.getSelectionModel().clearSelection();
        if (employeeId == null && !positions.isEmpty()) {
            positionCombo.getSelectionModel().selectFirst();
        }
    }

    private void selectPosition(Long positionId) {
        if (positionId == null) {
            return;
        }
        for (PositionRow row : positionCombo.getItems()) {
            if (row.id().equals(positionId)) {
                positionCombo.getSelectionModel().select(row);
                return;
            }
        }
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
