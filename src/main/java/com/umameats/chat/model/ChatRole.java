package com.umameats.chat.model;

/**
 * Which app a chat participant is using. Delivery chat uses this to decide who
 * the counterpart is; the support agent uses it to gate tools, so a driver can
 * never reach a customer refund and vice versa.
 */
public enum ChatRole {
    CUSTOMER,
    DRIVER
}
