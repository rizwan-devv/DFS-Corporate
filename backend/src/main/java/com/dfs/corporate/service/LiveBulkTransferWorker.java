package com.dfs.corporate.service;

import com.dfs.corporate.security.AccountPrincipal;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Separate bean so {@code @Async} works (self-invocation on same class would be sync).
 */
@Service
public class LiveBulkTransferWorker {

    private final LiveBulkTransferService bulkTransferService;

    public LiveBulkTransferWorker(LiveBulkTransferService bulkTransferService) {
        this.bulkTransferService = bulkTransferService;
    }

    @Async
    public void run(Long batchId, AccountPrincipal principal) {
        bulkTransferService.processBatch(batchId, principal);
    }
}
