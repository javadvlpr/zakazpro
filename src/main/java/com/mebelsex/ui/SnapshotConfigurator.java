package com.mebelsex.ui;

import com.mebelsex.backup.BackupReason;
import com.mebelsex.backup.BackupResult;
import com.mebelsex.backup.BackupService;
import com.mebelsex.customers.Customer;
import com.mebelsex.customers.CustomerService;
import com.mebelsex.employees.EmployeeService;
import com.mebelsex.employees.PositionRow;
import com.mebelsex.employees.PositionService;
import com.mebelsex.expenses.CategoryRow;
import com.mebelsex.expenses.ExpenseCatalogService;
import com.mebelsex.orders.OrderService;
import com.mebelsex.payments.PaymentMethod;
import com.mebelsex.work.OperationTypeService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import com.mebelsex.ui.components.MoneyField;
import com.mebelsex.ui.components.QuantityField;
import com.mebelsex.ui.components.SignedMoneyField;
import com.mebelsex.ui.components.UzDatePicker;
import com.mebelsex.settings.SettingsService;
import com.mebelsex.ui.settings.SettingsController;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.context.ApplicationContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ResourceBundle;

/**
 * Dev-only visual check. Seeded through the services, then driven with real events.
 * Requires MEBELSEX_DB_PATH; aborts without it.
 */
public class SnapshotConfigurator {

