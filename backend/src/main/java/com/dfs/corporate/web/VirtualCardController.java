package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.VirtualCardService;
import com.dfs.corporate.web.dto.VirtualCardOrderRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cards/virtual")
public class VirtualCardController {

    private final VirtualCardService virtualCardService;

    public VirtualCardController(VirtualCardService virtualCardService) {
        this.virtualCardService = virtualCardService;
    }

    @GetMapping
    public Map<String, Object> mine(@AuthenticationPrincipal AccountPrincipal principal) {
        return virtualCardService.mine(principal);
    }

    @PostMapping("/order")
    public Map<String, Object> order(@AuthenticationPrincipal AccountPrincipal principal,
                                     @Valid @RequestBody VirtualCardOrderRequest req) {
        return virtualCardService.order(principal, req.getEmbossName());
    }
}
