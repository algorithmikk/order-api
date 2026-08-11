package com.umameats.support.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerFacingReplyTest {

    @Test
    void stripsThinkBlocks() {
        String raw = "<think>Internal plan about refunds</think>\nSorry about that — I can help with a refund.";
        assertEquals("Sorry about that — I can help with a refund.", CustomerFacingReply.sanitize(raw));
    }

    @Test
    void keepsTextAfterFinalAnswerMarker() {
        String raw = "Weighing options.\nThus final answer:\nI'd be happy to help with a refund. What went wrong with the order?";
        assertEquals(
                "I'd be happy to help with a refund. What went wrong with the order?",
                CustomerFacingReply.sanitize(raw));
    }

    @Test
    void rejectsPolicyMonologueFromScreenshot() {
        String raw = "We need to handle a request for a refund. The user says 'Hello can I get refund'. "
                + "According to policy, refunds: 'Approve a refund for a genuine problem such as missing "
                + "items, a cold or incorrect order, or a delivery that never arrived. State the amount "
                + "in cents. Small refunds are approved immediately; anything larger is handed to a human "
                + "automatically, so always call this rather than promising a refund yourself.'";
        assertEquals("", CustomerFacingReply.sanitize(raw));
        assertTrue(CustomerFacingReply.looksLikeInternalMonologue(raw));
    }

    @Test
    void allowsNaturalCustomerReply() {
        String raw = "Of course — I can help with a refund. Was something missing, wrong, or never delivered?";
        assertEquals(raw, CustomerFacingReply.sanitize(raw));
        assertFalse(CustomerFacingReply.looksLikeInternalMonologue(raw));
    }
}
