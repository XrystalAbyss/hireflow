package com.hireflow.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Single-origin SPA Forward Controller.
 * Forwards non-API client-side routes (such as /auth/**, /student/**, /officer/**)
 * to /index.html so React Router can perform client-side routing on page refresh
 * and direct deep links without throwing 404s.
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {
        "/",
        "/auth/**",
        "/student/**",
        "/officer/**",
        "/admin/**",
        "/workshop/**"
    })
    public String forwardSpa() {
        return "forward:/index.html";
    }
}
