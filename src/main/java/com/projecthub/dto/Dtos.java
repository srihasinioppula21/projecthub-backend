package com.projecthub.dto;

import com.projecthub.model.ProjectStatus;
import com.projecthub.model.Role;
import com.projecthub.model.TaskStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class Dtos {

    // ---- Auth
    public record RegisterRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, message = "Password must be at least 6 characters") String password,
            @NotNull Role role) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record UserDto(Long id, String name, String email, Role role) {}

    public record AuthResponse(String token, UserDto user) {}

    // ---- Projects
    public record ProjectRequest(
            @NotBlank String title,
            String description,
            String technologies) {}

    public record ProjectDto(
            Long id, String title, String description, String technologies,
            ProjectStatus status, UserDto owner, List<UserDto> members,
            int progress, long taskCount, Instant createdAt) {}

    public record MemberRequest(@NotBlank @Email String email) {}

    public record ReviewRequest(@NotNull ProjectStatus decision, String comment) {}

    // ---- Tasks
    public record TaskRequest(
            @NotBlank String title,
            String description,
            TaskStatus status,
            Long assigneeId,
            LocalDate dueDate) {}

    public record TaskDto(
            Long id, String title, String description, TaskStatus status,
            Long assigneeId, String assigneeName, LocalDate dueDate) {}

    // ---- Feedback
    public record FeedbackRequest(@NotBlank String comment) {}

    public record FeedbackDto(Long id, String comment, UserDto author, Instant createdAt) {}

    // ---- AI
    public record AiRequest(@NotBlank String requirements) {}

    public record Idea(String title, String description) {}

    public record AiSuggestion(List<Idea> ideas, List<String> technologies,
                               List<String> tasks, String source) {}
}
