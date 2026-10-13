package com.mebelsex.ui.orders;

import com.mebelsex.orders.OrderListItem;
import com.mebelsex.ui.AlertHelper;
import com.mebelsex.ui.ThemeManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ResourceBundle;

/**
 * Opens the order form and the status-change dialog. Lives outside
 * OrdersController so other screens (e.g. dashboard) can reuse it.
 * The opening code was moved verbatim from OrdersController.
 */
@Component
public class OrderDialogs {

    private static final double MIN_DIALOG_WIDTH = 560;

    private final ApplicationContext context;
    private final ResourceBundle bundle;

    public OrderDialogs(ApplicationContext context) {
        this.context = context;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    public void openOrderForm(Long orderId, Runnable onSaved) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/orders/order_form.fxml"), bundle);
            loader.setControllerFactory(context::getBean);
            Parent root = loader.load();

            OrderFormController controller = loader.getController();
            controller.setOrder(orderId);

            Stage stage = new Stage();
            Window owner = ownerWindow();
            if (owner != null) {
                stage.initOwner(owner);
            }
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(orderId == null ? bundle.getString("ui.new_order") : bundle.getString("action.edit"));

            Scene scene = new Scene(root);
            ThemeManager theme = ThemeManager.getInstance();
            if (theme != null) {
                theme.install(scene);
            } else {
                scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
            }
            closeOnEscape(scene, stage);
            stage.setScene(scene);
            sizeDialog(stage);

            controller.setStage(stage);
            stage.showAndWait();

            if (controller.isSaved() && onSaved != null) {
                onSaved.run();
            }
        } catch (Throwable e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error("UI error", e);
            AlertHelper.error(bundle.getString("ui.error"), e.getMessage());
        }
    }

    public void openStatusDialog(OrderListItem item, Runnable onChanged) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/orders/status_dialog.fxml"), bundle);
            loader.setControllerFactory(context::getBean);
            Parent root = loader.load();

            StatusDialogController controller = loader.getController();
            controller.setInstanceData(item.id(), item.status(), item.paid());

            Stage stage = new Stage();
            Window owner = ownerWindow();
            if (owner != null) {
                stage.initOwner(owner);
            }
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(bundle.getString("action.change_status"));

            Scene scene = new Scene(root);
            ThemeManager theme = ThemeManager.getInstance();
            if (theme != null) {
                theme.install(scene);
            } else {
                scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
            }
            closeOnEscape(scene, stage);
            stage.setScene(scene);
            sizeDialog(stage);

            controller.setStage(stage);
            stage.showAndWait();

            if (controller.isSaved() && onChanged != null) {
                onChanged.run();
            }
        } catch (Throwable e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error("UI error", e);
            AlertHelper.error(bundle.getString("ui.error"), e.getMessage());
        }
    }

    private static void sizeDialog(Stage stage) {
        stage.sizeToScene();
        stage.setMinWidth(MIN_DIALOG_WIDTH);
    }

    private static void closeOnEscape(Scene scene, Stage stage) {
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });
    }

    private static Window ownerWindow() {
        return AlertHelper.getActiveWindow(Window.getWindows());
    }
}
