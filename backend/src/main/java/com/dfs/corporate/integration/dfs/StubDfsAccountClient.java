package com.dfs.corporate.integration.dfs;

import com.dfs.corporate.domain.Party;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Only active if explicitly selected; default client is {@link CorporateOnboardingHttpClient}.
 */
@Component
@ConditionalOnProperty(name = "dfs.account-api.use-stub", havingValue = "true")
public class StubDfsAccountClient implements DfsAccountClient {

    private static final Logger log = LoggerFactory.getLogger(StubDfsAccountClient.class);

    @Override
    public DfsAccountCreateResult createAccount(Party party) {
        log.info("Stub DFS client — party {}", party.getTrackingId());
        return DfsAccountCreateResult.deferred("Stub client (dfs.account-api.use-stub=true)");
    }
}
