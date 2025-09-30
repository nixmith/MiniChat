package minichat.server;

import java.io.*;
import java.net.*;
import java.time.Instant;
import java.util.regex.*;

public class ClientHandler extends Thread {
    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private final SessionRegistry registry;
    private String username;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^username\\s*=\\s*(\\S.*)$");

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
        out.println("Please set your username: username = <Name>");

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
                    out.println("Username cannot be empty. Please try again: username = <Name>");
                    continue;
                }

                // Try to register the username
                if (registry.add(proposedName, out, Instant.now())) {
                    this.username = proposedName;
                    System.out.println("User registered: " + username);

                    // Broadcast welcome message
                    registry.broadcastServer("Welcome, " + username + "!");
                    return true;
                } else {
                    out.println("Username already taken. Please choose another: username = <Name>");
                }
            } else {
                out.println("Please set your username: username = <Name>");
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
                System.out.println("User " + username + " is leaving");
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
                    // Broadcast the actual message without the "username = " prefix
                    registry.broadcastFrom(username, actualMessage);
                } else {
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
                System.out.println("User disconnected: " + username);
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }
}