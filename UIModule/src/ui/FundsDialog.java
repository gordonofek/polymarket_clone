package ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Popup that asks how much money to deposit or withdraw.
 * The confirm button only works for a positive amount with at most 2 decimals
 * (and, when withdrawing, no more than the balance).
 */
public final class FundsDialog {

    private FundsDialog() {}

    // returns the amount, or empty if the user cancelled
    public static Optional<Double> askDeposit(double balance, List<String> stylesheets) {
        return show("Add Funds", "How much do you want to deposit?", "Deposit", balance, false, stylesheets);
    }

    // same, with a "Withdraw all" button that fills in the whole balance
    public static Optional<Double> askWithdraw(double balance, List<String> stylesheets) {
        return show("Withdraw", "How much do you want to withdraw?", "Withdraw", balance, true, stylesheets);
    }

    private static Optional<Double> show(String title, String header, String confirmText,
                                         double balance, boolean isWithdraw, List<String> stylesheets) {
        Dialog<Double> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        UiUtils.styleDialog(dialog, stylesheets);

        ButtonType confirmType = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmType, ButtonType.CANCEL);

        Label balanceLabel = new Label(String.format("Current balance: $%.2f", balance));
        balanceLabel.getStyleClass().add("comm-title-label");

        TextField amountField = new TextField();
        amountField.setPromptText("0.00");
        HBox inputRow = new HBox(8, new Label("$"), amountField);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("history-sell");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        VBox content = new VBox(10, balanceLabel, inputRow);
        if (isWithdraw) {
            Button withdrawAllBtn = new Button("Withdraw all");
            withdrawAllBtn.getStyleClass().add("option-preview-btn");
            withdrawAllBtn.setDisable(balance <= 0);
            // Locale.ROOT so it's always "10.50", never "10,50"
            withdrawAllBtn.setOnAction(e -> amountField.setText(String.format(Locale.ROOT, "%.2f", balance)));
            inputRow.getChildren().add(withdrawAllBtn);
        }
        content.getChildren().add(errorLabel);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        // check the amount on every key press; the confirm button only works when it's valid
        Node confirmBtn = dialog.getDialogPane().lookupButton(confirmType);
        confirmBtn.setDisable(true);
        amountField.textProperty().addListener((obs, oldText, newText) -> {
            String error = validate(newText, balance, isWithdraw);
            confirmBtn.setDisable(error != null);
            // an empty field just disables the button, no need to complain yet
            boolean showError = error != null && !newText.isBlank();
            errorLabel.setText(showError ? error : "");
            errorLabel.setVisible(showError);
            errorLabel.setManaged(showError);
        });

        dialog.setResultConverter(button ->
                button == confirmType ? Double.parseDouble(amountField.getText().trim()) : null);

        // focus only works once the dialog is on screen
        Platform.runLater(amountField::requestFocus);
        return dialog.showAndWait();
    }

    // null when the amount is fine, otherwise what's wrong with it
    private static String validate(String text, double balance, boolean isWithdraw) {
        String trimmed = text.trim();
        if (!trimmed.matches("\\d+(\\.\\d{1,2})?")) {
            return "Enter a positive amount with up to 2 decimals.";
        }
        double amount = Double.parseDouble(trimmed);
        if (amount <= 0) {
            return "The amount must be more than 0.";
        }
        if (isWithdraw && amount > Math.round(balance * 100) / 100.0) {
            return String.format("You only have $%.2f.", balance);
        }
        return null;
    }
}