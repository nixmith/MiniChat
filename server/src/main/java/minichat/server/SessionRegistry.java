package minichat.server;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SessionRegistry {
    private final ConcurrentHashMap<String, ClientSession> sessions;

    public SessionRegistry() {
        this.sessions = new ConcurrentHashMap<>();
    }

    // Inner class to hold client session data
    private static class ClientSession {
        final PrintWriter writer;
        final Instant joinedAt;

        ClientSession(PrintWriter writer, Instant joinedAt) {
            this.writer = writer;
            this.joinedAt = joinedAt;
        }
    }

    // Add a new user to the registry
    public synchronized boolean add(String username, PrintWriter writer, Instant joinedAt) {
        if (sessions.containsKey(username)) {
            return false; // Username already taken
        }
        sessions.put(username, new ClientSession(writer, joinedAt));
        return true;
    }

    // Remove a user from the registry
    public void remove(String username) {
        sessions.remove(username);
    }

    // Broadcast a server message to all users
    public void broadcastServer(String text) {
        broadcast("Server: " + text);
    }

    // Broadcast a message from a specific user
    public void broadcastFrom(String username, String text) {
        broadcast(username + ": " + text);
    }

    // Internal broadcast method
    private void broadcast(String message) {
        List<String> failedUsers = new ArrayList<>();

        // Create a snapshot to avoid concurrent modification
        Map<String, ClientSession> snapshot = new HashMap<>(sessions);

        for (Map.Entry<String, ClientSession> entry : snapshot.entrySet()) {
            try {
                entry.getValue().writer.println(message);
                entry.getValue().writer.flush();

                // Check if the write actually failed (connection closed)
                if (entry.getValue().writer.checkError()) {
                    failedUsers.add(entry.getKey());
                }
            } catch (Exception e) {
                failedUsers.add(entry.getKey());
            }
        }

        // Remove failed users
        for (String username : failedUsers) {
            sessions.remove(username);
            System.err.println("Removed failed user: " + username);
        }
    }

    // Send list of active users to a specific user
    public void sendUserList(String requester, PrintWriter writer) {
        StringBuilder sb = new StringBuilder();
        sb.append("Active users:\n");

        // Get sorted list of usernames for consistent ordering
        List<String> usernames = new ArrayList<>(sessions.keySet());
        Collections.sort(usernames);

        int index = 1;
        for (String username : usernames) {
            sb.append("\t").append(index++).append(") ").append(username).append("\n");
        }

        // Send only to the requester
        try {
            writer.print(sb.toString());
            writer.flush();
        } catch (Exception e) {
            System.err.println("Failed to send user list to " + requester);
        }
    }

    // Close all sessions (for server shutdown)
    public void closeAll() {
        for (Map.Entry<String, ClientSession> entry : sessions.entrySet()) {
            try {
                entry.getValue().writer.close();
            } catch (Exception e) {
                // Ignore errors during shutdown
            }
        }
        sessions.clear();
    }

    // Get count of active users (for testing)
    public int getUserCount() {
        return sessions.size();
    }

    // Check if user exists (for testing)
    public boolean hasUser(String username) {
        return sessions.containsKey(username);
    }
}