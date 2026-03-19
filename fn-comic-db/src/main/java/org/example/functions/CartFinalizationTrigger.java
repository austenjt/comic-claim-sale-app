package org.example.functions;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.service.CartService;

@Slf4j
public class CartFinalizationTrigger {

    /**
     * Runs every hour.
     * Transitions any FINALIZING carts whose deadline has passed to FINALIZED,
     * independent of whether a user or admin has fetched the cart.
     */
    @FunctionName("finalizeOverdueCarts")
    public void run(
        @TimerTrigger(name = "cartFinalizationTimer", schedule = "0 0 * * * *")
        String timerInfo,
        ExecutionContext context)
    {
        log.info("Cart finalization sweep started");
        try {
            int count = CartService.getServiceInstance().finalizeOverdueCarts();
            if (count > 0) {
                log.info("Cart finalization sweep complete — {} cart(s) finalized", count);
            } else {
                log.info("Cart finalization sweep complete — no carts needed finalization");
            }
        } catch (Exception e) {
            log.error("Cart finalization sweep failed", e);
        }
    }
}
