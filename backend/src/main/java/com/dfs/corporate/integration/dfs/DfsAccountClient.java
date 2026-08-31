package com.dfs.corporate.integration.dfs;

import com.dfs.corporate.domain.Party;

/**
 * External DFS Account / core-banking API.
 * Replace {@link StubDfsAccountClient} with a real HTTP implementation when credentials are ready.
 */
public interface DfsAccountClient {

    /**
     * @return result with external account id on success
     */
    DfsAccountCreateResult createAccount(Party party);
}
