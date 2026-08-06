package com.umameats.chat.model;

/**
 * The authenticated caller of any chat request, resolved from the shared JWT.
 *
 * @param id        customer id or driver id (the JWT subject)
 * @param role      which app the caller is using
 * @param email     may be null for tokens issued before the email claim existed
 * @param firstName may be null; used only to address the user by name
 * @param locale    BCP-47 language tag replies should use
 */
public record ChatPrincipal(
        String id,
        ChatRole role,
        String email,
        String firstName,
        String locale) {

    public boolean isDriver() {
        return role == ChatRole.DRIVER;
    }

    public boolean isCustomer() {
        return role == ChatRole.CUSTOMER;
    }

    public String displayName() {
        if (firstName != null && !firstName.isBlank()) {
            return firstName;
        }
        return isDriver() ? "Driver" : "Customer";
    }
}
