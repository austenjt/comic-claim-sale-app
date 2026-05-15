package org.example.functions;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.service.CartService;

@Slf4j
public class SubmittedOrderTrigger {

    /**
     * Runs every hour.
     * Migrates any legacy FINALIZING/FINALIZED documents in Cosmos to SUBMITTED status.
     * Once all documents have been migrated this sweep becomes a no-op.
     */
    @FunctionName("sweepSubmittedOrders")
    public void run(
        @TimerTrigger(name = "submittedOrderTimer", schedule = "0 0 * * * *")
        String timerInfo,
        ExecutionContext context)
    {
        log.info("Submitted-order sweep started");
        try {
            int count = CartService.getServiceInstance().sweepSubmittedOrders();
            if (count > 0) {
                log.info("Submitted-order sweep complete — {} legacy document(s) migrated to SUBMITTED", count);
            } else {
                log.info("Submitted-order sweep complete — no documents needed migration");
            }
        } catch (Exception e) {
            log.error("Submitted-order sweep failed", e);
        }
    }
}
