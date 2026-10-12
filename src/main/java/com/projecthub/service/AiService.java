package com.projecthub.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projecthub.dto.Dtos.AiSuggestion;
import com.projecthub.dto.Dtos.Idea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Suggests project ideas, technologies and tasks from a student's requirements.
 * Uses the Claude API when ANTHROPIC_API_KEY is configured; otherwise (or if the call fails)
 * falls back to built-in keyword-based suggestions so the feature always works.
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${app.ai.api-key:}")
    private String apiKey;

    @Value("${app.ai.model}")
    private String model;

    public AiSuggestion suggest(String requirements) {
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                return callClaude(requirements);
            } catch (Exception e) {
                log.warn("AI API call failed, using offline suggestions: {}", e.getMessage());
            }
        }
        return offline(requirements);
    }

    // ------------------------------------------------------------ Claude API

    private AiSuggestion callClaude(String requirements) throws Exception {
        String prompt = """
                You help college students plan academic projects.
                Student requirements: "%s"

                Reply with ONLY valid JSON (no markdown) in exactly this shape:
                {"ideas":[{"title":"...","description":"one or two sentences"}],
                 "technologies":["..."],
                 "tasks":["..."]}
                Give 3 ideas, 6-8 suitable technologies, and 8-10 actionable tasks that follow the
                software development life cycle (planning, requirements, design, development, testing,
                deployment, maintenance).""".formatted(requirements.replace("\"", "'"));

        String body = mapper.writeValueAsString(Map.of(
                "model", model,
                "max_tokens", 1500,
                "messages", List.of(Map.of("role", "user", "content", prompt))));

        HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                .timeout(Duration.ofSeconds(60))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }

        String text = mapper.readTree(response.body()).path("content").path(0).path("text").asText();
        text = text.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalStateException("No JSON in model reply");
        }
        JsonNode json = mapper.readTree(text.substring(start, end + 1));

        List<Idea> ideas = new ArrayList<>();
        for (JsonNode n : json.path("ideas")) {
            ideas.add(new Idea(n.path("title").asText(), n.path("description").asText()));
        }
        List<String> techs = strings(json.path("technologies"));
        List<String> tasks = strings(json.path("tasks"));
        if (ideas.isEmpty() && techs.isEmpty() && tasks.isEmpty()) {
            throw new IllegalStateException("Empty suggestion");
        }
        return new AiSuggestion(ideas, techs, tasks, "ai");
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    // ------------------------------------------------------------ offline fallback

    private AiSuggestion offline(String requirements) {
        String r = requirements.toLowerCase(Locale.ROOT);
        List<Idea> ideas = new ArrayList<>();
        List<String> techs = new ArrayList<>();

        if (has(r, "health", "medical", "hospital", "fitness")) {
            ideas.add(new Idea("Patient Appointment & Records Portal", "Book appointments and keep digital health records with role-based access."));
            ideas.add(new Idea("Fitness Habit Tracker", "Track workouts, meals and goals with weekly progress charts."));
            techs.addAll(List.of("Spring Boot", "React.js", "MySQL", "Chart.js"));
        } else if (has(r, "ai", "ml", "machine learning", "chatbot", "nlp", "predict")) {
            ideas.add(new Idea("Smart Study Assistant Chatbot", "A chatbot that answers course questions from uploaded notes."));
            ideas.add(new Idea("Student Performance Predictor", "Predict at-risk students from attendance and marks."));
            techs.addAll(List.of("Python", "scikit-learn", "Spring Boot", "React.js", "REST APIs"));
        } else if (has(r, "iot", "sensor", "arduino", "raspberry", "smart")) {
            ideas.add(new Idea("Smart Campus Monitoring", "Collect sensor data and show live dashboards for rooms and energy use."));
            ideas.add(new Idea("Smart Attendance System", "Automate attendance using RFID or QR scans."));
            techs.addAll(List.of("Arduino/ESP32", "MQTT", "Spring Boot", "React.js", "MySQL"));
        } else if (has(r, "education", "learn", "college", "student", "course", "exam")) {
            ideas.add(new Idea("Online Exam & Quiz Platform", "Create timed quizzes with auto-grading and result analytics."));
            ideas.add(new Idea("Campus Resource Sharing Hub", "Students share notes, books and lab equipment with request tracking."));
            techs.addAll(List.of("Spring Boot", "React.js", "MySQL", "JWT Authentication"));
        } else if (has(r, "mobile", "android", "ios", "flutter")) {
            ideas.add(new Idea("Campus Events App", "Discover, register for and get reminders about college events."));
            ideas.add(new Idea("Expense Splitter for Hostel Mates", "Track shared expenses and settle balances."));
            techs.addAll(List.of("React Native", "Spring Boot", "MySQL", "Firebase Cloud Messaging"));
        } else {
            ideas.add(new Idea("Smart Task & Team Planner", "Plan work, assign tasks and visualise progress for a small team."));
            ideas.add(new Idea("Community Issue Reporting Portal", "Citizens report local issues with photos and track resolution status."));
            ideas.add(new Idea("Library Management System", "Catalogue, borrow and return books with fine calculation."));
            techs.addAll(List.of("Spring Boot", "React.js", "MySQL", "Spring Security + JWT", "Git & GitHub"));
        }
        for (String t : List.of("Postman (API testing)", "JUnit & Mockito")) {
            if (!techs.contains(t)) techs.add(t);
        }

        List<String> tasks = List.of(
                "Define the problem statement, scope and team roles",
                "Collect and document functional and non-functional requirements",
                "Design the database schema (ER diagram) and REST API contract",
                "Create wireframes for the main screens",
                "Set up the Spring Boot backend and the React frontend repositories",
                "Implement authentication and role-based access",
                "Build the core features end to end (API + UI)",
                "Write unit and integration tests and fix defects",
                "Deploy the application and prepare the demo",
                "Write the project report and plan maintenance");
        return new AiSuggestion(ideas, techs, tasks, "offline");
    }

    private static boolean has(String text, String... keywords) {
        for (String k : keywords) {
            // short keywords like "ai"/"ml" must match whole words only
            boolean found = k.length() <= 3
                    ? Pattern.compile("\\b" + k + "\\b").matcher(text).find()
                    : text.contains(k);
            if (found) return true;
        }
        return false;
    }
}
