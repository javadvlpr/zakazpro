package com.mebelsex.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

@Component
public class ViewLoader {

    private final ApplicationContext context;
    private final ResourceBundle bundle;

    public ViewLoader(ApplicationContext context) {
        this.context = context;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    public Parent load(String fxmlPath) {
        try {
            URL resource = getClass().getResource(fxmlPath);
            if (resource == null) {
                throw new IllegalArgumentException("FXML file not found: " + fxmlPath);
            }
            FXMLLoader loader = new FXMLLoader(resource, bundle);
            loader.setControllerFactory(context::getBean);
            return loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load FXML: " + fxmlPath, e);
        }
    }
}
