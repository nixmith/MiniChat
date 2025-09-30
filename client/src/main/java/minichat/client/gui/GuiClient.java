package minichat.client.gui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AOL 1990s-style GUI Chat Client
 * Fully encapsulated retro UI (actually started this years ago in python, ported over to Java)
 */
public class GuiClient extends JFrame {
    // Networking components
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private boolean isRegistered = false;
    private String username = "";
    private String currentRoom = "Main Lobby";

    // UI components
    private JTextPane transcriptPane;
    private StyledDocument transcriptDoc;
    private JTextArea inputArea;
    private JList<String> peopleList;
    private DefaultListModel<String> peopleModel;
    private JLabel statusLabel;
    private JComboBox<Integer> fontSizeCombo;
    private JComboBox<ColorItem> colorCombo;
    private JCheckBox timestampCheck;
    private JCheckBox wrapCheck;
    private GradientHeader headerPanel;
    private boolean sortAlphabetically = false;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Retro theme constants
     */
    static final class RetroTheme {
        static final Color BASE_GRAY = new Color(0xC0C0C0);
        static final Color SHADOW_DARK = new Color(0x808080);
        static final Color SHADOW_DEEPER = new Color(0x404040);
        static final Color HIGHLIGHT = Color.WHITE;
        static final Color ACCENT_NAVY = new Color(0x000080);
        static final Color ACCENT_TEAL = new Color(0x008080);
        static final Color SELECTION_BG = new Color(0x000080);
        static final Color SELECTION_FG = Color.WHITE;

        static final Font TITLE_FONT = new Font("MS Sans Serif", Font.BOLD, 18);
        static final Font UI_FONT = new Font("MS Sans Serif", Font.PLAIN, 11);
        static final Font MONO_FONT = new Font("Courier New", Font.PLAIN, 11);

        static final Dimension BUTTON_SIZE = new Dimension(80, 25);
        static final Insets PANEL_INSETS = new Insets(3, 3, 3, 3);
    }

    /**
     * Message types for transcript rendering
     */
    enum MessageType {
        USER, SERVER, ERROR
    }

    /**
     * Color item for color combo box
     */
    static class ColorItem {
        final String name;
        final Color color;

