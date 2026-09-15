package com.b2b.instantneed.order.service;

import com.b2b.instantneed.common.storage.StorageService;
import com.b2b.instantneed.order.entity.Order;
import com.b2b.instantneed.order.entity.OrderItem;
import com.b2b.instantneed.order.repository.OrderRepository;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final Locale IN = new Locale("en", "IN");
    private static final String BORDER = "#333333";
    private static final String BORDER_WIDTH = "1.25px";
    private static final String LIGHT = "#f2f2f2";

    private final StorageService storageService;
    private final OrderRepository orderRepository;
    private final InvoiceNumberService invoiceNumberService;

    public String generateAndStore(Order order) {
        try {
            if (order.getInvoiceNumber() == null || order.getInvoiceNumber().isBlank()) {
                order.setInvoiceNumber(invoiceNumberService.next(order.getPlacedAt() != null
                        ? order.getPlacedAt() : java.time.Instant.now()));
            }
            byte[] pdf = buildPdf(order);
            String filename = order.getInvoiceNumber() + ".pdf";
            String url = storageService.storeBytes(pdf, "invoices", filename);
            log.info("[INVOICE] Generated invoice {} for order {}", order.getInvoiceNumber(), order.getOrderNumber());
            return url;
        } catch (Exception e) {
            log.error("[INVOICE] Failed to generate invoice for {}: {}", order.getOrderNumber(), e.getMessage(), e);
            return null;
        }
    }

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

    private byte[] buildPdf(Order order) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.useSVGDrawer(new BatikSVGDrawer());
        builder.useFont(() -> InvoiceService.class.getResourceAsStream("/fonts/NotoSans-Regular.ttf"),
                "Noto Sans", 400, BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.useFont(() -> InvoiceService.class.getResourceAsStream("/fonts/NotoSans-Bold.ttf"),
                "Noto Sans", 700, BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.withHtmlContent(buildHtml(order), null);
        builder.toStream(out);
        builder.run();
        return out.toByteArray();
    }

    private String buildHtml(Order order) {
        String date = DateTimeFormatter.ofPattern("dd-MM-yyyy")
                .withZone(INDIA).format(order.getPlacedAt());
        Map<String, Object> address = order.getShippingAddressSnapshot();
        Map<String, Object> customer = order.getCustomerSnapshot();
        String customerName = first(s(customer, "businessName"), s(customer, "fullName"), "Retail Customer");
        String customerGstin = first(s(customer, "gstinUin"), "—");
        String customerAddress = addressLine(address);

        StringBuilder rows = new StringBuilder();
        Map<String, TaxGroup> groups = new LinkedHashMap<>();
        int index = 1;
        for (OrderItem item : order.getItems()) {
            BigDecimal taxable = taxable(item);
            BigDecimal cgst = amount(item.getCgstAmount());
            BigDecimal sgst = amount(item.getSgstAmount());
            BigDecimal cgstRate = rate(item.getCgstRate());
            BigDecimal sgstRate = rate(item.getSgstRate());
            BigDecimal gross = amount(item.getLineTotal());
            String groupKey = cgstRate.stripTrailingZeros().toPlainString() + "/"
                    + sgstRate.stripTrailingZeros().toPlainString();
            TaxGroup group = groups.computeIfAbsent(groupKey,
                    ignored -> new TaxGroup(cgstRate, sgstRate));
            group.add(taxable, cgst, sgst);

            rows.append("<tr>")
                    .append(cell(String.valueOf(index++), "center"))
                    .append(cell(e(item.getProductNameSnapshot()), "left"))
                    .append(cell(e(first(item.getHsnCodeSnapshot(), "—")), "center"))
                    .append(cell(String.valueOf(item.getQuantity()), "center"))
                    .append(cell(e(first(item.getUnitOfMeasurementSnapshot(), "—")), "center"))
                    .append(cell(money(item.getMrpSnapshot()), "right"))
                    .append(cell(money(item.getUnitPrice()), "right"))
                    .append(cell(percent(cgstRate), "center"))
                    .append(cell(money(cgst), "right"))
                    .append(cell(percent(sgstRate), "center"))
                    .append(cell(money(sgst), "right"))
                    .append(cell(money(gross), "right"))
                    .append("</tr>");
        }

        StringBuilder taxRows = new StringBuilder();
        BigDecimal totalTaxable = BigDecimal.ZERO;
        BigDecimal totalCgst = BigDecimal.ZERO;
        BigDecimal totalSgst = BigDecimal.ZERO;
        for (TaxGroup group : groups.values()) {
            totalTaxable = totalTaxable.add(group.taxable);
            totalCgst = totalCgst.add(group.cgst);
            totalSgst = totalSgst.add(group.sgst);
            taxRows.append("<tr>")
                    .append(cell(percent(group.cgstRate.add(group.sgstRate)), "center"))
                    .append(cell(money(group.taxable), "right"))
                    .append(cell(money(group.cgst), "right"))
                    .append(cell(money(group.sgst), "right"))
                    .append(cell(money(group.cgst.add(group.sgst)), "right"))
                    .append("</tr>");
        }
        taxRows.append("<tr class=\"bold\">")
                .append(cell("Total", "center"))
                .append(cell(money(totalTaxable), "right"))
                .append(cell(money(totalCgst), "right"))
                .append(cell(money(totalSgst), "right"))
                .append(cell(money(totalCgst.add(totalSgst)), "right"))
                .append("</tr>");

        String transport = first(order.getTransport(), "—");
        String vehicle = first(order.getVehicleNumber(), "—");
        String eway = first(order.getEwayBillNumber(), "—");
        String invoiceNumber = first(order.getInvoiceNumber(), order.getOrderNumber());

        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>"
                + "<style>"
                + "@page{size:A4;margin:12mm}*{box-sizing:border-box}"
                + "body{font-family:'Noto Sans',Arial,sans-serif;font-size:10px;color:#111;margin:0}"
                + "table{width:100%;border-collapse:collapse}td,th{border:" + BORDER_WIDTH + " solid " + BORDER + ";padding:6px 5px}"
                + "th{background:" + LIGHT + ";font-weight:700;text-align:center}"
                + ".no-border td{border:0}.header{font-size:25px;font-weight:800}.subhead{font-size:14px}"
                + ".small{font-size:9px}.bold{font-weight:700}.right{text-align:right}.center{text-align:center}"
                + ".invoice{border:" + BORDER_WIDTH + " solid " + BORDER + ";padding:16px}.section{margin-top:10px}.label{font-weight:700}.terms{line-height:1.7}"
                + "</style></head><body>"
                + "<div class=\"invoice\">"
                + "<table class=\"no-border\"><tr><td style=\"width:65%;vertical-align:top\">"
                + "<div class=\"header\">INSTANTNEED</div><div class=\"subhead\">B2B Wholesale</div>"
                + "<div>Shop No. 5959, 12 Cross Road, Ambala-133001, Haryana</div>"
                + "<div class=\"bold\" style=\"font-size:13px;margin-top:5px\">GSTIN / UIN : 06AAMFI3712M1Z6</div>"
                + "</td><td style=\"text-align:right;vertical-align:top;border:0\"><div style=\"font-size:22px;font-weight:800\">TAX INVOICE</div>"
                + "<div style=\"font-size:13px;margin-top:8px\">Original Copy</div></td></tr></table>"
                + "<table class=\"section\"><tr><td style=\"width:50%;vertical-align:top\">"
                + info("Invoice No.", invoiceNumber) + info("Dated", date)
                + info("Place of Supply", "Haryana (06)") + info("Reverse Charge", "N")
                + "</td><td style=\"width:50%;vertical-align:top\">"
                + info("Transport", transport) + info("Vehicle No.", vehicle)
                + info("E-Way Bill No.", eway) + "</td></tr></table>"
                + "<table class=\"section\"><tr><td style=\"width:50%;vertical-align:top\">"
                + "<div class=\"label\">Billed To:</div><div>" + e(customerName) + "</div><div>" + customerAddress + "</div>"
                + "<div class=\"bold\">GSTIN/UIN : " + e(customerGstin) + "</div></td>"
                + "<td style=\"width:50%;vertical-align:top\"><div class=\"label\">Shipped To:</div><div>" + e(customerName) + "</div>"
                + "<div>" + customerAddress + "</div><div class=\"bold\">GSTIN/UIN : " + e(customerGstin) + "</div></td></tr></table>"
                + "<table class=\"section\"><thead><tr>"
                + header("S.N.") + header("Description of Goods", "width:24%") + header("HSN Code")
                + header("Qty") + header("Unit") + header("MRP (₹)") + header("Price (₹)", "width:8%")
                + header("CGST") + header("CGST Amt") + header("SGST") + header("SGST Amt") + header("Total (₹)")
                + "</tr></thead><tbody>" + rows + "</tbody><tfoot><tr class=\"bold\">"
                + "<td colspan=\"3\" style=\"text-align:right\">Grand Total</td>"
                + cell(String.valueOf(order.getItems().stream().mapToInt(OrderItem::getQuantity).sum()), "center")
                + cell("—", "center") + "<td colspan=\"6\"></td>" + cell(money(order.getTotalAmount()), "right")
                + "</tr></tfoot></table>"
                + "<table class=\"section\" style=\"width:68%\"><thead><tr>"
                + header("Tax Rate") + header("Taxable Amt. (₹)") + header("CGST Amt. (₹)")
                + header("SGST Amt. (₹)") + header("Total Tax (₹)") + "</tr></thead><tbody>" + taxRows + "</tbody></table>"
                + "<table class=\"section\"><tr><td><span class=\"label\">Amount in Words (Rupees) : </span>"
                + e(amountToWords(order.getTotalAmount())) + "</td></tr></table>"
                + "<table class=\"section\"><tr><td style=\"width:55%;vertical-align:top\" class=\"terms\"><div class=\"label\">Terms &amp; Conditions:</div>"
                + "1. E. &amp; O.E.<br/>2. Goods once sold will not be taken back.<br/>"
                + "3. Interest @ 18% p.a. will be charged if payment is not made within the stipulated time.<br/>"
                + "4. Subject to Ambala Jurisdiction only.</td><td style=\"vertical-align:bottom;text-align:right\">"
                + "Receiver's Signature :<br/><br/><br/><span class=\"bold\">for INSTANTNEED</span><br/>Authorised Signatory</td></tr></table>"
                + "</div>"
                + "</body></html>";
    }

    private static String info(String label, String value) {
        return "<div><span class=\"label\">" + label + " :</span> " + e(value) + "</div>";
    }

    private static String header(String text) { return header(text, ""); }
    private static String header(String text, String style) {
        return "<th style=\"" + style + "\">" + text + "</th>";
    }

    private static String cell(String text, String align) {
        return "<td class=\"" + align + "\">" + text + "</td>";
    }

    private static String addressLine(Map<String, Object> address) {
        if (address == null) return "—";
        String line1 = first(s(address, "line1"), s(address, "addressLine1"), "");
        String line2 = first(s(address, "line2"), s(address, "addressLine2"), "");
        String city = first(s(address, "city"), "");
        String state = first(s(address, "state"), "");
        String postal = first(s(address, "postalCode"), "");
        return e(line1) + (line2.isBlank() ? "" : ", " + e(line2)) + "<br/>"
                + e(city) + ", " + e(state) + " " + e(postal);
    }

    private static BigDecimal taxable(OrderItem item) {
        return item.getTaxableAmount() != null ? item.getTaxableAmount() : amount(item.getLineTotal());
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal rate(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String money(BigDecimal value) {
        NumberFormat nf = NumberFormat.getNumberInstance(IN);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(amount(value));
    }

    private static String percent(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString() + "%";
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    private static String s(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) return null;
        return map.get(key).toString();
    }

    private static String e(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String amountToWords(BigDecimal value) {
        long rupees = value == null ? 0 : value.setScale(0, RoundingMode.HALF_UP).longValue();
        if (rupees == 0) return "Zero Rupees Only";
        return "Rupees " + words(rupees).trim() + " Only";
    }

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    private static String words(long n) {
        if (n == 0) return "";
        if (n < 20) return ONES[(int) n] + " ";
        if (n < 100) return TENS[(int) (n / 10)] + (n % 10 == 0 ? "" : " " + ONES[(int) (n % 10)]) + " ";
        if (n < 1_000) return ONES[(int) (n / 100)] + " Hundred " + words(n % 100);
        if (n < 1_00_000) return words(n / 1_000) + "Thousand " + words(n % 1_000);
        if (n < 1_00_00_000) return words(n / 1_00_000) + "Lakh " + words(n % 1_00_000);
        return words(n / 1_00_00_000) + "Crore " + words(n % 1_00_00_000);
    }

    private static final class TaxGroup {
        private final BigDecimal cgstRate;
        private final BigDecimal sgstRate;
        private BigDecimal taxable = BigDecimal.ZERO;
        private BigDecimal cgst = BigDecimal.ZERO;
        private BigDecimal sgst = BigDecimal.ZERO;

        private TaxGroup(BigDecimal cgstRate, BigDecimal sgstRate) {
            this.cgstRate = cgstRate;
            this.sgstRate = sgstRate;
        }

        private void add(BigDecimal taxable, BigDecimal cgst, BigDecimal sgst) {
            this.taxable = this.taxable.add(taxable);
            this.cgst = this.cgst.add(cgst);
            this.sgst = this.sgst.add(sgst);
        }
    }
}
