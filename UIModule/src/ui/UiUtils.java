package ui;

import engine.dto.TradeRecordDTO;
import internals.ActionType;
import javafx.animation.Animation;
import javafx.animation.ScaleTransition;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

import java.util.List;

/**
 * Helper methods used by more than one part of the UI:
 * animations, themed dialogs and buy/sell history labels.
 */
public final class UiUtils {

    private UiUtils() {}

    /** #####################################
     *      Animations
     *  #####################################
     */

    // scales the node to the given size and back again
    public static ScaleTransition createPulseAnimation(Node node, int millis, double scale) {
        ScaleTransition anim = new ScaleTransition(Duration.millis(millis), node);
        anim.setFromX(1.0);
        anim.setFromY(1.0);
        anim.setToX(scale);
        anim.setToY(scale);
        anim.setAutoReverse(true);
        anim.setCycleCount(2);
        return anim;
    }

    public static void playIfEnabled(ScaleTransition anim, AppState state) {
        if (state.isAnimationsEnabled() && anim.getStatus() != Animation.Status.RUNNING) {
            anim.playFromStart();
        }
    }

    // button that shrinks slightly when pressed
    public static void attachClickAnimation(Button btn, AppState state) {
        ScaleTransition pressAnim = createPulseAnimation(btn, 100, 0.92);
        btn.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> playIfEnabled(pressAnim, state));
    }

    // table row that shrinks slightly when pressed
    public static <T> TableRow<T> createAnimatedRow(AppState state) {
        TableRow<T> row = new TableRow<>();
        ScaleTransition scaleAnim = createPulseAnimation(row, 100, 0.96);
        row.setOnMousePressed(event -> {
            if (!row.isEmpty()) {
                playIfEnabled(scaleAnim, state);
            }
        });
        return row;
    }

    /** #####################################
     *      Dialogs
     *  #####################################
     */

    // applies the current theme to a popup
    public static void styleDialog(Dialog<?> dialog, List<String> stylesheets) {
        if (stylesheets != null && !stylesheets.isEmpty()) {
            dialog.getDialogPane().getStylesheets().addAll(stylesheets);
        }
        // Adds a custom class so we can style the popup container
        dialog.getDialogPane().getStyleClass().add("modern-dialog");
    }

    public static void showAlert(Alert.AlertType type, String title, String content, List<String> stylesheets) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        styleDialog(alert, stylesheets);
        alert.showAndWait();
    }

    /** #####################################
     *      History labels
     *  #####################################
     */

    // green label for buys, red label for sells
    public static Label createHistoryLabel(String text, boolean isBuy) {
        Label label = new Label(text);
        label.getStyleClass().add(isBuy ? "history-buy" : "history-sell");
        return label;
    }

    public static Label createTradeLabel(TradeRecordDTO trade) {
        String text = String.format("[%s] Option: %s | Amount: %d | Paid: $%.2f",
                trade.action(), trade.optionName(), trade.amount(), trade.pricePaid());
        return createHistoryLabel(text, trade.action() == ActionType.BUY);
    }
}