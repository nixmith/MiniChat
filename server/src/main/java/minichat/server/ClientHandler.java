package minichat.server;

import java.io.*;
import java.net.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.*;

public class ClientHandler extends Thread {
    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private final SessionRegistry registry;
    private String username;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^username\\s*=\\s*(\\S.*)$");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public ClientHandler(Socket socket, SessionRegistry registry) {
        this.socket = socket;
        this.registry = registry;
    }

    @Override
    public void run() {
        try {
            // Setup I/O streams with UTF-8 encoding
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            // Phase 1: Registration
            if (!handleRegistration()) {
                return;
            }

            // Phase 2: Chat loop
            handleChatLoop();

        } catch (IOException e) {
            System.err.println("Client handler error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private boolean handleRegistration() throws IOException {
        out.println("Please set your username: username = <name>");

        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            Matcher matcher = USERNAME_PATTERN.matcher(line);
            if (matcher.matches()) {
                String proposedName = matcher.group(1).trim();

                if (proposedName.isEmpty()) {
                    out.println("Username cannot be empty. Please try again: username = <name>");
                    continue;
                }

                // Try to register the username
                if (registry.add(proposedName, out, Instant.now())) {
                    this.username = proposedName;

                    // Print to server console with timestamp
                    String timestamp = LocalDateTime.now().format(TIME_FORMAT);
                    System.out.println(timestamp + " Welcome " + username);

                    // Broadcast welcome message without comma or exclamation (fix discrepancy)
                    registry.broadcastServer("Welcome " + username);
                    return true;
                } else {
                    out.println("Username already taken. Please choose another: username = <name>");
                }
            } else {
                out.println("Please set your username: username = <name>");
            }
        }

        return false; // Connection closed during registration
    }

    private void handleChatLoop() throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            // Check for special commands FIRST
            if (line.equals("Bye")) {
                String timestamp = LocalDateTime.now().format(TIME_FORMAT);
                System.out.println(timestamp + " " + username + " disconnected with a Bye message.");
                // The cleanup() method will handle the goodbye broadcast
                break; // Will trigger cleanup
            } else if (line.equals("AllUsers")) {
                // Send user list ONLY to the requesting client
                registry.sendUserList(username, out);
            } else {
                // Check if message incorrectly starts with "username = "
                // This handles the bug where client might still send it
                Matcher matcher = USERNAME_PATTERN.matcher(line);
                if (matcher.matches()) {
                    // Extract the actual message part
                    String actualMessage = matcher.group(1).trim();
                    // Print to server console
                    String timestamp = LocalDateTime.now().format(TIME_FORMAT);
                    System.out.println(timestamp + " " + username + ": " + actualMessage);
                    // Broadcast the actual message without the "username = " prefix
                    registry.broadcastFrom(username, actualMessage);
                } else {
                    // Print to server console
                    String timestamp = LocalDateTime.now().format(TIME_FORMAT);
                    System.out.println(timestamp + " " + username + ": " + line);
                    // Broadcast regular message as-is
                    registry.broadcastFrom(username, line);
                }
            }
        }
    }

    private void cleanup() {
        try {
            if (username != null) {
                registry.remove(username);
                // Broadcast goodbye message
                registry.broadcastServer("Goodbye " + username);

                // Print to server console
                String timestamp = LocalDateTime.now().format(TIME_FORMAT);
                System.out.println(timestamp + " Server: Goodbye " + username);
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }
}