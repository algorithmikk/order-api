package com.umameats.chat.service;

import com.umameats.chat.model.ChatMessage;
import com.umameats.model.Order;

/**
 * Sends a push to whichever party did not write the message, so a backgrounded
 * app still surfaces it. Separate from {@link DeliveryChatService} because
 * delivery of a message must never fail on a push provider outage.
 */
public interface DeliveryChatNotifier {

    void notifyCounterpart(Order order, ChatMessage message);
}