    public void runSnapshots(Stage primaryStage, ApplicationContext context, String dir) {
        String dbPath = System.getenv("MEBELSEX_DB_PATH");
        if (dbPath == null || dbPath.isBlank()) {
            System.err.println("SNAPSHOT ABORTED: MEBELSEX_DB_PATH is not set.");
            Platform.exit();
            return;
        }
        System.out.println("Snapshot dir active: " + dir + " DB: " + dbPath);
        File outDir = new File(dir);
        if (!outDir.exists())
            outDir.mkdirs();

        // SAFETY: point backups at a temp folder under target/ before anything runs.
        try {
            java.nio.file.Path backupFolder = java.nio.file.Paths.get("target", "snapshot_backups").toAbsolutePath();
            context.getBean(SettingsService.class).saveBackup(backupFolder.toString(), false, "00:00", false, 60);
        } catch (Exception e) {
            System.err.println("WARN: cannot set temp backup folder: " + e.getMessage());
        }

        // --- seed through the services ---
        CustomerService customerService = context.getBean(CustomerService.class);
        OrderService orderService = context.getBean(OrderService.class);
        try {
            LocalDate today = LocalDate.now();
            Customer c1 = customerService.create("Nodirbek Karimov", "+998901234567", "Toshkent", "");
            Customer c2 = customerService.create("Javohir To'rayev", "+998991234567", "Samarqand", "");
            orderService.create(c1.getId(), "Oshxona mebeli", new BigDecimal("25000000"), today.plusDays(3),
                    "Namuna", new BigDecimal("5000000"), PaymentMethod.CASH, today);
            orderService.create(c2.getId(), "Yotoqxona shkafi", new BigDecimal("12000000"), today.minusDays(2),
                    null, null, null, null);
            // A previous-month order so the reports have data across two months.
            LocalDate prevMonthOrder = today.minusMonths(1).withDayOfMonth(10);
            orderService.create(c1.getId(), "O'tgan oy stoli", new BigDecimal("8000000"),
                    prevMonthOrder.plusDays(15), prevMonthOrder, null, new BigDecimal("3000000"),
                    PaymentMethod.CASH, prevMonthOrder);

            ExpenseCatalogService catalogService = context.getBean(ExpenseCatalogService.class);
            // V1 already seeds baseline categories; reuse "Material" instead of creating a duplicate.
            Long materialId = catalogService.listCategories(true).stream()
                    .filter(c -> "Material".equals(c.name()))
                    .map(CategoryRow::id)
                    .findFirst()
                    .orElseGet(() -> catalogService.createCategory("Material").id());
            catalogService.createItem(materialId, "Fanera", "m²", new BigDecimal("85000"));
            catalogService.createItem(materialId, "Kley", "kg", new BigDecimal("40000"));

            // --- Phase 4: positions, operation types, employees ---
            PositionService positionService = context.getBean(PositionService.class);
            OperationTypeService operationTypeService = context.getBean(OperationTypeService.class);
            EmployeeService employeeService = context.getBean(EmployeeService.class);
            PositionRow duradgor = positionService.create("Duradgor");
            operationTypeService.create("Kesish", "m²", new BigDecimal("25000"));
            operationTypeService.create("Yig'ish", "dona", new BigDecimal("60000"));
            employeeService.create("Sardor Rahimov", "+998901112233", duradgor.id(), new BigDecimal("50000"));
            employeeService.create("Bekzod Yusupov", "+998902223344", duradgor.id(), null);
        } catch (Exception ignored) {
        }

        Thread snapshotThread = new Thread(() -> {
            try {
                Thread.sleep(1500);

                // Dev-only focused mode: when MEBELSEX_SNAPSHOT_ONLY is set, run only the
                // named steps (additive; the full flow below is never touched).
                String only = System.getenv("MEBELSEX_SNAPSHOT_ONLY");
                if (only != null && !only.isBlank()) {
                    int failures = runOnly(primaryStage, outDir, only, context);
                    Thread.sleep(500);
                    Platform.exit();
                    if (failures > 0) {
                        System.err.println("SELF-CHECK FAILED: " + failures + " failure(s)");
                        System.exit(1);
                    }
                    return;
                }

                // reload Bosh sahifa so it reflects the seeded orders, then 00) dashboard
                fireMenu(primaryStage, "#menuHome");
                Thread.sleep(1500);
                Platform.runLater(() -> takeSnapshot(primaryStage.getScene().getRoot(),
                        new File(outDir, "00-dashboard.png")));
                Thread.sleep(800);

                // navigate to Zakazlar
                fireMenu(primaryStage, "#menuOrders");
                Thread.sleep(1500);

                // open the card with a real double-click on the first row
                Platform.runLater(() -> {
                    TableView<?> table = (TableView<?>) primaryStage.getScene().lookup("#table");
                    if (table != null && !table.getItems().isEmpty()) {
                        table.getSelectionModel().select(0);
                        table.fireEvent(doubleClick());
                        System.out.println("DOUBLE-CLICKED first order row");
                    } else {
                        System.err.println("ERROR: orders table is empty");
                    }
                });
                Thread.sleep(2000);

                // 01) order card
                Platform.runLater(() -> {
                    Node content = contentArea(primaryStage);
                    takeSnapshot((Parent) content, new File(outDir, "01-order-card.png"));
                    javafx.scene.control.TextField amount = (TextField) content.lookup("#amountField");
                    javafx.scene.control.TextField comment = (TextField) content.lookup("#commentField");
                    if (amount != null)
                        amount.setText("1 000 000");
                    if (comment != null)
                        comment.setText("Naqd to'lov");
                });
                Thread.sleep(800);

                // 01b) Xulosa tab
                Platform.runLater(() -> {
                    Node content = contentArea(primaryStage);
                    Node tabs = content.lookup("#tabs");
                    if (tabs instanceof javafx.scene.control.TabPane tabPane) {
                        tabPane.getSelectionModel().select(1);
                    }
                });
                Thread.sleep(700);
                Platform.runLater(() -> takeSnapshot((Parent) contentArea(primaryStage),
                        new File(outDir, "01b-order-card-summary.png")));
                Thread.sleep(500);
                Platform.runLater(() -> {
                    Node content = contentArea(primaryStage);
                    Node tabs = content.lookup("#tabs");
                    if (tabs instanceof javafx.scene.control.TabPane tabPane) {
                        tabPane.getSelectionModel().select(0);
                    }
                });
                Thread.sleep(500);

                // add a REGULAR payment through the quick-add row
                Platform.runLater(() -> {
                    Node content = contentArea(primaryStage);
                    Node add = content.lookup("#addButton");
                    if (add instanceof Button button) {
                        button.fire();
                        System.out.println("FIRED #addButton");
                    }
                });
                Thread.sleep(1500);

                // 02) card with the new payment
                Platform.runLater(() -> takeSnapshot((Parent) contentArea(primaryStage),
                        new File(outDir, "02-order-card-payment.png")));
                Thread.sleep(600);

                // select the first payment row and open the void dialog
                Platform.runLater(() -> {
                    Node content = contentArea(primaryStage);
                    TableView<?> payments = (TableView<?>) content.lookup("#paymentsTable");
                    if (payments != null && !payments.getItems().isEmpty()) {
                        payments.getSelectionModel().select(0);
                        Node voidButton = content.lookup("#voidPaymentButton");
                        if (voidButton instanceof Button button) {
                            button.fire();
                            System.out.println("FIRED #voidPaymentButton");
                        }
                    } else {
                        System.err.println("ERROR: payments table is empty");
                    }
                });
                Thread.sleep(1500);

                // type a reason into the void dialog and confirm
                Platform.runLater(() -> {
                    Window dialog = findShowingDialog(primaryStage);
                    if (dialog != null && dialog.getScene() != null
                            && dialog.getScene().getRoot() instanceof javafx.scene.control.DialogPane pane) {
                        TextField editor = (TextField) dialog.getScene().lookup(".text-field");
                        if (editor != null) {
                            editor.setText("Xato kiritildi");
                        }
                        Node okButton = pane.lookupButton(javafx.scene.control.ButtonType.OK);
                        if (okButton instanceof Button ok) {
                            ok.fire();
                            System.out.println("CONFIRMED void reason");
                        } else {
                            System.err.println("ERROR: void dialog OK button not found");
                        }
                    } else {
                        System.err.println("ERROR: void dialog not found");
                    }
                });
                Thread.sleep(1500);

                // 03) card with the VOIDED payment
                Platform.runLater(() -> takeSnapshot((Parent) contentArea(primaryStage),
                        new File(outDir, "03-order-card-voided.png")));
                Thread.sleep(800);

                // open the edit form through the card's Tahrirlash button (real event)
                Platform.runLater(() -> {
                    Node content = contentArea(primaryStage);
                    Node edit = content.lookup("#editButton");
                    if (edit instanceof Button button) {
                        button.fire();
                        System.out.println("FIRED #editButton");
                    } else {
                        System.err.println("ERROR: #editButton not found");
                    }
                });
                Thread.sleep(2500);

                // 05) edit form dialog (customer name + already paid info)
                Platform.runLater(() -> {
                    Window dialog = findShowingDialog(primaryStage);
                    if (dialog != null && dialog.getScene() != null) {
                        takeSnapshot(dialog.getScene().getRoot(), new File(outDir, "05-edit-order.png"));
                    } else {
                        System.err.println("ERROR: edit form dialog not found");
                    }
                });
                Thread.sleep(800);

                // close the edit form dialog before continuing
                Platform.runLater(() -> {
                    Window dialog = findShowingDialog(primaryStage);
                    if (dialog instanceof Stage s) {
                        s.close();
                        System.out.println("CLOSED edit form dialog");
                    }
                });
                Thread.sleep(800);

                // open the edit form of the order that has an advance (the reported crash case)
                fireMenu(primaryStage, "#menuOrders");
                Thread.sleep(1500);
                Platform.runLater(() -> {
                    TableView<?> table = (TableView<?>) primaryStage.getScene().lookup("#table");
                    if (table != null && table.getItems().size() > 1) {
                        table.getSelectionModel().select(1);
                        Node edit = primaryStage.getScene().lookup("#editButton");
                        if (edit instanceof Button button) {
                            button.fire();
                            System.out.println("FIRED orders #editButton on row 1");
                        }
                    } else {
                        System.err.println("ERROR: orders table has fewer than 2 rows");
                    }
                });
                Thread.sleep(2500);

                // 06) edit form of the order with an advance (non-zero paid)
                Platform.runLater(() -> {
                    Window dialog = findShowingDialog(primaryStage);
                    if (dialog != null && dialog.getScene() != null) {
                        takeSnapshot(dialog.getScene().getRoot(), new File(outDir, "06-edit-order-advance.png"));
                    } else {
                        System.err.println("ERROR: advance edit form dialog not found");
                    }
                });
                Thread.sleep(800);
                Platform.runLater(() -> {
                    Window dialog = findShowingDialog(primaryStage);
                    if (dialog instanceof Stage s) {
                        s.close();
                        System.out.println("CLOSED advance edit form dialog");
                    }
                });
                Thread.sleep(800);

                // --- Part A polish checks: same screens at 1280x800 and 1024x680 ---
                // A1) order card (To'lovlar quick-add row)
                Platform.runLater(() -> {
                    TableView<?> table = (TableView<?>) primaryStage.getScene().lookup("#table");
                    if (table != null && !table.getItems().isEmpty()) {
                        table.getSelectionModel().select(0);
                        table.fireEvent(doubleClick());
                    }
                });
                Thread.sleep(2000);
                captureAtSizes(primaryStage, (Parent) contentArea(primaryStage), "A1-order-card", outDir);

                // A2) Tahrirlash order dialog
                Platform.runLater(() -> {
                    Node edit = contentArea(primaryStage).lookup("#editButton");
                    if (edit instanceof Button b)
                        b.fire();
                });
                Thread.sleep(2200);
                Window editDialog = findShowingDialog(primaryStage);
                if (editDialog instanceof Stage es && es.getScene() != null) {
                    captureAtSizes(es, es.getScene().getRoot(), "A2-edit-order", outDir);
                } else {
                    System.err.println("ERROR: A2 edit dialog not found");
                }
                closeShowingDialog(primaryStage);
                Thread.sleep(700);

                // A3) Yangi zakaz dialog with the advance section open
                fireMenu(primaryStage, "#menuHome");
                Thread.sleep(1500);
                Platform.runLater(() -> {
                    Node newBtn = primaryStage.getScene().lookup("#newOrderButton");
                    if (newBtn instanceof Button b)
                        b.fire();
                });
                Thread.sleep(2200);
                Platform.runLater(() -> {
                    Window d = findShowingDialog(primaryStage);
                    if (d != null && d.getScene() != null) {
                        Node cb = d.getScene().lookup("#advanceCheck");
                        if (cb instanceof javafx.scene.control.CheckBox c)
                            c.setSelected(true);
                    }
                });
                Thread.sleep(900);
                Window newDialog = findShowingDialog(primaryStage);
                if (newDialog instanceof Stage ns && ns.getScene() != null) {
                    captureAtSizes(ns, ns.getScene().getRoot(), "A3-new-order-advance", outDir);
                } else {
                    System.err.println("ERROR: A3 new-order dialog not found");
                }
                closeShowingDialog(primaryStage);
                Thread.sleep(700);

                // A4) customer form
                fireMenu(primaryStage, "#menuCustomers");
                Thread.sleep(1500);
                Platform.runLater(() -> {
                    Node add = primaryStage.getScene().lookup("#newCustomerButton");
                    if (add instanceof Button b)
                        b.fire();
                });
                Thread.sleep(2200);
                Window custDialog = findShowingDialog(primaryStage);
                if (custDialog instanceof Stage cs && cs.getScene() != null) {
                    captureAtSizes(cs, cs.getScene().getRoot(), "A4-customer-form", outDir);
                } else {
                    System.err.println("ERROR: A4 customer form not found");
                }
                closeShowingDialog(primaryStage);
                Thread.sleep(700);

                // --- Phase 3 expenses ---
                // B1) Katalog
                fireMenu(primaryStage, "#menuCatalog");
                Thread.sleep(1600);
                captureAtSizes(primaryStage, (Parent) contentArea(primaryStage), "B1-katalog", outDir);

                // Katalog: Ish turlari (1) and Lavozimlar (2) tabs
                for (int catalogTab = 1; catalogTab <= 2; catalogTab++) {
                    final int idx = catalogTab;
                    Platform.runLater(() -> {
                        TabPane tabs = (TabPane) contentArea(primaryStage).lookup("#tabs");
                        if (tabs != null)
                            tabs.getSelectionModel().select(idx);
                    });
                    Thread.sleep(900);
                    captureAtSizes(primaryStage, (Parent) contentArea(primaryStage),
                            "B1b-katalog-tab" + catalogTab, outDir);
                }

                // open an order card and switch to the Xarajatlar tab
                fireMenu(primaryStage, "#menuOrders");
                Thread.sleep(1500);
                Platform.runLater(() -> {
                    TableView<?> table = (TableView<?>) primaryStage.getScene().lookup("#table");
                    if (table != null && !table.getItems().isEmpty()) {
                        table.getSelectionModel().select(0);
                        table.fireEvent(doubleClick());
                    }
                });
                Thread.sleep(2000);
                Platform.runLater(() -> {
                    TabPane tabs = (TabPane) contentArea(primaryStage).lookup("#tabs");
                    if (tabs != null)
                        tabs.getSelectionModel().select(1);
                });
                Thread.sleep(800);

                // catalog expense with a decimal quantity
                Platform.runLater(() -> {
                    Node content = contentArea(primaryStage);
                    ComboBox<?> cat = (ComboBox<?>) content.lookup("#expenseCategoryCombo");
                    if (cat != null && !cat.getItems().isEmpty()) {
                        int index = 0;
                        for (int i = 0; i < cat.getItems().size(); i++) {
                            if (cat.getItems().get(i) instanceof CategoryRow cr && "Material".equals(cr.name())) {
                                index = i;
                                break;
                            }
                        }
                        cat.getSelectionModel().select(index);
                    }
                });
                Thread.sleep(1400);
                Platform.runLater(() -> {
                    Node content = contentArea(primaryStage);
                    ComboBox<?> item = (ComboBox<?>) content.lookup("#expenseItemCombo");
                    if (item != null && item.getItems().size() > 1)
                        item.getSelectionModel().select(1);
                    TextField qty = (TextField) content.lookup("#expenseQuantityField");
                    TextField price = (TextField) content.lookup("#expensePriceField");
                    if (qty != null)
                        qty.setText("15,5");
                    if (price != null)
                        price.setText("85 000");
                });
                Thread.sleep(600);
                Platform.runLater(() -> {
                    Node add = contentArea(primaryStage).lookup("#addExpenseButton");
                    if (add instanceof Button b)
                        b.fire();
                });
                Thread.sleep(1600);

                // one-off expense with a description
                Platform.runLater(() -> {
                    Node content = contentArea(primaryStage);
                    ComboBox<?> item = (ComboBox<?>) content.lookup("#expenseItemCombo");
                    if (item != null && !item.getItems().isEmpty())
                        item.getSelectionModel().selectFirst();
                    TextField qty = (TextField) content.lookup("#expenseQuantityField");
                    TextField price = (TextField) content.lookup("#expensePriceField");
                    TextField desc = (TextField) content.lookup("#expenseDescriptionField");
                    if (qty != null)
                        qty.setText("1");
                    if (price != null)
                        price.setText("50 000");
                    if (desc != null)
                        desc.setText("Transport xarajati");
                });
                Thread.sleep(600);
                Platform.runLater(() -> {
                    Node add = contentArea(primaryStage).lookup("#addExpenseButton");
                    if (add instanceof Button b)
                        b.fire();
                });
                Thread.sleep(1800);

                // B2) Xarajatlar tab with two expenses
                captureAtSizes(primaryStage, (Parent) contentArea(primaryStage), "B2-expenses", outDir);

                // void the first expense with a reason
                Platform.runLater(() -> {
                    Node content = contentArea(primaryStage);
                    TableView<?> t = (TableView<?>) content.lookup("#expensesTable");
                    if (t != null && !t.getItems().isEmpty()) {
                        t.getSelectionModel().select(0);
                        Node vb = content.lookup("#voidExpenseButton");
                        if (vb instanceof Button b)
                            b.fire();
                    }
                });
                Thread.sleep(1500);
                Platform.runLater(() -> {
                    Window d = findShowingDialog(primaryStage);
                    if (d != null && d.getScene() != null
                            && d.getScene().getRoot() instanceof javafx.scene.control.DialogPane pane) {
                        TextField editor = (TextField) d.getScene().lookup(".text-field");
                        if (editor != null)
                            editor.setText("Xato kiritildi");
                        Node ok = pane.lookupButton(javafx.scene.control.ButtonType.OK);
                        if (ok instanceof Button b)
                            b.fire();
                    }
                });
                Thread.sleep(1500);

                // B3) expenses with one VOIDED
                captureAtSizes(primaryStage, (Parent) contentArea(primaryStage), "B3-expense-voided", outDir);

                // B4) Xulosa tab with real totals
                Platform.runLater(() -> {
                    TabPane tabs = (TabPane) contentArea(primaryStage).lookup("#tabs");
                    if (tabs != null)
                        tabs.getSelectionModel().select(2);
                });
                Thread.sleep(800);
                captureAtSizes(primaryStage, (Parent) contentArea(primaryStage), "B4-xulosa", outDir);

                // --- Phase 4 Part B: Ishchilar, Ishlar, ish haqi ---
                // C1) Ishchilar screen with balances
                fireMenu(primaryStage, "#menuEmployees");
                Thread.sleep(1800);
                captureAtSizes(primaryStage, (Parent) contentArea(primaryStage), "C1-ishchilar", outDir);

                // open the first order card and switch to the Ishlar tab
                fireMenu(primaryStage, "#menuOrders");
                Thread.sleep(1500);
                Platform.runLater(() -> {
                    TableView<?> table = (TableView<?>) primaryStage.getScene().lookup("#table");
                    if (table != null && !table.getItems().isEmpty()) {
                        table.getSelectionModel().select(0);
                        table.fireEvent(doubleClick());
                    }
                });
                Thread.sleep(2000);
                Platform.runLater(() -> {
                    TabPane tabs = (TabPane) contentArea(primaryStage).lookup("#tabs");
                    if (tabs != null)
                        tabs.getSelectionModel().select(2);
                });
                Thread.sleep(800);

                // add two work logs through the quick-add row
                for (int i = 0; i < 2; i++) {
                    final int index = i;
                    Platform.runLater(() -> {
                        Node content = contentArea(primaryStage);
                        ComboBox<?> employee = (ComboBox<?>) content.lookup("#workEmployeeCombo");
                        if (employee != null && !employee.getItems().isEmpty())
                            employee.getSelectionModel().selectFirst();
                        ComboBox<?> operation = (ComboBox<?>) content.lookup("#workOperationCombo");
                        if (operation != null && operation.getItems().size() > index)
                            operation.getSelectionModel().select(index);
                        TextField qty = (TextField) content.lookup("#workQuantityField");
                        TextField price = (TextField) content.lookup("#workPriceField");
                        if (qty != null)
                            qty.setText(index == 0 ? "12" : "3");
                        if (price != null)
                            price.setText(index == 0 ? "25 000" : "60 000");
                    });
                    Thread.sleep(700);
                    Platform.runLater(() -> {
                        Node add = contentArea(primaryStage).lookup("#addWorkButton");
                        if (add instanceof Button b)
                            b.fire();
                    });
                    Thread.sleep(1600);
                }
                captureAtSizes(primaryStage, (Parent) contentArea(primaryStage), "C2-order-works", outDir);

                // void the first work log with a reason
                Platform.runLater(() -> {
                    Node content = contentArea(primaryStage);
                    TableView<?> t = (TableView<?>) content.lookup("#worksTable");
                    if (t != null && !t.getItems().isEmpty()) {
                        t.getSelectionModel().select(0);
                        Node vb = content.lookup("#voidWorkButton");
                        if (vb instanceof Button b)
                            b.fire();
                    }
                });
                Thread.sleep(1500);
                Platform.runLater(() -> {
                    Window d = findShowingDialog(primaryStage);
                    if (d != null && d.getScene() != null
                            && d.getScene().getRoot() instanceof javafx.scene.control.DialogPane pane) {
                        TextField editor = (TextField) d.getScene().lookup(".text-field");
                        if (editor != null)
                            editor.setText("Xato kiritildi");
                        Node ok = pane.lookupButton(javafx.scene.control.ButtonType.OK);
                        if (ok instanceof Button b)
                            b.fire();
                    }
                });
                Thread.sleep(1500);
                captureAtSizes(primaryStage, (Parent) contentArea(primaryStage), "C3-work-voided", outDir);

                // Xulosa tab with real labor and net profit
                Platform.runLater(() -> {
                    TabPane tabs = (TabPane) contentArea(primaryStage).lookup("#tabs");
                    if (tabs != null)
                        tabs.getSelectionModel().select(3);
                });
                Thread.sleep(800);
                captureAtSizes(primaryStage, (Parent) contentArea(primaryStage), "C4-xulosa-labor", outDir);

                // employee card: add a salary payment
                fireMenu(primaryStage, "#menuEmployees");
                Thread.sleep(1600);
                Platform.runLater(() -> {
                    TableView<?> t = (TableView<?>) primaryStage.getScene().lookup("#employeeTable");
                    if (t != null && !t.getItems().isEmpty()) {
                        t.getSelectionModel().select(0);
                        t.fireEvent(doubleClick());
                    }
                });
                Thread.sleep(2000);
                Platform.runLater(() -> {
                    TabPane tabs = (TabPane) contentArea(primaryStage).lookup("#tabs");
                    if (tabs != null)
                        tabs.getSelectionModel().select(1);
                });
                Thread.sleep(700);
                Platform.runLater(() -> {
                    TextField amt = (TextField) contentArea(primaryStage).lookup("#amountField");
                    if (amt != null)
                        amt.setText("100 000");
                });
                Thread.sleep(500);
                Platform.runLater(() -> {
                    Node add = contentArea(primaryStage).lookup("#addPaymentButton");
                    if (add instanceof Button b)
                        b.fire();
                });
                Thread.sleep(1600);
                captureAtSizes(primaryStage, (Parent) contentArea(primaryStage), "C5-employee-card", outDir);

                // dashboard with "Ishchilarga qarzimiz"
                fireMenu(primaryStage, "#menuHome");
                Thread.sleep(1600);
                captureAtSizes(primaryStage, (Parent) contentArea(primaryStage), "C6-dashboard", outDir);

                // --- Phase 5: Hisobotlar reports ---
                fireMenu(primaryStage, "#menuReports");
                Thread.sleep(1800);
                for (int t = 0; t < 5; t++) {
                    final int idx = t;
                    Platform.runLater(() -> {
                        Node content = contentArea(primaryStage);
                        ComboBox<?> combo = (ComboBox<?>) content.lookup("#reportTypeCombo");
                        if (combo != null && combo.getItems().size() > idx)
                            combo.getSelectionModel().select(idx);
                    });
                    Thread.sleep(1400);
                    captureAtSizes(primaryStage, (Parent) contentArea(primaryStage),
                            "R" + (t + 1) + "-report-month", outDir);
                }

                // Custom range: previous month 1st .. today
                Platform.runLater(() -> {
                    Node custom = contentArea(primaryStage).lookup("#periodCustomButton");
                    if (custom instanceof ToggleButton tb)
                        tb.setSelected(true);
                });
                Thread.sleep(500);
                Platform.runLater(() -> {
                    Node content = contentArea(primaryStage);
                    UzDatePicker from = (UzDatePicker) content.lookup("#fromField");
                    UzDatePicker to = (UzDatePicker) content.lookup("#toField");
                    if (from != null)
                        from.setValue(LocalDate.now().minusMonths(1).withDayOfMonth(1));
                    if (to != null)
                        to.setValue(LocalDate.now());
                });
                Thread.sleep(1600);
                for (int t = 0; t < 5; t++) {
                    final int idx = t;
                    Platform.runLater(() -> {
                        Node content = contentArea(primaryStage);
                        ComboBox<?> combo = (ComboBox<?>) content.lookup("#reportTypeCombo");
                        if (combo != null && combo.getItems().size() > idx)
                            combo.getSelectionModel().select(idx);
                    });
                    Thread.sleep(1400);
                    captureAtSizes(primaryStage, (Parent) contentArea(primaryStage),
                            "R" + (t + 1) + "-report-range", outDir);
                }

                // Bosh sahifa with the "Bu oy foyda" card
                fireMenu(primaryStage, "#menuHome");
                Thread.sleep(1800);
                captureAtSizes(primaryStage, (Parent) contentArea(primaryStage), "R6-dashboard-profit", outDir);

                // --- Phase 6: Sozlamalar, backup, restore ---
                fireMenu(primaryStage, "#menuSettings");
                Thread.sleep(1800);
                captureAtSizes(primaryStage, (Parent) contentArea(primaryStage), "S1-settings", outDir);

                // scrolled view of the settings page
                Platform.runLater(() -> {
                    Node sp = contentArea(primaryStage).lookup(".scroll-pane");
                    if (sp instanceof ScrollPane pane)
                        pane.setVvalue(1.0);
                });
                Thread.sleep(900);
                captureAtSizes(primaryStage, (Parent) contentArea(primaryStage), "S1b-settings-scrolled", outDir);
                Platform.runLater(() -> {
                    Node sp = contentArea(primaryStage).lookup(".scroll-pane");
                    if (sp instanceof ScrollPane pane)
                        pane.setVvalue(0.0);
                });
                Thread.sleep(500);

                // manual backups through the service, then re-open the screen
                BackupService settingsBackupService = context.getBean(BackupService.class);
                BackupResult firstBackup = settingsBackupService.createBackup(BackupReason.MANUAL);
                BackupResult previewBackup = settingsBackupService.createBackup(BackupReason.MANUAL);
                System.out.println("MADE backups: " + firstBackup.path() + ", " + previewBackup.path());

                fireMenu(primaryStage, "#menuSettings");
                Thread.sleep(1800);
                captureAtSizes(primaryStage, (Parent) contentArea(primaryStage), "S2-settings-after-backup", outDir);

                // restore preview dialog, driven through a real controller instance
                SettingsController settingsController = loadSettingsController(context);
                if (settingsController != null) {
                    Platform.runLater(() -> settingsController.restoreFrom(previewBackup.path()));
                    Thread.sleep(2000);
                    Window restoreDialog = findShowingDialog(primaryStage);
                    if (restoreDialog instanceof Stage rs && rs.getScene() != null) {
                        captureAtSizes(rs, rs.getScene().getRoot(), "S3-restore-preview", outDir);
                    } else {
                        System.err.println("ERROR: restore preview dialog not found");
                    }
                    closeShowingDialog(primaryStage);
                    Thread.sleep(700);
                } else {
                    System.err.println("ERROR: settings controller not loaded");
                }

                // dashboard with the "Oxirgi backup" card
                fireMenu(primaryStage, "#menuHome");
                Thread.sleep(1600);
                captureAtSizes(primaryStage, (Parent) contentArea(primaryStage), "S4-dashboard-backup", outDir);

                // back to Bosh sahifa
                fireMenu(primaryStage, "#menuHome");
                Thread.sleep(1500);

                // 04) dashboard after the changes
                Platform.runLater(() -> takeSnapshot(primaryStage.getScene().getRoot(),
                        new File(outDir, "04-dashboard.png")));
                Thread.sleep(500);

                // --- C4: live theme switch while the main window AND a dialog are open ---
                if (System.getenv("MEBELSEX_THEME") != null) {
                    fireMenu(primaryStage, "#menuHome");
                    Thread.sleep(1200);
                    Platform.runLater(() -> {
                        Node newBtn = primaryStage.getScene().lookup("#newOrderButton");
                        if (newBtn instanceof Button b)
                            b.fire();
                    });
                    Thread.sleep(2200);
                    Window openDialog = findShowingDialog(primaryStage);
                    Platform.runLater(() -> takeSnapshot(primaryStage.getScene().getRoot(),
                            new File(outDir, "T1-switch-before-main.png")));
                    Thread.sleep(500);
                    if (openDialog != null && openDialog.getScene() != null) {
                        Platform.runLater(() -> takeSnapshot(openDialog.getScene().getRoot(),
                                new File(outDir, "T2-switch-before-dialog.png")));
                        Thread.sleep(500);
                    }
                    final com.mebelsex.ui.ThemeManager tm = context.getBean(com.mebelsex.ui.ThemeManager.class);
                    final com.mebelsex.ui.Theme flipped = tm.current().toggled();
                    Platform.runLater(() -> tm.setTheme(flipped));
                    Thread.sleep(1200);
                    Platform.runLater(() -> takeSnapshot(primaryStage.getScene().getRoot(),
                            new File(outDir, "T3-switch-after-main.png")));
                    Thread.sleep(500);
                    if (openDialog != null && openDialog.getScene() != null) {
                        Platform.runLater(() -> takeSnapshot(openDialog.getScene().getRoot(),
                                new File(outDir, "T4-switch-after-dialog.png")));
                        Thread.sleep(500);
                    }
                    closeShowingDialog(primaryStage);
                    Thread.sleep(700);
                }

                Thread.sleep(1000);
                Platform.exit();
            } catch (Exception e) {
                e.printStackTrace();
                Platform.exit();
            }
        });
        snapshotThread.setDaemon(true);
        snapshotThread.start();
    }

