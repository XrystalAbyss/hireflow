# HireFlow TCP Networking Protocol Specification (v1.0)
## Stage 2C.3 — Swing Networking Dashboard and TCP Server Lifecycle Integration

---

### 1. Architectural Overview

HireFlow implements a low-overhead, bidirectional TCP client-server subsystem using standard Java `java.net.Socket` and `java.net.ServerSocket` primitives. Designed for campus recruitment coordination, the networking subsystem enables client terminals to query catalog state, inspect server capabilities, and subscribe to real-time corporate recruitment announcements without heavy third-party message brokers or web frameworks.

In Stage 2C.3, server lifecycle management is centralized inside `ApplicationContext` and managed through an interactive Swing desktop dashboard (`NetworkingPanel`):

```
+-----------------------------------------------------------------------------------------+
|                                    HIREFLOW DESKTOP                                     |
|  [ApplicationContext]                                                                   |
|         |                                                                               |
|         +---> [Database Infrastructure] (PostgreSQL 16 via HikariCP Connection Pool)    |
|         |                                                                               |
|         +---> [HireFlowTcpServer] (Explicit startup on localhost; bound to port 8085)   |
|         |            |                                                                  |
|         |            v                                                                  |
|         |     [ThreadPoolExecutor] (Worker pool: up to 25 concurrent connections)       |
|         |            |                                                                  |
|         |            +---> ClientSessionHandler #1 (Active Request Reader + Daemon)     |
|         |            +---> ClientSessionHandler #2 (Active Request Reader + Daemon)     |
|         |                                                                               |
|  [Swing EDT: NetworkingPanel]                                                           |
|         |                                                                               |
|         +---> [Server Controls] Start / Stop via SwingWorker (Zero UI Freezes)         |
|         +---> [Live Stat Cards] Connected Clients, Subscribers, Events, Server Uptime  |
|         +---> [Activity Stream] Sanitized In-Memory Telemetry Table (Zero Student PII)  |
+--------------------------------------------+--------------------------------------------+
                                             |
                   +-------------------------+-------------------------+
                   |                                                   |
                   v                                                   v
      [NetworkEventBroadcaster]                          [TransactionManager Hook]
     - Strict Event Allowlist Filter                     - `registerPostCommitAction(...)`
     - Clean Public Payload Builder                      - Dispatches ONLY after JDBC commit
     - Non-blocking Bounded Queue Offer                  - Zero announcements on rollback
     - Exception Isolation (Try/Catch)                   - Shielded from listener failures
                   |
     +-------------+-------------+
     |                           |
     v                           v
Subscriber #1 Queue         Subscriber #2 Queue
(Capacity: 50 FIFO)         (Capacity: 50 FIFO)
     |                           |
     v                           v
Writer Daemon #1            Writer Daemon #2
     |                           |
     v                           v
 TCP Client #1               TCP Client #2
 (Multiplexed Rx)            (Multiplexed Rx)
```

---

### 2. Wire Protocol Specification

- **Protocol Version:** `1.0`
- **Framing:** Strict single-line JSON terminated with standard newline (`\n`, UTF-8 encoded).
- **Maximum Frame Length:** 64 KB (`65,536` bytes) to prevent memory exhaustion from malformed frames (`ProtocolConstants.MAX_FRAME_LENGTH`).
- **Socket Read Timeout:** 10,000 ms (10 seconds) per cycle with active heartbeat acknowledgement probing for idle subscribers.

#### Request Envelope Structure
```json
{
  "version": "1.0",
  "requestId": "REQ-101",
  "command": "PING",
  "payload": {}
}
```

#### Response Envelope Structure
```json
{
  "version": "1.0",
  "requestId": "REQ-101",
  "status": "OK",
  "code": 200,
  "message": "PONG",
  "data": {
    "serverStatus": "ONLINE"
  }
}
```

---

### 3. Supported Protocol Commands

| Command | Direction | Parameters | Description | Status Codes |
| :--- | :--- | :--- | :--- | :--- |
| `PING` | Client $\rightarrow$ Server | `payload` (optional) | Latency and connectivity probe; echoes timestamp and server status. | `200 OK` |
| `SERVER_INFO` | Client $\rightarrow$ Server | None | Retrieves server name, active storage mode, uptime, and supported commands. | `200 OK` |
| `GET_DRIVES` | Client $\rightarrow$ Server | None | Returns the active public catalog of recruitment drives. | `200 OK` |
| `SUBSCRIBE_EVENTS` | Client $\rightarrow$ Server | None | Registers client connection to receive asynchronous public recruitment events. | `200 OK`, `400 Bad Request` |
| `UNSUBSCRIBE_EVENTS` | Client $\rightarrow$ Server | None | Unregisters client from the event delivery registry. | `200 OK` |
| `HEARTBEAT` | Client $\rightarrow$ Server | None | Explicit client-initiated keep-alive probe returning `HEARTBEAT_ACK`. | `200 OK` |
| `HEARTBEAT_ACK` | Client $\rightarrow$ Server | None | Client acknowledgement responding to server-initiated `HEARTBEAT_PROBE`. | `200 OK` |

---

### 4. Active Heartbeat Probing & Acknowledgement Verification

In standard socket networking, a successful return from `socket.getOutputStream().write(...)` merely indicates that packet bytes were placed into the local operating system's transmission buffer, not that the remote endpoint is alive or received the data.

To solve this correctness hazard:
1. When a subscribed client is idle for 3 read timeout cycles (30 seconds), the server dispatches a lightweight `HEARTBEAT_PROBE` frame.
2. The server marks `probePending = true` for the session.
3. The client implementation (`HireFlowTcpClient`) immediately responds with a `HEARTBEAT_ACK` frame upon receiving `HEARTBEAT_PROBE`.
4. The server's `ClientSessionHandler` receives the incoming `HEARTBEAT_ACK`, clears `probePending = false`, and records the successful round-trip confirmation.
5. If the next probe cycle arrives and `probePending` remains unacknowledged, or if the socket encounters an `IOException`, the dead socket is cleanly unbuffered, closed, and unlinked.

