package com.b2b.instantneed.order.controller;

import com.b2b.instantneed.admin.controller.AdminOrderController;
import com.b2b.instantneed.admin.service.AdminOrderService;
import com.b2b.instantneed.admin.service.AdminReportService;
import com.b2b.instantneed.order.service.InvoiceService;
import com.b2b.instantneed.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderInvoiceAccessTest {

    @Test
    void customerInvoiceRouteIsAbsent() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OrderController(mock(OrderService.class))).build();

        mvc.perform(get("/api/v1/orders/{orderId}/invoice", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminInvoiceRouteStillStreamsPdf() throws Exception {
        UUID orderId = UUID.randomUUID();
        AdminOrderService orders = mock(AdminOrderService.class);
        given(orders.getInvoicePdf(orderId)).willReturn(new AdminOrderService.InvoiceFile(
                "%PDF-1.4".getBytes(), "invoice.pdf"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminOrderController(
                orders, mock(AdminReportService.class), mock(InvoiceService.class))).build();

        mvc.perform(get("/api/v1/admin/orders/{orderId}/invoice", orderId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"invoice.pdf\""))
                .andExpect(content().contentType("application/pdf"));
    }
}