    /**
     * Dev-only: executes only the steps named in {@code only} (comma list) and returns.
     * Used so a snapshot run for one change does not re-capture unchanged screens.
     */
    private static int runOnly(Stage primaryStage, File outDir, String only, ApplicationContext context)
            throws InterruptedException {
        java.util.Set<String> steps = new java.util.HashSet<>();
        for (String part : only.split(",")) {
            if (!part.isBlank())
                steps.add(part.trim());
        }
        System.out.println("Snapshot ONLY mode: " + steps);

        if (steps.contains("inputs")) {
            return runInputChecks(outDir);
        }

        if (steps.contains("orders")) {
            fireMenu(primaryStage, "#menuOrders");
            Thread.sleep(1800);
            Platform.runLater(() -> takeSnapshot((Parent) contentArea(primaryStage),
                    new File(outDir, "p0-orders.png")));
            Thread.sleep(600);
        }

        if (steps.contains("settings-about")) {
            fireMenu(primaryStage, "#menuSettings");
            Thread.sleep(1800);
            Platform.runLater(() -> {
                Node sp = contentArea(primaryStage).lookup(".scroll-pane");
                if (sp instanceof ScrollPane pane)
                    pane.setVvalue(1.0);
            });
            Thread.sleep(900);
            Platform.runLater(() -> {
                for (String id : new String[] { "#openLogsButton", "#createDiagnosticsButton", "#versionLabel" }) {
                    Node node = contentArea(primaryStage).lookup(id);
                    if (node != null) {
                        javafx.geometry.Bounds b = node.localToScene(node.getBoundsInLocal());
                        System.out.println("PROBE " + id + " visible=" + node.isVisible() + " w=" + b.getWidth()
                                + " h=" + b.getHeight() + " x=" + b.getMinX() + " y=" + b.getMinY());
                    } else {
                        System.out.println("PROBE " + id + " MISSING");
                    }
                }
                takeSnapshot((Parent) contentArea(primaryStage), new File(outDir, "pB-about.png"));
            });
            Thread.sleep(600);
        }

        if (steps.contains("new-order")) {
            fireMenu(primaryStage, "#menuHome");
            Thread.sleep(1500);
            Platform.runLater(() -> {
                Node newBtn = primaryStage.getScene().lookup("#newOrderButton");
                if (newBtn instanceof Button b)
                    b.fire();
            });
            Thread.sleep(2000);
            Platform.runLater(() -> {
                Window d = findShowingDialog(primaryStage);
                if (d != null && d.getScene() != null) {
                    Node product = d.getScene().lookup("#productField");
                    if (product instanceof TextField t)
                        t.setText("Oshxona mebeli");
                    Node price = d.getScene().lookup("#priceField");
                    if (price instanceof TextField t)
                        t.setText("25000000");
                    Node notes = d.getScene().lookup("#notesField");
                    if (notes instanceof javafx.scene.control.TextArea t)
                        t.setText("Mijoz rang tanladi");
                }
            });
            Thread.sleep(900);
            Platform.runLater(() -> {
                Window d = findShowingDialog(primaryStage);
                if (d != null && d.getScene() != null) {
                    takeSnapshot(d.getScene().getRoot(), new File(outDir, "p0-new-order.png"));
                } else {
                    System.err.println("ERROR: new-order dialog not found");
                }
            });
            Thread.sleep(600);
            closeShowingDialog(primaryStage);
            Thread.sleep(600);
        }

        if (steps.contains("license-banner")) {
            fireMenu(primaryStage, "#menuHome");
            Thread.sleep(1500);
            setDevLicenseState(context, "READ_ONLY");
            Thread.sleep(1500);
            Platform.runLater(() -> takeSnapshot((Parent) primaryStage.getScene().getRoot(),
                    new File(outDir, "lic-banner-readonly.png")));
            Thread.sleep(900);
            setDevLicenseState(context, "EXPIRING");
            Thread.sleep(1500);
            Platform.runLater(() -> takeSnapshot((Parent) primaryStage.getScene().getRoot(),
                    new File(outDir, "lic-banner-expiring.png")));
            Thread.sleep(700);
        }

        if (steps.contains("license-settings")) {
            setDevLicenseState(context, "VALID");
            fireMenu(primaryStage, "#menuSettings");
            Thread.sleep(1800);
            Platform.runLater(() -> {
                Node sp = contentArea(primaryStage).lookup(".scroll-pane");
                if (sp instanceof ScrollPane pane)
                    pane.setVvalue(0.15);
            });
            Thread.sleep(900);
            Platform.runLater(() -> takeSnapshot((Parent) contentArea(primaryStage),
                    new File(outDir, "lic-settings.png")));
            Thread.sleep(600);
        }

        return 0;
    }

