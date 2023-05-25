import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final int PORT = 8000;
    private static final Map<String, Integer> clientPorts = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastSignalTime = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Relay server started on port: " + PORT);

        // Runnable to check for inactive clients and remove them
        Runnable checkInactiveClients = () -> {
            long currentTime = System.currentTimeMillis();
            for (Map.Entry<String, Long> entry : lastSignalTime.entrySet()) {
                String username = entry.getKey();
                long lastSignal = entry.getValue();
                if (currentTime - lastSignal > 30000) { // 30 seconds of inactivity
                    clientPorts.remove(username);
                    lastSignalTime.remove(username);
                    System.out.println(username + " is offline.");
                }
            }
        };

        // Create a scheduled executor service to run the checkInactiveClients Runnable every second
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        executorService.scheduleAtFixedRate(checkInactiveClients, 0, 1000, TimeUnit.MILLISECONDS);

        while (true) {
            Socket socket = new Socket();
            try {
                socket = serverSocket.accept();
                DataInputStream input = new DataInputStream(socket.getInputStream());
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());

                String command = input.readUTF();
                if (command.equals("NEW")) {
                    int port = input.readInt();
                    String username = input.readUTF();
                    clientPorts.put(username, port);
                    lastSignalTime.put(username, System.currentTimeMillis());
                    System.out.println(username + " joined on port: " + port);
                } else if (command.equals("REQUEST")) {
                    output.writeInt(clientPorts.size());
                    for (Map.Entry<String, Integer> entry : clientPorts.entrySet()) {
                        output.writeUTF(entry.getKey());
                        output.writeInt(entry.getValue());
                    }
                    output.flush();
                } else if (command.equals("REMOVE")) {
                    String username = input.readUTF();
                    clientPorts.remove(username);
                    lastSignalTime.remove(username);
                    System.out.println(username + " has left.");
                } else if (command.equals("KEEP_ALIVE")) {
                    String username = input.readUTF();
                    lastSignalTime.put(username, System.currentTimeMillis());
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
