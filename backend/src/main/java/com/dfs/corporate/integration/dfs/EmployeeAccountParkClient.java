package com.dfs.corporate.integration.dfs;

import com.dfs.corporate.domain.EmployeeBulkRow;
import com.dfs.corporate.domain.Party;

/**
 * Parks / opens employee accounts on DFS (corporate bulkAccounts API or stub).
 */
public interface EmployeeAccountParkClient {

    /**
     * @param accountOpened true when DFS returned an account number and row can be marked OPEN
     */
    record ParkResult(
            boolean success,
            String parkRef,
            String message,
            String dfsAccountNo,
            String dfsCustomerId,
            boolean accountOpened
    ) {
        public static ParkResult parked(String parkRef, String message) {
            return new ParkResult(true, parkRef, message, null, null, false);
        }

        public static ParkResult opened(String parkRef, String message, String accountNo, String customerId) {
            return new ParkResult(true, parkRef, message, accountNo, customerId, true);
        }

        public static ParkResult failed(String message) {
            return new ParkResult(false, null, message, null, null, false);
        }
    }

    ParkResult park(Party party, EmployeeBulkRow row);
}
