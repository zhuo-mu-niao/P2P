import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class P2PClient {
    private static final String HOST = "localhost";
    private static final int RELAY_PORT = 8000;

    private ServerSocket serverSocket;
    private String username;
    private Map<String, Integer> peerPorts = new ConcurrentHashMap<>();
    private Map<String, Socket> peerConnections = new ConcurrentHashMap<>();
    private Timer keepAliveTimer;
    private TimerTask keepAliveTask;

    private JFrame frame;
    private JTextField usernameField;
    private JButton loginButton;
    private JButton logoutButton;
    private JButton refreshButton;
    private JList<String> onlineUsersList;
    private JButton connectToUserButton;
    private JTextArea messagesArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton sendFileButton;
    private JButton exitButton;

    public P2PClient() {
        frame = new JFrame("P2P Client");

        JPanel topPanel = new JPanel();
        usernameField = new JTextField(20);
        topPanel.add(new JLabel("User name:"));
        topPanel.add(usernameField);

        loginButton = new JButton("Log In");
        topPanel.add(loginButton);

        logoutButton = new JButton("Log Out");
        topPanel.add(logoutButton);

        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Connect to the server and inform it about the new client
                try {
                    serverSocket = new ServerSocket(0);
                    username = usernameField.getText();
                    Socket relaySocket = new Socket(HOST, RELAY_PORT);
                    DataOutputStream output = new DataOutputStream(relaySocket.getOutputStream());
                    output.writeUTF("NEW");
                    output.writeInt(serverSocket.getLocalPort());
                    output.writeUTF(username);
                    output.flush();
                    output.close();
                    relaySocket.close();

                    messagesArea.append("Logged in as: " + username + " on port: " + serverSocket.getLocalPort() + "\n");

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            while (!serverSocket.isClosed()) {
                                try {
                                    Socket peerSocket = serverSocket.accept();
                                    DataInputStream input = new DataInputStream(peerSocket.getInputStream());
                                    String peerUsername = input.readUTF();
                                    peerConnections.put(peerUsername, peerSocket);
                                    new Thread(new ReceiveRunnable(peerSocket, peerUsername)).start();
                                } catch (IOException ioException) {
                                    if (!serverSocket.isClosed()) {
                                        ioException.printStackTrace();
                                    }
                                }
                            }
                        }
                    }).start();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }

                // Start the keep alive timer
                startKeepAliveTimer();
            }
        });

        logoutButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                disconnectFromServer();
                closePeerConnections();
                clearData();
            }
        });

        refreshButton = new JButton("Refresh");
        topPanel.add(refreshButton);

        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    Socket relaySocketRefresh = new Socket(HOST, RELAY_PORT);
                    DataOutputStream outputRefresh = new DataOutputStream(relaySocketRefresh.getOutputStream());
                    outputRefresh.writeUTF("REQUEST");
                    outputRefresh.flush();

                    DataInputStream inputRefresh = new DataInputStream(relaySocketRefresh.getInputStream());
                    int size = inputRefresh.readInt();
                    peerPorts.clear();
                    DefaultListModel<String> listModel = new DefaultListModel<>();
                    for (int i = 0; i < size; i++) {
                        String peerUsername = inputRefresh.readUTF();
                        int peerPort = inputRefresh.readInt();
                        peerPorts.put(peerUsername, peerPort);
                        listModel.addElement(peerUsername);
                    }
                    onlineUsersList.setModel(listModel);

                    inputRefresh.close();
                    relaySocketRefresh.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        });

        onlineUsersList = new JList<>();
        JScrollPane onlineUsersScroll = new JScrollPane(onlineUsersList);

        connectToUserButton = new JButton("Connect");
        connectToUserButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedUser = onlineUsersList.getSelectedValue();
                if (selectedUser != null) {
                    try {
                        Socket peerSocket = new Socket(HOST, peerPorts.get(selectedUser));
                        DataOutputStream output = new DataOutputStream(peerSocket.getOutputStream());
                        output.writeUTF(username);
                        output.flush();
                        peerConnections.put(selectedUser, peerSocket);
                        new Thread(new ReceiveRunnable(peerSocket, selectedUser)).start();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
            }
        });

        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.add(onlineUsersScroll, BorderLayout.CENTER);
        usersPanel.add(connectToUserButton, BorderLayout.SOUTH);

        messagesArea = new JTextArea(20, 50);
        JScrollPane messagesScroll = new JScrollPane(messagesArea);

        JPanel bottomPanel = new JPanel();
        messageField = new JTextField(30);
        sendButton = new JButton("Send");
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedUser = onlineUsersList.getSelectedValue();
                if (selectedUser != null) {
                    Socket peerSocket = peerConnections.get(selectedUser);
                    if (peerSocket != null) {
                        try {
                            DataOutputStream output = new DataOutputStream(peerSocket.getOutputStream());
                            output.writeUTF("MESSAGE");
                            output.writeUTF(messageField.getText());
                            String formattedMessage = "send to " + selectedUser + ": " + messageField.getText() + "\n";
                            messagesArea.append(formattedMessage);
                            output.flush();
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                        }
                    }
                }
            }
        });

        sendFileButton = new JButton("Send File");
        sendFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedUser = onlineUsersList.getSelectedValue();
                if (selectedUser != null) {
                    JFileChooser fileChooser = new JFileChooser();
                    int returnVal = fileChooser.showOpenDialog(frame);
                    if (returnVal == JFileChooser.APPROVE_OPTION) {
                        File file = fileChooser.getSelectedFile();
                        sendFile(selectedUser, file);
                    }
                }
            }
        });

        bottomPanel.add(messageField);
        bottomPanel.add(sendButton);
        bottomPanel.add(sendFileButton);

        exitButton = new JButton("Exit");
        topPanel.add(exitButton);

        exitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exit();
            }
        });

        frame.getContentPane().add(topPanel, BorderLayout.NORTH);
        frame.getContentPane().add(usersPanel, BorderLayout.WEST);
        frame.getContentPane().add(messagesScroll, BorderLayout.CENTER);
        frame.getContentPane().add(bottomPanel, BorderLayout.SOUTH);

        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    private void sendFile(String receiver, File file) {
        Socket peerSocket = peerConnections.get(receiver);
        if (peerSocket != null) {
            try {
                DataOutputStream output = new DataOutputStream(peerSocket.getOutputStream());
                output.writeUTF("FILE");
                output.writeUTF(file.getName());
                output.writeLong(file.length());

                FileInputStream fileInputStream = new FileInputStream(file);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                fileInputStream.close();
                output.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class ReceiveRunnable implements Runnable {
        private final Socket socket;
        private final String username;

        public ReceiveRunnable(Socket socket, String username) {
            this.socket = socket;
            this.username = username;
        }

        @Override
        public void run() {
            try {
                DataInputStream input = new DataInputStream(socket.getInputStream());
                while (!socket.isClosed()) {
                    String messageType = input.readUTF();
                    if (messageType.equals("MESSAGE")) {
                        String message = input.readUTF();
                        messagesArea.append(username + ": " + message + "\n");
                    } else if (messageType.equals("FILE")) {
                        String fileName = input.readUTF();
                        long fileSize = input.readLong();
                        receiveFile(username, fileName, fileSize, input);
                    }
                }
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    e.printStackTrace();
                }
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void receiveFile(String sender, String fileName, long fileSize, DataInputStream input) throws IOException {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(fileName));
            int returnVal = fileChooser.showSaveDialog(frame);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                byte[] buffer = new byte[4096];
                long remainingSize = fileSize;
                int bytesRead;
                while (remainingSize > 0 && (bytesRead = input.read(buffer, 0, (int) Math.min(buffer.length, remainingSize))) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                    remainingSize -= bytesRead;
                }
                fileOutputStream.close();
                messagesArea.append(sender + " sent a file: " + fileName + " (" + fileSize + " bytes)\n");
            }
        }
    }

    private void disconnectFromServer() {
        try {
            // Disconnect from the server
            Socket relaySocket = new Socket(HOST, RELAY_PORT);
            DataOutputStream output = new DataOutputStream(relaySocket.getOutputStream());
            output.writeUTF("REMOVE");
            output.writeUTF(username);
            output.flush();
            output.close();
            relaySocket.close();

            messagesArea.append("Disconnected from the server\n");
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        // Cancel the keep alive timer
        keepAliveTask.cancel();
    }

    private void closePeerConnections() {
        // Close all peer connections
        for (Socket socket : peerConnections.values()) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        peerConnections.clear();

        messagesArea.append("Closed all peer connections\n");
    }

    private void clearData() {
        // Clear data and reset UI
        serverSocket = null;
        peerPorts.clear();
        DefaultListModel<String> listModel = new DefaultListModel<>();
        onlineUsersList.setModel(listModel);

        messagesArea.append("Cleared data\n");
    }

    private void exit() {
        disconnectFromServer();
        closePeerConnections();
        clearData();

        // Close the server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        // Exit the application
        System.exit(0);
    }

    private void sendKeepAliveSignal() {
        try {
            Socket relaySocket = new Socket(HOST, RELAY_PORT);
            DataOutputStream output = new DataOutputStream(relaySocket.getOutputStream());
            output.writeUTF("KEEP_ALIVE");
            output.writeUTF(username);
            output.flush();
            output.close();
            relaySocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startKeepAliveTimer() {
        keepAliveTimer = new Timer();
        keepAliveTask = new TimerTask() {
            @Override
            public void run() {
                sendKeepAliveSignal();
            }
        };
        keepAliveTimer.schedule(keepAliveTask, 10000, 10000); // Send every 10 seconds
    }

    public static void main(String[] args) {
        new P2PClient();
    }
}
