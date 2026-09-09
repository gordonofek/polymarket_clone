package ui;

import engine.Engine;
import engine.EngineInterface;
import engine.dto.*;

import internals.ActionType;
import javafx.animation.Animation;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javafx.animation.ScaleTransition;
import javafx.util.Duration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

public class MainController {

    EngineInterface engine = new Engine();

    // Top part
    @FXML private Button loadFileBtn;
    @FXML private TextField filePathField;
    @FXML ProgressBar progressBar;

    // Active user - events
    @FXML private Label userNameLabelEvents;
    @FXML private Label userBalanceLabelEvents;

    // Tab
    @FXML private Tab usersTab;

    // Filters
    @FXML private TextField searchField;
    @FXML private Button clearFiltersBtn;
    @FXML ComboBox<String> methodFilter;
    @FXML ComboBox<String> statusFilter;
    @FXML ComboBox<String> commissionFilter;

    // Events display
    @FXML private VBox eventsContainer;

    // Events
    private List<EventSummaryDTO> allEvents = new ArrayList<>();
    private EventSummaryDTO selectedEvent = null;

    // Events details and Trade panel
    @FXML private Label detailIdLabel;
    @FXML private Label detailMethodLabel;
    @FXML private Label detailStatusLabel;
    @FXML private Label detailTitleLabel;
    @FXML private Label detailDescLabel;
    @FXML private Label detailCommLabel;
    @FXML private VBox optionsContainer;
    @FXML private ListView<Label> participationsHistoryListView;
    @FXML private ListView<Label> eventparticipationsListView;

    @FXML private LineChart<Number, Number> priceHistoryChart;


    // users list
    @FXML private TableView<UserSummaryDTO> usersTableView;
    @FXML private TableColumn<UserSummaryDTO, String> userNameCol;
    @FXML private TableColumn<UserSummaryDTO, Number> userBalanceCol;
    private String activeUsername = null;
    @FXML private Label activeUserDisplayLabel;

    // top header
    @FXML private Label userNameLabel;
    @FXML private Label userBalanceLabel;

    // portfolio table
    // portfolio table
    @FXML private TableView<PortfolioItemDTO> portfolioTableView;
    @FXML private TableColumn<PortfolioItemDTO, String> portfolioEventCol;
    @FXML private TableColumn<PortfolioItemDTO, String> portfolioOptionCol;
    @FXML private TableColumn<PortfolioItemDTO, Number> portfolioSharesCol;

    // event overview
    @FXML private Label userEventTitleLabel;
    @FXML private VBox userEventOptionsBox;

