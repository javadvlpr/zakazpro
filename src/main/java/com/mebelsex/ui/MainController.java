package com.mebelsex.ui;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import com.mebelsex.license.LicenseService;
import com.mebelsex.license.LicenseStatus;
import com.mebelsex.license.LicenseStatusCalculator;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

@Controller
public class MainController {
    
    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private final ViewLoader viewLoader;
    private final ThemeManager themeManager;
    private final LicenseService licenseService;
    private final ResourceBundle bundle;

    @FXML
    private StackPane contentArea;

    @FXML
    private HBox menuHome;
    @FXML
    private HBox menuOrders;
    @FXML
    private HBox menuCustomers;
    @FXML
    private HBox menuEmployees;
    @FXML
    private HBox menuReports;
    @FXML
    private HBox menuCatalog;
    @FXML
    private HBox menuSettings;
    @FXML
    private Button themeToggleButton;
    @FXML
    private FontIcon themeToggleIcon;
    @FXML
    private HBox licenseBanner;
    @FXML
    private Label licenseBannerLabel;
    @FXML
    private Button licenseBannerAction;

    private final Map<HBox, String> views = new HashMap<>();
    private static final Set<String> UNBUILT_SCREENS = Set.of();

    public MainController(ViewLoader viewLoader, ThemeManager themeManager, LicenseService licenseService) {
        this.viewLoader = viewLoader;
        this.themeManager = themeManager;
        this.licenseService = licenseService;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    @FXML
    public void initialize() {
        javafx.scene.layout.HBox.setHgrow(contentArea, javafx.scene.layout.Priority.ALWAYS);
        contentArea.setMaxWidth(Double.MAX_VALUE);

        setupMenu(menuHome, "/fxml/dashboard.fxml");
        setupMenu(menuOrders, "/fxml/orders/orders_screen.fxml");
        setupMenu(menuCustomers, "/fxml/customers/customers.fxml");
        setupMenu(menuEmployees, "/fxml/employees.fxml");
        setupMenu(menuReports, "/fxml/reports.fxml");
        setupMenu(menuCatalog, "/fxml/catalog.fxml");
        setupMenu(menuSettings, "/fxml/settings.fxml");

        // Load default view
        selectMenu(menuHome);

        themeManager.themeProperty().addListener((o, old, now) -> updateThemeButton());
        updateThemeButton();
        setupLicenseBanner();
    }

    /**
     * Banner at the top of the content area: amber when the license expires soon,
     * red when the app is READ_ONLY. Updated live by the service's periodic re-check.
     */
    private void setupLicenseBanner() {
        licenseService.addListener(this::applyLicenseStatus);
        applyLicenseStatus(licenseService.current());
    }

    private void applyLicenseStatus(LicenseStatus status) {
        if (status == null || licenseBanner == null) {
            return;
        }
        if (!javafx.application.Platform.isFxApplicationThread()) {
            javafx.application.Platform.runLater(() -> applyLicenseStatus(status));
            return;
        }
        licenseBanner.getStyleClass().removeAll("license-banner-readonly", "license-banner-warning");
        if (status.state() == LicenseStatus.State.READ_ONLY) {
            String key = LicenseStatusCalculator.REASON_CLOCK.equals(status.reasonKey())
                    ? "license.banner.readonly.clock" : "license.banner.readonly.expired";
            showBanner("license-banner-readonly", bundle.getString(key));
        } else if (status.state() == LicenseStatus.State.VALID && status.expiringSoon()) {
            int days = status.daysLeft() == null ? 0 : status.daysLeft();
            showBanner("license-banner-warning",
                    com.mebelsex.common.Messages.format("license.banner.expiring", days));
        } else {
            hideBanner();
        }
    }

    private void showBanner(String styleClass, String text) {
        licenseBannerLabel.setText(text);
        licenseBannerAction.setText(bundle.getString("license.banner.action"));
        licenseBanner.getStyleClass().add(styleClass);
        licenseBanner.setManaged(true);
        licenseBanner.setVisible(true);
    }

    private void hideBanner() {
        licenseBanner.setManaged(false);
        licenseBanner.setVisible(false);
    }

    /** Banner link: opens Sozlamalar where the license can be renewed. */
    @FXML
    public void handleOpenLicenseSettings() {
        selectMenu(menuSettings);
    }

    /** Public hook (restore flows / dialogs) to jump to Sozlamalar. */
    public void showSettings() {
        selectMenu(menuSettings);
    }

    /** Sidebar moon/sun button: switches the theme (applies live and persists). */
    @FXML
    public void handleToggleTheme() {
        themeManager.setTheme(themeManager.current().toggled());
    }

    private void updateThemeButton() {
        if (themeToggleIcon == null || themeToggleButton == null) {
            return;
        }
        boolean dark = themeManager.current() == Theme.DARK;
        themeToggleIcon.setIconLiteral(dark ? "fth-sun" : "fth-moon");
        themeToggleButton.setTooltip(new Tooltip(
                bundle.getString(dark ? "toolbar.theme.to_light" : "toolbar.theme.to_dark")));
    }

    /** Shows an arbitrary node (e.g. the order card) in the content area. */
    public void showNode(Node node) {
        contentArea.getChildren().setAll(node);
    }

    /** Returns to the Zakazlar screen. */
    public void showOrders() {
        selectMenu(menuOrders);
    }

    /** Returns to the Ishchilar screen. */
    public void showEmployees() {
        selectMenu(menuEmployees);
    }

    private void setupMenu(HBox menu, String fxmlPath) {
        views.put(menu, fxmlPath);
        menu.setOnMouseClicked(e -> selectMenu(menu));
    }

    private void selectMenu(HBox activeMenu) {
        views.keySet().forEach(lbl -> lbl.getStyleClass().remove("active"));
        activeMenu.getStyleClass().add("active");

        String fxmlPath = views.get(activeMenu);
        try {
            Node view = viewLoader.load(fxmlPath);
            contentArea.getChildren().setAll(view);
        } catch (Exception e) {
            log.error("Failed to load view: " + fxmlPath, e);
            if (UNBUILT_SCREENS.contains(fxmlPath)) {
                showPlaceholder(bundle.getString("ui.placeholder"));
            } else {
                showPlaceholder(bundle.getString("ui.error_loading_screen"));
            }
        }
    }

    private void showPlaceholder(String message) {
        Label placeholder = new Label(message);
        placeholder.getStyleClass().addAll("page-title", "muted-text");

        VBox container = new VBox(placeholder);
        container.setAlignment(javafx.geometry.Pos.CENTER);
        container.getStyleClass().add("content-area");

        contentArea.getChildren().setAll(container);
    }
}
