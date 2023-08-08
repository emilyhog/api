package org.example1;

import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;


public class CrptApi {
    private final int requestLimit;
    private final TimeUnit timeUnit;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
    }
    public void startScheduler() {
        scheduler.scheduleAtFixedRate(this::resetRequestCount, 0, 1, timeUnit);
    }
    private void resetRequestCount() {
        requestCount.set(0);
        System.out.println("Request count reset.");
    }

    public void createDocumentAndSign(Document document, String signature) {
        if (requestCount.get() >= requestLimit) {
            System.out.println("Request limit reached. Waiting for the next interval.");
            return;
        }

        executorService.submit(() -> {
            try {
                signDocument(document, signature);
                sendRequestToAPI(document, signature); // Make a request to the mock API
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                synchronized (CrptApi.this) {
                    incrementRequestCount();
                }
            }
        });
    }

    private void signDocument(Document document, String signature) {
        System.out.println("Document signed: " + document.getValue() + " Signature: " + signature);
    }
    private synchronized void incrementRequestCount() {
        requestCount.incrementAndGet();
    }
    private void sendRequestToAPI(Document document, String signature) {
        System.out.println("Sending request to mock API...");
        String mockApiUrl = "https://example.com/mock-api";
        Gson gson = new Gson();
        String jsonData = gson.toJson(new MockRequest(document.getValue(), signature));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockApiUrl))
                .POST(HttpRequest.BodyPublishers.ofString(jsonData))
                .header("Content-Type", "application/json")
                .build();

        CompletableFuture<Void> future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> {
                    synchronized (CrptApi.this) {
                        incrementRequestCount();
                    }
                    System.out.println("Response received from mock API: " + response.statusCode());
                    return null;
                });

        future.exceptionally(ex -> {
            System.out.println("Error while sending request to mock API: " + ex.getMessage());
            return null;
        });
    }

    private static class Document {
        private String value;

        public Document(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public void shutdown() {
        executorService.shutdown();
        scheduler.shutdown();
    }

    private static class MockRequest {
        private String documentValue;
        private String signature;

        public MockRequest(String documentValue, String signature) {
            this.documentValue = documentValue;
            this.signature = signature;
        }
    }

    public static void main(String[] args) {
        int requestLimit = 10;
        TimeUnit timeUnit = TimeUnit.SECONDS;
        CrptApi crptApi = new CrptApi(timeUnit, requestLimit);
        crptApi.startScheduler();
        for (int i = 0; i < 100; i++) {
            Document document = new Document(UUID.randomUUID().toString());
            String signature = "+" + i;
            crptApi.createDocumentAndSign(document, signature);
        }
        crptApi.shutdown();
    }
}