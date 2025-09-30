package minichat.server;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Server {
    private ServerSocket serverSocket;
    private final SessionRegistry registry;
    private final AtomicBoolean running;
    private Thread acceptThread;

    public Server(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.registry = new SessionRegistry();
        this.running = new AtomicBoolean(true);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down server...");
            shutdown();
        }));
    }

    public void start() {
        System.out.println("Server started on port " + serverSocket.getLocalPort());
        System.out.println("------------------------------------------------------------------------");

        acceptThread = Thread.currentThread();
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                // Start a new thread for each client
                ClientHandler handler = new ClientHandler(clientSocket, registry);
                handler.start();
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    public void shutdown() {
        running.set(false);
        try {
            // Close all client sessions
            registry.closeAll();

            // Close server socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error during shutdown: " + e.getMessage());
        }
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java minichat.server.Server <port>");
            System.exit(1);
        }

        try {
            int port = Integer.parseInt(args[0]);
            Server server = new Server(port);
            server.start();
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number: " + args[0]);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            System.exit(1);
        }
    }
}
