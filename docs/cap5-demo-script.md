# HireFlow — Stage 2 CAP-5 Live Demonstration Script (Final Freeze)

> **Evaluation:** Continuous Assessment Program (CAP-5) Defense  
> **Topic:** HireFlow Stage 2 Final — JDBC Persistence, Multi-Threaded TCP Sockets & Swing Dashboard Integration  
> **Target Duration:** 5–7 Minutes Live Demonstration + Defense Q&A  
> **Technology Stack:** Core Java 21 LTS, Java Sockets (`java.net.Socket`, `ServerSocket`), PostgreSQL 16, HikariCP, Flyway, Swing GUI (Zero Spring, Zero Web Frameworks)  
> **Automated Verification:** **285 Passing Tests** across 11 test suites.

---

## 1. Technical Demonstration Overview

This live demonstration proves the seamless integration between **PostgreSQL ACID database transactions**, **high-concurrency TCP socket networking**, and **non-blocking Swing desktop user interfaces** in core Java 21:

```
[Swing UI (EDT)] ──> [RecruitmentDriveService] ──> [TransactionManager (JDBC)] ──> [PostgreSQL 16]
        │                                                                               │
 [NetworkingPanel]                                                            (Commit Succeeded)
        │                                                                               │
[HireFlowTcpServer] <─── [ApplicationContext] <─── [Post-Commit Hook] <─────────────────┘
        │
   (Port 8085)
        ├───────> Client Terminal #1 (Active Subscriber)  ──> Receives Public Event Frame
        ├───────> Client Terminal #2 (Active Subscriber)  ──> Receives Public Event Frame
        └───────> Client Terminal #3 (Active Subscriber)  ──> Receives Public Event Frame
```

---

## 2. Minute-by-Minute Demonstration Script (6 Minutes Total)

```
Timeline:
[00:00 - 01:00] Act 1: Launch in JDBC Dev Mode & Verify Database Connection Pool
[01:00 - 02:30] Act 2: NetworkingPanel Dashboard & Starting the TCP Server
[02:30 - 03:45] Act 3: Multi-Client Connection & Subscription Registry
[03:45 - 05:00] Act 4: Transactional Post-Commit Event Broadcasting
[05:00 - 06:00] Act 5: Disconnect Resilience, Server Shutdown & Resource Cleanup
[06:00 - 07:00] Evaluation Defense Q&A
```

---

### Act 1: Launch in JDBC Mode & Pool Verification (00:00 – 01:00)

**Presenter Talking Points:**
> *"Good morning, Professor. Today we demonstrate HireFlow Stage 2C.3. Our desktop application runs on pure core Java 21, managing both high-integrity PostgreSQL database operations and concurrent TCP client sockets without any third-party web frameworks."*

**Live Action:**
1. Open PowerShell and launch the compiled application in PostgreSQL dev mode:
   ```bash
   java -Dhireflow.storage.mode=JDBC_DEV -jar target/hireflow-1.0.0.jar
   ```
2. Point out the window title bar and status bar:
   - *"Notice the title bar confirms `[PostgreSQL: hireflow_dev]`. Our `ApplicationContext` initialized Flyway migrations, validated schema Version 1, and established a HikariCP pool with 10 connections."*
   - *"The bottom status bar shows our database catalog: 11 Students, 5 Drives, 6 Applications, and 2 Confirmed Interview Sessions."*

---

### Act 2: NetworkingPanel Dashboard & Server Lifecycle (01:00 – 02:30)

**Presenter Talking Points:**
> *"Now we navigate to our newly integrated Networking screen. In HireFlow, the TCP server lifecycle is explicitly governed by the ApplicationContext and operated through a non-blocking Swing management screen."*

**Live Action:**
1. Click **"Networking"** in the sidebar.
2. Direct the evaluator's attention to the screen layout:
   - **Status Badge:** Shows `OFFLINE` (light red badge).
   - **Port Input:** Configurable listen port (`8085` default, supports dynamic `0`).
   - **Stat Cards:** 4 live metric cards: `CONNECTED CLIENTS (0)`, `ACTIVE SUBSCRIBERS (0)`, `EVENTS DISPATCHED (0)`, and `SERVER UPTIME (00:00:00)`.
   - **Protocol Standards:** Shows protocol framing rules: Single-line JSON, 64 KB maximum frame limit, strict public allowlist.
