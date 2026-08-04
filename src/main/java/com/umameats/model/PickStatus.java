package com.umameats.model;

public final class PickStatus {
    public static final String PENDING = "PENDING";
    public static final String FOUND = "FOUND";
    public static final String SUBSTITUTED = "SUBSTITUTED";
    public static final String UNAVAILABLE = "UNAVAILABLE";

    private PickStatus() {}

    public static boolean isResolved(String status) {
        return FOUND.equals(status) || SUBSTITUTED.equals(status) || UNAVAILABLE.equals(status);
    }
}