    /**
     * Dev-only self-check for the numeric input fields (no PNG; text log only). Runs real
     * editing operations on the FX thread inside a real Stage and records every exception so a
     * crash from modifying a control inside its own text listener is reproduced deterministically.
     * Returns the number of failed operations (0 = all good).
     */
    private static int runInputChecks(File outDir) throws InterruptedException {
        java.util.List<String> logLines = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.List<String> failures = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(8);
                box.setPadding(new javafx.geometry.Insets(16));
                MoneyField money = new MoneyField();
                SignedMoneyField signed = new SignedMoneyField();
                QuantityField quantity = new QuantityField();
                UzDatePicker date = new UzDatePicker();
                box.getChildren().addAll(money, signed, quantity, date);
                javafx.scene.Scene scene = new javafx.scene.Scene(box, 420, 320);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();

                checkControl("MoneyField", money, logLines, failures);
                checkControl("SignedMoneyField", signed, logLines, failures);
                checkControl("QuantityField", quantity, logLines, failures);
                checkControl("UzDatePicker.editor", date.getEditor(), logLines, failures);

                stage.hide();
                stage.close();
            } catch (Throwable t) {
                failures.add("SETUP :: " + describe(t));
                t.printStackTrace();
            } finally {
                latch.countDown();
            }
        });
        latch.await();

        File log = new File(outDir, "inputs-check.txt");
        try (PrintWriter pw = new PrintWriter(new FileWriter(log, false))) {
            pw.println("INPUT SELF-CHECK (text log only, no PNG)");
            for (String line : logLines)
                pw.println(line);
            pw.println("FAILURES: " + failures.size());
            for (String f : failures)
                pw.println(f);
        } catch (Exception e) {
            System.err.println("WARN: cannot write " + log + ": " + e.getMessage());
        }
        for (String line : logLines)
            System.out.println(line);
        for (String f : failures)
            System.err.println(f);
        System.out.println("INPUT SELF-CHECK: " + (failures.isEmpty() ? "PASS" : failures.size() + " failure(s)"));
        return failures.size();
    }

    /** Runs every editing operation against one control, wrapping each one in try/catch. */
    private static void checkControl(String field, javafx.scene.control.TextInputControl control,
            java.util.List<String> logLines, java.util.List<String> failures) {
        control.clear();
        step(field, "appendText(25000000) then deletePreviousChar until empty", control, logLines, failures, () -> {
            control.clear();
            control.appendText("25000000");
            while (!control.getText().isEmpty())
                control.deletePreviousChar();
        });
        step(field, "positionCaret(0) then deleteNextChar until empty", control, logLines, failures, () -> {
            control.clear();
            control.appendText("25000000");
            control.positionCaret(0);
            while (!control.getText().isEmpty())
                control.deleteNextChar();
        });
        step(field, "replaceText(0, len, 25000000)", control, logLines, failures, () -> {
            control.clear();
            control.appendText("12345678");
            control.replaceText(0, control.getLength(), "25000000");
        });
        step(field, "selectAll + replaceSelection(7)", control, logLines, failures, () -> {
            control.clear();
            control.appendText("12345678");
            control.selectAll();
            control.replaceSelection("7");
        });
        step(field, "selectAll + replaceSelection(paste) 25 000 000 so'm", control, logLines, failures, () -> {
            control.clear();
            control.selectAll();
            control.replaceSelection("25 000 000 so'm");
        });
        step(field, "positionCaret(2) + deletePreviousChar over separator", control, logLines, failures, () -> {
            control.clear();
            control.appendText("25000");
            control.positionCaret(2);
            control.deletePreviousChar();
        });
        step(field, "KEY_PRESSED BACK_SPACE", control, logLines, failures, () -> {
            control.clear();
            control.appendText("25000");
            control.positionCaret(control.getLength());
            control.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                    javafx.scene.input.KeyCode.BACK_SPACE, false, false, false, false));
        });
        step(field, "KEY_PRESSED DELETE", control, logLines, failures, () -> {
            control.clear();
            control.appendText("25000");
            control.positionCaret(0);
            control.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                    javafx.scene.input.KeyCode.DELETE, false, false, false, false));
        });
        step(field, "KEY_TYPED letter", control, logLines, failures, () -> {
            control.clear();
            control.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_TYPED, "a", "",
                    javafx.scene.input.KeyCode.UNDEFINED, false, false, false, false));
        });
        step(field, "appendText(letter)", control, logLines, failures, () -> {
            control.clear();
            control.appendText("a");
        });
        step(field, "programmatic setText(5000000)", control, logLines, failures, () -> {
            control.clear();
            control.setText("5000000");
        });
        control.clear();
    }

    private static void step(String field, String op, javafx.scene.control.TextInputControl control,
            java.util.List<String> logLines, java.util.List<String> failures, Runnable action) {
        try {
            action.run();
            logLines.add("OK   " + field + " | " + op + " -> \"" + control.getText() + "\"");
        } catch (Throwable t) {
            String msg = "FAIL " + field + " | " + op + " :: " + describe(t);
            failures.add(msg);
            logLines.add(msg);
        }
    }

    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder(t.getClass().getName());
        if (t.getMessage() != null)
            sb.append(": ").append(t.getMessage());
        StackTraceElement[] st = t.getStackTrace();
        if (st.length > 0)
            sb.append(" at ").append(st[0]);
        return sb.toString();
    }

    /** Dev-only: flips the forced license state at runtime so each state can be snapshotted. */
    private static void setDevLicenseState(ApplicationContext context, String state) {
        System.setProperty(com.mebelsex.license.LicenseService.FORCE_PROPERTY, state);
        try {
            context.getBean(com.mebelsex.license.LicenseService.class).evaluate();
        } catch (RuntimeException e) {
            System.err.println("WARN: cannot force license state: " + e.getMessage());
        }
    }

    private static Node contentArea(Stage primaryStage) {
        return primaryStage.getScene().lookup("#contentArea");
    }

    /** Loads a throwaway Sozlamalar controller so the restore dialog can be driven. */
    private static SettingsController loadSettingsController(ApplicationContext context) {
        try {
            FXMLLoader loader = new FXMLLoader(SnapshotConfigurator.class.getResource("/fxml/settings.fxml"),
                    com.mebelsex.common.Messages.bundle());
            loader.setControllerFactory(context::getBean);
            loader.load();
            return loader.getController();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Captures the same root at 1280x800 then 1024x680. */
    private static void captureAtSizes(Stage stage, Parent root, String base, File outDir) throws InterruptedException {
        captureAt(stage, root, base, 1280, 800, outDir);
        captureAt(stage, root, base, 1024, 680, outDir);
    }

    private static void captureAt(Stage stage, Parent root, String base, int w, int h, File outDir)
            throws InterruptedException {
        Platform.runLater(() -> {
            stage.setWidth(w);
            stage.setHeight(h);
        });
        Thread.sleep(600);
        Platform.runLater(() -> takeSnapshot(root, new File(outDir, base + "-" + w + "x" + h + ".png")));
        Thread.sleep(500);
    }

    private static void closeShowingDialog(Stage primaryStage) {
        Platform.runLater(() -> {
            Window d = findShowingDialog(primaryStage);
            if (d instanceof Stage s)
                s.close();
        });
    }

    private static void fireMenu(Stage primaryStage, String menuId) {
        Platform.runLater(() -> {
            HBox menu = (HBox) primaryStage.getScene().lookup(menuId);
            if (menu != null) {
                menu.fireEvent(doubleClick());
            }
        });
    }

    private static MouseEvent doubleClick() {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 2,
                true, true, true, true, true, true, true, true, true, true, null);
    }

    /** First showing Stage other than the main one. */
    private static Window findShowingDialog(Stage primaryStage) {
        for (Window w : Window.getWindows()) {
            if (w != primaryStage && w.isShowing() && w instanceof Stage && w.getScene() != null) {
                return w;
            }
        }
        return null;
    }

    private static void takeSnapshot(Parent root, File dest) {
        try {
            WritableImage fxImage = root.snapshot(new javafx.scene.SnapshotParameters(), null);
            int w = (int) fxImage.getWidth();
            int h = (int) fxImage.getHeight();
            BufferedImage bImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            PixelReader pr = fxImage.getPixelReader();
            int[] buffer = new int[w * h];
            pr.getPixels(0, 0, w, h, javafx.scene.image.WritablePixelFormat.getIntArgbInstance(), buffer, 0, w);
            bImage.setRGB(0, 0, w, h, buffer, 0, w);
            ImageIO.write(bImage, "png", dest);
            System.out.println("Saved snapshot: " + dest.getName());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void dumpLayout(Parent root, File dest, boolean append) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(dest, append))) {
            pw.println("--- DUMP ---");
            dumpNode(root, pw, 0);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void dumpNode(Node node, PrintWriter pw, int depth) {
        String indent = "  ".repeat(depth);
        String name = node.getClass().getSimpleName();
        String id = node.getId();
        String styles = String.join(",", node.getStyleClass());
        double w = node.getBoundsInParent().getWidth();
        double h = node.getBoundsInParent().getHeight();

        String sizeInfo = "";
        if (node instanceof javafx.scene.layout.Region r) {
            sizeInfo = String.format(" [minW:%.1f prefW:%.1f maxW:%.1f]", r.getMinWidth(), r.getPrefWidth(),
                    r.getMaxWidth());
            w = r.getWidth();
            h = r.getHeight();
        }

        String text = "";
        if (node instanceof Label label)
            text = " '" + label.getText() + "'";

        pw.printf("%s%s (#%s) styles:[%s] size:%.1fx%.1f%s%s%n",
                indent, name, id, styles, w, h, sizeInfo, text);

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                dumpNode(child, pw, depth + 1);
            }
        }
    }
}
