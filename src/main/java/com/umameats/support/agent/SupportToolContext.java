package com.umameats.support.agent;

import com.umameats.chat.model.ChatPrincipal;
import com.umameats.support.model.SupportThread;

/**
 * Everything a tool is allowed to know about the caller.
 *
 * <p>Tools take the identity from here rather than from their arguments, so a
 * model that hallucinates someone else's customer id cannot reach their data.
 *
 * @param thread mutable; tools may update it and the agent persists it after the turn
 */
public record SupportToolContext(ChatPrincipal principal, SupportThread thread) {
}
