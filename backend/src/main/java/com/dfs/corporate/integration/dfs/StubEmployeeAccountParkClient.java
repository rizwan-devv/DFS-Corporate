package com.dfs.corporate.integration.dfs;

import com.dfs.corporate.domain.EmployeeBulkRow;
import com.dfs.corporate.domain.Party;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "dfs.employee-onboard.use-stub", havingValue = "true", matchIfMissing = false)
public class StubEmployeeAccountParkClient implements EmployeeAccountParkClient {

    private static final Logger log = LoggerFactory.getLogger(StubEmployeeAccountParkClient.class);

    @Override
    public ParkResult park(Party party, EmployeeBulkRow row) {
        String ref = "PARK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Stub park employee partyId={} row={} mobile={} parkRef={}",
                party.getId(), row.getPublicId(), row.getMobile(), ref);
        return ParkResult.parked(ref, "Parked (stub) — await DFS account confirmation");
    }
}
