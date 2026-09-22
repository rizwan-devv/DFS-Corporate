package com.dfs.corporate.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Weekly refresh of official AML lists (OFAC SDN + UN XML). Disabled with aml.import.schedule-enabled=false.
 */
@Component
@ConditionalOnProperty(name = "aml.import.schedule-enabled", havingValue = "true", matchIfMissing = true)
public class AmlWatchlistImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(AmlWatchlistImportScheduler.class);

    private final AmlWatchlistImportService importService;

    public AmlWatchlistImportScheduler(AmlWatchlistImportService importService) {
        this.importService = importService;
    }

    /** Default: Sunday 02:00 server time. Override with aml.import.cron */
    @Scheduled(cron = "${aml.import.cron:0 0 2 * * SUN}")
    public void weeklyRefresh() {
        log.info("Starting scheduled AML watchlist refresh");
        try {
            importService.refreshOfficialLists();
        } catch (Exception e) {
            log.error("Scheduled AML watchlist refresh failed: {}", e.getMessage(), e);
        }
    }
}
