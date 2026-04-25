package org.example.functions.email;

import org.example.functions.model.ArchivedOrder;
import org.example.functions.model.ArchivedOrderItem;
import org.example.functions.model.Cart;
import org.example.functions.model.CartItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain-text email body builders for customer-facing order notifications.
 *
 * <p>This class contains <b>only template construction</b> — pure functions of the input
 * model. Sending the resulting email (SMTP setup, recipient resolution, error handling)
 * is the caller's responsibility and is performed via
 * {@link org.example.functions.service.EmailService}.</p>
 *
 * <p>Splitting templates from sending makes the customer-facing copy easy to find for
 * edits, leaves the door open for moving the bodies to a properties file or a templating
 * engine later, and keeps {@link org.example.functions.service.CartService} from drowning
 * in formatting code.</p>
 */
public final class EmailTemplates {

    /** A subject + body pair for a single email. */
    public record Email(String subject, String body) {}

    private EmailTemplates() {}

    // ─── Cart (active order) emails ───────────────────────────────────────────

    /** Sent when a user submits an order — admin still has to mark it paid. */
    public static Email orderSubmitted(Cart cart) {
        String body = "Hi " + cart.getUserName() + ",\n\n"
            + "Thank you for your order! We've received your claim and it is currently being processed.\n\n"
            + "A Lightning Comics team member will contact you shortly to arrange payment.\n\n"
            + "ORDER SUMMARY\n"
            + "=============\n"
            + buildOrderItemsText(cart) + "\n"
            + (cart.getCustomerNotes() != null && !cart.getCustomerNotes().isBlank()
                ? "Your notes: " + cart.getCustomerNotes() + "\n\n" : "")
            + "Thank you for shopping with Lightning Comics!\n";
        return new Email("Your Order is Being Processed", body);
    }

    /** Sent when admin marks a cart as PAID. */
    public static Email paymentReceived(Cart cart) {
        String body = "Hi " + cart.getUserName() + ",\n\n"
            + "Great news! We've received your payment for your recent order.\n\n"
            + "ORDER RECEIPT\n"
            + "=============\n"
            + buildOrderItemsText(cart) + "\n"
            + "Thank you for your payment!\n\n"
            + "Lightning Comics\n";
        return new Email("Payment Received", body);
    }

    /** Sent when admin marks a cart as FULFILLED — items are on the way. */
    public static Email fulfillment(Cart cart) {
        // Build item list, grouping set members under their set name
        StringBuilder itemsText = new StringBuilder();
        List<CartItem> nonContainers = cart.getItems().stream()
            .filter(i -> !i.isSetContainer()).toList();
        Map<Integer, List<CartItem>> byGroup = new LinkedHashMap<>();
        List<CartItem> singles = new ArrayList<>();
        for (CartItem item : nonContainers) {
            if (item.getCollectionGroup() != null && item.getCollectionGroup() > 0) {
                byGroup.computeIfAbsent(item.getCollectionGroup(), k -> new ArrayList<>()).add(item);
            } else {
                singles.add(item);
            }
        }
        for (CartItem item : singles) {
            String num = item.getComicNumber() != null ? " " + item.getComicNumber() : "";
            itemsText.append(String.format("  %s%s  ($%.2f)%n", item.getComicTitle(), num, item.getPrice()));
        }
        for (Map.Entry<Integer, List<CartItem>> entry : byGroup.entrySet()) {
            CartItem container = cart.getItems().stream()
                .filter(i -> i.isSetContainer() && entry.getKey().equals(i.getCollectionGroup()))
                .findFirst().orElse(null);
            String setName = container != null ? container.getComicTitle() : "Set " + entry.getKey();
            double setTotal = entry.getValue().stream().mapToDouble(CartItem::getPrice).sum();
            itemsText.append(String.format("  %s (Set)  ($%.2f)%n", setName, setTotal));
            for (CartItem member : entry.getValue()) {
                String num = member.getComicNumber() != null ? " " + member.getComicNumber() : "";
                itemsText.append(String.format("    - %s%s%n", member.getComicTitle(), num));
            }
        }

        // Order summary
        double subtotal = nonContainers.stream().mapToDouble(CartItem::getPrice).sum();
        double discount = cart.getDiscountAmount();
        double shipping = cart.getShippingCost();
        double grandTotal = Math.max(0, subtotal - discount) + shipping;

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("  Subtotal:  $%.2f%n", subtotal));
        if (discount > 0) {
            summary.append(String.format("  Discount:  -$%.2f", discount));
            if (cart.getDiscountDescription() != null && !cart.getDiscountDescription().isBlank()) {
                summary.append("  (").append(cart.getDiscountDescription()).append(")");
            }
            summary.append("\n");
        }
        if (shipping > 0) {
            summary.append(String.format("  Shipping:  $%.2f%n", shipping));
        }
        summary.append(String.format("  Total:     $%.2f%n", grandTotal));

