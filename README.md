# MiniChat
# Programming Assignment 1
# Author: Nicholas D. Smith
# ID: 2471223

## Overview
MiniChat is a multi-threaded client-server chat application built using Java TCP sockets. It allows multiple users to connect to a centralized server and communicate in real-time through a group chat interface, via CLI or my custom GUI.

## Features
- **Multi-client Support**: Server handles multiple concurrent client connections with multi-threading
- **User Registration**: Each client registers with unique usernames
- **Real-time Broadcasting**: Messages instantly broadcast to all users
- **User Management**: List all active users with `AllUsers` command
- **Graceful Disconnection**: Handling client disconnect with goodbye message
- **GUI Support**: Insanely hi-tech graphical user interface for maximal enhancement of user experience

## Compilation Instructions

### Using the Build Scripts

#### On Linux/macOS:
```bash
chmod +x scripts/compile.sh
./scripts/compile.sh
```

#### On Windows:
```cmd
scripts\compile.bat
```

## Running the Application

### Step 1: Start the Server
```bash
java -jar jar/server.jar <port>
```
Example:
```bash
java -jar jar/server.jar 8989
```

### Step 2: Start Client(s)

#### Command-Line Client:
```bash
java -jar jar/client.jar <host> <port>
```
Example:
```bash
java -jar jar/client.jar localhost 8989
```

#### GUI Client:
```bash
java -jar jar/client-gui.jar <host> <port>
```
Then enter details in the connection dialog.

## Usage Guide

### Client Commands

1. **Username Registration**
   - When prompted, enter your username
   - Example: `Nick`

2. **Send Messages**
   - Simply type your message and press Enter
   - The message will be broadcast to all connected users
   - Example: `Heyyy :) A/S/L?!`

3. **View Active Users**
   - Type `AllUsers` to see a list of all connected users
   - Shows username and connection time

4. **Disconnect**
   - Type `Bye` to disconnect gracefully
   - Server will broadcast a goodbye message to all users

### Server Console Output
The server displays:
- Server startup message with port number
- User connection/registration with timestamp
- All chat messages with timestamps
- User disconnect messages
