package com.umameats.support.service;

import com.umameats.chat.model.ChatPrincipal;
import com.umameats.chat.security.ChatForbiddenException;
import com.umameats.support.model.SupportMessage;
import com.umameats.support.model.SupportThread;
import com.umameats.support.model.SupportThreadState;
import com.umameats.support.repository.SupportMessageRepository;
import com.umameats.support.repository.SupportThreadRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Lifecycle of support conversations, separate from the agent that talks in them.
 */
@Slf4j
@Service
public class SupportThreadService {

    private static final int MESSAGE_PAGE_SIZE = 50;

    private final SupportThreadRepository threadRepository;
    private final SupportMessageRepository messageRepository;

    public SupportThreadService(
            SupportThreadRepository threadRepository,
            SupportMessageRepository messageRepository) {
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * The user's open conversation, created if they do not have one.
     *
     * <p>Reusing an open thread is what makes support feel continuous: reopening
     * the screen resumes where they left off instead of losing the context the
     * agent already gathered.
     *
     * @param orderId order the user opened support from, may be null
     */
    public SupportThread getOrCreateThread(ChatPrincipal principal, String orderId) {
        SupportThread thread = threadRepository.findOpenThread(principal.id())
                .orElseGet(() -> createThread(principal, orderId));

        // Arriving from a different order re-points the existing thread, so the
        // agent stops answering about the previous one.
        if (orderId != null && !orderId.equals(thread.getOrderId())) {
            thread.setOrderId(orderId);
            thread.setUpdatedAt(System.currentTimeMillis());
            threadRepository.save(thread);
        }
        return thread;
    }

    public SupportThread requireOwnedThread(String threadId, ChatPrincipal principal) {
        SupportThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ChatForbiddenException("Conversation not found"));

        if (!principal.id().equals(thread.getPrincipalId())) {
            log.warn("Rejected support thread {} access by {}", threadId, principal.id());
            throw new ChatForbiddenException("This conversation belongs to someone else");
        }
        return thread;
    }

    public List<SupportMessage> listMessages(SupportThread thread) {
        return messageRepository.findRecent(thread.getThreadId(), MESSAGE_PAGE_SIZE);
    }

    public SupportThread escalate(SupportThread thread, String reason) {
        thread.setState(SupportThreadState.WAITING_HUMAN.name());
        thread.setEscalatedAt(System.currentTimeMillis());
        thread.setEscalationReason(reason);
        thread.setUpdatedAt(System.currentTimeMillis());
        return threadRepository.save(thread);
    }

    /** Closing a thread means the next question starts with a clean slate. */
    public SupportThread resolve(SupportThread thread) {
        thread.setState(SupportThreadState.RESOLVED.name());
        thread.setUpdatedAt(System.currentTimeMillis());
        return threadRepository.save(thread);
    }

    private SupportThread createThread(ChatPrincipal principal, String orderId) {
        long now = System.currentTimeMillis();

        SupportThread thread = new SupportThread();
        thread.setThreadId(UUID.randomUUID().toString());
        thread.setPrincipalId(principal.id());
        thread.setPrincipalRole(principal.role().name());
        thread.setState(SupportThreadState.AI.name());
        thread.setOrderId(orderId);
        thread.setLocale(principal.locale());
        thread.setCreatedAt(now);
        thread.setUpdatedAt(now);
        thread.setRefundedCents(0L);

        log.info("Opened support thread {} for {} {}", thread.getThreadId(), principal.role(), principal.id());
        return threadRepository.save(thread);
    }
}
