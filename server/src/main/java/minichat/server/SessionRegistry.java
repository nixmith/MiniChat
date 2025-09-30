package minichat.server;

import java.io.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SessionRegistry {
    private final ConcurrentHashMap<String, ClientSession> sessions;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FULL_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy");

    public SessionRegistry() {
        this.sessions = new ConcurrentHashMap<>();
    }

    // Inner class holds client session data
    private static class ClientSession {
        final PrintWriter writer;
        final Instant joinedAt;

        ClientSession(PrintWriter writer, Instant joinedAt) {
            this.writer = writer;
            this.joinedAt = joinedAt;
        }
    }

    // Add new user to registry
    public synchronized boolean add(String username, PrintWriter writer, Instant joinedAt) {
        if (sessions.containsKey(username)) {
            return false; // Username already taken
        }
        sessions.put(username, new ClientSession(writer, joinedAt));
        return true;
    }

    // Remove user from registry
    public void remove(String username) {
        sessions.remove(username);
    }

    // Broadcast server message to all users
    public void broadcastServer(String text) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        broadcast(timestamp + " Server: " + text);
    }

    // Broadcast a message from specific user
    public void broadcastFrom(String username, String text) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        broadcast(timestamp + " " + username + ": " + text);
    }

    // Internal broadcast
    private void broadcast(String message) {
        List<String> failedUsers = new ArrayList<>();

        // Create snapshot to avoid concurrently modifying
        Map<String, ClientSession> snapshot = new HashMap<>(sessions);

        for (Map.Entry<String, ClientSession> entry : snapshot.entrySet()) {
            try {
                entry.getValue().writer.println(message);
                entry.getValue().writer.flush();

                // Check if write actually failed (connection closed)
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

    // Send list of active users to specific user
    public void sendUserList(String requester, PrintWriter writer) {
        StringBuilder sb = new StringBuilder();

        // Add header with current time
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        sb.append("\nList of users connected at time: ").append(timestamp).append("\n");

        // Get sorted list of usernames for consistent ordering
        List<String> usernames = new ArrayList<>(sessions.keySet());
        Collections.sort(usernames);

        int index = 1;
        for (String username : usernames) {
            ClientSession session = sessions.get(username);
            if (session != null) {
                // Convert Instant to ZonedDateTime for proper timezone formatting
                ZonedDateTime joinedDateTime = ZonedDateTime.ofInstant(
                        session.joinedAt, ZoneId.systemDefault());
                String joinedFormatted = joinedDateTime.format(FULL_DATE_FORMAT);

                sb.append("\t").append(index++).append(") ").append(username)
                        .append(" since ").append(joinedFormatted).append("\n");
            }
        }
        sb.append("\n");

        // Send only to requester
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
                // Ignore
            }
        }
        sessions.clear();
    }

    // Get count of active users
    public int getUserCount() {
        return sessions.size();
    }

    // Check if user exists
    public boolean hasUser(String username) {
        return sessions.containsKey(username);
    }
}
