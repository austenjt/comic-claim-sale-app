package org.example.functions;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.service.CartService;

@Slf4j
public class ReturnEventPrunerTrigger {

    private static final int RETURN_EVENT_RETENTION_DAYS = 30;

    /**
     * Runs weekly on Sunday at 04:00 UTC.
     * Deletes return events older than 30 days to prevent indefinite accumulation.
     */
    @FunctionName("pruneReturnEvents")
    public void run(
        @TimerTrigger(name = "returnEventPrunerTimer", schedule = "0 0 4 * * 0")
        String timerInfo,
        ExecutionContext context)
    {
        log.info("Return event pruner started — deleting events older than {} days", RETURN_EVENT_RETENTION_DAYS);
        try {
            int count = CartService.getServiceInstance().pruneOldReturnEvents(RETURN_EVENT_RETENTION_DAYS);
            log.info("Return event pruner complete — {} event(s) deleted", count);
        } catch (Exception e) {
            log.error("Return event pruner failed", e);
        }
    }
}
