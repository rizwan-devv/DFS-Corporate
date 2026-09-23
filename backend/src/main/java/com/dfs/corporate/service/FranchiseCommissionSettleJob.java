package com.dfs.corporate.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FranchiseCommissionSettleJob {

    private static final Logger log = LoggerFactory.getLogger(FranchiseCommissionSettleJob.class);

    private final FranchiseCommissionSettlementService settlementService;
    private final boolean enabled;

    public FranchiseCommissionSettleJob(
            FranchiseCommissionSettlementService settlementService,
            @Value("${app.commission.settle-enabled:true}") boolean enabled) {
        this.settlementService = settlementService;
        this.enabled = enabled;
    }

    @Scheduled(initialDelay = 60_000, fixedDelayString = "${app.commission.settle-interval-ms:300000}")
    public void run() {
        if (!enabled) return;
        try {
            var r = settlementService.settleAllLocked();
            if (r.getCreated() > 0 || r.getPosted() > 0 || r.getFailed() > 0) {
                log.info("Franchise commission settle: {}", r.getMessage());
            }
        } catch (Exception ex) {
            log.warn("Franchise commission settle job failed: {}", ex.getMessage());
        }
    }
}
