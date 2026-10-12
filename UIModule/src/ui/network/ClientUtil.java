package ui.network;

import dto.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;

public class ClientUtil {

    // server + context path; the context path is the WAR's name (finalName in web/pom.xml)
    public static final String BASE_URL = "http://localhost:8080/guess-market";
    private final HttpClient client;
    private final Gson gson;

    public ClientUtil() {
        this.client = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    // the uploader becomes the market maker of every event in the file
    public void uploadFile(File xmlFile, String username) throws Exception
    {
        String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/FileUpload?userName=" + encodedUsername))
                .header("Content-Type", "application/xml")
                .POST(HttpRequest.BodyPublishers.ofFile(xmlFile.toPath()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String message;
            try {
                message = JsonParser.parseString(response.body()).getAsJsonObject().get("message").getAsString();
            } catch (RuntimeException notOurJson) {
                // e.g. a Tomcat error page instead of our JSON
                message = "Server error: " + response.statusCode();
            }
            throw new Exception(message);
        }
    }

    public void login(String username) throws Exception {
        String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/login?userName=" + encodedUsername))
                .GET() // Or POST, depending on how you wrote your LoginServlet
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Login failed: " + response.body());
        }
    }

    public void fetchDeltaUpdates(int currentVersion, Consumer<EventsDeltaDTO> onSuccess, Consumer<String> onError) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/events?version=" + currentVersion))
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        // המרה של ה-JSON לאובייקט
                        EventsDeltaDTO delta = gson.fromJson(response.body(), EventsDeltaDTO.class);
                        // חובה להריץ את העדכון על ה-JavaFX Application Thread
                        Platform.runLater(() -> onSuccess.accept(delta));
                    } else {
                        Platform.runLater(() -> onError.accept("Failed to fetch updates"));
                    }
                })
                .exceptionally(e -> {
                    Platform.runLater(() -> onError.accept(e.getMessage()));
                    return null;
                });
    }

    public List<EventSummaryDTO> getEventsSummary() throws Exception
    {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/events"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Type listType = new TypeToken<List<EventSummaryDTO>>(){}.getType();
            return gson.fromJson(response.body(), listType);
        } else {
            throw new Exception("Failed to fetch events: " + response.body());
        }
    }

    public EventDetailsDTO getEventDetails(String eventName) throws Exception
    {
        // Encode the event name to handle spaces (e.g., "World Cap Winner" -> "World%20Cap%20Winner")
        String encodedName = URLEncoder.encode(eventName, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/events?eventName=" + encodedName))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return gson.fromJson(response.body(), EventDetailsDTO.class);
        } else {
            throw new Exception("Failed to fetch event details: " + response.body());
        }
    }

    public List<ParticipantHoldingDTO> getEventParticipants(String eventName) throws Exception {
        // 1. Build the URL with the eventName AND the action parameter
        String url = BASE_URL + "/events?eventName=" + encode(eventName) + "&action=participants";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch event participants: server error " + response.statusCode());
        }

        Type listType = new TypeToken<List<ParticipantHoldingDTO>>(){}.getType();
        return gson.fromJson(response.body(), listType);
    }

    public TradeReceiptDTO startEvent(String username, String eventName) throws Exception {
        String url = BASE_URL + "/events";

        // 1. Format the parameters just like a web form submission
        String formData = "action=startEvent&eventName=" + encode(eventName) + "&username=" + encode(username);

        // 2. Build the POST request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded") // Tells Tomcat to expect form data
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        // 3. Send the request
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to start event: server error " + response.statusCode());
        }

        // 4. Parse the returned JSON back into the TradeReceiptDTO
        return new Gson().fromJson(response.body(), TradeReceiptDTO.class);
    }

    public CloseEventReceiptDTO closeEvent(String username, String eventName, String winningOption) throws Exception {
        String url = BASE_URL + "/events";

        // 1. Format the parameters for the POST body
        String formData = "action=closeEvent&eventName=" + encode(eventName) + "&winningOption=" + encode(winningOption)
                + "&username=" + encode(username);

        // 2. Build the POST request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        // 3. Send the request
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to close event: server error " + response.statusCode());
        }

        // 4. Parse the returned JSON back into a CloseEventReceiptDTO
        return new Gson().fromJson(response.body(), CloseEventReceiptDTO.class);
    }

    public List<UserSummaryDTO> getUsersSummary() throws Exception
    {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/users"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Type listType = new TypeToken<List<UserSummaryDTO>>(){}.getType();
            return gson.fromJson(response.body(), listType);
        } else {
            throw new Exception("Failed to fetch users: " + response.body());
        }
    }

    public UserDetailsDTO getUserDetails(String username) throws Exception
    {
        // Encode the user name to handle spaces (e.g., "John Smith" -> "John+Smith")
        String encodedName = URLEncoder.encode(username, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/users?userName=" + encodedName))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return gson.fromJson(response.body(), UserDetailsDTO.class);
        } else {
            throw new Exception("Failed to fetch user details: " + response.body());
        }
    }

    public UserEventOverviewDTO getUserEventOverview(String userName, String eventName) throws Exception
    {
        // Encode the event name to handle spaces (e.g., "World Cap Winner" -> "World%20Cap%20Winner")
        String encodedUserName = URLEncoder.encode(userName, StandardCharsets.UTF_8);
        String encodedEventName = URLEncoder.encode(eventName, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/users?userName=" + encodedUserName + "&eventName=" + encodedEventName))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return gson.fromJson(response.body(), UserEventOverviewDTO.class);
        } else {
            throw new Exception("Failed to fetch user event overview: " + response.body());
        }
    }

    public boolean isUserMarketMaker(String username, String eventName) throws Exception {
        // 1. Build the URL with both eventName and username parameters
        String url = BASE_URL + "/events?eventName=" + encode(eventName)
                + "&action=isMarketMaker&username=" + encode(username);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to check market maker: server error " + response.statusCode());
        }

        return gson.fromJson(response.body(), Boolean.class);
    }


    /**
     * A POST request matching your TradeServlet.
     * Because your Servlet uses request.getParameter()[cite: 17, 18], you must send the data 
     * as an application/x-www-form-urlencoded string (like a standard HTML form submission), NOT as a JSON body.
     */
    public TradeReceiptDTO placeOrder(String username, String eventName, String optionName, int amount, ActionType action, double limitPrice) throws Exception {
        String url = BASE_URL + "/events";

        // 1. Format all parameters into a single form data string
        String formData = "action=placeOrder" +
                "&username=" + encode(username) +
                "&eventName=" + encode(eventName) +
                "&optionName=" + encode(optionName) +
                "&amount=" + amount +
                "&actionType=" + action.name() +
                "&limitPrice=" + limitPrice;

        // 2. Build the POST request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        // 3. Send the request
        // (Assuming your HttpClient variable is named 'client')
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to place order: server error " + response.statusCode());
        }

        // 4. Parse the returned JSON back into a TradeReceiptDTO
        return new Gson().fromJson(response.body(), TradeReceiptDTO.class);
    }

    // both return the new balance, or throw with the server's message (e.g. "Insufficient funds")
    public double depositFunds(String username, double amount) throws Exception {
        return changeFunds("deposit", username, amount);
    }

    public double withdrawFunds(String username, double amount) throws Exception {
        return changeFunds("withdraw", username, amount);
    }

    private double changeFunds(String action, String username, double amount) throws Exception {
        String formData = "action=" + action +
                "&userName=" + encode(username) +
                "&amount=" + amount;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/users"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String message;
            try {
                message = JsonParser.parseString(response.body()).getAsJsonObject().get("message").getAsString();
            } catch (RuntimeException notOurJson) {
                // e.g. a Tomcat error page instead of our JSON
                message = "Server error: " + response.statusCode();
            }
            throw new Exception(message);
        }
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        return body.get("balance").getAsDouble();
    }

    // makes a value safe to put in a URL or form body (spaces, &, = ...)
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}