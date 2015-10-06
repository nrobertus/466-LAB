package receiver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * @date 09/30/2015
 * @author Andrew Jenkins, Nathan Robertus
 */
public class Receiver {

    static DatagramPacket sendPkt, rcvPkt = initReceiveDataConstraints();
    static byte[] rcvData = new byte[1024];
    static int numOfPacketsToDrop = -1;
    static int[] packetIndicesToDrop;
    static String[] window;
    static byte currentSequence, sequenceNumberSize, windowSize;

    /**
     * @param args the command line arguments
     * @throws java.io.IOException
     */
    public static void main(String[] args) throws IOException {
        final DatagramSocket senderSocket = new DatagramSocket(9878),
                receiverSocket = new DatagramSocket(9876);

        while (window == null || !allPacketsReceived(window)) {
            receiverSocket.receive(rcvPkt);
            rcvData = rcvPkt.getData();

            // Initialize window size and sequence size
            if (window == null) {
                initWindow(rcvData[0]);
                windowSize = currentSequence = rcvData[0];
                sequenceNumberSize = rcvData[3];
            }

            if (!shouldDropPacket(rcvPkt)) {
                window = updateWindow(window, rcvPkt.getData()[2]);
                sendPkt = initSendDataConstraints(rcvData[0], rcvData[1], rcvData[2], rcvPkt.getAddress(), 9879);
                senderSocket.send(sendPkt);
            } else {
                System.out.println("Packet " + rcvData[2] + " was dropped");
            }
        }
    }

    /**
     * Determines if all packets have been successfully received
     *
     * @param window Current window
     * @return Whether all packets have been received
     */
    public static boolean allPacketsReceived(String[] window) {
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
     * Check to see if an integer array contains a given value
     *
     * @param arr Haystack
     * @param val Needle
     * @return The index of the value, if found, or -1 if not found
     */
    public static int arrayContains(int[] arr, int val) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == val) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Initialize receive data packet
     *
     * @return Initialized packet
     */
    public static DatagramPacket initReceiveDataConstraints() {
        byte[] receiveData = new byte[1024];

        return new DatagramPacket(receiveData, receiveData.length);
    }

    /**
     * Initialize send data packet
     *
     * @param windowSize Size of window to track packets
     * @param packetsToDrop Number of packets to artifically drop
     * @param currentSequence Current sequence number
     * @param address Address of host to connect to
     * @param portNumber Port number to connect to
     * @throws java.net.UnknownHostException
     * @return Initialized data packet
     */
    public static DatagramPacket initSendDataConstraints(byte windowSize, byte packetsToDrop, byte currentSequence,
            InetAddress address, int portNumber) throws UnknownHostException {
        byte[] sendData = {windowSize, packetsToDrop, currentSequence};

        return new DatagramPacket(sendData, sendData.length, address, portNumber);
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
        window[window.length - 1] = ((sequenceNumber >= sequenceNumberSize) || sequenceNumber < 0) ? "-" : String.valueOf(sequenceNumber);
        return window;
    }

    /**
     * Determines if a packet should be dropped, and if so, drops it.
     *
     * @param rcvPkt Received packet
     * @return Whether the received packet should be artificially dropped
     */
    public static boolean shouldDropPacket(DatagramPacket rcvPkt) {
        // Initialize which packets we will be dropping
        if (numOfPacketsToDrop == -1) {
            numOfPacketsToDrop = rcvPkt.getData()[1];
            packetIndicesToDrop = new int[numOfPacketsToDrop];

            Random rand = new Random();
            int temp;
            for (int i = 0; i < packetIndicesToDrop.length; i++) {
                temp = rand.nextInt(rcvPkt.getData()[3] - 1);
                while (arrayContains(packetIndicesToDrop, temp) != -1) {
                    temp = rand.nextInt(rcvPkt.getData()[3] - 1);
                }
                packetIndicesToDrop[i] = temp;
            }
        }

        int ret = arrayContains(packetIndicesToDrop, rcvPkt.getData()[2]);
        if (ret != -1) {
            packetIndicesToDrop[ret] = -1;
            return true;
        }

        return false;
    }

    /**
     * Updates the window with the new given information
     *
     * @param window Current window to update
     * @param sequenceNumber Sequence number to update
     * @return Updated window
     */
    public static String[] updateWindow(String[] window, int sequenceNumber) {
        int indexToUpdate = -1;
        for (int i = 0; i < window.length; i++) {
            if (window[i] == null || window[i].equals(String.valueOf(sequenceNumber)) || window[i].subSequence(0, window[i].length() - 1).equals(String.valueOf(sequenceNumber))) {
                indexToUpdate = i;
                break;
            }
        }

        // ACKed
        window[indexToUpdate] = String.valueOf(sequenceNumber) + "#";

        // See if we're ready to shift the window.
        int timesToShift = 0;
        for (int i = 0; i < window.length; i++) {
            if (window[i] != null && window[i].contains("#")) {
                timesToShift++;
            } else {
                break;
            }
        }

        // Shift the window timesToShift times
        for (int i = 0; i < timesToShift; i++) {
            shiftWindow(window, currentSequence);
            currentSequence++;
        }

        System.out.println("Packet " + sequenceNumber + " is receieved, window " + printWindow(window));

        return window;
    }
}
