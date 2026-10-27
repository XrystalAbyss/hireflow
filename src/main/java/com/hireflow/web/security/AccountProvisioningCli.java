package com.hireflow.web.security;

import com.hireflow.context.ApplicationContext;
import com.hireflow.db.DatabaseConfig;

import java.io.Console;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Administrative CLI tool for explicit, secure account provisioning.
 * <p>
 * Constraints strictly enforced:
 * <ul>
 *     <li>Requires explicit opt-in via JVM system property {@code -Dhireflow.allow.provisioning=true}.</li>
 *     <li>Never accepts passwords committed in Git or provides default credentials.</li>
 *     <li>Reads credentials securely via {@link System#console()} or explicit property.</li>
 *     <li>Normalizes usernames and verifies student foreign key constraints.</li>
 * </ul>
 */
public class AccountProvisioningCli {

    private static final Logger LOGGER = Logger.getLogger(AccountProvisioningCli.class.getName());

    public static void main(String[] args) {
        String allowed = System.getProperty("hireflow.allow.provisioning", "false");
        if (!"true".equalsIgnoreCase(allowed)) {
            System.err.println("ERROR: Account provisioning is disabled. Specify -Dhireflow.allow.provisioning=true to authorize.");
            System.exit(1);
            return;
        }

        if (args.length < 3) {
            System.err.println("Usage: java AccountProvisioningCli <username> <role: STUDENT|PLACEMENT_OFFICER|ADMINISTRATOR> <displayName> [studentId]");
            System.exit(1);
            return;
        }

        String username = args[0];
        UserRole role;
        try {
            role = UserRole.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException ex) {
            System.err.println("ERROR: Invalid role. Must be STUDENT, PLACEMENT_OFFICER, or ADMINISTRATOR.");
            System.exit(1);
            return;
        }

        String displayName = args[2];
        String studentId = (args.length >= 4) ? args[3] : null;

        char[] passwordChars;
        String propPassword = System.getProperty("hireflow.provision.password");
        if (propPassword != null && !propPassword.isEmpty()) {
            passwordChars = propPassword.toCharArray();
        } else {
            Console console = System.console();
            if (console == null) {
                System.err.println("ERROR: No interactive console available and -Dhireflow.provision.password not set.");
                System.exit(1);
                return;
            }
            passwordChars = console.readPassword("Enter password for user '%s': ", username);
        }

        try (ApplicationContext ctx = ApplicationContext.create(DatabaseConfig.load())) {
            AuthenticationService authService = ctx.getAuthenticationService();
            UserAccount account = authService.provisionAccount(username, passwordChars, role, studentId, displayName);
            System.out.println("SUCCESS: User account provisioned with ID: " + account.getId() + ", role: " + account.getRole());
        } catch (Exception ex) {
            System.err.println("ERROR: Failed to provision user account: " + ex.getMessage());
            System.exit(1);
        } finally {
            if (passwordChars != null) {
                Arrays.fill(passwordChars, '\0');
            }
        }
    }
}