3. Click **"▶ Start TCP Server"**:
   - The button disables instantly, and a `SwingWorker` performs non-blocking `ServerSocket.bind()` on loopback `127.0.0.1:8085`.
   - The status badge transitions smoothly to `RUNNING` (green).
   - The bound socket displays `127.0.0.1:8085`.
   - The uptime timer increments every second (`00:00:01`, `00:00:02`...).
   - The Activity Log table appends: `SERVER_START | SUCCESS | Bound to 127.0.0.1:8085`.

---

### Act 3: Multi-Client Connection & Subscription Registry (02:30 – 03:45)

**Presenter Talking Points:**
> *"To demonstrate our multi-threaded client-server socket architecture, we will spawn three separate Java TCP client terminals. Each client runs an independent socket connection serviced by our server's thread pool."*

**Live Action:**
1. Open 3 PowerShell terminals side-by-side and execute the demo client in each:
   ```bash
   # Terminal 1:
   java -cp target/hireflow-1.0.0.jar com.hireflow.network.client.NetworkDemoClient 8085 127.0.0.1

   # Terminal 2:
   java -cp target/hireflow-1.0.0.jar com.hireflow.network.client.NetworkDemoClient 8085 127.0.0.1

   # Terminal 3:
   java -cp target/hireflow-1.0.0.jar com.hireflow.network.client.NetworkDemoClient 8085 127.0.0.1
   ```
2. In all 3 terminals, choose **Option 3** (`Subscribe to Public Recruitment Announcements`).
3. Look at the Swing **Networking Dashboard**:
   - **CONNECTED CLIENTS** immediately increments to `3`.
   - **ACTIVE SUBSCRIBERS** immediately increments to `3`.
   - The activity stream updates in real-time on the EDT:
     ```
     [HH:mm:ss] CLIENT_CONNECTED  | SUCCESS | Remote address /127.0.0.1:XXXXX
     [HH:mm:ss] CLIENT_SUBSCRIBED | SUCCESS | Registered active announcement subscriber
     ```
4. Explain the threading model:
   - *"Each client session is handled by a worker thread in `ThreadPoolExecutor`. Subscribed clients receive asynchronous events through their own isolated `ArrayBlockingQueue` with a bounded capacity of 50 items and a dedicated writer daemon thread, ensuring slow clients cannot back up the server or freeze other clients."*

---

### Act 4: Transactional Post-Commit Event Broadcasting (03:45 – 05:00)

**Presenter Talking Points:**
> *"Now we demonstrate the critical link between ACID database transactions and network broadcasting. When an administrator creates a recruitment drive in Swing, the broadcast event must ONLY fire after the PostgreSQL transaction commits. If the transaction rolls back, zero events must be broadcast."*

**Live Action:**
1. In HireFlow's Swing UI, click **"Companies & Drives"** in the sidebar.
2. Select the **"Recruitment Drives"** tab and click **"Create Placement Drive"**.
3. Fill in the form:
   - Drive ID: `DRV-CAP5-DEMO`
   - Company: `CMP-001` (Google Cloud)
   - Job Role: `Distributed Systems Engineer`
   - Minimum CGPA: `8.50`
   - Eligible Branches: `CSE`, `ECE`
   - Date: 10 days in the future
4. Click **"Publish Recruitment Drive"**.
5. Switch to the 3 client terminal windows:
   - **All 3 terminals simultaneously display the received event**:
     ```json
     <<< ASYNC EVENT RECEIVED:
     {
       "version": "1.0",
       "status": "EVENT",
       "code": 200,
       "eventType": "JOB_DRIVE_CREATED",
       "data": {
         "driveId": "DRV-CAP5-DEMO",
         "companyName": "Google Cloud",
         "jobRole": "Distributed Systems Engineer",
         "minimumCgpa": 8.5,
         "eligibleBranches": ["CSE", "ECE"]
       }
     }
     ```
6. Return to **"Networking"** on Swing:
   - **EVENTS DISPATCHED** card now displays `1`.
   - Activity log table records: `EVENT_DISPATCHED | SUCCESS | Broadcast JOB_DRIVE_CREATED to 3 subscribers`.
7. Explain the candidate privacy guardrail:
   - *"Notice what is NOT in the JSON payload: no student roll numbers, no candidate GPA records, no application statuses. Our `NetworkEventBroadcaster` enforces a strict allowlist policy protecting candidate privacy."*

---

### Act 5: Disconnect Resilience & Clean Server Shutdown (05:00 – 06:00)

**Presenter Talking Points:**
> *"Finally, we demonstrate fault isolation, thread cleanup, and graceful shutdown ordering."*

