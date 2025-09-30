package minichat.client.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class GuiClient extends JFrame {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean connected = false;
    private boolean isRegistered = false;

    // GUI Components
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton connectButton;
    private JButton disconnectButton;
    private JTextField serverField;
    private JTextField portField;
    private JTextField usernameField;
    private JLabel statusLabel;
    private JButton allUsersButton;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public GuiClient() {
        super("MiniChat Client");
        initializeGUI();
    }

    private void initializeGUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Top panel - Connection settings
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(BorderFactory.createTitledBorder("Connection"));

        topPanel.add(new JLabel("Server:"));
        serverField = new JTextField("localhost", 10);
        topPanel.add(serverField);

        topPanel.add(new JLabel("Port:"));
        portField = new JTextField("8989", 5);
        topPanel.add(portField);

        topPanel.add(new JLabel("Username:"));
        usernameField = new JTextField(10);
        topPanel.add(usernameField);

        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> connect());
        topPanel.add(connectButton);

        disconnectButton = new JButton("Disconnect");
        disconnectButton.setEnabled(false);
        disconnectButton.addActionListener(e -> disconnect());
        topPanel.add(disconnectButton);

        add(topPanel, BorderLayout.NORTH);

        // Center panel - Chat area
        chatArea = new JTextArea(20, 50);
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Chat"));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom panel - Message input
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel inputPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        messageField.setEnabled(false);
        messageField.addActionListener(e -> sendMessage());
        inputPanel.add(messageField, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        sendButton = new JButton("Send");
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());
        buttonPanel.add(sendButton);

        allUsersButton = new JButton("All Users");
        allUsersButton.setEnabled(false);
        allUsersButton.addActionListener(e -> sendAllUsersCommand());
        buttonPanel.add(allUsersButton);

        inputPanel.add(buttonPanel, BorderLayout.EAST);
        bottomPanel.add(inputPanel, BorderLayout.CENTER);

        // Status bar
        statusLabel = new JLabel("Disconnected");
        statusLabel.setBorder(BorderFactory.createEtchedBorder());
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        // Window settings
        pack();
        setLocationRelativeTo(null);

        // Handle window closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (connected) {
                    disconnect();
                }
            }
        });
    }

    private void connect() {
        String server = serverField.getText().trim();
        String portText = portField.getText().trim();
        String username = usernameField.getText().trim();

        if (server.isEmpty() || portText.isEmpty() || username.isEmpty()) {
            showError("Please fill in all connection fields");
            return;
        }

        try {
            int port = Integer.parseInt(portText);

            // Connect to server
            socket = new Socket(server, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            connected = true;
            updateConnectionStatus(true);

            // Start reader thread
            Thread readerThread = new Thread(this::readFromServer);
            readerThread.setDaemon(true);
            readerThread.start();

            // Send username registration
            Thread.sleep(100);
            out.println("username = " + username);
            isRegistered = true;

            appendToChat("System", "Connected to " + server + ":" + port);

        } catch (NumberFormatException e) {
            showError("Invalid port number");
        } catch (IOException e) {
            showError("Connection failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void disconnect() {
        if (connected) {
            try {
                out.println("Bye");
                Thread.sleep(100); // Give server time to process

                connected = false;
                isRegistered = false;
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }

                appendToChat("System", "Disconnected from server");
                updateConnectionStatus(false);

            } catch (IOException | InterruptedException e) {
                showError("Error during disconnect: " + e.getMessage());
            }
        }
    }

    private void readFromServer() {
        try {
            String line;
            boolean firstLine = true;
            while (connected && (line = in.readLine()) != null) {
                // Skip the initial prompt line
                if (firstLine && line.contains("Please set your username")) {
                    firstLine = false;
                    continue;
                }

                final String message = line;
                SwingUtilities.invokeLater(() -> {
                    appendToChat(message);
                });
            }
        } catch (IOException e) {
            if (connected) {
                SwingUtilities.invokeLater(() -> {
                    appendToChat("System", "Connection lost");
                    updateConnectionStatus(false);
                });
            }
        }
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty() && connected && isRegistered) {
            out.println(message);
            messageField.setText("");

            // If user typed "Bye", prepare for disconnect
            if (message.equals("Bye")) {
                SwingUtilities.invokeLater(() -> {
                    connected = false;
                    isRegistered = false;
                    updateConnectionStatus(false);
                });
            }
        }
    }

    private void sendAllUsersCommand() {
        if (connected && isRegistered) {
            out.println("AllUsers");
        }
    }

    private void appendToChat(String sender, String message) {
        String timestamp = LocalTime.now().format(TIME_FORMAT);
        chatArea.append(String.format("[%s] %s: %s\n", timestamp, sender, message));
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void appendToChat(String fullMessage) {
        String timestamp = LocalTime.now().format(TIME_FORMAT);
        chatArea.append(String.format("[%s] %s\n", timestamp, fullMessage));
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void updateConnectionStatus(boolean isConnected) {
        connectButton.setEnabled(!isConnected);
        disconnectButton.setEnabled(isConnected);
        messageField.setEnabled(isConnected);
        sendButton.setEnabled(isConnected);
        allUsersButton.setEnabled(isConnected);

        serverField.setEnabled(!isConnected);
        portField.setEnabled(!isConnected);
        usernameField.setEnabled(!isConnected);

        statusLabel.setText(isConnected ?
                "Connected as " + usernameField.getText() : "Disconnected");
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error",
                JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GuiClient client = new GuiClient();
            client.setVisible(true);
        });
    }
}