package com.umameats.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.umameats.model.Order;
import com.umameats.model.OrderItem;

import lombok.extern.slf4j.Slf4j;

/**
 * Service to send email notifications to restaurants when new orders are placed
 */
@Slf4j
@Service
public class RestaurantNotificationService {

    private final RestTemplate restTemplate;

    @Value("${restaurant.notification.url:https://umameats-landing-saas.vercel.app/api/notifications/orders}")
    private String restaurantNotificationUrl;

    @Value("${store.api.url:https://api.umameats.com/api/v1/stores}")
    private String storeApiUrl;

    public RestaurantNotificationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Send email notification to restaurant when a new order is placed
     */
    public void sendNewOrderNotification(Order order) {
        try {
            log.info("Sending new order notification to restaurant for order: {}", order.getOrderId());

            // Fetch store details to get email
            Map<String, Object> storeDetails = fetchStoreDetails(order.getStoreId());
            if (storeDetails == null) {
                log.warn("Could not fetch store details for storeId: {}, skipping notification", order.getStoreId());
                return;
            }

            String storeEmail = (String) storeDetails.get("email");
            if (storeEmail == null || storeEmail.isEmpty()) {
                log.warn("Store {} has no email configured, skipping notification", order.getStoreId());
                return;
            }

            // Build notification request
            Map<String, Object> notificationRequest = new HashMap<>();
            notificationRequest.put("orderId", order.getOrderId());
            notificationRequest.put("status", "CREATED");
            
            // Store info
            Map<String, Object> store = new HashMap<>();
            store.put("storeId", order.getStoreId());
            store.put("name", storeDetails.get("name"));
            store.put("email", storeEmail);
            store.put("phoneNumber", storeDetails.get("phoneNumber"));
            store.put("address", storeDetails.get("address"));
            store.put("city", storeDetails.get("city"));
            store.put("state", storeDetails.get("state"));
            store.put("zipCode", storeDetails.get("zipCode"));
            notificationRequest.put("store", store);

            // Items
            List<Map<String, Object>> items = order.getItems().stream()
                .map(item -> {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("name", item.getItemName());
                    itemMap.put("quantity", item.getQuantity());
                    itemMap.put("price", item.getPrice());
                    return itemMap;
                })
                .collect(Collectors.toList());
            notificationRequest.put("items", items);

            // Delivery address
            if (order.getDeliveryAddress() != null) {
                Map<String, Object> deliveryAddress = new HashMap<>();
                deliveryAddress.put("fullName", order.getDeliveryAddress().getFullName());
                deliveryAddress.put("phone", order.getDeliveryAddress().getPhone());
                deliveryAddress.put("street", order.getDeliveryAddress().getStreet());
                deliveryAddress.put("city", order.getDeliveryAddress().getCity());
                deliveryAddress.put("state", order.getDeliveryAddress().getState());
                deliveryAddress.put("zipCode", order.getDeliveryAddress().getZipCode());
                notificationRequest.put("deliveryAddress", deliveryAddress);
            }

            // Total amount (convert from cents to dollars)
            notificationRequest.put("totalAmount", order.getTotalAmount() / 100.0);
            
            // Special instructions
            if (order.getSpecialInstructions() != null) {
                notificationRequest.put("specialInstructions", order.getSpecialInstructions());
            }

            // Send async to avoid blocking
            new Thread(() -> {
                try {
                    restTemplate.postForEntity(restaurantNotificationUrl, notificationRequest, String.class);
                    log.info("Restaurant notification sent successfully for order: {}", order.getOrderId());
                } catch (Exception e) {
                    log.error("Failed to send restaurant notification for order: {}", order.getOrderId(), e);
                }
            }).start();

        } catch (Exception e) {
            log.error("Error preparing restaurant notification for order: {}", order.getOrderId(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchStoreDetails(String storeId) {
        try {
            String url = storeApiUrl + "/" + storeId;
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            log.error("Failed to fetch store details for storeId: {}", storeId, e);
            return null;
        }
    }
}

