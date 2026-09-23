package com.dfs.corporate.integration.dfs;

import com.dfs.corporate.domain.EmployeeBulkRow;
import com.dfs.corporate.domain.Party;

/**
 * Parks employee registration data on DFS for later account opening.
 * Stub until the real consumer park API is provided.
 */
public interface EmployeeAccountParkClient {

    record ParkResult(boolean success, String parkRef, String message) {}

    ParkResult park(Party party, EmployeeBulkRow row);
}
