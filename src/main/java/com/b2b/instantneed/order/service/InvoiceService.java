package com.b2b.instantneed.order.service;

import com.b2b.instantneed.common.storage.StorageService;
import com.b2b.instantneed.order.entity.Order;
import com.b2b.instantneed.order.entity.OrderItem;
import com.b2b.instantneed.order.repository.OrderRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final ZoneOffset IST    = ZoneOffset.ofHoursMinutes(5, 30);
    private static final Locale     IN     = new Locale("en", "IN");
    private static final String     NAVY   = "#0d2b5e";
    private static final String     BLUE   = "#1a56db";
    private static final String     PRIMARY = "#4F46E5";

    private final StorageService  storageService;
    private final OrderRepository orderRepository;

    /**
     * Called before the order is persisted — order.getItems() is still a plain ArrayList.
     */
    public String generateAndStore(Order order) {
        try {
            byte[] pdf = buildPdf(order);
            String filename = order.getOrderNumber() + ".pdf";
            String url = storageService.storeBytes(pdf, "invoices", filename);
            log.info("[INVOICE] Generated invoice for {} → {}", order.getOrderNumber(), url);
            return url;
        } catch (Exception e) {
            log.error("[INVOICE] Failed to generate invoice for {}: {}", order.getOrderNumber(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * Retroactive generation: loads order fresh with items, generates and stores.
     */
    @Transactional
    public String generateAndStoreById(UUID orderId) {
        Order order = orderRepository.findWithItemsById(orderId).orElse(null);
        if (order == null) {
            log.warn("[INVOICE] Order {} not found", orderId);
            return null;
        }
        String url = generateAndStore(order);
        if (url != null) {
            order.setInvoicePath(url);
            orderRepository.save(order);
        }
        return url;
    }

    // ── PDF rendering ─────────────────────────────────────────────────────────

    private byte[] buildPdf(Order order) throws Exception {
        String html = buildHtml(order);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.useSVGDrawer(new BatikSVGDrawer());
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        builder.run();
        return out.toByteArray();
    }

    // ── HTML template (mirrors lib/invoice.ts :: buildInvoiceHtml) ────────────

    private String buildHtml(Order order) {
        long displayTotal = Math.round(order.getTotalAmount().doubleValue());
        double roundOff   = displayTotal - order.getTotalAmount().doubleValue();

        // Date in IST, matching new Date(str).toLocaleString("en-IN", {...})
        java.time.ZonedDateTime zdt = order.getPlacedAt().atZone(IST);
        String dateStr = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).format(zdt)
                + ", "
                + DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH).format(zdt)
                              .toLowerCase(Locale.ENGLISH);

        // Shipping address snapshot
        Map<String, Object> addr = order.getShippingAddressSnapshot();
        String addrFn     = s(addr, "fullName");
        String addrLine1  = s(addr, "line1");
        if (addrLine1 == null) addrLine1 = s(addr, "addressLine1");
        String addrLine2  = s(addr, "line2");
        if (addrLine2 == null) addrLine2 = s(addr, "addressLine2");
        String addrCity   = s(addr, "city");
        String addrState  = s(addr, "state");
        String addrPostal = s(addr, "postalCode");
        String addrPhone  = s(addr, "phoneNumber");

        String pmRaw     = order.getPaymentMethod() != null ? order.getPaymentMethod() : "";
        String pmDisplay = "COD".equalsIgnoreCase(pmRaw) ? "Cash On Delivery" : pmRaw;

        // ── Item rows ──────────────────────────────────────────────────────────
        StringBuilder itemRows = new StringBuilder();
        List<OrderItem> items = order.getItems();
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            itemRows
                .append("<tr style=\"border-bottom:1px solid #e4eaf5;\">")
                .append("<td style=\"padding:10px;text-align:center;color:").append(BLUE)
                    .append(";font-weight:600;\">").append(i + 1).append("</td>")
                .append("<td style=\"padding:10px;\">")
                    .append("<div style=\"font-weight:700;font-size:12px;margin-bottom:2px;\">")
                        .append(e(item.getProductNameSnapshot())).append("</div>")
                    .append("<div style=\"color:").append(BLUE).append(";font-size:10px;\">")
                        .append(e(item.getSkuSnapshot())).append("</div>")
                .append("</td>")
                .append("<td style=\"padding:10px;text-align:center;font-size:12px;\">")
                    .append(item.getQuantity()).append("</td>")
                .append("<td style=\"padding:10px;text-align:right;font-size:12px;\">")
                    .append(amt(item.getUnitPrice())).append("</td>")
                .append("<td style=\"padding:10px;text-align:right;font-size:12px;\">")
                    .append(amt(item.getLineTotal())).append("</td>")
                .append("</tr>");
        }

        // ── Round-off row (only if diff ≥ 0.01) ───────────────────────────────
        StringBuilder roundOffRow = new StringBuilder();
        if (Math.abs(roundOff) >= 0.01) {
            roundOffRow
                .append("<tr>")
                .append("<td colspan=\"2\"></td>")
                .append("<td colspan=\"2\" style=\"padding:3px 10px;color:#555;text-align:right;\">Round Off</td>")
                .append("<td style=\"padding:3px 10px;text-align:right;\">&#x20b9;")
                    .append(String.format("%.2f", roundOff)).append("</td>")
                .append("</tr>");
        }

        return "<!DOCTYPE html>\n"
            + "<html>\n"
            + "<head>\n"
            + "  <meta charset=\"utf-8\"/>\n"
            + "  <style>\n"
            + "    * { box-sizing: border-box; margin: 0; padding: 0; }\n"
            + "    body { font-family: 'DejaVu Sans', Arial, sans-serif; font-size: 11px; color: #222; line-height: 1.4; padding: 16px; }\n"
            + "    table { width: 100%; border-collapse: collapse; }\n"
            + "  </style>\n"
            + "</head>\n"
            + "<body>\n"

            // ── Header: logo | ORDER CONFIRMATION ────────────────────────────
            + "<table style=\"margin-bottom:10px;border-bottom:2px solid #d0d8ea;padding-bottom:10px;\">\n"
            + "  <tr>\n"
            + "    <td style=\"vertical-align:top;\">\n"
            + "      <table style=\"width:auto;border-collapse:separate;\">\n"
            + "        <tr>\n"
            + "          <td style=\"vertical-align:middle;padding-right:8px;\">\n"
            + "            <svg width=\"48\" height=\"48\" viewBox=\"0 0 64 64\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n"
            + "              <rect width=\"64\" height=\"64\" rx=\"15\" fill=\"#2563eb\"/>\n"
            + "              <path d=\"M35 10L19 37L30 37L24 54L40 27L30 27Z\" fill=\"white\"/>\n"
            + "            </svg>\n"
            + "          </td>\n"
            + "          <td style=\"vertical-align:middle;\">\n"
            + "            <div style=\"font-size:22px;font-weight:bold;line-height:1;\">"
            + "              <span style=\"color:" + BLUE + ";\">Instant</span>"
            + "              <span style=\"color:" + NAVY + ";\">Need</span>"
            + "            </div>\n"
            + "            <div style=\"color:#666;font-size:10px;margin-top:2px;\">Your Business, Our Priority.</div>\n"
            + "          </td>\n"
            + "        </tr>\n"
            + "      </table>\n"
            + "    </td>\n"
            + "    <td style=\"text-align:right;vertical-align:top;\">\n"
            + "      <div style=\"color:" + BLUE + ";font-size:19px;font-weight:bold;margin-bottom:6px;\">ORDER CONFIRMATION</div>\n"
            + "      <div style=\"margin-bottom:3px;\"><strong>Order ID:</strong> #" + e(order.getOrderNumber()) + "</div>\n"
            + "      <div><strong>Date:</strong> " + dateStr + "</div>\n"
            + "    </td>\n"
            + "  </tr>\n"
            + "</table>\n"

            // ── Company info | Order Placed box ──────────────────────────────
            + "<table style=\"margin-bottom:18px;\">\n"
            + "  <tr>\n"
            + "    <td style=\"vertical-align:top;width:55%;\">\n"
            + "      <div style=\"color:" + BLUE + ";font-weight:bold;font-size:12px;margin-bottom:4px;\">InstantNeed Private Limited</div>\n"
            + "      <div>5959, 12 Cross Road</div>\n"
            + "      <div style=\"margin-bottom:5px;\">Ambala Cantt, Haryana 133001</div>\n"
            + "      <div><strong>Phone:</strong> +91 8295781959</div>\n"
            + "      <div><strong>Email:</strong> Support@instantneed.in</div>\n"
            + "      <div><strong>Website:</strong> www.instantneed.in</div>\n"
            + "    </td>\n"
            + "    <td style=\"vertical-align:top;width:45%;padding-left:16px;\">\n"
            + "      <div style=\"border:1px solid #d0d8ea;border-radius:8px;padding:12px 16px;\">\n"
            + "        <div style=\"color:" + BLUE + ";font-weight:bold;font-size:14px;margin-bottom:3px;\">Order Placed!</div>\n"
            + "        <div style=\"color:#555;font-size:10.5px;\">Thank you for your order.</div>\n"
            + "        <div style=\"color:#555;font-size:10.5px;\">We'll notify you when it ships.</div>\n"
            + "      </div>\n"
            + "    </td>\n"
            + "  </tr>\n"
            + "</table>\n"

            // ── ORDER SUMMARY bar + items table ──────────────────────────────
            + "<div style=\"margin-bottom:12px;\">\n"
            + "  <div style=\"background-color:" + NAVY + ";color:white;padding:8px 14px;"
            + "font-weight:bold;font-size:13px;letter-spacing:0.5px;margin-bottom:0;\">\n"
            + "    ORDER SUMMARY\n"
            + "  </div>\n"
            + "  <table style=\"border:1px solid #d0d8ea;border-top:none;\">\n"
            + "    <thead>\n"
            + "      <tr style=\"background-color:" + BLUE + ";color:white;\">\n"
            + "        <th style=\"padding:8px 10px;width:36px;text-align:center;font-size:10.5px;\">#</th>\n"
            + "        <th style=\"padding:8px 10px;text-align:left;font-size:10.5px;\">ITEM NAME</th>\n"
            + "        <th style=\"padding:8px 10px;text-align:center;font-size:10.5px;\">QUANTITY</th>\n"
            + "        <th style=\"padding:8px 10px;text-align:right;font-size:10.5px;\">RATE (&#x20b9;)</th>\n"
            + "        <th style=\"padding:8px 10px;text-align:right;font-size:10.5px;\">AMOUNT (&#x20b9;)</th>\n"
            + "      </tr>\n"
            + "    </thead>\n"
            + "    <tbody>" + itemRows + "</tbody>\n"
            + "    <tfoot>\n"
            + "      <tr style=\"border-top:2px solid #d0d8ea;\">\n"
            + "        <td colspan=\"2\"></td>\n"
            + "        <td colspan=\"2\" style=\"padding:7px 10px;color:" + BLUE + ";font-weight:700;text-align:right;\">Subtotal</td>\n"
            + "        <td style=\"padding:7px 10px;text-align:right;font-weight:700;\">&#x20b9;" + amt(order.getSubtotalAmount()) + "</td>\n"
            + "      </tr>\n"
            + roundOffRow
            + "      <tr style=\"background-color:#f0f4ff;\">\n"
            + "        <td colspan=\"3\" style=\"padding:10px;\"></td>\n"
            + "        <td style=\"padding:10px;font-weight:bold;font-size:12px;text-align:right;color:" + PRIMARY + ";\">TOTAL AMOUNT</td>\n"
            + "        <td style=\"padding:10px;text-align:right;font-weight:bold;font-size:16px;color:#111111;\">&#x20b9;" + displayTotal + "</td>\n"
            + "      </tr>\n"
            + "    </tfoot>\n"
            + "  </table>\n"
            + "</div>\n"

            // ── Amount in words ───────────────────────────────────────────────
            + "<p style=\"margin-bottom:12px;font-size:11px;\">"
            + "<strong style=\"color:" + NAVY + ";\">Amount in Words:</strong> " + amountToWords(displayTotal)
            + "</p>\n"

            // ── Shipping address | Payment method ─────────────────────────────
            + "<table style=\"margin-bottom:12px;border-collapse:separate;border-spacing:10px 0;\">\n"
            + "  <tr>\n"
            + "    <td style=\"border:1px solid #d0d8ea;border-radius:7px;padding:10px 12px;vertical-align:top;width:50%;\">\n"
            + "      <div style=\"color:" + BLUE + ";font-weight:bold;font-size:11px;margin-bottom:7px;"
            + "padding-bottom:6px;border-bottom:1px dashed #d0d8ea;\">SHIPPING ADDRESS</div>\n"
            + (addrFn   != null ? "<div style=\"font-weight:600;\">" + e(addrFn) + "</div>\n" : "")
            + "<div>" + e(addrLine1) + "</div>\n"
            + (addrLine2 != null && !addrLine2.isBlank() ? "<div>" + e(addrLine2) + "</div>\n" : "")
            + "<div>" + e(addrCity) + ", " + e(addrState) + " " + e(addrPostal) + "</div>\n"
            + (addrPhone != null ? "<div>" + e(addrPhone) + "</div>\n" : "")
            + "    </td>\n"
            + "    <td style=\"border:1px solid #d0d8ea;border-radius:7px;padding:10px 12px;vertical-align:top;width:50%;\">\n"
            + "      <div style=\"color:" + BLUE + ";font-weight:bold;font-size:11px;margin-bottom:7px;"
            + "padding-bottom:6px;border-bottom:1px dashed #d0d8ea;\">PAYMENT METHOD</div>\n"
            + "      <div style=\"font-weight:600;margin-bottom:3px;\">" + e(pmDisplay) + "</div>\n"
            + "      <div style=\"color:#666;font-size:10.5px;\">Payment due on delivery</div>\n"
            + "    </td>\n"
            + "  </tr>\n"
            + "</table>\n"

            // ── Need Help? | Thank you ────────────────────────────────────────
            + "<table style=\"margin-bottom:10px;\">\n"
            + "  <tr>\n"
            + "    <td style=\"vertical-align:top;font-size:10.5px;line-height:1.7;\">\n"
            + "      <div style=\"color:" + NAVY + ";font-weight:bold;margin-bottom:3px;\">Need Help?</div>\n"
            + "      <div>Phone: +91 8295781959</div>\n"
            + "      <div>Email: Support@instantneed.in</div>\n"
            + "      <div>Mon &#x2013; Sat | 10:00 AM &#x2013; 7:00 PM</div>\n"
            + "    </td>\n"
            + "    <td style=\"text-align:right;vertical-align:bottom;\">\n"
            + "      <div style=\"color:" + BLUE + ";font-weight:bold;font-size:12.5px;margin-bottom:3px;\">"
            + "Thank you for choosing InstantNeed.</div>\n"
            + "      <div style=\"color:#555;font-size:10.5px;\">We look forward to serving your business again!</div>\n"
            + "    </td>\n"
            + "  </tr>\n"
            + "</table>\n"

            // ── Footer bar ────────────────────────────────────────────────────
            + "<table style=\"background-color:" + NAVY + ";border-radius:4px;\">\n"
            + "  <tr>\n"
            + "    <td style=\"padding:8px 14px;font-weight:bold;font-size:12px;color:white;\">InstantNeed</td>\n"
            + "    <td style=\"padding:8px 14px;text-align:right;color:#c0ceea;font-size:10px;\">"
            + "This is a system generated invoice and does not require a signature.</td>\n"
            + "  </tr>\n"
            + "</table>\n"

            + "</body>\n"
            + "</html>";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** HTML-escape a value (null-safe). */
    private static String e(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** Format a BigDecimal with en-IN locale and 2 decimal places. */
    private static String amt(BigDecimal v) {
        if (v == null) return "0.00";
        NumberFormat nf = NumberFormat.getNumberInstance(IN);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(v);
    }

    /** Pull a String from a JSON snapshot map. */
    private static String s(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    // ── Amount in words (mirrors lib/invoice.ts :: amountToWords) ────────────

    private static String amountToWords(long rupees) {
        if (rupees == 0) return "Zero Rupees Only";
        return "Rupees " + words(rupees).trim() + " Only";
    }

    private static final String[] ONES = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private static String words(long n) {
        if (n == 0) return "";
        if (n < 20)       return ONES[(int) n] + " ";
        if (n < 100)      return TENS[(int)(n / 10)] + (n % 10 != 0 ? " " + ONES[(int)(n % 10)] : "") + " ";
        if (n < 1_000)    return ONES[(int)(n / 100)] + " Hundred " + words(n % 100);
        if (n < 1_00_000) return words(n / 1_000)     + "Thousand " + words(n % 1_000);
        if (n < 1_00_00_000) return words(n / 1_00_000)    + "Lakh "  + words(n % 1_00_000);
        return                  words(n / 1_00_00_000) + "Crore " + words(n % 1_00_00_000);
    }
}
