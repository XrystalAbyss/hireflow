package com.hireflow.network.client;

import com.hireflow.network.protocol.ProtocolConstants;
import com.hireflow.network.protocol.ResponseMessage;

import java.net.ConnectException;
import java.util.List;
import java.util.Map;

/**
 * Command-line demonstration client for the HireFlow TCP Networking Subsystem.
 * Demonstrates connectivity, command execution, structured protocol exchange, and event subscription.
 * Designed for clear presentation during an Advanced Java viva examination.
 */
public class NetworkDemoClient {

    public static void main(String[] args) {
        String host = ProtocolConstants.DEFAULT_HOST;
        int port = ProtocolConstants.DEFAULT_PORT;

        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }
        if (args.length >= 2) {
            host = args[1];
        }

        System.out.println("===============================================================================");
        System.out.println("              HIREFLOW - JAVA TCP CLIENT DEMONSTRATION                         ");
        System.out.println("        Event Broadcasting & Subscription Management (Stage 2C.2)         ");
        System.out.println("===============================================================================");
        System.out.printf("Connecting to HireFlow TCP Server at %s:%d...%n%n", host, port);

        try (HireFlowTcpClient client = new HireFlowTcpClient(host, port)) {
            client.connect();
            System.out.println("[+] Successfully connected to TCP Server!");

            // 1. PING / PONG
            System.out.println("\n-------------------------------------------------------------------------------");
            System.out.println("1. Connectivity Check (PING / PONG)");
            System.out.println("-------------------------------------------------------------------------------");
            long startPing = System.currentTimeMillis();
            ResponseMessage pingResp = client.ping();
            long pingLatency = System.currentTimeMillis() - startPing;
            System.out.println("   Status   : " + pingResp.status() + " (Code: " + pingResp.code() + ")");
            System.out.println("   Response : " + pingResp.message());
            System.out.println("   Data     : " + pingResp.data());
            System.out.println("   Latency  : " + pingLatency + " ms");

            // 2. SERVER_INFO
            System.out.println("\n-------------------------------------------------------------------------------");
            System.out.println("2. Server Capability & Metadata (SERVER_INFO)");
            System.out.println("-------------------------------------------------------------------------------");
            ResponseMessage infoResp = client.getServerInfo();
            System.out.println("   Status   : " + infoResp.status() + " (Code: " + infoResp.code() + ")");
            if (infoResp.data() instanceof Map<?, ?> infoMap) {
                for (Map.Entry<?, ?> entry : infoMap.entrySet()) {
                    System.out.printf("   - %-18s: %s%n", entry.getKey(), entry.getValue());
                }
            }

            // 3. GET_DRIVES
            System.out.println("\n-------------------------------------------------------------------------------");
            System.out.println("3. Public Recruitment Drives Catalog (GET_DRIVES)");
            System.out.println("-------------------------------------------------------------------------------");
            ResponseMessage drivesResp = client.getDrives();
            System.out.println("   Status   : " + drivesResp.status() + " (Code: " + drivesResp.code() + ")");

            if (drivesResp.data() instanceof Map<?, ?> dataMap && dataMap.get("drives") instanceof List<?> list) {
                System.out.printf("%n   %-10s | %-16s | %-32s | %-8s | %-12s%n",
                        "Drive ID", "Company", "Job Title", "Min CGPA", "Deadline");
                System.out.println("   -----------+------------------+----------------------------------+----------+-------------");

                for (Object item : list) {
                    if (item instanceof Map<?, ?> d) {
                        System.out.printf("   %-10s | %-16s | %-32s | %-8s | %-12s%n",
                                d.get("driveId"),
                                d.get("companyName"),
                                d.get("jobTitle"),
                                d.get("minimumCgpa"),
                                d.get("applicationDeadline"));
                    }
                }
            }

            // 4. SUBSCRIBE_EVENTS
            System.out.println("\n-------------------------------------------------------------------------------");
            System.out.println("4. Subscribe to Public Announcements (SUBSCRIBE_EVENTS)");
            System.out.println("-------------------------------------------------------------------------------");
            ResponseMessage subResp = client.subscribeEvents(event -> {
                System.out.println("\n[!] ASYNCHRONOUS EVENT RECEIVED FROM SERVER:");
                System.out.println("    Type   : " + event.getEventType());
                System.out.println("    EventId: " + event.getEventId());
                System.out.println("    Message: " + event.message());
                System.out.println("    Data   : " + event.getEventPayload());
            });
            System.out.println("   Status   : " + subResp.status() + " (Code: " + subResp.code() + ")");
            System.out.println("   Response : " + subResp.message());
            System.out.println("   Details  : " + subResp.data());

            // 5. HEARTBEAT
            System.out.println("\n-------------------------------------------------------------------------------");
            System.out.println("5. Health Check & Heartbeat (HEARTBEAT)");
            System.out.println("-------------------------------------------------------------------------------");
            ResponseMessage hbResp = client.heartbeat();
            System.out.println("   Status   : " + hbResp.status() + " (Code: " + hbResp.code() + ")");
            System.out.println("   Response : " + hbResp.message());
            System.out.println("   Data     : " + hbResp.data());

            // 6. UNSUBSCRIBE_EVENTS
            System.out.println("\n-------------------------------------------------------------------------------");
            System.out.println("6. Unsubscribe from Announcements (UNSUBSCRIBE_EVENTS)");
            System.out.println("-------------------------------------------------------------------------------");
            ResponseMessage unsubResp = client.unsubscribeEvents();
            System.out.println("   Status   : " + unsubResp.status() + " (Code: " + unsubResp.code() + ")");
            System.out.println("   Response : " + unsubResp.message());
            System.out.println("   Details  : " + unsubResp.data());

            // 7. Clean Disconnect
            System.out.println("\n-------------------------------------------------------------------------------");
            System.out.println("7. Clean Disconnect");
            System.out.println("-------------------------------------------------------------------------------");
            System.out.println("   Closing client socket cleanly...");
            client.close();
            System.out.println("[+] Socket closed. Demonstration completed successfully!");

        } catch (ConnectException ce) {
            System.err.println("\n[!] Connection refused: No HireFlow TCP server is running at " + host + ":" + port);
            System.err.println("    Please start the server first before launching this client.");
        } catch (Exception ex) {
            System.err.println("\n[!] Error during TCP client demonstration: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
