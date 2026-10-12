package com.projecthub.controller;

import com.projecthub.dto.Dtos.UserDto;
import com.projecthub.service.ProjectService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final ProjectService service;

    public UserController(ProjectService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public UserDto me() {
        return ProjectService.toDto(service.currentUser());
    }
}
