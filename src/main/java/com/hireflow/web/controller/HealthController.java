package com.hireflow.web.controller;

import com.hireflow.context.ApplicationContext;
import com.hireflow.web.listener.WebContextListener;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;

/**
 * Controller providing minimal system health status (GET /health).
 * <p>
 * Emits sanitized diagnostic JSON. Never exposes passwords, internal IP addresses,
 * or database connection strings.
 */
@WebServlet(name = "HealthController", urlPatterns = {"/health"})
public class HealthController extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        resp.setHeader("Pragma", "no-cache");

        Object ctxAttr = getServletContext().getAttribute(WebContextListener.APP_CONTEXT_ATTR);
        boolean isUp = false;
        String mode = "UNKNOWN";
        boolean dbHealthy = false;
        int totalDrives = 0;

        if (ctxAttr instanceof ApplicationContext) {
            ApplicationContext ctx = (ApplicationContext) ctxAttr;
            isUp = !ctx.isClosed();
            mode = ctx.getMode().name();
            if (ctx.getMode().isJdbc()) {
                dbHealthy = ctx.getDataSourceProvider()
                        .map(ds -> ds.verifyConnectionHealth(2))
                        .orElse(false);
            } else {
                dbHealthy = true; // In-memory mode is always healthy
            }
            try {
                totalDrives = ctx.getDriveService().getTotalDrivesCount();
            } catch (Exception ignored) {
            }
        }

        int statusCode = isUp && dbHealthy ? HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE;
        resp.setStatus(statusCode);

        PrintWriter out = resp.getWriter();
        out.print("{\n");
        out.print("  \"status\": \"" + (isUp && dbHealthy ? "UP" : "DOWN") + "\",\n");
        out.print("  \"service\": \"HireFlow Web Portal\",\n");
        out.print("  \"milestone\": \"Stage 3A (CAP-6)\",\n");
        out.print("  \"storageMode\": \"" + mode + "\",\n");
        out.print("  \"databaseHealthy\": " + dbHealthy + ",\n");
        out.print("  \"totalDrives\": " + totalDrives + ",\n");
        out.print("  \"timestamp\": \"" + Instant.now().toString() + "\"\n");
        out.print("}\n");
        out.flush();
    }
}
