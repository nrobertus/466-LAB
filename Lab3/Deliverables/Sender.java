package sender;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @date 09/30/2015
 * @author Andrew Jenkins, Nathan Robertus
 */
public class Sender {

    static byte windowSize, packetsToDrop, sequenceNumberSize, currentSequence = 0;
    static DatagramPacket sendPkt, rcvPkt;
    static long[] timers;
    InetAddress IPAddress;
    static Timer timer = new Timer();
    static byte resendIndex = -1;
    static String[] window;
    int port;

    /**
     * @param args the command line arguments
     * @throws java.net.SocketException
     * @throws java.net.UnknownHostException
     */
    public static void main(String[] args) throws SocketException, UnknownHostException, IOException {
        final DatagramSocket senderSocket = new DatagramSocket(9877),
                receiverSocket = new DatagramSocket(9879);

        // Track timers on each packet, and resend them if they time out
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                // Don't want to run this logic before everything is initialized
                // or after packet transfer has completed
                if (window == null || allPacketsDelivered(window)) {
                    return;
                }

                resendIndex = needToResendPacket();
                if (resendIndex != -1) {
                    try {
                        sendPkt = initSendDataConstraints(resendIndex, "localhost", 9876);
                    } catch (UnknownHostException ex) {
                    }
                    try {
                        resendPacket(senderSocket, window, resendIndex);
                    } catch (IOException ex) {
                    }
                }
            }
        }, 50, 50);

        initData();
        while (!allPacketsDelivered(window)) {
            if (canSendPacket(window)) {
                sendPkt = initSendDataConstraints(currentSequence, "localhost", 9876);
                sendPacket(senderSocket, window);
            } else {
                rcvPkt = initReceiveDataConstraints();
                receiverSocket.receive(rcvPkt);
                window = updateWindow(window, rcvPkt.getData()[2], true);
            }
        }

        System.out.println("All packets have been successfully delivered.");
        System.exit(0);
    }

    /**
     * Determines if all packets have been successfully delivered
     *
     * @param window Current window
     * @return Whether all packets have been delivered
     */
    public static boolean allPacketsDelivered(String[] window) {
        for (int i = 0; i < window.length; i++) {
            // Check if our String contains a number... if it does, not all
            // packets have been ACKed
            if (window[i] == null || window[i].matches(".*\\d+.*")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if we are in a position to send a new packet
     *
     * @param window Current window
     * @return Whether we are ready to send a new packet
     */
    public static boolean canSendPacket(String[] window) {
        for (int i = 0; i < window.length; i++) {
            if (window[i] == null || (!window[i].equals("-") && !window[i].contains("*") && !window[i].contains("#"))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get input from user for required program parameters
     */
    public static void initData() {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter the window’s size on the sender: ");
        windowSize = scanner.nextByte();

        System.out.print("Enter the maximum sequence number on the sender: ");
        sequenceNumberSize = scanner.nextByte();

        System.out.print("Select the packet(s) that will be dropped: ");
        packetsToDrop = scanner.nextByte();

        timers = new long[windowSize];
        initWindow(windowSize);
    }

    /**
     * Initialize receive data packet
     *
     * @return DatagramPacket initialized packet
     */
    public static DatagramPacket initReceiveDataConstraints() {
        byte[] receiveData = new byte[1024];

        return new DatagramPacket(receiveData, receiveData.length);
    }

    /**
     * Initialize send data packet
     *
     * @param currentSequence Current sequence number
     * @param hostName Name of host to connect to
     * @param portNumber Port number to connect to
     * @throws java.net.UnknownHostException
     * @return Initialized data packet
     */
    public static DatagramPacket initSendDataConstraints(byte currentSequence, String hostName, int portNumber) throws UnknownHostException {
        byte[] sendData = {windowSize, packetsToDrop, currentSequence, sequenceNumberSize};
        InetAddress IPAddress = InetAddress.getByName(hostName);

        return new DatagramPacket(sendData, sendData.length, IPAddress, portNumber);
    }

    /**
     * Initialize the window with the initial values
     *
     * @param windowSize Size of window
     */
    public static void initWindow(int windowSize) {
        window = new String[windowSize];
        for (int i = 0; i < windowSize; i++) {
            window[i] = String.valueOf(i);
        }
    }

    /**
     * Determines if a packet needs to be resent
     *
     * @return Whether the packet needs to be resent
     */
    public static byte needToResendPacket() {
        long currentTime = System.currentTimeMillis();
        for (byte i = 0; i < timers.length; i++) {
            // Wait 200ms before we resend packets
            if (currentTime - timers[i] > 200 && window[i].contains("*")) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Formats the window to be readable by the console
     *
     * @param window
     * @return Formatted string
     */
    public static String printWindow(String[] window) {
        String concat = "[";

        for (int i = 0; i < window.length; i++) {
            concat += window[i] + ",";
        }

        return concat.subSequence(0, concat.length() - 1) + "]";
    }

    /**
     * Attempt to resend a packet that has timed out
     *
     * @param socket Socket to send the packet to
     * @param window Current window
     * @param resendIndex Window index of the packet to resend
     * @throws IOException
     */
    public static void resendPacket(DatagramSocket socket, String[] window, int resendIndex) throws IOException {
        byte sequenceNumber = Byte.valueOf(window[resendIndex].substring(0, window[resendIndex].indexOf("*")));
        sendPkt = initSendDataConstraints(sequenceNumber, "localhost", 9876);
        socket.send(sendPkt);
        startTimerOnSentPacket(sequenceNumber % windowSize);
        window = updateWindow(window, sequenceNumber, false);
        System.out.println("Packet " + sequenceNumber + " has been resent, window " + printWindow(window));
    }

    /**
     * Send a packet
     *
     * @param socket Socket to send the packet to
     * @param window Current window
     * @throws IOException
     */
    public static void sendPacket(DatagramSocket socket, String[] window) throws IOException {
        socket.send(sendPkt);
        startTimerOnSentPacket(currentSequence % windowSize);
        window = updateWindow(window, currentSequence, false);
        System.out.println("Packet " + currentSequence + " is sent, window " + printWindow(window));
        currentSequence++;
    }

    /**
     * Shifts the window to the right by one
     *
     * @param window Window to shift
     * @param sequenceNumber sequenceNumber to add
     * @return Updated window
     */
    public static String[] shiftWindow(String[] window, int sequenceNumber) {
        // Shift the window
        for (int i = 0; i < window.length - 1; i++) {
            window[i] = window[i + 1];
        }
        // Determine if we've hit the limit, and add the final value in accordingly
        window[window.length - 1] = (sequenceNumber >= sequenceNumberSize) ? "-" : String.valueOf(sequenceNumber);
        return window;
    }

    /**
     * Begins the timer on a sent packet
     *
     * @param indexToUpdate Timer index to restart
     */
    public static void startTimerOnSentPacket(int indexToUpdate) {
        timers[indexToUpdate] = System.currentTimeMillis();
    }

    /**
     * Updates the window with the new given information
     *
     * @param window Current window to update
     * @param sequenceNumber Sequence number to update
     * @param status Status to update the sequence number with. True indicates
     * ACKed, false indicates sent, but not ACKed.
     * @return Updated window
     */
    public static String[] updateWindow(String[] window, int sequenceNumber, boolean status) {
        int indexToUpdate = -1;

        // Find the incideces we need to update
        for (int i = 0; i < window.length; i++) {
            if (window[i] == null || window[i].equals(String.valueOf(sequenceNumber)) || window[i].subSequence(0, window[i].length() - 1).equals(String.valueOf(sequenceNumber))) {
                indexToUpdate = i;
                break;
            }
        }

        if (!status) {
            // Sent, not ACKed
            window[indexToUpdate] = String.valueOf(sequenceNumber) + "*";
        } else {
            // ACKed
            window[indexToUpdate] = String.valueOf(sequenceNumber) + "#";
        }

        // See if we're ready to shift the window.
        int timesToShift = 0;
        for (int i = 0; i < window.length; i++) {
            if (window[i] != null && !window[i].contains("*")) {
                timesToShift++;
            } else {
                break;
            }
        }

        // Shift the window timesToShift times
        for (int i = 0; i < timesToShift; i++) {
            shiftWindow(window, currentSequence + i);
        }

        if (status) {
            System.out.println("Ack " + sequenceNumber + " is receieved, window " + printWindow(window));
        }

        return window;
    }
}
