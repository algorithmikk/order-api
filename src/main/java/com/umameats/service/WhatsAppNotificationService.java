package com.umameats.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import com.umameats.model.Order;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service to send WhatsApp notifications to store owners via Twilio
 * when new orders are placed.
 */
@Slf4j
@Service
public class WhatsAppNotificationService {

    private final SecretsManagerService secretsManagerService;
    private final RestTemplate restTemplate;

    @Value("${store.api.url:https://api.umameats.com/api/v1/stores}")
    private String storeApiUrl;

    // Twilio Content Template SID for new order notification
    private static final String CONTENT_TEMPLATE_SID = "***REDACTED_TWILIO_SID***";

    // Order dashboard URL base
    private static final String ORDER_DASHBOARD_URL = "https://www.umameats.com/dashboard";

    private String accountSid;
    private String authToken;
    private String whatsappFrom;
    private boolean initialized = false;

    public WhatsAppNotificationService(SecretsManagerService secretsManagerService, RestTemplate restTemplate) {
        this.secretsManagerService = secretsManagerService;
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() {
        try {
            accountSid = secretsManagerService.getSecretValue("prod/twilio", "ACCOUNT_SID");
            authToken = secretsManagerService.getSecretValue("prod/twilio", "AUTH_TOKEN");
            whatsappFrom = secretsManagerService.getSecretValue("prod/twilio", "WHATSAPP_FROM");

            if (accountSid != null && authToken != null && whatsappFrom != null) {
                Twilio.init(accountSid, authToken);
                initialized = true;
                log.info("WhatsApp notification service initialized successfully. From: {}", whatsappFrom);
            } else {
                log.warn("WhatsApp notification service not initialized - missing Twilio credentials in prod/twilio");
            }
        } catch (Exception e) {
            log.warn("WhatsApp notification service not initialized: {}", e.getMessage());
        }
    }

    /**
     * Send WhatsApp notification to store owner when a new order is placed.
     * Runs async to avoid blocking the order flow.
     */
    public void sendNewOrderWhatsApp(Order order) {
        if (!initialized) {
            log.warn("WhatsApp service not initialized, skipping notification for order: {}", order.getOrderId());
            return;
        }

        new Thread(() -> {
            try {
                // Fetch store details to get phone number
                Map<String, Object> storeDetails = fetchStoreDetails(order.getStoreId());
                if (storeDetails == null) {
                    log.warn("Could not fetch store details for storeId: {}, skipping WhatsApp", order.getStoreId());
                    return;
                }

                String storePhone = (String) storeDetails.get("phoneNumber");
                if (storePhone == null || storePhone.isEmpty()) {
                    log.warn("Store {} has no phone number, skipping WhatsApp", order.getStoreId());
                    return;
                }

                // Clean phone number - ensure it starts with +
                String cleanPhone = storePhone.replaceAll("[^+0-9]", "");
                if (!cleanPhone.startsWith("+")) {
                    cleanPhone = "+" + cleanPhone;
                }

                // Build the order link for the template variable {{1}}
                String orderLink = ORDER_DASHBOARD_URL + "?orderId=" + order.getOrderId();

                // Send WhatsApp message using Twilio Content Template
                Message message = Message.creator(
                        new PhoneNumber("whatsapp:" + cleanPhone),    // To
                        new PhoneNumber("whatsapp:" + whatsappFrom),  // From
                        ""  // Body is ignored when using contentSid
                )
                .setContentSid(CONTENT_TEMPLATE_SID)
                .setContentVariables("{\"1\":\"" + orderLink + "\"}")
                .create();

                log.info("WhatsApp notification sent to store {} ({}): messageSid={}, status={}",
                        order.getStoreId(), cleanPhone, message.getSid(), message.getStatus());

            } catch (Exception e) {
                log.error("Failed to send WhatsApp notification for order {}: {}",
                        order.getOrderId(), e.getMessage());
            }
        }).start();
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

