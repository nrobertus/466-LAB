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
    static DatagramPacket sendPkt, rcvPkt;
    static byte[] rcvData = new byte[1024];
    static int numOfPacketsToDrop = -1;
    static int[] packetIndicesToDrop;

    /**
     * @param args the command line arguments
     * @throws java.io.IOException
     */
    public static void main(String[] args) throws IOException {
        final DatagramSocket senderSocket = new DatagramSocket(9878),
                             receiverSocket = new DatagramSocket(9876);
        DatagramPacket rcvPkt = initReceiveDataConstraints();
                
        while(true) {
            receiverSocket.receive(rcvPkt);
            rcvData = rcvPkt.getData();
            System.out.println(rcvData[2]);
            if(!shouldDropPacket(rcvPkt)) {
                sendPkt = initSendDataConstraints(rcvData[0], rcvData[1], rcvData[2], rcvPkt.getAddress(), 9879);
                senderSocket.send(sendPkt);
            }
            else {
                System.out.println("Packet " + rcvData[2] + " was dropped");
            }
        }
    }
    
     /**
     * Initialize send data packet
     * @param windowSize        Size of window to track packets
     * @param packetsToDrop     Number of packets to artifically drop
     * @param currentSequence   Current sequence number
     * @param address           Address of host to connect to
     * @param portNumber        Port number to connect to
     * @throws java.net.UnknownHostException
     * @return Initialized data packet
     */
    public static DatagramPacket initSendDataConstraints(byte windowSize, byte packetsToDrop, byte currentSequence, 
        InetAddress address, int portNumber) throws UnknownHostException {
        byte[] sendData = {windowSize, packetsToDrop, currentSequence};   
        
        return new DatagramPacket(sendData, sendData.length, address, portNumber);
    }
    
    /**
     * Initialize receive data packet
     * @return Initialized packet
     */
    public static DatagramPacket initReceiveDataConstraints() {
        byte[] receiveData = new byte[1024];   
        
        return new DatagramPacket(receiveData, receiveData.length);
    }  
    
    /**
     * Determines if a packet should be dropped, and if so, drops it.
     * @param rcvPkt Received packet
     * @return Whether the received packet should be artificially dropped
     */
    public static boolean shouldDropPacket(DatagramPacket rcvPkt) {
        // Initialize which packets we will be dropping
        if(numOfPacketsToDrop == -1) {
            numOfPacketsToDrop = rcvPkt.getData()[1];
            packetIndicesToDrop = new int[numOfPacketsToDrop];
            
            Random rand = new Random();
            int temp;
            for(int i = 0; i < packetIndicesToDrop.length; i++) {
                temp = rand.nextInt(rcvPkt.getData()[3] - 1);
                while(arrayContains(packetIndicesToDrop, temp) != -1) {
                    temp = rand.nextInt(rcvPkt.getData()[3] - 1);
                }
                packetIndicesToDrop[i] = temp;
            }
        }
        
        int ret = arrayContains(packetIndicesToDrop, rcvPkt.getData()[2]);
        if(ret != -1) {
            packetIndicesToDrop[ret] = -1;
            return true;
        }
        
        return false;
    }
    
    /**
     * Check to see if an integer array contains a given value
     * @param arr Haystack
     * @param val Needle
     * @return The index of the value, if found, or -1 if not found
     */
    public static int arrayContains(int[] arr, int val) {
        for(int i = 0; i < arr.length;  i++) {
            if(arr[i] == val)
                return i;
        }
        
        return -1;
    }
}
