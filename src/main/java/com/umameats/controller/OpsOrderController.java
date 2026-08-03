package com.umameats.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.umameats.model.OpsStatusUpdateRequest;
import com.umameats.model.Order;
import com.umameats.model.OrderStatus;
import com.umameats.model.SeedDemoOrderRequest;
import com.umameats.service.OpsOrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Operator-only order board for UmaMeats Control Center.
 * Guarded by X-Admin-Token (app.admin-token / APP_ADMIN_TOKEN).
 *
 * GET  /api/v1/ops/orders
 * POST /api/v1/ops/orders/seed-demo
 * PATCH /api/v1/ops/orders/{orderId}/status
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ops/orders")
@RequiredArgsConstructor
@CrossOrigin(
        origins = {
                "http://localhost:3000",
                "https://ops-control-center-theta.vercel.app",
                "https://www.umameats.com",
                "https://umameats.com"
        },
        allowCredentials = "true",
        allowedHeaders = {
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Admin-Token"
        },
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PATCH, RequestMethod.OPTIONS}
)
public class OpsOrderController {

    private final OpsOrderService opsOrderService;

    @Value("${app.admin-token:#{null}}")
    private String adminToken;

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid admin token"));
    }

    private boolean isValidAdmin(String token) {
        return adminToken != null && !adminToken.isBlank() && adminToken.equals(token);
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(value = "status", required = false) List<String> status) {

        if (!isValidAdmin(token)) {
            return unauthorized();
        }

        List<OrderStatus> statuses = OpsOrderService.parseStatuses(status);
        List<Order> orders = opsOrderService.listOrders(statuses);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orders", orders);
        body.put("fetchedAt", Instant.now().toString());
        body.put("count", orders.size());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/seed-demo")
    public ResponseEntity<?> seedDemo(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody SeedDemoOrderRequest request) {

        if (!isValidAdmin(token)) {
            return unauthorized();
        }

        try {
            Map<String, Object> body = opsOrderService.seedDemoOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Ops seed-demo failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "seed-demo failed"));
        }
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<?> forceStatus(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable String orderId,
            @RequestBody OpsStatusUpdateRequest request) {

        if (!isValidAdmin(token)) {
            return unauthorized();
        }

        try {
            Order updated = opsOrderService.forceUpdateStatus(orderId, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Ops force status failed for {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "status update failed"));
        }
    }
}
