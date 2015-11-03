package distancevector;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import javax.swing.JOptionPane;

/**
 * @author Andrew Jenkins, Nathan Robertus
 * @date 11/2/2015
 */
public class DistanceVector {
    private static int routerID = -1;
    private static int[] routerPorts, distanceVector;
    private static DatagramSocket sendSocket, rcvSocket;
    private static DatagramPacket sendPkt, rcvPkt = initReceiveDataConstraints();
    private static byte[] rcvData = new byte[1024];
    private static boolean updated = true;
    
    public static void main(String[] args) throws IOException {
        setRouterID();
        initializeRouter();
        initializeSockets();
        initReceiveDataConstraints();
        
        while(true) {
            if(updated) {
                updateRouters();
            }
                        
            rcvSocket.receive(rcvPkt);
            rcvData = rcvPkt.getData();
            updateDistanceVector(rcvPkt.getPort());
        }
    }
    
    /**
     * Set the ID for the router
     */
    public static void setRouterID() {
        String[] options = new String[] {"0", "1", "2"};
        routerID = JOptionPane.showOptionDialog(null, "Choose a unique router ID", "Selection",
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
            null, options, options[0]);
    }
    
    /**
     * Attempts to update the distance vector with received data
     * 
     * @param incomingPortNumber port number of the router that sent the packet
     */
    public static void updateDistanceVector(int incomingPortNumber) {
        int[] incomingDistanceVector = covertByteArrayToIntArray(rcvData);
        int incomingRouterNumber = -1,thirdRouterNumber = -1;
        
        // Determine the incoming router's router number
        for(int i = 0; i < routerPorts.length; i++) {
             if(routerPorts[i] == incomingPortNumber)
                 incomingRouterNumber = i;
        }
        
        // Determine the third router number
        int[] routerPortsClone = routerPorts.clone();
        routerPortsClone[routerID] = -1;
        routerPortsClone[incomingRouterNumber] = -1;
        for(int i = 0; i < routerPortsClone.length; i++) {
            if(routerPortsClone[i] != -1) {
                thirdRouterNumber = i;
                break;
            }
        }

        System.out.println("Receives distance vector from router " + incomingRouterNumber + ": " + formattedDistanceVector(incomingDistanceVector));
        updated = false;
        for(int i = 0; i < distanceVector.length; i++) {
            if(i == routerID) {
                // Distance from router to itself should always be 0
                continue;
            }
            
            // Check immediate connection to Y
            if(incomingDistanceVector[routerID] < distanceVector[incomingRouterNumber]) {
                distanceVector[incomingRouterNumber] = incomingDistanceVector[routerID];
                updated = true;
            }
            
            // Check if it's cheaper to get to Z through Y, rather than directly
            if(incomingDistanceVector[thirdRouterNumber] + distanceVector[thirdRouterNumber] < distanceVector[incomingRouterNumber]) {
                distanceVector[incomingRouterNumber] = incomingDistanceVector[thirdRouterNumber] + distanceVector[thirdRouterNumber];
                updated = true;
            }
        }
        
        if(updated) {
            System.out.println("Distance vector on router " + routerID + " is updated to: " + formattedDistanceVector(distanceVector));
        }
        else {
            System.out.println("Distance vector on router " + routerID + " is not updated");
        }
    }
    
    /**
     * Send an updated Distance vector to all neighbor routers
     * 
     * @throws UnknownHostException
     * @throws IOException 
     */
    public static void updateRouters() throws UnknownHostException, IOException {
        for(int i = 0; i < routerPorts.length; i++) {
            // Skip ourselves
            if(i == routerID) {
                continue;
            }
            
            sendPkt = initSendDataConstraints(routerPorts[i] + 3);
            sendSocket.send(sendPkt);
        }
    }
    
    /**
     * Initialize receiver and sender sockets
     * @throws java.net.SocketException
     */
    public static void initializeSockets() throws SocketException {
        sendSocket = new DatagramSocket(routerPorts[routerID]);
        rcvSocket = new DatagramSocket(routerPorts[routerID] + routerPorts.length);
    }
    
    /**
     * Get the router's port number, and initial distance vector
     * @throws IOException 
     */
    public static void initializeRouter() throws IOException {
        assignRouterPortNumbers();
        assignDistanceVector();
    }
    
    /**
     * Read the configuration file to determine the
     * router's assigned port number
     * @throws FileNotFoundException
     * @throws IOException 
     */
    public static void assignRouterPortNumbers() throws FileNotFoundException, IOException {
        BufferedReader bufferedReader = new BufferedReader(new FileReader("configuration.txt"));
        String line = bufferedReader.readLine();
        String[] ports = line.split("\t");
        
        routerPorts = new int[ports.length];
        for(int i = 0; i < ports.length; i++) {
            routerPorts[i] = Integer.valueOf(ports[i]);
        }
        
        System.out.println("Router " + routerID + " is running on port " + routerPorts[routerID]);
        bufferedReader.close();
    }
    
    /**
     * Read the configuration file to determine the
     * router's initial distance vector.
     * @throws IOException 
     */
    public static void assignDistanceVector() throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new FileReader("configuration.txt"));
        String line;
        String[] vector;
        
        // Skip to the line we want
        for(int i = 0; i < routerID + 1; i++) {
            bufferedReader.readLine();
        }
        
        line = bufferedReader.readLine();
        vector = line.split("\t");
        distanceVector = new int[vector.length];
        for(int i = 0; i < vector.length; i++) {
            distanceVector[i] = Integer.valueOf(vector[i]);
        }
        System.out.println("Distance vector on router " + routerID + " is: " + formattedDistanceVector(distanceVector));
    }
    
    /**
     * Print a string formatted distance vector
     * @param vector Vector to format
     * @return Formatted vector
     */
    public static String formattedDistanceVector(int[] vector) {
        String formattedVector = "<";
        
        for(int i = 0; i < vector.length - 1; i++) {
            formattedVector += vector[i] + ", ";
        }
        
        return formattedVector += vector[vector.length - 1] + ">";
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
     * @param portNumber Port number to connect to
     * @throws java.net.UnknownHostException
     * @return Initialized data packet
     */
    public static DatagramPacket initSendDataConstraints(int portNumber) throws UnknownHostException {
        InetAddress IPAddress = InetAddress.getByName("localhost");
        
        byte[] data = convertIntArrayToByteArray(distanceVector);
        return new DatagramPacket(data, data.length, IPAddress, portNumber);
    }
    
    /**
     * Converts an integer array to a byte array, for sending over the network
     * @param inData
     * @return byte[] Converted array
     */
    public static byte[] convertIntArrayToByteArray(int[] inData) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(inData.length * 4);        
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(inData);
        
        return byteBuffer.array();
    }
    
    /**
     * Converts a byte array to an integer array, for receving from the network
     * @param inData
     * @return int[] Coverted array
     */
    public static int[] covertByteArrayToIntArray(byte[] inData) {
         IntBuffer intBuffer = ByteBuffer.wrap(inData).order(ByteOrder.BIG_ENDIAN).asIntBuffer();
         int[] outData = new int[distanceVector.length];
         intBuffer.get(outData);
         
         return outData;
    }
}
