package com.dfs.corporate.web;

import com.dfs.corporate.service.CustomerAcknowledgmentService;
import com.dfs.corporate.web.dto.AcknowledgmentPublicResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/acknowledgment")
public class AcknowledgmentPublicController {

    private final CustomerAcknowledgmentService acknowledgmentService;

    public AcknowledgmentPublicController(CustomerAcknowledgmentService acknowledgmentService) {
        this.acknowledgmentService = acknowledgmentService;
    }

    @GetMapping("/{token}")
    public AcknowledgmentPublicResponse get(@PathVariable String token) {
        return acknowledgmentService.getPublic(token);
    }

    @PostMapping("/{token}/confirm")
    public AcknowledgmentPublicResponse confirm(@PathVariable String token, HttpServletRequest request) {
        return acknowledgmentService.confirm(token, clientIp(request));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
