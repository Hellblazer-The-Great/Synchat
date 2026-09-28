package com.synchat.service;

import org.json.JSONArray;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * NETWORKING & DATA PARSING
 * Performs a real HTTP GET request over the internet to a public JSON API
 * and parses the response with org.json. Runs on its own background
 * executor so the JavaFX Application Thread is never blocked; the parsed
 * result is handed back through a callback which the caller marshals onto
 * the UI thread with Platform.runLater.
 */
public class QuoteService {
    private static final String QUOTE_API = "https://zenquotes.io/api/today";
    private final HttpClient client = HttpClient.newHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void fetchDailyQuote(Consumer<String> onResult) {
        CompletableFuture.supplyAsync(this::fetchAndParse, executor).thenAccept(onResult);
    }

    private String fetchAndParse() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(QUOTE_API)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // DATA PARSING: turn the raw JSON text body into structured objects.
            JSONArray arr = new JSONArray(response.body());
            String quote = arr.getJSONObject(0).getString("q");
            String author = arr.getJSONObject(0).getString("a");
            return "\"" + quote + "\" \u2014 " + author;
        } catch (Exception e) {
            return "Could not load today's quote (check internet connection).";
        }
    }
}