        ColorItem(String name, Color color) {
            this.name = name;
            this.color = color;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Custom theme for that AOL/Windows 95 vibe
     */
    static final class AOLMetalTheme extends DefaultMetalTheme {
        @Override
        public ColorUIResource getPrimary1() { return new ColorUIResource(RetroTheme.ACCENT_NAVY); }
        @Override
        public ColorUIResource getPrimary2() { return new ColorUIResource(RetroTheme.ACCENT_TEAL); }
        @Override
        public ColorUIResource getPrimary3() { return new ColorUIResource(RetroTheme.BASE_GRAY); }
        @Override
        public ColorUIResource getSecondary1() { return new ColorUIResource(RetroTheme.SHADOW_DARK); }
        @Override
        public ColorUIResource getSecondary2() { return new ColorUIResource(RetroTheme.BASE_GRAY); }
        @Override
        public ColorUIResource getSecondary3() { return new ColorUIResource(RetroTheme.HIGHLIGHT); }
        @Override
        public FontUIResource getControlTextFont() { return new FontUIResource(RetroTheme.UI_FONT); }
        @Override
        public FontUIResource getMenuTextFont() { return new FontUIResource(RetroTheme.UI_FONT); }
    }

    /**
     * Gradient header panel with gradient
     */
    final class GradientHeader extends JPanel {
        private JLabel titleLabel;
        private JButton signOffButton;

        GradientHeader() {
            setLayout(new BorderLayout());
            setPreferredSize(new Dimension(800, 45));
            setBorder(create3DBorder(true));

            // Title on the left
            JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
            titlePanel.setOpaque(false);
            titleLabel = new JLabel("MiniChat AOL Room - " + currentRoom);
            titleLabel.setFont(RetroTheme.TITLE_FONT);
            titleLabel.setForeground(Color.WHITE);
            titlePanel.add(titleLabel);

            // Sign Off button on the right
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
            buttonPanel.setOpaque(false);
            signOffButton = createRetroButton("Sign Off");
            signOffButton.setPreferredSize(RetroTheme.BUTTON_SIZE);
            signOffButton.addActionListener(e -> signOff());
            buttonPanel.add(signOffButton);

            add(titlePanel, BorderLayout.WEST);
            add(buttonPanel, BorderLayout.EAST);
        }

        void updateRoom(String room) {
            currentRoom = room;
            titleLabel.setText("MiniChat AOL Room - " + room);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Create gradient (teal to navy)
            GradientPaint gradient = new GradientPaint(
                    0, 0, RetroTheme.ACCENT_TEAL,
                    getWidth(), 0, RetroTheme.ACCENT_NAVY
            );
            g2.setPaint(gradient);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    /**
     * Constructor
     */
    public GuiClient() {
        super("MiniChat - powered by America Online");
        initializeLookAndFeel();
        buildUI();
        wireActions();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(850, 600);
        setLocationRelativeTo(null);

        // Initially disable input til connect
        inputArea.setEnabled(false);
    }

    /**
     * Initialize the LAF with AOL theme
     */
    private void initializeLookAndFeel() {
        try {
            MetalLookAndFeel.setCurrentTheme(new AOLMetalTheme());
            UIManager.setLookAndFeel(new MetalLookAndFeel());

            // Set selection colors
            UIManager.put("List.selectionBackground", RetroTheme.SELECTION_BG);
            UIManager.put("List.selectionForeground", RetroTheme.SELECTION_FG);
            UIManager.put("TextArea.selectionBackground", RetroTheme.SELECTION_BG);
            UIManager.put("TextArea.selectionForeground", RetroTheme.SELECTION_FG);
            UIManager.put("TextField.selectionBackground", RetroTheme.SELECTION_BG);
            UIManager.put("TextField.selectionForeground", RetroTheme.SELECTION_FG);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Build the complete UI
     */
    private void buildUI() {
        getContentPane().setBackground(RetroTheme.BASE_GRAY);
        setLayout(new BorderLayout());

        // Header
        headerPanel = new GradientHeader();
        add(headerPanel, BorderLayout.NORTH);

        // Main content area)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.72);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(8);
        splitPane.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

        // Left transcript
        splitPane.setLeftComponent(createTranscriptPanel());

        // Right people list
        splitPane.setRightComponent(createPeoplePanel());

        add(splitPane, BorderLayout.CENTER);

        // Bottom input area
        add(createInputPanel(), BorderLayout.SOUTH);

        // Menu bar
        setJMenuBar(createMenuBar());
    }

    /**
     * Create the transcript panel
     */
    private JPanel createTranscriptPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(RetroTheme.BASE_GRAY);
        panel.setBorder(create3DBorder(false));

        transcriptPane = new JTextPane();
        transcriptDoc = transcriptPane.getStyledDocument();
        transcriptPane.setEditable(false);
        transcriptPane.setBackground(Color.WHITE);
        transcriptPane.setFont(new Font("MS Sans Serif", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(transcriptPane);
        scrollPane.setBorder(BorderFactory.createLoweredBevelBorder());
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Create people list panel
     */
    private JPanel createPeoplePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(RetroTheme.BASE_GRAY);

        // Title with border
        TitledBorder titleBorder = BorderFactory.createTitledBorder(
                create3DBorder(false),
                "People in Room",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                RetroTheme.UI_FONT,
                RetroTheme.ACCENT_NAVY
        );
        panel.setBorder(titleBorder);

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        toolbar.setBackground(RetroTheme.BASE_GRAY);

        JButton refreshBtn = createRetroButton("Refresh");
        refreshBtn.setPreferredSize(new Dimension(65, 22));
        refreshBtn.addActionListener(e -> requestUserList());

        JToggleButton sortBtn = new JToggleButton("Sort A-Z");
        sortBtn.setFont(RetroTheme.UI_FONT);
        sortBtn.setPreferredSize(new Dimension(65, 22));
        sortBtn.addActionListener(e -> {
            sortAlphabetically = sortBtn.isSelected();
            refreshPeopleList();
        });

        toolbar.add(refreshBtn);
        toolbar.add(sortBtn);
        panel.add(toolbar, BorderLayout.NORTH);

        // People list
        peopleModel = new DefaultListModel<>();
        peopleList = new JList<>(peopleModel);
        peopleList.setFont(RetroTheme.UI_FONT);
        peopleList.setBackground(Color.WHITE);
        peopleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        peopleList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = peopleList.getSelectedValue();
                    if (selected != null && selected.startsWith("• ")) {
                        String name = selected.substring(2);
                        inputArea.insert("@" + name + " ", inputArea.getCaretPosition());
                        inputArea.requestFocus();
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(peopleList);
        scrollPane.setBorder(BorderFactory.createLoweredBevelBorder());
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Create the input panel
     */
    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(RetroTheme.BASE_GRAY);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(3, 3, 3, 3),
                create3DBorder(true)
        ));

        // Controls panel
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        controlsPanel.setBackground(RetroTheme.BASE_GRAY);

        // Font size combo
        controlsPanel.add(new JLabel("Size:"));
        fontSizeCombo = new JComboBox<>(new Integer[]{10, 12, 14});
        fontSizeCombo.setSelectedItem(12);
        fontSizeCombo.setPreferredSize(new Dimension(50, 22));
        controlsPanel.add(fontSizeCombo);

        // Color combo
        controlsPanel.add(new JLabel("Color:"));
        colorCombo = new JComboBox<>(new ColorItem[]{
                new ColorItem("Black", Color.BLACK),
                new ColorItem("Navy", RetroTheme.ACCENT_NAVY),
                new ColorItem("Teal", RetroTheme.ACCENT_TEAL),
                new ColorItem("Maroon", new Color(0x800000))
        });
        colorCombo.setPreferredSize(new Dimension(80, 22));
        colorCombo.setRenderer(new ColorSwatchRenderer());
        controlsPanel.add(colorCombo);

        // Checkboxes
        timestampCheck = new JCheckBox("Timestamp", true);
        timestampCheck.setFont(RetroTheme.UI_FONT);
        timestampCheck.setBackground(RetroTheme.BASE_GRAY);
        controlsPanel.add(timestampCheck);

        wrapCheck = new JCheckBox("Wrap", true);
        wrapCheck.setFont(RetroTheme.UI_FONT);
        wrapCheck.setBackground(RetroTheme.BASE_GRAY);
        wrapCheck.addActionListener(e -> inputArea.setLineWrap(wrapCheck.isSelected()));
        controlsPanel.add(wrapCheck);

        panel.add(controlsPanel, BorderLayout.NORTH);

        // Input area with send button
        JPanel inputPanel = new JPanel(new BorderLayout(3, 0));
        inputPanel.setBackground(RetroTheme.BASE_GRAY);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

        inputArea = new JTextArea(3, 40);
        inputArea.setFont(new Font("MS Sans Serif", Font.PLAIN, 12));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createLoweredBevelBorder());
        inputPanel.add(inputScroll, BorderLayout.CENTER);

        JButton sendButton = createRetroButton("Send");
        sendButton.setPreferredSize(new Dimension(70, 50));
        sendButton.addActionListener(e -> sendMessage());
        inputPanel.add(sendButton, BorderLayout.EAST);

        panel.add(inputPanel, BorderLayout.CENTER);

        // Status bar
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(RetroTheme.BASE_GRAY);
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(2, 0, 0, 0),
                BorderFactory.createLoweredBevelBorder()
        ));

        statusLabel = new JLabel("Disconnected");
        statusLabel.setFont(new Font("MS Sans Serif", Font.PLAIN, 10));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        statusPanel.add(statusLabel, BorderLayout.CENTER);

        panel.add(statusPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Create the menu bar
     */
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // Room menu
        JMenu roomMenu = new JMenu("Room");
        roomMenu.setMnemonic('R');

        JMenuItem refreshItem = new JMenuItem("Send \"AllUsers\"");
        refreshItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK));
        refreshItem.addActionListener(e -> requestUserList());

        JMenuItem clearItem = new JMenuItem("Clear Transcript");
        clearItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK));
        clearItem.addActionListener(e -> clearTranscript());

