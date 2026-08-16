package ui;

import engine.Engine;
import engine.EngineInterface;
import engine.dto.EventDetailsDTO;
import engine.dto.EventSummaryDTO;
import engine.dto.TradeReceiptDTO;
import internals.ActionType;

import java.util.List;
import java.util.Scanner;

public class ConsoleUI {

    private EngineInterface engine;
    private boolean isRunning;
    private Scanner scanner;

    private static List<String> MENU_OPTIONS = List.of(
            "Load System Details (XML)",
            "Display All Events",
            "Event Trading Status",
            "Participate in Event",
            "Close Event",
            "Exit"
    );

    public ConsoleUI() {
        this.engine = new Engine();
        this.isRunning = true;
        this.scanner = new Scanner(System.in);
    }

    public void start() {
        System.out.println("Welcome to Guess Market!");

        while (isRunning) {
            printMenu();
            int choice = getUserInput();
            processInput(choice);
        }

        System.out.println("Goodbye!");
        scanner.close();
    }

    private void printMenu()
    {
        System.out.println("###### Main Menu ######");
        for (int i=0;i<MENU_OPTIONS.size();i++)
        {
            System.out.println((i+1) + "). " + MENU_OPTIONS.get(i));
        }
        System.out.print("Please enter your choice by typing a number (1-" + MENU_OPTIONS.size() + "): ");
    }

    private int getUserInput() {
        try {
            String input = scanner.nextLine();
            int numInput = Integer.parseInt(input);
            // 1 <= input <= MENU.OPTIONS.size()
            if (numInput > MENU_OPTIONS.size() || numInput < 1) return -1;
            return numInput;
        }
        catch (NumberFormatException exception)
        {
            return -1; // invalid option
        }
    }

    private void processInput(int choice)
    {
        switch (choice) {
            case 1 -> handleLoadData();
            case 2 -> handleDisplayEvents();
            case 3 -> handleEventStatus();
            case 4 -> handleParticipate();
            case 5 -> handleCloseEvent();
            case 6 -> this.isRunning = false;
            default -> System.out.println("Invalid input, please enter a number between 1 and "+MENU_OPTIONS.size());
        }
    }



    /**
     *  ################ HANDLERS ################
     */

