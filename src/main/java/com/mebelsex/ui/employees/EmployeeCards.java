package com.mebelsex.ui.employees;

import com.mebelsex.ui.AlertHelper;
import com.mebelsex.ui.MainController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ResourceBundle;

/** Loads the employee card and shows it inside the content area. */
@Component
public class EmployeeCards {

    private final ApplicationContext context;
    private final MainController mainController;
    private final ResourceBundle bundle;

    public EmployeeCards(ApplicationContext context, MainController mainController) {
        this.context = context;
        this.mainController = mainController;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    public void open(Long employeeId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/employees/employee_card.fxml"), bundle);
            loader.setControllerFactory(context::getBean);
            Parent root = loader.load();

            EmployeeCardController controller = loader.getController();
            controller.setEmployeeId(employeeId);
            mainController.showNode(root);
        } catch (Throwable e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error("UI error", e);
            AlertHelper.error(bundle.getString("ui.error"), e.getMessage());
        }
    }
}
