package com.projecthub.controller;

import com.projecthub.dto.Dtos.AiRequest;
import com.projecthub.dto.Dtos.AiSuggestion;
import com.projecthub.service.AiService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService ai;

    public AiController(AiService ai) {
        this.ai = ai;
    }

    @PostMapping("/suggest")
    public AiSuggestion suggest(@Valid @RequestBody AiRequest req) {
        return ai.suggest(req.requirements());
    }
}
