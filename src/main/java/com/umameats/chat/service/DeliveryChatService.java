package com.umameats.chat.service;

import com.umameats.chat.model.ChatMessage;
import com.umameats.chat.model.ChatPrincipal;
import com.umameats.chat.repository.ChatMessageRepository;
import com.umameats.chat.security.ChatForbiddenException;
import com.umameats.chat.stream.ChatEventBus;
import com.umameats.model.Order;
import com.umameats.model.OrderStatus;
import com.umameats.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Customer/driver messaging for a single delivery.
 *
 * <p>The conversation is deliberately short-lived: it opens when a driver is
 * working the order and closes an hour after drop-off, so neither party can use
 * it to reach the other outside the job.
 */
@Slf4j
@Service
public class DeliveryChatService {

    /** Statuses during which a driver is actively working the order. */
    private static final Set<OrderStatus> ACTIVE_STATUSES = EnumSet.of(
            OrderStatus.DRIVER_EN_ROUTE_TO_STORE,
            OrderStatus.DRIVER_SHOPPING,
            OrderStatus.AWAITING_SHOPPING_APPROVAL,
            OrderStatus.SHOPPING_COMPLETE,
            OrderStatus.PICKED_UP,
            OrderStatus.OUT_FOR_DELIVERY);

    /** Window after drop-off for "you left my drink out" style follow-ups. */
    private static final long POST_DELIVERY_GRACE_MS = Duration.ofMinutes(60).toMillis();

    private static final int MAX_BODY_LENGTH = 1000;

    private final ChatMessageRepository chatMessageRepository;
    private final OrderRepository orderRepository;
    private final ChatEventBus chatEventBus;
    private final DeliveryChatNotifier notifier;

    public DeliveryChatService(
            ChatMessageRepository chatMessageRepository,
            OrderRepository orderRepository,
            ChatEventBus chatEventBus,
            DeliveryChatNotifier notifier) {
        this.chatMessageRepository = chatMessageRepository;
        this.orderRepository = orderRepository;
        this.chatEventBus = chatEventBus;
        this.notifier = notifier;
    }

    /**
     * History is readable for as long as the order exists, even after the
     * conversation closes, so both sides keep a record of what was agreed.
     */
    public List<ChatMessage> listMessages(String orderId, ChatPrincipal principal, int limit) {
        requireParticipant(orderId, principal);
        return chatMessageRepository.findByOrderId(orderId, limit);
    }

    public ChatMessage postMessage(String orderId, ChatPrincipal principal, String body) {
        Order order = requireParticipant(orderId, principal);

        if (!isChatOpen(order)) {
            throw new ChatForbiddenException("This delivery conversation is closed");
        }

        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Message body is required");
        }
        if (trimmed.length() > MAX_BODY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_BODY_LENGTH);
        }

        ChatMessage message = new ChatMessage();
        message.setOrderId(orderId);
        message.setMessageId(System.currentTimeMillis() + "#" + UUID.randomUUID());
        message.setSenderId(principal.id());
        message.setSenderRole(principal.role().name());
        message.setBody(trimmed);
        message.setCreatedAt(System.currentTimeMillis());
        chatMessageRepository.save(message);

        // Broadcast to everyone on the topic including the sender's other devices;
        // clients de-duplicate by messageId, which keeps this branch-free.
        chatEventBus.publish(ChatEventBus.deliveryTopic(orderId), "message", message);
        notifier.notifyCounterpart(order, message);

        return message;
    }

    /**
     * Whether new messages may be sent. Callers use this to hide the composer
     * rather than letting the user type into a dead thread.
     */
    public boolean isChatOpen(Order order) {
        if (order.getStatus() == null) {
            return false;
        }
        if (ACTIVE_STATUSES.contains(order.getStatus())) {
            return true;
        }
        if (order.getStatus() == OrderStatus.DELIVERED && order.getDeliveredAt() != null) {
            return System.currentTimeMillis() - order.getDeliveredAt() < POST_DELIVERY_GRACE_MS;
        }
        return false;
    }

    /**
     * @return the order, once the caller is confirmed to be its customer or its driver
     * @throws ChatForbiddenException if the caller is neither
     */
    public Order requireParticipant(String orderId, ChatPrincipal principal) {
        Order order = orderRepository.findById(orderId, principal.id())
                .orElseThrow(() -> new ChatForbiddenException("Order not found"));

        boolean allowed = principal.isDriver()
                ? matches(order.getDriverId(), principal.id()) || matches(order.getAssignedDriverId(), principal.id())
                : matches(order.getCustomerId(), principal.id());

        if (!allowed) {
            log.warn("Rejected chat access to order {} by {} {}", orderId, principal.role(), principal.id());
            throw new ChatForbiddenException("You are not a participant in this delivery");
        }
        return order;
    }

    private boolean matches(String candidate, String principalId) {
        return candidate != null && candidate.equals(principalId);
    }
}
