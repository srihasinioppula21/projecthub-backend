package com.projecthub.controller;

import com.projecthub.dto.Dtos.*;
import com.projecthub.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    // ---- projects
    @GetMapping("/projects")
    public List<ProjectDto> list() {
        return service.list();
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDto create(@Valid @RequestBody ProjectRequest req) {
        return service.create(req);
    }

    @GetMapping("/projects/{id}")
    public ProjectDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/projects/{id}")
    public ProjectDto update(@PathVariable Long id, @Valid @RequestBody ProjectRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/projects/{id}/members")
    public ProjectDto addMember(@PathVariable Long id, @Valid @RequestBody MemberRequest req) {
        return service.addMember(id, req);
    }

    @PostMapping("/projects/{id}/submit")
    public ProjectDto submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @PostMapping("/projects/{id}/review")
    public ProjectDto review(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        return service.review(id, req);
    }

    // ---- tasks
    @GetMapping("/projects/{id}/tasks")
    public List<TaskDto> tasks(@PathVariable Long id) {
        return service.listTasks(id);
    }

    @PostMapping("/projects/{id}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDto createTask(@PathVariable Long id, @Valid @RequestBody TaskRequest req) {
        return service.createTask(id, req);
    }

    @PutMapping("/tasks/{taskId}")
    public TaskDto updateTask(@PathVariable Long taskId, @Valid @RequestBody TaskRequest req) {
        return service.updateTask(taskId, req);
    }

    @DeleteMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable Long taskId) {
        service.deleteTask(taskId);
    }

    // ---- feedback
    @GetMapping("/projects/{id}/feedback")
    public List<FeedbackDto> feedback(@PathVariable Long id) {
        return service.listFeedback(id);
    }

    @PostMapping("/projects/{id}/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackDto addFeedback(@PathVariable Long id, @Valid @RequestBody FeedbackRequest req) {
        return service.addFeedback(id, req);
    }
}