**Live Action:**
1. In Terminal 1, press `Ctrl+C` (abrupt socket disconnection):
   - In Swing, **CONNECTED CLIENTS** drops to `2` and **ACTIVE SUBSCRIBERS** drops to `2`.
   - Activity log records: `CLIENT_DISCONNECTED | CLOSED | Client socket disconnected`.
   - Point out: *"The server cleanly terminated the writer daemon thread and unlinked the subscriber without throwing unhandled exceptions."*
2. Post another drive or ping from Terminal 2: Terminal 2 and 3 continue to function flawlessly.
3. On the **Networking Dashboard**, click **"■ Stop TCP Server"**:
   - The status badge transitions to `STOPPING` then `OFFLINE`.
   - Terminals 2 and 3 show clean disconnect messages.
   - The main HireFlow desktop application continues running smoothly with its PostgreSQL connection pool intact.
4. Close the HireFlow desktop window:
   - `ApplicationContext.close()` guarantees strict shutdown ordering: TCP server and client threads are terminated first, followed by the HikariCP connection pool.

---

## 3. Defense Questions & Architectural Answers (Viva Preparation)

### Q1: Why not use Spring Boot, WebSockets, or Kafka for this networking milestone?
> **Answer:** Standard university placement portals operate within bounded campus LAN or cloud VPC perimeters. Adding heavy frameworks like Spring Boot or Kafka adds massive dependency overhead, external broker processes, and configuration bloat. By implementing core Java sockets with newline-delimited JSON framing, a bounded `ArrayBlockingQueue` per client, and dedicated writer daemons, HireFlow achieves deterministic sub-millisecond dispatching with zero external runtime dependencies.

### Q2: How does HireFlow guarantee that a failed database transaction does not broadcast a phantom event?
> **Answer:** Through `TransactionManager.registerPostCommitAction(Runnable)`. During `RecruitmentDriveService.createDrive()`, the event broadcaster is registered as a post-commit hook stored in the transaction's ThreadLocal context. Only after `connection.commit()` returns successfully does the `TransactionManager` execute the hook. Furthermore, post-commit execution is placed outside the rollback try-catch block and wrapped in exception isolation, so even if a network broadcast listener fails, the already-committed database operation remains intact.

### Q3: What prevents a slow client from exhausting server memory or blocking the Swing EDT?
> **Answer:** Three distinct architectural safeguards:
> 1. **Thread Separation:** The Swing EDT only interacts with UI components; server start/stop runs on `SwingWorker` background threads.
> 2. **Bounded Backpressure:** Every subscriber has an isolated `ArrayBlockingQueue` capped at 50 frames. Dispatching uses non-blocking `offer()`.
> 3. **Ejection Policy:** If a client's queue fills up to 50 items because it is slow or frozen, `offer()` fails and the server immediately unregisters the client, shuts down its writer daemon, and closes its socket, keeping server memory strictly bounded.

### Q4: How do you verify socket liveness for idle subscribers?
> **Answer:** Java's `OutputStream.write()` only writes to the local OS network buffer. To differentiate an active connection from a dead socket, our server sends a `HEARTBEAT_PROBE` frame every 30 seconds of subscriber inactivity. The client's `HireFlowTcpClient` immediately returns a `HEARTBEAT_ACK` frame. If the client fails to acknowledge or the socket encounters an `IOException`, the server detects the broken link and reclaims all resources.

---

## 4. Verification Evidence Summary

- **Total Test Count:** **285 passing tests** (0 failed, 0 errors, 0 skipped).
- **Maven Verification Command:**
  ```bash
  mvn clean verify
  ```
- **Key Test Suites:**
  - `Stage2FinalAcceptanceTest`: 2 comprehensive tests covering the real Swing GUI drive creation workflow, 3-client TCP broadcast, subscriber disconnect isolation, and PostgreSQL restart persistence.
  - `NetworkingPanelIntegrationTest`: 17 comprehensive tests verifying Swing controls, live metrics, EDT responsiveness, post-commit transaction hook isolation, and multi-client broadcasting.
  - `HireFlowEventBroadcastingTest`: 18 tests verifying backpressure, queue limits, subscriber ejection, and allowlist privacy.
  - `HireFlowTcpServerTest`: 22 tests verifying protocol framing, request handling, and socket timeouts.
  - `JdbcPersistenceIntegrationTest`: 9 tests verifying end-to-end PostgreSQL CRUD across restarts.
  - `JdbcInterviewSessionRepositoryTest`: 23 tests verifying GiST temporal exclusion constraints and schedule publication transactions.
