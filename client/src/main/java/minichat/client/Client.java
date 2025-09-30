package minichat.client;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private BufferedReader consoleReader;
    private final AtomicBoolean running;
    private boolean isRegistered = false;  // Track registration status

    public Client() {
        this.running = new AtomicBoolean(true);
    }

    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);

        // Setup I/O streams with UTF-8 encoding
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        consoleReader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));

        System.out.println("Connected to server at " + host + ":" + port);
    }

    public void start() {
        // Handle username registration
        handleUsernameRegistration();

        // Start reader thread for incoming messages
        Thread readerThread = new Thread(this::readFromServer);
        readerThread.setDaemon(true);
        readerThread.start();

        // Start writer thread for outgoing messages
        Thread writerThread = new Thread(this::writeToServer);
        writerThread.start();

        // Wait for writer thread to complete (user types "Bye")
        try {
            writerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Cleanup
        shutdown();
    }

    private void handleUsernameRegistration() {
        try {
            // Read initial server prompt
            String prompt = in.readLine();
            if (prompt != null) {
                System.out.println(prompt);
            }

            // Get username from user
            System.out.println("Enter your username:");
            String username = consoleReader.readLine();

            // Send registration message
            if (!username.startsWith("username = ")) {
                username = "username = " + username;
            }
            out.println(username);

            // Mark as registered after sending username
            isRegistered = true;

        } catch (IOException e) {
            System.err.println("Error during registration: " + e.getMessage());
            running.set(false);
        }
    }

    private void readFromServer() {
        try {
            String line;
            while (running.get() && (line = in.readLine()) != null) {
                System.out.println(line);

                // Check if server is asking for username again (registration failed)
                if (line.contains("Username already taken") ||
                        line.contains("Username cannot be empty")) {
                    isRegistered = false;  // Reset registration status
                    System.out.println("Enter your username:");
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                System.err.println("Connection to server lost");
            }
        }
        running.set(false);
    }

    private void writeToServer() {
        try {
            String line;
            while (running.get() && (line = consoleReader.readLine()) != null) {
                // Only prepend "username = " if we're not registered
                // and it's not already in the correct format
                if (!isRegistered) {
                    if (!line.startsWith("username = ")) {
                        line = "username = " + line;
                    }
                    isRegistered = true;  // Assume registration after sending
                }

                // Send message as-is (no modification for regular messages)
                out.println(line);

                // Check if user wants to quit
                if (line.equals("Bye")) {
                    running.set(false);
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading from console: " + e.getMessage());
        }
        running.set(false);
    }

    private void shutdown() {
        running.set(false);
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore errors during shutdown
        }
        System.out.println("Disconnected from server");
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java minichat.client.Client <host> <port>");
            System.exit(1);
        }

        String host = args[0];
        try {
            int port = Integer.parseInt(args[1]);

            Client client = new Client();
            client.connect(host, port);
            client.start();

        } catch (NumberFormatException e) {
            System.err.println("Invalid port number: " + args[1]);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
            System.exit(1);
        }
    }
}