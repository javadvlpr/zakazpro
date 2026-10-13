package com.mebelsex.ui.orders;

import com.mebelsex.ui.AlertHelper;
import com.mebelsex.ui.MainController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ResourceBundle;

/** Loads the order card and shows it inside the content area. Shared by Zakazlar and Bosh sahifa. */
@Component
public class OrderCards {

    private final ApplicationContext context;
    private final MainController mainController;
    private final ResourceBundle bundle;

    public OrderCards(ApplicationContext context, MainController mainController) {
        this.context = context;
        this.mainController = mainController;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    public void open(Long orderId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/orders/order_card.fxml"), bundle);
            loader.setControllerFactory(context::getBean);
            Parent root = loader.load();

            OrderCardController controller = loader.getController();
            controller.setOrderId(orderId);
            mainController.showNode(root);
        } catch (Throwable e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error("UI error", e);
            AlertHelper.error(bundle.getString("ui.error"), e.getMessage());
        }
    }
}
