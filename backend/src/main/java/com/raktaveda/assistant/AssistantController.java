package com.raktaveda.assistant;

import com.raktaveda.assistant.AssistantDtos.AnswerResponse;
import com.raktaveda.assistant.AssistantDtos.QuestionRequest;
import com.raktaveda.security.AuthPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** General-question helper. Logged-in users and institutions only (admins are not allowed). */
@RestController
@RequestMapping("/api/v1/assistant")
@PreAuthorize("hasAnyRole('USER','INSTITUTION')")
public class AssistantController {

    private final AssistantService service;

    public AssistantController(AssistantService service) {
        this.service = service;
    }

    @PostMapping("/ask")
    public AnswerResponse ask(@AuthenticationPrincipal AuthPrincipal principal, @RequestBody QuestionRequest body) {
        return service.ask(principal, body.question());
    }
}
