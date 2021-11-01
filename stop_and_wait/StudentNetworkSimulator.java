import java.util.*;
import java.io.*;

public class StudentNetworkSimulator extends NetworkSimulator
{
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                     Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B 
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity): 
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment): 
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     *  Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData): 
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *          returns the Packet's payload
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;

    //region self define attributes
    private ArrayList<Packet> senderWindowPackets;
    private ArrayList<Packet> receiverWindowPackets;
    private int nextSeqnum;
    private int bSeqnum;
    private boolean isWaitting;

    // attributes for statistics
    private int originalTransimittedByA, retransmitByA, numDeliverdToB, numOfACK, numOfCorruptedPackets;

    //endregion

    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

    // This is the constructor.  Don't touch!
    public StudentNetworkSimulator(int numMessages,
                                   double loss,
                                   double corrupt,
                                   double avgDelay,
                                   int trace,
                                   int seed,
                                   int winsize,
                                   double delay)
    {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        // LimitSeqNo = winsize*2; // set appropriately; assumes SR here!
        LimitSeqNo = WindowSize + 1;  // stop and wait
        RxmtInterval = delay;

        this.senderWindowPackets = new ArrayList<Packet>();
        this.receiverWindowPackets = new ArrayList<Packet>();
        this.nextSeqnum = 0;
        this.bSeqnum = 0;
        this.isWaitting = false;

        // statistics
        this.originalTransimittedByA = 0;
        this.retransmitByA = 0;
        this.numDeliverdToB = 0;
        this.numOfACK = 0;


    }

    
    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message)
    {
        System.out.println("aOutput");
        // if currently in the waiting state, do not take message from above
        if (isWaitting) {
            System.out.println("blocked by waiting");
            System.out.println("");
            return;
        }
        Packet newPacket = new Packet(this.nextSeqnum, 0, 0,message.getData());
        // set sequence number to the next sequence number
        this.moveToNextSeqnum();
        // add checksum to the packet
        this.addChecksum(newPacket);
        // save the packet
        if (this.senderWindowPackets.size() >= this.WindowSize) {
            this.senderWindowPackets.remove(0);
        }
        this.senderWindowPackets.add(newPacket);
        // set state to waiting
        this.isWaitting = true;
        // start the timer
        this.startTimer(A, RxmtInterval);
        // send the packet
        this.toLayer3(A, newPacket);
        System.out.println("");

        // add 1 to the count for transmitted packet
    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet)
    {
        System.out.println("aInput");
        // if the receied is ACK (checksum ok and not corrupted and acknum is the save sequence num)
        if (this.evaluateChecksum(packet) &&
                (packet.getAcknum() == this.senderWindowPackets.get(0).getSeqnum())) {
            // stop timmer
            this.stopTimer(A);
            // leave the waitting state
            this.isWaitting = false;
            System.out.println("ack");
        }
        // if the received packet is corrupted ro not ACK (acknum different from save sequenc enum
        else {
            this.toLayer3(A, this.senderWindowPackets.get(0));

            // restart timer (stop and start timer)
            this.stopTimer(A);
            this.startTimer(A, RxmtInterval);
            System.out.println("nak");
        }

        System.out.println("");

    }
    
    // This routine will be called when A's timer expires (thus generating a 
    // timer interrupt). You'll probably want to use this routine to control 
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped. 
    protected void aTimerInterrupt()
    {
        System.out.println("aTimerInterrupt");
        this.toLayer3(A, this.senderWindowPackets.get(0));

        // restart timer (stop and start timer)
        this.stopTimer(A);
        this.startTimer(A, RxmtInterval);
    }
    
    // This routine will be called once, before any of your other A-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit()
    {
        System.out.println("In aInit");
        this.startTimer(A, RxmtInterval);
        this.stopTimer(A );
        System.out.println("Exit aInit");

    }
    
    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet)
    {
        System.out.println("bInput");
        // if perfecty
        if (this.evaluateChecksum(packet) && packet.getSeqnum() == this.bSeqnum) {
            System.out.println("correct message");
            // move to next seqeuence number check in b
            this.moveBSeqnum();
            // extract packet and send to layer above
            toLayer5(packet.getPayload());
            // create new ack packet, and adding proper checksum to it
            Packet ackPacket = new Packet(0, packet.getSeqnum(), 0);
            this.addChecksum(ackPacket);
            // send ack
            toLayer3(B, ackPacket);
        }
        // if corrupt or wrong seqnum
        else {
            System.out.println("inccorect message, send nak");
            // create new ack packet, and adding proper checksum to it
            Packet ackPacket = new Packet(0, packet.getSeqnum(), 0);
            this.addChecksum(ackPacket);
            // send ack
            toLayer3(B, ackPacket);
        }
        System.out.println("");
    }
    
    // This routine will be called once, before any of your other B-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit()
    {

    }


    //region self define function
    public void moveToNextSeqnum() {
        this.nextSeqnum = (this.nextSeqnum + 1) / this.LimitSeqNo;
    }
    public void moveBSeqnum() {
        this.bSeqnum = (this.bSeqnum + 1) / this.LimitSeqNo;
    }

    public void addChecksum(Packet p) {
        int newChecksum = caculateChecksum(p);
        p.setChecksum(newChecksum);
    }

    public int caculateChecksum(Packet p) {
        int newChecksum = 0;
        newChecksum += p.getSeqnum();
        newChecksum += p.getAcknum();
        for (Character c : p.getPayload().toCharArray()) {
            newChecksum += Character.getNumericValue(c);
        }
        return newChecksum;
    }
    //endregion

    public boolean evaluateChecksum(Packet p) {
        int newChecksum = 0;
        newChecksum += p.getSeqnum();
        newChecksum += p.getAcknum();
        for (Character c : p.getPayload().toCharArray()) {
            newChecksum += Character.getNumericValue(c);
        }
        return newChecksum == p.getChecksum();
    }

    // Use to print final statistics
    protected void Simulation_done()
    {
            // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
            System.out.println("\n\n===============STATISTICS=======================");
            System.out.println("Number of original packets transmitted by A:" + "<YourVariableHere>");
            System.out.println("Number of retransmissions by A:" + "<YourVariableHere>");
            System.out.println("Number of data packets delivered to layer 5 at B:" + "<YourVariableHere>");
            System.out.println("Number of ACK packets sent by B:" + "<YourVariableHere>");
            System.out.println("Number of corrupted packets:" + "<YourVariableHere>");
            System.out.println("Ratio of lost packets:" + "<YourVariableHere>" );
            System.out.println("Ratio of corrupted packets:" + "<YourVariableHere>");
            System.out.println("Average RTT:" + "<YourVariableHere>");
            System.out.println("Average communication time:" + "<YourVariableHere>");
            System.out.println("==================================================");

            // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
            System.out.println("\nEXTRA:");
            // EXAMPLE GIVEN BELOW
            //System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>"); 
    }        

}
