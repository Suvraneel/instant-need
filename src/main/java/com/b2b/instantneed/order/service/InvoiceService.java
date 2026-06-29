package com.b2b.instantneed.order.service;

import com.b2b.instantneed.common.storage.StorageService;
import com.b2b.instantneed.order.entity.Order;
import com.b2b.instantneed.order.entity.OrderItem;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneOffset.UTC);

    private static final Color PRIMARY   = new Color(67, 56, 202);   // indigo-700
    private static final Color LIGHT_BG  = new Color(238, 242, 255); // indigo-50
    private static final Color MUTED     = new Color(107, 114, 128); // gray-500
    private static final Color BORDER    = new Color(229, 231, 235); // gray-200

    private final StorageService storageService;

    public String generateAndStore(Order order) {
        try {
            byte[] pdf = buildPdf(order);
            String filename = order.getOrderNumber() + ".pdf";
            return storageService.storeBytes(pdf, "invoices", filename);
        } catch (Exception e) {
            log.error("[INVOICE] Failed to generate invoice for {}: {}", order.getOrderNumber(), e.getMessage(), e);
            return null;
        }
    }

    // ── PDF building ──────────────────────────────────────────────────────────

    private byte[] buildPdf(Order order) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 45, 45, 50, 50);
        PdfWriter.getInstance(doc, out);
        doc.open();

        Font titleFont  = new Font(Font.HELVETICA, 20f, Font.BOLD, PRIMARY);
        Font h2Font     = new Font(Font.HELVETICA, 11f, Font.BOLD, PRIMARY);
        Font boldSmall  = new Font(Font.HELVETICA,  9f, Font.BOLD, Color.BLACK);
        Font normal     = new Font(Font.HELVETICA,  9f, Font.NORMAL, Color.BLACK);
        Font muted      = new Font(Font.HELVETICA,  8f, Font.NORMAL, MUTED);
        Font tableHdr   = new Font(Font.HELVETICA,  9f, Font.BOLD, Color.WHITE);
        Font totalBold  = new Font(Font.HELVETICA, 10f, Font.BOLD, PRIMARY);

        // ── Page header ───────────────────────────────────────────────────────
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);

        PdfPCell left = noBorder();
        left.addElement(new Paragraph("InstantNeed", titleFont));
        left.addElement(new Paragraph("Wholesale B2B Platform", muted));
        header.addCell(left);

        PdfPCell right = noBorder();
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph invTitle = new Paragraph("TAX INVOICE", h2Font);
        invTitle.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(invTitle);
        Paragraph invNum = new Paragraph("# " + order.getOrderNumber(), muted);
        invNum.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(invNum);
        Paragraph invDate = new Paragraph("Date: " + DATE_FMT.format(order.getPlacedAt()), muted);
        invDate.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(invDate);
        header.addCell(right);

        doc.add(header);
        doc.add(new Chunk(new LineSeparator(0.5f, 100f, BORDER, Element.ALIGN_CENTER, -4f)));
        doc.add(Chunk.NEWLINE);

        // ── Bill-to + payment info ─────────────────────────────────────────────
        PdfPTable info = new PdfPTable(2);
        info.setWidthPercentage(100);
        info.setSpacingBefore(6);

        PdfPCell billTo = noBorder();
        billTo.addElement(new Paragraph("BILL TO", muted));
        Map<String, Object> cust = order.getCustomerSnapshot();
        if (cust != null) {
            String name = str(cust, "fullName");
            String biz  = str(cust, "businessName");
            String gst  = str(cust, "gstVatNumber");
            if (name != null) billTo.addElement(new Paragraph(name, boldSmall));
            if (biz  != null) billTo.addElement(new Paragraph(biz, normal));
            if (gst  != null && !gst.isBlank()) billTo.addElement(new Paragraph("GST: " + gst, muted));
        }
        Map<String, Object> addr = order.getShippingAddressSnapshot();
        if (addr != null) {
            String line1  = str(addr, "line1");
            String line2  = str(addr, "line2");
            String city   = str(addr, "city");
            String state  = str(addr, "state");
            String postal = str(addr, "postalCode");
            String country= str(addr, "country");
            String phone  = str(addr, "phoneNumber");
            if (line1 != null)  billTo.addElement(new Paragraph(line1, normal));
            if (line2 != null && !line2.isBlank()) billTo.addElement(new Paragraph(line2, normal));
            String cityLine = join(", ", city, state) + (postal != null ? " " + postal : "");
            if (!cityLine.isBlank()) billTo.addElement(new Paragraph(cityLine.trim(), normal));
            if (country != null) billTo.addElement(new Paragraph(country, normal));
            if (phone   != null) billTo.addElement(new Paragraph("Phone: " + phone, normal));
        }
        info.addCell(billTo);

        PdfPCell payCell = noBorder();
        payCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph payLabel = new Paragraph("PAYMENT METHOD", muted);
        payLabel.setAlignment(Element.ALIGN_RIGHT);
        payCell.addElement(payLabel);
        Paragraph payVal = new Paragraph(order.getPaymentMethod().toUpperCase(), boldSmall);
        payVal.setAlignment(Element.ALIGN_RIGHT);
        payCell.addElement(payVal);
        info.addCell(payCell);

        doc.add(info);
        doc.add(Chunk.NEWLINE);

        // ── Items table ───────────────────────────────────────────────────────
        PdfPTable items = new PdfPTable(5);
        items.setWidthPercentage(100);
        items.setWidths(new float[]{0.5f, 3.5f, 1.5f, 0.8f, 1.5f});
        items.setSpacingBefore(4);

        String[] cols  = {"#", "Product", "SKU", "Qty", "Amount"};
        int[]    aligns = {Element.ALIGN_CENTER, Element.ALIGN_LEFT, Element.ALIGN_LEFT,
                           Element.ALIGN_CENTER, Element.ALIGN_RIGHT};
        for (int i = 0; i < cols.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(cols[i], tableHdr));
            c.setBackgroundColor(PRIMARY);
            c.setPadding(7);
            c.setBorder(Rectangle.NO_BORDER);
            c.setHorizontalAlignment(aligns[i]);
            items.addCell(c);
        }

        int row = 1;
        for (OrderItem item : order.getItems()) {
            Color rowBg = (row % 2 == 0) ? LIGHT_BG : Color.WHITE;
            addCell(items, String.valueOf(row),                        normal,    Element.ALIGN_CENTER, rowBg);
            addCell(items, item.getProductNameSnapshot(),              boldSmall, Element.ALIGN_LEFT,   rowBg);
            addCell(items, item.getSkuSnapshot(),                      normal,    Element.ALIGN_LEFT,   rowBg);
            addCell(items, String.valueOf(item.getQuantity()),         normal,    Element.ALIGN_CENTER, rowBg);
            addCell(items, rupees(item.getLineTotal()),                normal,    Element.ALIGN_RIGHT,  rowBg);
            row++;
        }
        doc.add(items);

        // ── Totals ────────────────────────────────────────────────────────────
        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(35);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setSpacingBefore(6);

        addTotalRow(totals, "Subtotal", rupees(order.getSubtotalAmount()), normal,    BORDER);
        addTotalRow(totals, "Shipping", "Free",                            normal,    BORDER);

        PdfPCell tLabel = new PdfPCell(new Phrase("TOTAL", totalBold));
        tLabel.setBackgroundColor(LIGHT_BG);
        tLabel.setPadding(7);
        tLabel.setBorderColor(PRIMARY);
        tLabel.setBorder(Rectangle.TOP | Rectangle.BOTTOM | Rectangle.LEFT);
        totals.addCell(tLabel);

        PdfPCell tValue = new PdfPCell(new Phrase(rupees(order.getTotalAmount()), totalBold));
        tValue.setBackgroundColor(LIGHT_BG);
        tValue.setPadding(7);
        tValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tValue.setBorderColor(PRIMARY);
        tValue.setBorder(Rectangle.TOP | Rectangle.BOTTOM | Rectangle.RIGHT);
        totals.addCell(tValue);

        doc.add(totals);

        // ── Customer note ─────────────────────────────────────────────────────
        if (order.getCustomerNote() != null && !order.getCustomerNote().isBlank()) {
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("Note: " + order.getCustomerNote(), muted));
        }

        // ── Footer ────────────────────────────────────────────────────────────
        doc.add(Chunk.NEWLINE);
        doc.add(new Chunk(new LineSeparator(0.5f, 100f, BORDER, Element.ALIGN_CENTER, -2f)));
        Paragraph footer = new Paragraph(
                "This is a computer-generated invoice. No signature required.", muted);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(4);
        doc.add(footer);

        doc.close();
        return out.toByteArray();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static PdfPCell noBorder() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingBottom(6);
        return cell;
    }

    private static void addCell(PdfPTable table, String text, Font font, int align, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", font));
        c.setPadding(6);
        c.setBorderColor(BORDER);
        c.setHorizontalAlignment(align);
        c.setBackgroundColor(bg);
        table.addCell(c);
    }

    private static void addTotalRow(PdfPTable table, String label, String value, Font font, Color borderColor) {
        PdfPCell l = new PdfPCell(new Phrase(label, font));
        l.setPadding(5);
        l.setBorderColor(borderColor);
        table.addCell(l);

        PdfPCell v = new PdfPCell(new Phrase(value, font));
        v.setPadding(5);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        v.setBorderColor(borderColor);
        table.addCell(v);
    }

    private static String rupees(BigDecimal amount) {
        return amount == null ? "₹0.00" : "₹" + String.format("%.2f", amount);
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (!sb.isEmpty()) sb.append(sep);
                sb.append(p);
            }
        }
        return sb.toString();
    }
}
