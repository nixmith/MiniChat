package minichat.server;

import org.junit.jupiter.api.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

public class ServerIntegrationTest {
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
    public void testCompleteServerFlow() throws Exception {
        // Connect three raw socket clients
        TestClient uno = new TestClient("localhost", serverPort);
        TestClient cs = new TestClient("localhost", serverPort);
        TestClient unocc = new TestClient("localhost", serverPort);

        // Test 1: Pre-registration rule - messages before registration are ignored
        uno.send("Hello before registration");
        Thread.sleep(100);
        assertNull(cs.readWithTimeout(200), "CS should not receive message before UNO registers");
        assertNull(unocc.readWithTimeout(200), "UNOCC should not receive message before UNO registers");

        // Clear any prompts
        uno.readWithTimeout(200);
        cs.readWithTimeout(200);
        unocc.readWithTimeout(200);

        // Test 2: Registration
        uno.send("username = UNO");
        Thread.sleep(100);

        // All clients should receive welcome message
        String unoWelcome = uno.readWithTimeout(1000);
        assertNotNull(unoWelcome);
        assertTrue(unoWelcome.contains("Welcome UNO"), "UNO welcome: " + unoWelcome);

        cs.send("username = CS");
        Thread.sleep(100);

        String csWelcome1 = cs.readWithTimeout(1000);
        String csWelcome2 = uno.readWithTimeout(1000);
        assertTrue(csWelcome1.contains("Welcome CS") ||
                (csWelcome2 != null && csWelcome2.contains("Welcome CS")));

        unocc.send("username = UNOCC");
        Thread.sleep(100);

        String unoccWelcome = unocc.readWithTimeout(1000);
        assertNotNull(unoccWelcome);
        assertTrue(unoccWelcome.contains("Welcome UNOCC"));

        // Test 3: Broadcast message
        uno.send("Hi");
        Thread.sleep(100);

        String msgAtUno = uno.readWithTimeout(1000);
        String msgAtCs = cs.readWithTimeout(1000);
        String msgAtUnocc = unocc.readWithTimeout(1000);

        assertNotNull(msgAtUno, "UNO should receive its own message");
        assertNotNull(msgAtCs, "CS should receive UNO's message");
        assertNotNull(msgAtUnocc, "UNOCC should receive UNO's message");

        assertTrue(msgAtUno.contains("Hi") && msgAtUno.contains("UNO"));
        assertTrue(msgAtCs.contains("Hi") && msgAtCs.contains("UNO"));
        assertTrue(msgAtUnocc.contains("Hi") && msgAtUnocc.contains("UNO"));

        // Test 4: AllUsers command
        uno.send("AllUsers");
        Thread.sleep(100);

        String userList = uno.readMultilineWithTimeout(1000);
        assertNotNull(userList, "UNO should receive user list");
        assertTrue(userList.contains("UNO"), "List should contain UNO");
        assertTrue(userList.contains("CS"), "List should contain CS");
        assertTrue(userList.contains("UNOCC"), "List should contain UNOCC");

        // CS and UNOCC should not receive the user list
        assertNull(cs.readWithTimeout(200), "CS should not receive user list");
        assertNull(unocc.readWithTimeout(200), "UNOCC should not receive user list");

        // Test 5: Bye command
        unocc.send("Bye");
        Thread.sleep(200);

        String goodbyeAtUno = uno.readWithTimeout(1000);
        String goodbyeAtCs = cs.readWithTimeout(1000);

        assertNotNull(goodbyeAtUno, "UNO should receive goodbye");
        assertNotNull(goodbyeAtCs, "CS should receive goodbye");
        assertTrue(goodbyeAtUno.contains("Goodbye UNOCC"));
        assertTrue(goodbyeAtCs.contains("Goodbye UNOCC"));

        // Verify UNOCC's connection is closed
        assertTrue(unocc.isConnectionClosed(), "UNOCC connection should be closed");

        // Cleanup
        uno.close();
        cs.close();
    }

    // Helper class for test clients
    private static class TestClient {
        private Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;
        private ExecutorService executor = Executors.newSingleThreadExecutor();

        TestClient(String host, int port) throws IOException {
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        }

        void send(String message) {
            writer.println(message);
        }

        String readWithTimeout(long timeoutMs) {
            Future<String> future = executor.submit(() -> {
                try {
                    return reader.readLine();
                } catch (IOException e) {
                    return null;
                }
            });

            try {
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                return null;
            } catch (Exception e) {
                return null;
            }
        }

        String readMultilineWithTimeout(long timeoutMs) {
            StringBuilder sb = new StringBuilder();
            long endTime = System.currentTimeMillis() + timeoutMs;

            while (System.currentTimeMillis() < endTime) {
                String line = readWithTimeout(100);
                if (line == null) {
                    break;
                }
                sb.append(line).append("\n");
            }

            return sb.length() > 0 ? sb.toString() : null;
        }

        boolean isConnectionClosed() {
            try {
                socket.sendUrgentData(0);
                return false;
            } catch (IOException e) {
                return true;
            }
        }

        void close() throws IOException {
            executor.shutdown();
            socket.close();
        }
    }
}