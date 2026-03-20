package com.fairshare.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public String getBotResponse(String userQuery, Map<String, Object> contextData) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "SplitBot is not configured. Please set the GEMINI_API_KEY environment variable.";
        }

        try {
            Map<String, Object> balances = (Map<String, Object>) contextData.get("balances");
            List<Map<String, Object>> recentExpenses = (List<Map<String, Object>>) contextData.get("recent_expenses");
            Object settlements = contextData.get("settlements");

            String systemPrompt = String.format(
                "You are 'SplitBot', a helpful and concise financial assistant for FairShare.\n" +
                "Your job is to answer questions about the group's expenses and debts.\n\n" +
                "Context provided:\n" +
                "- Members & Balances (netDebt values): %s\n" +
                "- Recent Expenses: %s\n" +
                "- Smart Settlements (who needs to pay whom): %s\n\n" +
                "Knowledge about Smart Settlement:\n" +
                "- Uses Greedy Transaction Minimization Algorithm\n" +
                "- Complexity: O(n log n)\n" +
                "- Goal: Minimize total transactions needed\n" +
                "- Example: Instead of A→B→C, suggests A→C directly\n\n" +
                "Rules:\n" +
                "1. Be concise and friendly\n" +
                "2. Use emojis\n" +
                "3. IMPORTANT: Positive netDebt = user OWES money (is a debtor). Negative netDebt = user is OWED money (is a creditor). Do NOT confuse these.\n" +
                "4. All amounts are stored in PAISE (1/100 of a rupee). You MUST divide by 100 to get rupees. For example, 83333 = ₹833.33, 250000 = ₹2,500.00\n" +
                "5. Always use usernames as provided\n" +
                "6. Smart Settlements show the optimised payments: 'fromUser' should pay 'toUser' the given 'amount'\n" +
                "7. If answer not in context, say so politely",
                objectMapper.writeValueAsString(balances),
                objectMapper.writeValueAsString(recentExpenses),
                objectMapper.writeValueAsString(settlements)
            );

            String url = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                model, apiKey
            );

            Map<String, Object> requestBody = Map.of(
                "system_instruction", Map.of(
                    "parts", List.of(Map.of("text", systemPrompt))
                ),
                "contents", List.of(
                    Map.of("parts", List.of(Map.of("text", userQuery)))
                )
            );

            String body = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());

            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                return candidates.get(0).path("content").path("parts").get(0).path("text").asText();
            }

            return "I couldn't generate a response. Please try again.";
        } catch (Exception e) {
            return "Oops! I encountered a technical glitch while thinking: " + e.getMessage();
        }
    }
}
