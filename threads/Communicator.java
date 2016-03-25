package nachos.threads;

import nachos.machine.Lib;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
        sharedLock.acquire(); // acquire the lock so that no speakers or audience can enter

        numSpeaker += 1;
        while (numAudience == 0 || hasActiveSpeaker) {
            speaker.sleep();
        }
        hasActiveSpeaker = true;

        this.word = word;
        this.fetched = false;

        while (this.fetched == false) {
            audience.wake();
            speaker.sleep();
        }

        numSpeaker -= 1;
        hasActiveSpeaker = false;
        speaker.wake();
        sharedLock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
        sharedLock.acquire(); // acquire the lock so that no speakers or audience can enter

        numAudience += 1;
        while (numSpeaker == 0 || hasActiveAudience) {
            audience.sleep();
        }
        hasActiveAudience = true;

        while (this.fetched == true) {
            speaker.wake();
            audience.sleep();
        }

        int ret = this.word;
        this.fetched = true;
        speaker.wakeAll();

        numAudience -= 1;
        hasActiveAudience = false;
        audience.wake();
        sharedLock.release();
        return ret;
    }

    /**
     * + : speakers waiting
     * 0 : free
     * - : audiences waiting
     */

    int word;

    int numSpeaker;
    boolean hasActiveSpeaker;
    int numAudience;
    boolean hasActiveAudience;

    boolean fetched = true;

    private Lock sharedLock = new Lock();
    private Condition speaker = new Condition(sharedLock);
    private Condition audience = new Condition(sharedLock);
}

class CommunicatorTest {
    static final int SPEAK = 0;
    static final int LISTEN = 1;

    static int numClients = 6;
    static int numClientLoop = 4;
    static int leftBits = numClients * numClientLoop;
    static int leftOnes = leftBits / 2;
    static int currentBalance = 0;

    static int[] status;

    /**
     * Used for testing.
     * The bit decides whether a client speaks or listens.
     * There must be at least one client speaking or listening.
     */
    static int nextBit() {
        double random = Math.random();
        if ((currentBalance != numClients - 1 && random * leftBits < leftOnes) || currentBalance == 1 - numClients) {
            currentBalance += 1;
            leftBits -= 1;
            return SPEAK;
        }
        currentBalance -= 1;
        leftOnes -= 1;
        leftBits -= 1;
        return LISTEN;
    }

    private static class PingPong implements Runnable {
        Communicator communicator;
        int id;

        public PingPong(Communicator communicator1, int id1) {
            communicator = communicator1;
            id = id1;
        }

        void report(int method, int answer) {
            if (method == SPEAK) {
                Lib.debug('x',  String.format("@%d Speaking %09d", id, answer));
                status[id] = answer;
            } else {
                int found = -1;
                for (int i = 0; i < numClientLoop; ++i) {
                    if (status[i] == answer) {
                        found = i;
                        status[i] = -1;
                        break;
                    }
                }
                Lib.assertTrue(found >= 0);

                Lib.debug('x', String.format("@%d Listened %09d from %d", id, answer, found));
            }
        }

        public void run() {
            for (int i = 0; i < numClientLoop; ++i) { // randomly speaking or listening
                int bit = nextBit();
                if (bit == 0) {
                    int x = (int) (Math.random() * 1e9);
                    report(SPEAK, x);
                    communicator.speak(x);
                } else {
                    Lib.debug('x', String.format("> @%d: listen", id));
                    int y = communicator.listen();
                    report(LISTEN, y);
                }
            }
        }
    }

    public static void selfTest() {
        status = new int[numClients];

        Lib.debug('t', "Self test for Communicator");

        Communicator communicator = new Communicator();
        for (int i = 1; i < numClients; ++i) {
            new KThread(new PingPong(communicator, i)).setName("child" + Integer.toString(i)).fork();
        }

        new PingPong(communicator, 0).run();
    }

}