---

### 5. Asynchronous Event Broadcasting Envelope

When domain lifecycle events occur, the server broadcasts asynchronous event frames to all registered subscribers. Asynchronous event frames are distinguished by `"status": "EVENT"` and include an immutable event envelope:

```json
{
  "version": "1.0",
  "requestId": "none",
  "status": "EVENT",
  "code": 200,
  "eventType": "JOB_DRIVE_CREATED",
  "eventId": "3c027b68-f99a-4712-a7d0-1c73ecbb5cb1",
  "timestamp": "2026-10-11T11:43:20.125Z",
  "message": "Placement drive created: Cloud Platform Engineer",
  "data": {
    "driveId": "DRV-101",
    "companyId": "CMP-001",
    "companyName": "Google Cloud",
    "jobTitle": "Cloud Platform Engineer",
    "minimumCgpa": 8.0,
    "eligibleBranches": ["CSE", "IT"],
    "applicationDeadline": "2026-10-25"
  }
}
```

---

### 6. Privacy Safeguards & Public-Event Allowlist

To protect candidate personal data and sensitive university placement operations, `NetworkEventBroadcaster` enforces an uncompromising allowlist policy:

#### Permitted Public Events
1. **`JOB_DRIVE_CREATED`**: Broadcasts new job openings and public eligibility parameters (title, minimum CGPA, branches, deadline).
2. **`COMPANY_ADDED`**: Broadcasts new corporate partners joining the placement roster.

#### Prohibited Confidential Domains (Strictly Filtered)
The following events and data structures are **strictly banned** from network broadcasting:
- Student profile information (`Student` domain model).
- Student Roll Numbers, Names, and Academic Transcripts (`email`, `cgpa`, `backlogs`).
- Application submissions and status transitions (`APPLIED`, `SHORTLISTED`, `REJECTED`, `SELECTED`).
- Interview session schedules and time slots.
- Room allocations and confidential interview panels.
- Candidate evaluations and interview notes.

Furthermore, whole domain objects are never serialized directly; clean, minimal JSON maps containing only authorized public attributes are synthesized.

---

### 7. Slow-Client Backpressure & Overload Policy

A slow, frozen, or adversarial client must not impede other connected clients or block business services.

1. **Bounded Event Queues:** Each subscriber is allocated an isolated FIFO queue (`ArrayBlockingQueue<ResponseMessage>`) bounded at **50 events**.
2. **Independent Writer Daemons:** Each subscriber is served by an independent daemon thread (`hireflow-subscriber-writer-%d`) that drains its bounded queue directly to the client socket.
3. **Documented Overload Policy:** If a client stops consuming packets and its bounded queue reaches capacity (50 items), the non-blocking `offerEvent` call triggers immediate subscriber ejection:
   - The overloaded subscriber is instantly unregistered from the active subscriber registry.
   - The client socket is closed, aborting any stalled writes.
   - Server memory remains strictly bounded ($O(N)$ with $N \le 25$).
4. **Non-Blocking Dispatching:** Broadcaster iteration across subscribers never performs network I/O on the publishing thread. Event dispatching takes $< 50\ \mu\text{s}$ and never blocks business services or the Swing EDT.

---

### 8. Transactional Consistency & Exception Isolation

When HireFlow runs in PostgreSQL storage mode (`JDBC_DEV` / `JDBC_TEST`), recruitment drive creation occurs inside ACID database transactions.

- **Post-Commit Hooks:** The application registers an event broadcast action via `TransactionManager.registerPostCommitAction(Runnable)`.
- **Commit Boundary:** The public announcement is emitted **only after** `connection.commit()` has successfully completed on PostgreSQL.
- **Rollback Guarantee:** If a transaction is rolled back or fails due to a database exception, pending post-commit hooks are discarded from the thread-local context. **No ghost recruitment announcements are ever broadcast.**
- **Post-Commit Exception Isolation:** In `TransactionManager`, post-commit actions are executed strictly outside the transactional rollback block. Any exception thrown during listener notification is logged safely to `System.err` and cannot convert an already committed database change into an apparent rollback or failed transaction.
- **Broadcaster Isolation:** `NetworkEventBroadcaster.dispatchPublicAnnouncement` is wrapped in comprehensive `try-catch (Throwable t)` blocks so that subscriber formatting errors cannot interrupt calling threads.

---

### 9. Swing Desktop Networking Dashboard (`NetworkingPanel`)

The desktop application integrates an operational server management console without altering recruitment services:

1. **Server Lifecycle Ownership:** Controlled through `ApplicationContext.startTcpServer(port)` and `stopTcpServer()`.
2. **Dynamic Port Binding:** Allows configuring the listen port (default `8085`, or `0` for an ephemeral operating system port).
3. **Clean Shutdown Ordering:** `ApplicationContext.close()` disconnects active TCP clients and shuts down the server before closing the HikariCP database connection pool.
4. **Sanitized In-Memory Telemetry Stream:** A bounded deque (`capacity = 200`) captures network activity (`CLIENT_CONNECTED`, `CLIENT_SUBSCRIBED`, `EVENT_DISPATCHED`, `CLIENT_DISCONNECTED`) without persisting to the database and without logging confidential student records or database passwords.
5. **Non-Blocking Execution:** Start and stop actions run in `SwingWorker` instances to maintain a silky 60 FPS desktop user interface. Live metrics are refreshed every second via a `javax.swing.Timer` executing strictly on the EDT.