        // Optional sections
        String trackingLine = (cart.getTrackingNumber() != null && !cart.getTrackingNumber().isBlank())
            ? "\nTracking Number: " + cart.getTrackingNumber() + "\n"
            : "";
        String sellerNotesLine = (cart.getAdminNotes() != null && !cart.getAdminNotes().isBlank())
            ? "\nNote from seller:\n" + cart.getAdminNotes() + "\n"
            : "";

        String body = "Hi " + cart.getUserName() + ",\n\n"
            + "Lightning Comics has fulfilled your order and it's on the way!\n\n"
            + "ORDER SUMMARY\n"
            + "─────────────────────────────\n"
            + itemsText
            + "\n"
            + summary
            + trackingLine
            + sellerNotesLine
            + "\nThank you for your business. We hope you enjoy your comics!\n\n"
            + "Lightning Comics\n";
        return new Email("Your Order Has Shipped", body);
    }

    // ─── Archived order emails ────────────────────────────────────────────────

    /** Sent when admin marks an already-archived order as PAID. */
    public static Email archivedPaymentReceived(ArchivedOrder order) {
        StringBuilder sb = new StringBuilder();
        double subtotal = 0;
        for (ArchivedOrderItem item : order.getItems()) {
            if (item.getPrice() == 0) continue;
            String num = item.getComicNumber() != null ? " " + item.getComicNumber() : "";
            sb.append(String.format("  %s%s — $%.2f%n", item.getComicTitle(), num, item.getPrice()));
            subtotal += item.getPrice();
        }
        sb.append(String.format("%nSubtotal: $%.2f%n", subtotal));
        if (order.getShippingCost() > 0) {
            sb.append(String.format("Shipping: $%.2f%n", order.getShippingCost()));
        }
        if (order.getDiscountAmount() > 0) {
            String desc = order.getDiscountDescription() != null
                ? " (" + order.getDiscountDescription() + ")"
                : "";
            sb.append(String.format("Discount%s: -$%.2f%n", desc, order.getDiscountAmount()));
        }
        double total = subtotal + order.getShippingCost() - order.getDiscountAmount();
        sb.append(String.format("Total: $%.2f%n", total));

        String body = "Hi " + order.getUserName() + ",\n\n"
            + "Great news! We've received your payment for your recent order.\n\n"
            + "ORDER RECEIPT\n"
            + "=============\n"
            + sb + "\n"
            + "Thank you for your payment!\n\n"
            + "Lightning Comics\n";
        return new Email("Payment Received", body);
    }

    // ─── Shared helpers ───────────────────────────────────────────────────────

    /** Itemized list + subtotal/shipping/discount/total block used by Cart-based emails. */
    private static String buildOrderItemsText(Cart cart) {
        StringBuilder sb = new StringBuilder();
        double subtotal = 0;
        for (CartItem item : cart.getItems()) {
            if (item.isSetContainer()) continue;
            String num = item.getComicNumber() != null ? " " + item.getComicNumber() : "";
            sb.append(String.format("  %s%s — $%.2f%n", item.getComicTitle(), num, item.getPrice()));
            subtotal += item.getPrice();
        }
        sb.append(String.format("%nSubtotal: $%.2f%n", subtotal));
        if (cart.getShippingCost() > 0) {
            sb.append(String.format("Shipping: $%.2f%n", cart.getShippingCost()));
        }
        if (cart.getDiscountAmount() > 0) {
            String desc = cart.getDiscountDescription() != null
                ? " (" + cart.getDiscountDescription() + ")"
                : "";
            sb.append(String.format("Discount%s: -$%.2f%n", desc, cart.getDiscountAmount()));
        }
        double total = subtotal + cart.getShippingCost() - cart.getDiscountAmount();
        sb.append(String.format("Total: $%.2f%n", total));
        return sb.toString();
    }
}
