package com.hireflow.network.server;

import com.hireflow.context.ApplicationContext;
import com.hireflow.db.TransactionManager;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.network.protocol.ResponseMessage;
import com.hireflow.service.event.HireFlowEvent;
import com.hireflow.service.event.HireFlowEventListener;
import com.hireflow.service.event.HireFlowEventType;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Event integration adapter bridging HireFlow domain events to connected TCP subscriber clients.
 * <p>
 * Key responsibilities:
 * 1. Security boundary: Strictly restricts broadcast events to permitted public announcements
 *    (JOB_DRIVE_CREATED and COMPANY_ADDED). All private student records, applications, and interview
 *    data are filtered and rejected.
 * 2. Non-blocking dispatch: Asynchronously enqueues events into per-subscriber bounded queues;
 *    never blocks business services or the Swing Event Dispatch Thread (EDT).
 * 3. Transactional correctness: Defers public event dispatch until the active database transaction
 *    successfully commits via {@link TransactionManager#registerPostCommitAction(Runnable)}.
 *    Rolled-back transactions never broadcast announcements.
 */
public class NetworkEventBroadcaster implements HireFlowEventListener {

    private final ApplicationContext context;
    private final HireFlowTcpServer server;
    private final Set<ClientSessionHandler> subscribers = ConcurrentHashMap.newKeySet();
    private final AtomicLong eventSequence = new AtomicLong(0);

    /**
     * Strict allowlist of event types permitted for unauthenticated public broadcast.
     */
    private static final Set<HireFlowEventType> PERMITTED_PUBLIC_EVENTS = Set.of(
            HireFlowEventType.JOB_DRIVE_CREATED,
            HireFlowEventType.COMPANY_ADDED
    );

    public NetworkEventBroadcaster() {
        this(null, null);
    }

    public NetworkEventBroadcaster(ApplicationContext context) {
        this(context, null);
    }

    public NetworkEventBroadcaster(ApplicationContext context, HireFlowTcpServer server) {
        this.context = context;
        this.server = server;
    }

    public void registerSubscriber(ClientSessionHandler session) {
        if (session != null) {
            subscribers.add(session);
        }
    }

    public void unregisterSubscriber(ClientSessionHandler session) {
        if (session != null) {
            subscribers.remove(session);
        }
    }

    public int getSubscriberCount() {
        return subscribers.size();
    }

    public long getDispatchedEventCount() {
        return eventSequence.get();
    }

    public void clearSubscribers() {
        subscribers.clear();
    }

    @Override
    public void onEvent(HireFlowEvent event) {
        if (event == null || event.type() == null) {
            return;
        }

        // Strict security boundary: drop non-public events immediately
        if (!PERMITTED_PUBLIC_EVENTS.contains(event.type())) {
            return;
        }

        // Transaction correctness: defer until database transaction commit
        if (TransactionManager.isCurrentTransactionActive()) {
            TransactionManager.registerPostCommitAction(() -> dispatchPublicAnnouncement(event));
        } else {
            dispatchPublicAnnouncement(event);
        }
    }

    private void dispatchPublicAnnouncement(HireFlowEvent event) {
        try {
            Map<String, Object> publicPayload = buildPublicPayload(event);

            String eventId = "EVT-" + System.currentTimeMillis() + "-" + eventSequence.incrementAndGet();
            String timestamp = event.timestamp() != null ? event.timestamp().toString() : LocalDateTime.now().toString();
            String message = event.message() != null ? event.message() : "Public recruitment announcement";

            ResponseMessage eventEnvelope = ResponseMessage.event(
                    event.type().name(),
                    eventId,
                    timestamp,
                    message,
                    publicPayload
            );

            // Non-blocking dispatch to all registered subscribers
            int dispatchedCount = 0;
            for (ClientSessionHandler subscriber : subscribers) {
                boolean accepted = subscriber.offerEvent(eventEnvelope);
                if (!accepted) {
                    // If queue rejected the event (overload or closed), unregister subscriber
                    subscribers.remove(subscriber);
                } else {
                    dispatchedCount++;
                }
            }

            if (server != null) {
                server.logActivity("EVENT_BROADCAST",
                        "Broadcast " + event.type() + " (" + (event.sourceId() != null ? event.sourceId() : "") + ") to " + dispatchedCount + " subscriber(s)",
                        "DISPATCHED");
            }
        } catch (Exception t) {
            System.err.println("[NetworkEventBroadcaster] Error dispatching public announcement: " + t.getMessage());
        }
    }

    private Map<String, Object> buildPublicPayload(HireFlowEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();

        if (event.type() == HireFlowEventType.JOB_DRIVE_CREATED) {
            String driveId = event.sourceId();
            payload.put("driveId", driveId != null ? driveId : "");

            if (context != null && context.getDriveRepository() != null && driveId != null) {
                Optional<RecruitmentDrive> driveOpt = context.getDriveRepository().findById(driveId);
                if (driveOpt.isPresent()) {
                    RecruitmentDrive d = driveOpt.get();
                    payload.put("companyId", d.getCompanyId());
                    String companyName = context.getCompanyRepository() != null
                            ? context.getCompanyRepository().findById(d.getCompanyId()).map(Company::getName).orElse(d.getCompanyId())
                            : d.getCompanyId();
                    payload.put("companyName", companyName);
                    payload.put("jobTitle", d.getJobTitle());
                    payload.put("minimumCgpa", d.getMinimumCgpa());
                    payload.put("eligibleBranches", new ArrayList<>(d.getEligibleBranches()));
                    payload.put("applicationDeadline", d.getApplicationDeadline().toString());
                    return payload;
                }
            }

            // Fallback if drive record details unavailable
            payload.put("description", event.type().getDescription());
            payload.put("announcement", event.message() != null ? event.message() : "");

        } else if (event.type() == HireFlowEventType.COMPANY_ADDED) {
            String companyId = event.sourceId();
            payload.put("companyId", companyId != null ? companyId : "");

            if (context != null && context.getCompanyRepository() != null && companyId != null) {
                Optional<Company> compOpt = context.getCompanyRepository().findById(companyId);
                if (compOpt.isPresent()) {
                    Company c = compOpt.get();
                    payload.put("companyName", c.getName());
                    payload.put("industry", c.getIndustry());
                    return payload;
                }
            }

            payload.put("description", event.type().getDescription());
            payload.put("announcement", event.message() != null ? event.message() : "");
        }

        return payload;
    }
}
