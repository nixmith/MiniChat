package minichat.client;

import org.junit.jupiter.api.*;
import minichat.server.Server;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

public class ClientSmokeTest {
    private Server server;
    private Thread serverThread;
    private int serverPort;

    @BeforeEach
    public void setUp() throws Exception {
        // Start server on ephemeral port
        server = new Server(0);
        serverPort = server.getPort();

        serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Wait for server to be ready
        Thread.sleep(100);
    }

    @AfterEach
    public void tearDown() throws Exception {
        server.shutdown();
        serverThread.interrupt();
        Thread.sleep(100);
    }

    @Test
    public void testClientBasicFlow() throws Exception {
        // Create piped streams for simulating console input
        PipedOutputStream pipedOut = new PipedOutputStream();
        PipedInputStream pipedIn = new PipedInputStream(pipedOut);
        PrintWriter consoleWriter = new PrintWriter(pipedOut, true);

        // Save original System.in and redirect
        InputStream originalIn = System.in;
        System.setIn(pipedIn);

        // Start client in separate thread
        Thread clientThread = new Thread(() -> {
            try {
                Client client = new Client();
                client.connect("localhost", serverPort);
                client.start();
            } catch (Exception e) {
                fail("Client threw exception: " + e.getMessage());
            }
        });

        clientThread.start();

        // Simulate user input
        Thread.sleep(200); // Wait for connection
        consoleWriter.println("Alice");     // Username (will be prepended with "username = ")
        Thread.sleep(200); // Wait for registration
        consoleWriter.println("Hello");     // Send a message
        Thread.sleep(200); // Wait for broadcast
        consoleWriter.println("Bye");       // Disconnect

        // Wait for client to finish (with timeout)
        clientThread.join(3000);

        // Verify client thread terminated normally
        assertFalse(clientThread.isAlive(), "Client thread should have terminated");

        // Restore System.in
        System.setIn(originalIn);

        // Cleanup
        consoleWriter.close();
        pipedIn.close();
        pipedOut.close();
    }
}