        JMenuItem signOffItem = new JMenuItem("Sign Off");
        signOffItem.addActionListener(e -> signOff());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));

        roomMenu.add(refreshItem);
        roomMenu.add(clearItem);
        roomMenu.addSeparator();
        roomMenu.add(signOffItem);
        roomMenu.add(exitItem);

        // Edit menu
        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic('E');

        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        copyItem.addActionListener(e -> {
            Component focused = getFocusOwner();
            if (focused instanceof JTextComponent) {
                ((JTextComponent)focused).copy();
            }
        });

        JMenuItem pasteItem = new JMenuItem("Paste");
        pasteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));
        pasteItem.addActionListener(e -> {
            Component focused = getFocusOwner();
            if (focused instanceof JTextComponent) {
                ((JTextComponent)focused).paste();
            }
        });

        JMenuItem selectAllItem = new JMenuItem("Select All");
        selectAllItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK));
        selectAllItem.addActionListener(e -> {
            Component focused = getFocusOwner();
            if (focused instanceof JTextComponent) {
                ((JTextComponent)focused).selectAll();
            }
        });

        editMenu.add(copyItem);
        editMenu.add(pasteItem);
        editMenu.add(selectAllItem);

        // People menu
        JMenu peopleMenu = new JMenu("People");
        peopleMenu.setMnemonic('P');

        JMenuItem refreshPeopleItem = new JMenuItem("Refresh List");
        refreshPeopleItem.addActionListener(e -> requestUserList());
        peopleMenu.add(refreshPeopleItem);

        // Help menu
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic('H');

        JMenuItem aboutItem = new JMenuItem("About...");
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);

        menuBar.add(roomMenu);
        menuBar.add(editMenu);
        menuBar.add(peopleMenu);
        menuBar.add(helpMenu);

        return menuBar;
    }

    /**
     * Wire keyboard actions and shortcuts no one will ever use
     */
    private void wireActions() {
        // Enter to send, Shift+Enter for newline
        inputArea.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "send");
        inputArea.getActionMap().put("send", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });

        inputArea.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "newline");
        inputArea.getActionMap().put("newline", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                inputArea.insert("\n", inputArea.getCaretPosition());
            }
        });

        // Escape to clear input
        inputArea.getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"), "clear");
        inputArea.getActionMap().put("clear", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                inputArea.setText("");
            }
        });

        // Ctrl+L to clear transcript
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK), "clearTranscript");
        getRootPane().getActionMap().put("clearTranscript", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearTranscript();
            }
        });
    }

    /**
     * Retro-styled button
     */
    private JButton createRetroButton(String text) {
        JButton button = new JButton(text);
        button.setFont(RetroTheme.UI_FONT);
        button.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        button.setFocusPainted(false);
        button.setContentAreaFilled(true);
        button.setBackground(RetroTheme.BASE_GRAY);

        // Creates a "press" effect
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                button.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                button.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
            }
        });

        return button;
    }

    /**
     * Create 3D border effect
     */
    private Border create3DBorder(boolean raised) {
        if (raised) {
            return BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(RetroTheme.HIGHLIGHT),
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(RetroTheme.SHADOW_DARK),
                            BorderFactory.createEmptyBorder(1, 1, 1, 1)
                    )
            );
        } else {
            return BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(RetroTheme.SHADOW_DARK),
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(RetroTheme.HIGHLIGHT),
                            BorderFactory.createEmptyBorder(1, 1, 1, 1)
                    )
            );
        }
    }

    /**
     * Color swatch/menu renderer (color/combo box, was not sure what to call it)
     */
    static final class ColorSwatchRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);

            if (value instanceof ColorItem) {
                ColorItem item = (ColorItem) value;
                label.setIcon(new ColorSwatchIcon(item.color));
            }

            return label;
        }

        private static class ColorSwatchIcon implements Icon {
            private final Color color;

            ColorSwatchIcon(Color color) {
                this.color = color;
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                g.setColor(color);
                g.fillRect(x, y, getIconWidth(), getIconHeight());
                g.setColor(Color.BLACK);
                g.drawRect(x, y, getIconWidth() - 1, getIconHeight() - 1);
            }

            @Override
            public int getIconWidth() { return 16; }

            @Override
            public int getIconHeight() { return 14; }
        }
    }

    // ===== NETWORKING METHODS =====

    /**
     * Connect to server
     */
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);

        // Setup streams w UTF-8 encoding
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

        // Update status
        SwingUtilities.invokeLater(() -> {
            setConnectionStatus("Connected to " + host + ":" + port + " - Awaiting registration", true);
        });
    }

    /**
     * Start the client (handle registration and begin chat)
     */
    public void start() {
        // Handle user registration
        handleUsernameRegistration();

        // Start reader thread for incoming messages
        Thread readerThread = new Thread(this::readFromServer);
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Handle username registration
     */
    private void handleUsernameRegistration() {
        // Separate thread to read the server prompt first
        new Thread(() -> {
            try {
                // Read server initial prompt
                String serverPrompt = in.readLine();
                System.out.println("Server prompt: " + serverPrompt);

                // ask user for username in EDT
                SwingUtilities.invokeLater(() -> {
                    String prompt = JOptionPane.showInputDialog(
                            this,
                            "Enter your username:",
                            "AOL Chat - Sign On",
                            JOptionPane.PLAIN_MESSAGE
                    );

                    if (prompt != null && !prompt.trim().isEmpty()) {
                        username = prompt.trim();

                        // Send username registration
                        new Thread(() -> {
                            String regMessage = "username = " + username;
                            out.println(regMessage);
                            isRegistered = true;

                            SwingUtilities.invokeLater(() -> {
                                setConnectionStatus("Connected as " + username, true);
                                // Enable input area since we registered
                                inputArea.setEnabled(true);
                                inputArea.requestFocus();
                                // Add self to people list
                                if (!peopleModel.contains("• " + username)) {
                                    peopleModel.addElement("• " + username);
                                    refreshPeopleList();
                                }
                            });
                        }).start();
                    } else {
                        System.exit(0);
                    }
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    appendMessage(MessageType.ERROR, "", "Registration failed: " + e.getMessage(), Color.RED);
                });
            }
        }).start();
    }

    /**
     * Read messages from server
     */
    private void readFromServer() {
        try {
            String line;
            while (running.get() && (line = in.readLine()) != null) {
                final String message = line;

                SwingUtilities.invokeLater(() -> {
                    processIncomingMessage(message);
                });
            }
        } catch (IOException e) {
            if (running.get()) {
                SwingUtilities.invokeLater(() -> {
                    appendMessage(MessageType.ERROR, "", "Connection to server lost", Color.RED);
                    setConnectionStatus("Disconnected", false);
                });
            }
        }
        running.set(false);
    }

    /**
     * Process incoming message
     */
    private void processIncomingMessage(String message) {
        // Filter prompts
        if (message.contains("Please set your username") ||
                message.contains("Please choose another")) {

            if (message.contains("Username already taken") ||
                    message.contains("Username cannot be empty")) {
                isRegistered = false;
                appendMessage(MessageType.ERROR, "", message, Color.RED);
                handleUsernameRegistration();
            }
            return;
        }

        // Check for start of user list
        if (message.contains("List of the users connected")) {
            appendMessage(MessageType.SERVER, "", message, RetroTheme.ACCENT_NAVY);
            peopleModel.clear();
            peopleModel.addElement("• " + username); // Add self
            return;
        }

        // Check for user list item (format: "1) username since ...")
        if (message.matches("\\s*\\d+\\)\\s+\\S+.*since.*")) {
            appendMessage(MessageType.SERVER, "", message, RetroTheme.ACCENT_NAVY);
            // Parse/add to people list
            String userPart = message.trim().replaceFirst("\\d+\\)\\s+", "");
            String userName = userPart.split("\\s+since")[0].trim();
            if (!peopleModel.contains("• " + userName)) {
                peopleModel.addElement("• " + userName);
            }
            refreshPeopleList();
            return;
        }

        // Parse timestamp messages
        // Format: "HH:mm:ss Server: message" or "HH:mm:ss username: message"
        if (message.matches("^\\d{2}:\\d{2}:\\d{2}\\s+.*")) {
            String[] timeParts = message.split("\\s+", 2);
            if (timeParts.length == 2) {
                String timestamp = timeParts[0];
                String remainder = timeParts[1];

                // Check if server message
                if (remainder.startsWith("Server:")) {
                    String content = remainder.substring(7).trim();
                    appendMessage(MessageType.SERVER, "", "Server: " + content, RetroTheme.ACCENT_NAVY);

                    // Welcome message - add user to list
                    if (content.startsWith("Welcome ")) {
                        String newUser = content.substring(8).trim();
                        if (!peopleModel.contains("• " + newUser)) {
                            peopleModel.addElement("• " + newUser);
                            refreshPeopleList();
                        }
                    }
                    // Goodbye message - remove user from list
                    else if (content.startsWith("Goodbye ")) {
                        String leavingUser = content.substring(8).trim();
                        peopleModel.removeElement("• " + leavingUser);
                        refreshPeopleList();
                    }
                } else {
                    // User message format: "username: message"
                    String[] userParts = remainder.split(":\\s+", 2);
                    if (userParts.length == 2) {
                        String user = userParts[0];
                        String content = userParts[1];

                        // Add user to list if not there
                        if (!peopleModel.contains("• " + user)) {
                            peopleModel.addElement("• " + user);
                            refreshPeopleList();
                        }

                        // Display message with correct color
                        Color messageColor = Color.BLACK;
                        if (user.equals(username)) {
                            // Our own message being echoed back
                            ColorItem selected = (ColorItem) colorCombo.getSelectedItem();
                            messageColor = selected.color;
                        }
                        appendMessage(MessageType.USER, user, content, messageColor);
                    } else {
                        // Couldnt parse, show as-is
                        appendMessage(MessageType.USER, "", remainder, Color.BLACK);
                    }
                }
            }
        } else {
            // Fallback display as-is
            appendMessage(MessageType.USER, "", message, Color.BLACK);
        }
    }

    /**
     * Send message to server
     */
    private void sendMessage() {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;

        if (!isRegistered) {
            // *Should not happen*
            JOptionPane.showMessageDialog(this,
                    "Not connected to server",
                    "Connection Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Send regular message
        out.println(text);

        // Check for the special commands
        if (text.equals("Bye")) {
            signOff();
        } else if (text.equals("AllUsers")) {
            // User list received from server
        }
        // Note: Don't display own message locally - wait for server echo

        inputArea.setText("");
        inputArea.requestFocus();
    }

    /**
     * Request user list
     */
    private void requestUserList() {
        if (isRegistered) {
            out.println("AllUsers");
        }
    }

    /**
     * Sign off
     */
    private void signOff() {
        if (isRegistered && out != null) {
            out.println("Bye");
        }
        running.set(false);

        // Close the socket
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore
        }

        // Exit after short delay -> lets message send
        SwingUtilities.invokeLater(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }
            dispose();
            System.exit(0);
        });
    }

    /**
     * Clear transcript
     */
    private void clearTranscript() {
        try {
            transcriptDoc.remove(0, transcriptDoc.getLength());
        } catch (BadLocationException e) {
            // Ignore
        }
    }

    /**
     * Refresh people list
     */
    private void refreshPeopleList() {
        if (sortAlphabetically) {
            java.util.List<String> items = new ArrayList<>();
            for (int i = 0; i < peopleModel.size(); i++) {
                items.add(peopleModel.get(i));
            }
            Collections.sort(items);
            peopleModel.clear();
            for (String item : items) {
                peopleModel.addElement(item);
            }
        }

        // Update status bar w user count
        if (isRegistered) {
            setConnectionStatus("Connected as " + username, true);
        }
    }

    /**
     * Append message to transcript
     */
    private void appendMessage(MessageType type, String username, String text, Color color) {
        try {
            // Limit transcript size
            if (transcriptDoc.getLength() > 100000) {
                transcriptDoc.remove(0, 10000);
            }

            // Styles
            SimpleAttributeSet timestampStyle = new SimpleAttributeSet();
            StyleConstants.setFontFamily(timestampStyle, "Courier New");
            StyleConstants.setFontSize(timestampStyle, 11);
            StyleConstants.setForeground(timestampStyle, RetroTheme.SHADOW_DARK);

            SimpleAttributeSet usernameStyle = new SimpleAttributeSet();
            StyleConstants.setBold(usernameStyle, true);
            StyleConstants.setFontSize(usernameStyle, (Integer) fontSizeCombo.getSelectedItem());

            SimpleAttributeSet messageStyle = new SimpleAttributeSet();
            StyleConstants.setForeground(messageStyle, color);
            StyleConstants.setFontSize(messageStyle, (Integer) fontSizeCombo.getSelectedItem());
            if (type == MessageType.SERVER) {
                StyleConstants.setItalic(messageStyle, true);
            }

            // Add timestamp if enabled
            if (timestampCheck.isSelected() && type == MessageType.USER) {
                String timestamp = "[" + LocalDateTime.now().format(TIME_FORMAT) + "] ";
                transcriptDoc.insertString(transcriptDoc.getLength(), timestamp, timestampStyle);
            }

            // Add username for user messages
            if (type == MessageType.USER && !username.isEmpty()) {
                transcriptDoc.insertString(transcriptDoc.getLength(), "<" + username + ">: ", usernameStyle);
            }

            // Add message
            transcriptDoc.insertString(transcriptDoc.getLength(), text + "\n", messageStyle);

            // Autoscroll
            transcriptPane.setCaretPosition(transcriptDoc.getLength());

        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    /**
     * Set connection status
     */
    public void setConnectionStatus(String text, boolean connected) {
        if (connected && isRegistered && peopleModel.size() > 0) {
            statusLabel.setText(text + " — " + peopleModel.size() + " users");
        } else {
            statusLabel.setText(text);
        }
        statusLabel.setForeground(connected ? Color.BLACK : Color.RED);
    }

    /**
     * Show about dialog
     */
    private void showAboutDialog() {
        JDialog dialog = new JDialog(this, "About AOL Chat", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(300, 200);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(RetroTheme.BASE_GRAY);
        panel.setBorder(create3DBorder(true));

        JLabel label = new JLabel("<html><center><b>AOL Chat Room</b><br><br>" +
                "Version 1.7<br><br>" +
                "ASL? Any1?<br>" +
                "© 1995 America Online</center></html>");
        label.setFont(RetroTheme.UI_FONT);

        panel.add(label);
        dialog.add(panel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setBackground(RetroTheme.BASE_GRAY);
        JButton okButton = createRetroButton("OK");
        okButton.addActionListener(e -> dialog.dispose());
        buttonPanel.add(okButton);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    /**
     * Main method for test
     */
    public static void main(String[] args) {
        // Only run in standalone mode for testing
        if (Boolean.getBoolean("gui.standalone")) {
            SwingUtilities.invokeLater(() -> {
                GuiClient client = new GuiClient();
                client.setVisible(true);

                // For testing, auto-connect to localhost:8989
                try {
                    client.connect("localhost", 8989);
                    client.start();
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(client,
                            "Failed to connect: " + e.getMessage(),
                            "Connection Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            });
        } else {
            // Normal parse command line args
            if (args.length != 2) {
                System.err.println("Usage: java minichat.client.gui.GuiClient <host> <port>");
                System.exit(1);
            }

            String host = args[0];
            try {
                int port = Integer.parseInt(args[1]);

                SwingUtilities.invokeLater(() -> {
                    GuiClient client = new GuiClient();
                    client.setVisible(true);

                    try {
                        client.connect(host, port);
                        client.start();
                    } catch (IOException e) {
                        JOptionPane.showMessageDialog(client,
                                "Failed to connect to " + host + ":" + port + "\n" + e.getMessage(),
                                "Connection Error",
                                JOptionPane.ERROR_MESSAGE);
                        System.exit(1);
                    }
                });

            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[1]);
                System.exit(1);
            }
        }
    }
}