    @FXML
    public void initialize() {

        // users
        userNameCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().name()));
        userBalanceCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleDoubleProperty(cellData.getValue().balance()));
        userBalanceCol.setCellFactory(column -> new TableCell<UserSummaryDTO, Number>() {
            @Override
            protected  void updateItem(Number balance, boolean empty) {
                super.updateItem(balance, empty);
                if (empty || balance == null) {
                    setText(null);
                }
                else {
                    setText(String.format("%.2f", balance.doubleValue()));
                }
            }
        });
        // users - portfolio
        portfolioEventCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().eventName()));
        portfolioOptionCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().optionName()));
        portfolioSharesCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().shares()));

        if (usersTab != null) {
            usersTab.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
                if (isNowSelected) {
                    refreshUsersTab();
                }
            });
        }

        // listener
        searchField.textProperty().addListener((obs, oldText, newText) -> updateEventList());
        methodFilter.setOnAction(e -> updateEventList());
        statusFilter.setOnAction(e -> updateEventList());
        commissionFilter.setOnAction(e -> updateEventList());

        // clear filters
        clearFiltersBtn.setOnAction(e -> {
            searchField.clear();
            methodFilter.getSelectionModel().selectFirst();
            statusFilter.getSelectionModel().selectFirst();
            commissionFilter.getSelectionModel().selectFirst();
        });

        usersTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                loadUserDetails(newSelection);

                this.activeUsername = newSelection.name();
                if (activeUserDisplayLabel != null) {
                    activeUserDisplayLabel.setText("Trading as: " + activeUsername);
                }
            }
        });
    }

    public void loadData(List<EventSummaryDTO> events)
    {
        this.allEvents = events;
        updateEventList();

        List<UserSummaryDTO> users = engine.getAllUsers();
        usersTableView.getItems().setAll(users);
    }

    public void updateEventList()
    {
        // clear the events
        eventsContainer.getChildren().clear();
        // get the filters
        String search = searchField.getText().toLowerCase();
        String method = methodFilter.getValue();
        String status = statusFilter.getValue();
        String commission = commissionFilter.getValue();

        List<EventSummaryDTO> filteredEvents = allEvents.stream()
                .filter(event -> search.isEmpty() || event.name().toLowerCase().contains(search))
                .filter(event -> method == null || method.contains("All") || event.tradingMethodType().equalsIgnoreCase(method.replace(" ", "_")))
                .filter(event -> status == null || status.contains("All") || event.status().equalsIgnoreCase(status.replace(" ", "_")))
                .filter(event -> commission == null || commission.contains("All") || event.commissionType().equalsIgnoreCase(commission.replace(" ", "_")))
                .toList();

        // for every matching event, create a tile
        for (EventSummaryDTO event : filteredEvents) {
            GridPane tile = createEventTile(event);
            eventsContainer.getChildren().add(tile);
        }
    }

    private GridPane createEventTile(EventSummaryDTO eventSummary) {
        EventDetailsDTO details = engine.getEventState(eventSummary.id());

        GridPane tile = new GridPane();
        tile.getStyleClass().add("event-tile");

        // column constraints
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHalignment(HPos.RIGHT);
        tile.getColumnConstraints().addAll(col1, col2);

        // id, trading method, status
        Label idLabel = new Label("#" + eventSummary.id());
        idLabel.getStyleClass().add("event-id-label");

        Label methodLabel = new Label(eventSummary.tradingMethodType());
        methodLabel.getStyleClass().addAll("method-badge", "method-lmsr");

        HBox topLeftBox = new HBox(8, idLabel, methodLabel);
        topLeftBox.setAlignment(Pos.CENTER_LEFT);

        // status
        Label statusLabel = new Label(eventSummary.status().replace("_", " "));
        statusLabel.getStyleClass().addAll("status-badge", "status-" + eventSummary.status().toLowerCase());

        // name, options header
        Label titleLabel = new Label(eventSummary.name());
        titleLabel.getStyleClass().add("event-title-label");

        Label optionsHeader = new Label("Available Options:");
        optionsHeader.getStyleClass().add("options-header-label");

        // description, options
        Label descLabel = new Label(eventSummary.description());
        descLabel.setWrapText(true);
        descLabel.getStyleClass().add("event-desc-label");
        GridPane.setValignment(descLabel, VPos.TOP); // Align text to top

        HBox optionsBox = new HBox(8);
        optionsBox.setAlignment(Pos.TOP_RIGHT);

        // buttons to event options
        for (OptionDetailsDTO option : details.options()) {
            Button optBtn = new Button(String.format("%s\n$%.2f", option.optionName(), option.currentPrice()));
            optBtn.getStyleClass().add("option-preview-btn");
            optBtn.setTextAlignment(TextAlignment.CENTER);
            optionsBox.getChildren().add(optBtn);
        }

        // commission
        Label commTitle = new Label("Commission:");
        commTitle.getStyleClass().add("comm-title-label");

        String commTypeStr = eventSummary.commissionType().replace("_", " ");
        Label commVal = new Label(eventSummary.commission() + "% (" + commTypeStr + ")");
        commVal.getStyleClass().add("comm-val-label");

        HBox commBox = new HBox(6, commTitle, commVal);
        commBox.setAlignment(Pos.BOTTOM_LEFT);
        commBox.setPadding(new Insets(10, 0, 0, 0));

        // add to grid
        tile.add(topLeftBox, 0, 0);
        tile.add(statusLabel, 1, 0);

        tile.add(titleLabel, 0, 1);
        tile.add(optionsHeader, 1, 1);

        tile.add(descLabel, 0, 2);
        tile.add(optionsBox, 1, 2);
        GridPane.setRowSpan(optionsBox, 2); // Allow options box to span across rows 2 and 3

        tile.add(commBox, 0, 3);


        // ----------- Testing Animation --------------
        ScaleTransition clickAnimation = new ScaleTransition(Duration.millis(200), tile);
        clickAnimation.setFromX(1.0);
        clickAnimation.setFromY(1.0);
        clickAnimation.setToX(1.02);
        clickAnimation.setToY(1.02);
        clickAnimation.setAutoReverse(true);
        clickAnimation.setCycleCount(2);

        tile.setOnMouseClicked(e -> {
            if(clickAnimation.getStatus() != Animation.Status.RUNNING) {
                clickAnimation.playFromStart();
            }
            handleTileClick(eventSummary);
        });

        return tile;
    }

    @FXML
    private void handleOpenFile()
    {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Guess Market XML File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Files", "*.xml"));

        Stage stage = (Stage) loadFileBtn.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            String filePath = selectedFile.getAbsolutePath();

            loadFileBtn.setDisable(true);
            filePathField.setText("Loading: " + filePath);

            progressBar.setVisible(true);

            Task<List<EventSummaryDTO>> loadTask = new Task<>() {
                @Override
                protected List<EventSummaryDTO> call() throws Exception {
                    int totalSteps = 20;
                    for (int i = 1; i <= totalSteps; i++)
                    {
                        Thread.sleep(100);
                        updateProgress(i, totalSteps);
                    }
                    engine.loadDataFromXML(filePath);
                    return engine.getAllEvents();
                }
            };
            progressBar.progressProperty().bind(loadTask.progressProperty());

            loadTask.setOnSucceeded(e-> {
                progressBar.setVisible(false);

                loadFileBtn.setDisable(false);
                filePathField.setText(filePath);

                List<EventSummaryDTO> loadedEvents = loadTask.getValue();

                loadData(loadedEvents);


                Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                successAlert.setTitle("Success");
                successAlert.setHeaderText(null);
                successAlert.setContentText("File loaded successfully into Guess Market");
                successAlert.showAndWait();
            });

            loadTask.setOnFailed(e-> {
                loadFileBtn.setDisable(false);
                filePathField.setText("Error loading file.");

                Throwable exception = loadTask.getException();

                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("Loading Error");
                errorAlert.setHeaderText("Failed to load the XML file");
                errorAlert.setContentText(exception.getMessage());
                errorAlert.showAndWait();
            });
            new Thread(loadTask).start();
        }
    }

    @FXML
    private void handleTileClick(EventSummaryDTO eventSummary)
    {
        this.selectedEvent = eventSummary;

        boolean isOrderBook = eventSummary.tradingMethodType().contains("ORDER_BOOK");

        detailIdLabel.setText("#" + eventSummary.id());

        String methodStr = eventSummary.tradingMethodType();
        detailMethodLabel.setText(methodStr);
        detailMethodLabel.getStyleClass().setAll("method-badge", "method-" + methodStr.toLowerCase());

        String statusStr = eventSummary.status().replace("_", " ");
        detailStatusLabel.setText(statusStr);
        detailStatusLabel.getStyleClass().setAll("status-badge", "status-" + eventSummary.status().toLowerCase());

        detailTitleLabel.setText(eventSummary.name());
        detailDescLabel.setText(eventSummary.description());

        String commTypeStr = eventSummary.commissionType().replace("_", " ");
        detailCommLabel.setText(eventSummary.commission() + "% (" + commTypeStr + ")");

        optionsContainer.getChildren().clear();

        EventDetailsDTO historyDetails = engine.getEventState(eventSummary.id());
        List<ParticipantHoldingDTO> participantsDetails = engine.getEventParticipants(eventSummary.id());

        for (OptionDetailsDTO option : historyDetails.options()) {
            double price = option.currentPrice();
            String optionName = option.optionName();
            double percentage = price * 100;

            HBox row = new HBox(20);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPrefHeight(50);
            row.getStyleClass().add("option-card");

            // Left side: Option Name using the CSS class
            Label nameLbl = new Label(optionName);
            nameLbl.getStyleClass().add("option-name-label");

            // Spacer
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // Right side: Percentage using the CSS class
            Label percentLbl = new Label(String.format("%.1f%%", percentage));
            percentLbl.getStyleClass().add("option-percent-label");

            // Right side: Price using the CSS class
            Label priceLbl = new Label(String.format("$%.2f", price));
            priceLbl.getStyleClass().add("option-price-label");

            HBox actionButtons = new HBox(10);
            actionButtons.setAlignment(Pos.CENTER_RIGHT);

            Button buyBtn = new Button(String.format("Buy %.2f¢", price * 100));
            buyBtn.getStyleClass().add("buy-option-btn");
            buyBtn.setOnAction(e -> handleBuyAction(eventSummary, optionName));
            actionButtons.getChildren().add(buyBtn);

            if(isOrderBook) {
                Button sellBtn = new Button("Sell");
                sellBtn.getStyleClass().add("sell-option-btn");
                sellBtn.setOnAction(e -> handleSellAction(eventSummary, optionName));
                actionButtons.getChildren().add(sellBtn);
            }

            // Elements added cleanly with the spacer separating them
            row.getChildren().addAll(nameLbl, spacer, percentLbl, priceLbl, actionButtons);
            optionsContainer.getChildren().add(row);
        }

        // trade history of the event
        participationsHistoryListView.getItems().clear();
        List<TradeRecordDTO> history = historyDetails.tradeHistory();
        if(history.isEmpty()) {
            Label emptyLbl = new Label("No trades recorded yet for this event. :C");
            participationsHistoryListView.getItems().add(emptyLbl);
        }
        else {
            for (TradeRecordDTO trade : history)
            {
                String text = String.format("[%s] Option: %s | Amount: %d | Paid: %.2f$",
                        trade.action(),
                        trade.optionName(),
                        trade.amount(),
                        trade.pricePaid()
                );
                Label tradeLabel = new Label(text);
                tradeLabel.setMaxWidth(Double.MAX_VALUE);

                if (trade.action() == ActionType.BUY) {
                    tradeLabel.getStyleClass().add("history-buy");
                }
                else {
                    tradeLabel.getStyleClass().add("history-sell");
                }
                participationsHistoryListView.getItems().add(tradeLabel);
            }
        }

        // users participating in the event
        eventparticipationsListView.getItems().clear();
        if(participantsDetails.isEmpty()) {
            Label emptyParticipantsLbl = new Label("No Users have taken part in this event. :C");
            eventparticipationsListView.getItems().add(emptyParticipantsLbl);
        }
        else {
            for (ParticipantHoldingDTO participant : participantsDetails) {
                String username = participant.username();
                for (Map.Entry<String, Integer> holding : participant.shares().entrySet()) {
                    String optionName = holding.getKey();
                    int shareCount = holding.getValue();
                    if (shareCount > 0 ) {
                        String text = String.format("User: %s | Option '%s' | Shares: %d", username, optionName, shareCount);
                        Label holdingLabel = new Label(text);
                        holdingLabel.getStyleClass().add("holding-label");
                        eventparticipationsListView.getItems().add(holdingLabel);
                    }
                }
            }
        }
        updatePriceGraph(historyDetails);
    }

    private void handleBuyAction(EventSummaryDTO event, String optionName) {
        boolean isOrderBook = event.tradingMethodType().toUpperCase().contains("ORDER_BOOK");
        showTradeDialog(event, optionName, ActionType.BUY, isOrderBook);
        updateBalance(activeUsername);
    }

    private void handleSellAction(EventSummaryDTO event, String optionName) {
        showTradeDialog(event, optionName, ActionType.SELL, true);
        updateBalance(activeUsername);
    }

    private void showTradeDialog(EventSummaryDTO event, String optionName, ActionType action, boolean isOrderBook) {
        if (this.activeUsername == null) {
            showAlert(Alert.AlertType.WARNING, "No User Selected", "Please go to the Users tab and select a user to trade.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(action.name() + " Shares - " + optionName);
        dialog.setHeaderText("Event: " + event.name() + "\nOption: " + optionName);

        // Set the button types
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField amountField = new TextField();
        amountField.setPromptText("e.g. 10");
        grid.add(new Label("Amount of shares:"), 0, 0);
        grid.add(amountField, 1, 0);

        TextField priceField = new TextField();
        priceField.setPromptText("e.g. 0.50");

        // Only show the price limit field if it's an Order Book event
        if (isOrderBook) {
            grid.add(new Label("Price limit ($):"), 0, 1);
            grid.add(priceField, 1, 1);
        }

        dialog.getDialogPane().setContent(grid);

        // Convert the result when OK is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    int amount = Integer.parseInt(amountField.getText().trim());
                    if (amount <= 0) {
                        showAlert(Alert.AlertType.ERROR, "Invalid Input", "Amount must be greater than 0.");
                        return null;
                    }

                    double priceLimit = 0.0;
                    if (isOrderBook) {
                        priceLimit = Double.parseDouble(priceField.getText().trim());
                        if (priceLimit <= 0) {
                            showAlert(Alert.AlertType.ERROR, "Invalid Input", "Price limit must be greater than 0.");
                            return null;
                        }
                    }

                    // Send the trade to the engine
                    TradeReceiptDTO receipt = engine.placeOrder(activeUsername ,event.id(), optionName, amount, action, priceLimit);

                    if (receipt.isSuccess()) {
                        String msg = String.format("Trade successful!\nTotal Paid: $%.2f\nCommission: $%.2f",
                                receipt.totalCost(), receipt.commission());
                        showAlert(Alert.AlertType.INFORMATION, "Success", msg);

                        // Immediately re-trigger the click event to refresh the right panel with new prices!
                        handleTileClick(event);
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Trade Failed", receipt.message());
                    }

                } catch (NumberFormatException e) {
                    showAlert(Alert.AlertType.ERROR, "Invalid Input", "Please enter valid numeric values.");
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void updatePriceGraph(EventDetailsDTO details) {
        priceHistoryChart.getData().clear();

        for (OptionDetailsDTO option : details.options()) {
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(option.optionName());

            int tradeCounter = 1;
            List<TradeRecordDTO> history = details.tradeHistory();

            // Loop backwards because history is newest-to-oldest, but graphs read left-to-right!
            for (int i = history.size() - 1; i >= 0; i--) {
                TradeRecordDTO trade = history.get(i);

                if (trade.optionName().equals(option.optionName())) {
                    // Calculate unit price so it fits on the 0.0 to 1.0 Y-axis
                    double unitPrice = trade.amount() > 0 ? trade.pricePaid() / trade.amount() : 0.0;
                    series.getData().add(new XYChart.Data<>(tradeCounter, unitPrice));
                    tradeCounter++;
                }
            }

            // Optional: Plot the CURRENT price as the final point on the graph
            XYChart.Data<Number, Number> lastPoint = new XYChart.Data<>(tradeCounter, option.currentPrice());
            series.getData().add(lastPoint);

            priceHistoryChart.getData().add(series);

            // Tag only the last point as the glowing "current price" dot (see
            // .poly-chart .endpoint-marker in default-theme.css). All other
            // per-point symbols stay invisible so the line itself reads as clean.
            if (lastPoint.getNode() != null) {
                lastPoint.getNode().getStyleClass().add("endpoint-marker");
            }
        }
    }

    private void updateBalance(String activeUsername) {
        UserDetailsDTO details = engine.getUserDetails(activeUsername);
        userBalanceLabelEvents.setText(String.format("$%.2f", details.balance()));
    }

    private void loadUserDetails(UserSummaryDTO user)
    {
        UserDetailsDTO details = engine.getUserDetails(user.name());
        userNameLabel.setText(details.name());
        userBalanceLabel.setText(String.format("$%.2f", details.balance()));
        portfolioTableView.getItems().clear();
        userEventOptionsBox.getChildren().clear();
        userEventTitleLabel.setText("Select an event from the portfolio to view it's current state");

        userNameLabelEvents.setText(details.name());
        userBalanceLabelEvents.setText(String.format("$%.2f", details.balance()));

        portfolioTableView.getItems().addAll(details.portfolio());
        activeUsername = details.name();
    }

    private void refreshUsersTab() {
        List<UserSummaryDTO> updatedUsers = engine.getAllUsers();
        usersTableView.getItems().setAll(updatedUsers);
        if(this.activeUsername != null) {
            for (UserSummaryDTO user : updatedUsers) {
                if (user.name().equals(this.activeUsername)) {
                    // reselect the user in the table
                    usersTableView.getSelectionModel().select(user);
                    loadUserDetails(user);
                    break;
                }
            }
        }
    }

}