    private void handleLoadData() {
        System.out.println("Enter full path for the xml file: ");
        String filePath = scanner.nextLine().trim();

        try {
            engine.loadDataFromXML(filePath);
            System.out.println("File successfully loaded!");
        }
        catch (Exception e)
        {
            System.out.println("Failed to load the file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleDisplayEvents() {
        List<EventSummaryDTO> allEvents = engine.getAllEvents();
        if (allEvents.isEmpty()) {
            System.out.println("No events are currently loaded");
            return;
        }
        System.out.println();
        for (int i=0;i<allEvents.size(); i++)
        {
            EventSummaryDTO event = allEvents.get(i);
            System.out.printf("%d) Id: %d | Name: %s%n   Description: %s%n   Commission: %d%% (%s)%n   Options: %s%n   Status: %s%n%n",
                    i+1,
                    event.id(),
                    event.name(),
                    event.description(),
                    event.commission(),
                    event.commissionType(),
                    String.join(", ", event.options()),
                    event.status());
        }
        System.out.println();
    }

    private void handleEventStatus() {
        List<EventSummaryDTO> allEvents = engine.getAllEvents();
        if (allEvents.isEmpty()) {
            System.out.println("No events are currently loaded");
            return;
        }
        handleDisplayEvents();
        System.out.print("Enter the number (not the id) of the event from the list above (1-" + allEvents.size() + "): ");
        String choice = scanner.nextLine().trim();

        try {
            int index = Integer.parseInt(choice);
            if (index < 1 || index > allEvents.size()) {
                System.out.println("Invalid input, enter the number (not the id) of the event from the list (1-" + allEvents.size() + "): ");
                return;
            }
            int eventId = allEvents.get(index - 1).id();
            EventDetailsDTO details = engine.getEventState(eventId);
            System.out.println("\n##### Event Details: " + details.name() + " #####");
            System.out.println("Status: " + details.status());
            System.out.printf("Event Pool Value: %.2f%n", details.poolValue());
            System.out.printf("Total Commission Collected: %.2f%n", details.totalCommission());
            System.out.println("\nOptions Current State:");
            for (int i=0;i<details.options().size(); i++)
            {
                var option = details.options().get(i);
                System.out.printf("  %d) %s - Current Value: %.2f%n", (i + 1), option.optionName(), option.currentPrice());
            }

            System.out.println("\nTrade History:");
            if (details.tradeHistory().isEmpty()) {
                System.out.println("  No trades have occurred yet.");
            }
            else {
                for (int i = details.tradeHistory().size() - 1; i >= 0; i--) {
                    var trade = details.tradeHistory().get(i);
                    // Fixed method name:
                    System.out.printf("  - Bought %d shares of %s | Total Paid: %.2f%n",
                            trade.amount(), trade.optionName(), trade.pricePaid());
                }
            }

            if ("CLOSED".equals(details.status())) {
                System.out.println("\n*** Event is CLOSED ***");
                System.out.println("Winning Option: " + details.winningOption());
            }
            System.out.println();

        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a valid number.");
        } catch (Exception e) {
            System.out.println("An unexpected error occurred: " + e.getMessage());
        }
    }

    private void handleParticipate() {
        // fetch active events
        List<EventSummaryDTO> activeEvents = engine.getActiveEvents();
        if(activeEvents.isEmpty()) {
            System.out.println("There are no active events right now");
            return;
        }
        System.out.println("###### Active Events ######");
        for (int i=0;i<activeEvents.size();i++) {
            EventSummaryDTO event = activeEvents.get(i);
            System.out.printf("%d) Id: %d | Name: %s%n", i + 1, event.id(), event.name());
        }
        try {
            // select event
            System.out.print("\nEnter the number of the event to purchase (1-"+activeEvents.size()+"): ");
            int index = Integer.parseInt(scanner.nextLine().trim());
            if (index < 1 || index > activeEvents.size()) {
                System.out.println("Invalid input. Please enter a valid number.");
                return;
            }
            int eventId = activeEvents.get(index - 1).id();

            // show current state of the chosen event
            EventDetailsDTO details = engine.getEventState(eventId);
            System.out.println("\n--- Current State of: " + details.name() + " ---");
            for (int i = 0; i < details.options().size(); i++) {
                var opt = details.options().get(i);
                System.out.printf("  %d) %s - Current Value: %.2f | Shares Bought: %d%n",
                        (i + 1), opt.optionName(), opt.currentPrice(), opt.sharesBought());
            }

            // select option
            System.out.print("Enter the number of the option that you would like to back (1-"+details.options().size()+"):");
            int optionIndex = Integer.parseInt(scanner.nextLine().trim());
            if(optionIndex < 1 || optionIndex > details.options().size()) {
                System.out.println("Invalid input. Please enter a valid number.");
                return;
            }
            String chosenOption = details.options().get(optionIndex - 1).optionName();

            // select amount
            System.out.print("Enter the amount of shares you want to buy: ");
            int amount = Integer.parseInt(scanner.nextLine().trim());
            if (amount <= 0) {
                System.out.println("Amount must be greater than 0.");
                return;
            }

            // execute trade
            TradeReceiptDTO receipt = engine.placeOrder(eventId, chosenOption, amount, ActionType.BUY, 0.0);

            if (receipt.isSuccess()) {
                System.out.println("\n*** Trade Successful! ***");
                double sharesCost = receipt.totalCost() - receipt.commission();
                System.out.printf("Total Paid: %.2f%n", receipt.totalCost());
                System.out.printf("  - Shares Cost: %.2f%n", sharesCost);
                System.out.printf("  - Commission: %.2f%n", receipt.commission());

                // fetch the updated state to show the user the updated market
                EventDetailsDTO updatedDetails = engine.getEventState(eventId);
                System.out.println("\n--- Updated Event State ---");
                System.out.printf("Event Pool Value: %.2f%n", updatedDetails.poolValue());
                for (int i = 0; i < updatedDetails.options().size(); i++) {
                    var opt = updatedDetails.options().get(i);
                    System.out.printf("  %s - New Value: %.2f | Total Shares: %d%n",
                            opt.optionName(), opt.currentPrice(), opt.sharesBought());
                }
            }
            else {
                System.out.println("\nTrade Failed: " + receipt.message());
            }
        }
        catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a valid number.");
        } catch (Exception e) {
            System.out.println("unexpected error occurred: " + e.getMessage());
        }
    }

    private void handleCloseEvent() {
        //TODO
    }

}
