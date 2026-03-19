package org.example.functions;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import lombok.extern.slf4j.Slf4j;
import org.example.functions.service.CartService;
import org.example.functions.util.EnvHelper;

@Slf4j
public class CartExpiryTrigger {

    /**
     * Runs daily at 02:00 UTC.
     * Expires OPEN carts that have been idle for longer than CART_EXPIRY_DAYS (default 7).
     * All items in expired carts are returned to available inventory via return events.
     */
    @FunctionName("expireAbandonedCarts")
    public void run(
        @TimerTrigger(name = "cartExpiryTimer", schedule = "0 0 2 * * *")
        String timerInfo,
        ExecutionContext context)
    {
        int expiryDays = EnvHelper.getCartExpiryDays();
        log.info("Cart expiry sweep started — expiring OPEN carts older than {} days", expiryDays);
        try {
            int count = CartService.getServiceInstance().expireAbandonedCarts();
            log.info("Cart expiry sweep complete — {} cart(s) expired", count);
        } catch (Exception e) {
            log.error("Cart expiry sweep failed", e);
        }
    }
}
