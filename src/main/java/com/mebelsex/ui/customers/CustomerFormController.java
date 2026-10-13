package com.mebelsex.ui.customers;

import com.mebelsex.common.BusinessException;
import com.mebelsex.customers.Customer;
import com.mebelsex.customers.CustomerService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

@Controller
@Scope("prototype")
public class CustomerFormController {

    private final CustomerService customerService;
    private final ResourceBundle bundle;

    @FXML
    private TextField nameField;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField addressField;
    @FXML
    private TextArea notesField;
    @FXML
    private Label errorLabel;

    private Stage stage;
    private Customer customer;
    private boolean saved = false;

    public CustomerFormController(CustomerService customerService) {
        this.customerService = customerService;
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
        if (customer != null) {
            nameField.setText(customer.getFullName());
            phoneField.setText(customer.getPhone());
            addressField.setText(customer.getAddress());
            notesField.setText(customer.getNotes());
        }
    }

    public boolean isSaved() {
        return saved;
    }

    @FXML
    public void handleCancel() {
        stage.close();
    }

    @FXML
    public void handleSave() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String phone = phoneField.getText() == null ? "" : phoneField.getText().trim();
        String address = addressField.getText() == null ? "" : addressField.getText().trim();
        String notes = notesField.getText() == null ? "" : notesField.getText().trim();

        try {
            if (customer == null) {
                customerService.create(name, phone.isEmpty() ? null : phone, address.isEmpty() ? null : address,
                        notes.isEmpty() ? null : notes);
            } else {
                customerService.update(customer.getId(), name, phone.isEmpty() ? null : phone,
                        address.isEmpty() ? null : address, notes.isEmpty() ? null : notes);
            }
            saved = true;
            stage.close();
        } catch (BusinessException e) {
            String errorMsg = e.getMessageKey();
            try {
                errorMsg = bundle.getString(e.getMessageKey());
            } catch (MissingResourceException ignored) {
            }

            errorLabel.setText(errorMsg);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }
}
