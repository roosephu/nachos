package nachos.threads;

import nachos.machine.Lib;

import java.util.LinkedList;
import java.util.Random;

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
        Lib.assertTrue(false);
        sharedLock.acquire(); // acquire the lock so that no speakers or audience can enter

        // We force the speaker to wait until an audience comes.
        waitSpeakers += 1;
        while (!hasActiveAudience || hasActiveSpeaker) {
            speaker.sleep();
        }
        waitSpeakers -= 1;
        hasActiveSpeaker = true;

        this.word = word;
        pair.wake();
        pair.sleep();

        hasActiveSpeaker = false;
        if (waitSpeakers > 0)
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

        waitAudiences += 1;
        while (hasActiveAudience) {
            audience.sleep();
        }
        waitAudiences -= 1;
        hasActiveAudience = true;

        speaker.wake();
        pair.sleep(); // The audience is waiting for a speaker to wake it up.
        int ret = word;
        pair.wake();

        hasActiveAudience = false;
        if (waitAudiences > 0) // A new audience may come, wake it up.
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

    boolean hasActiveSpeaker = false;
    boolean hasActiveAudience = false;
    int waitSpeakers = 0;
    int waitAudiences = 0;

    private Lock sharedLock = new Lock();
    private Condition speaker = new Condition(sharedLock);
    private Condition audience = new Condition(sharedLock);
    private Condition pair = new Condition(sharedLock);
}

class CommunicatorTest {
    static final int SPEAK = 0;
    static final int LISTEN = 1;

    static Random random = ThreadedKernel.random;
    static int numClients = 20;
    static int numClientLoop = 1000;
    static int terminated = 0;
    static int leftBits = numClients * numClientLoop;
    static int leftOnes = leftBits / 2;

    static Lock lock = new Lock();
    static Condition terminate = new Condition(lock);

    static int[] status;
    static int[] remaining;

    /**
     * Given current state, determine whether deadlock will happen.
     */
//    static boolean willDeadlock(int[] oldStatus, int[] remaining) {
//        int[] status = oldStatus.clone();
//        int freeClient = 0;
//        for (int i = 0; i < numClients; ++i)
//            if (status[i] == 0)
//                freeClient += remaining[i];
//
//        LinkedList<Integer> speakers = new LinkedList<>(), audiences = new LinkedList<>();
//        while (true) {
//            int maxId = -1;
//            for (int i = 0; i < numClients; ++i)
//                if (status[i] != 0 && (maxId == -1 || remaining[i] > remaining[maxId]))
//                    maxId = i;
//            if (maxId == -1) { // no client is allocating resources
//                break;
//            } else {
//                if (status[maxId] < 0) { // listening
//                    if (!speakers.isEmpty()) {
//                        int speaker = speakers.poll();
//                        freeClient += remaining[speaker];
//                        freeClient += remaining[maxId];
//                    } else {
//                        audiences.push(maxId);
//                    }
//                } else {
//                    if (!audiences.isEmpty()) {
//                        int audience = audiences.poll();
//                        freeClient += remaining[audience];
//                        freeClient += remaining[maxId];
//                    } else {
//                        speakers.push(maxId);
//                    }
//                }
//
//                status[maxId] = 0;
//            }
//        }
//
//        while (!speakers.isEmpty()) {
//            if (freeClient == 0)
//                return true;
//            int speaker = speakers.poll();
//            freeClient -= 1;
//            freeClient += remaining[speaker];
//        }
//
//        while (!audiences.isEmpty()) {
//            if (freeClient == 0)
//                return true;
//            int audience = audiences.poll();
//            freeClient -= 1;
//            freeClient += remaining[audience];
//        }
//
//        return false;
//    }

    static boolean isDeadlock() {
        int listening = 0, speaking = 0;
        for (int i = 0; i < numClients; ++i) {
            if (status[i] < 0) {
                listening += 1;
            } else if (status[i] > 0) {
                speaking += 1;
            }
        }
        return listening == numClients - terminated || speaking == numClients - terminated;
    }

    /**
     * Used for testing.
     * The bit decides whether a client speaks or listens.
     * There must be at least one client speaking or listening.
     */
    static int nextState(int id) {
        double r = random.nextDouble();
        int initBit = (int) (r * leftBits / (leftOnes + 1e-5));
        if (initBit == 0) {
            int x = (int) (random.nextDouble() * 1e9) + 1;
            status[id] = x;
            return x;
        } else {
            status[id] = -1;
            return -1;
        }
//        for (int i = 0; i < 2; ++i) {
//            int currentBit = initBit ^ i;
//            if (currentBit == 0) {
//                int x = (int) (Math.random() * 1e9) + 1;
//                status[id] = x;
//                if (!willDeadlock(status, remaining)) {
//                    return x;
//                }
//            } else {
//                status[id] = -1;
//                if (!willDeadlock(status, remaining)) {
//                    return -1;
//                }
//            }
//        }
//        Lib.debug('x', String.format("id: %d", id));
//        status[id] = 0;
//        for (int i = 0; i < numClients; ++i) {
//            Lib.debug('x', String.format("s/r: %d %d", status[i], remaining[i]));
//        }
//        Lib.assertTrue(false);
//        return -1;
    }

    private static class PingPong implements Runnable {
        Communicator communicator;
        int id;

        public PingPong(Communicator communicator1, int id1) {
            communicator = communicator1;
            id = id1;
        }

        void report(int method, int answer) {
            StringBuffer stringBuffer = new StringBuffer();
            for (int i = 0; i < numClients; ++i) {
                stringBuffer.append(String.format("%9d ", status[i]));
            }
            String stat = stringBuffer.toString();

            if (method == SPEAK) {
                Lib.debug('x',  String.format("@%d Speaking %09d        [%s]", id, answer, stat));
            } else {
                int found = -1;
                for (int i = 0; i < numClients; ++i) {
                    if (status[i] == answer) {
                        found = i;
//                        status[i] = 0;
                        break;
                    }
                }
                Lib.debug('x', String.format("@%d Listened %09d from %d [%s]", id, answer, found, stat));
//                Lib.assertTrue(found >= 0);
            }
        }

        public void run() {
            for (int i = 0; i < numClientLoop; ++i) { // randomly speaking or listening
                remaining[id] -= 1;
                int decide = nextState(id);
                if (isDeadlock()) {
                    Lib.debug('x', "Deadlocking");
                    lock.acquire();
                    terminate.wake();
                    lock.release();
                    return;
                }
                if (decide > 0) {
                    status[id] = decide;
                    report(SPEAK, decide);
                    communicator.speak(decide);
                    status[id] = 0;
                } else {
//                    Lib.debug('x', String.format("> @%d: listen", id));
                    status[id] = -1;
                    int y = communicator.listen();
                    report(LISTEN, y);
                    status[id] = 0;
                }
            }
            terminated += 1;
        }
    }

    public static void selfTest() {

        long seed = random.nextLong();
//         long seed = -4047517733952003518l;
        random.setSeed(seed);
        Lib.debug('x', String.format("seed = %d", seed));
        status = new int[numClients];
        remaining = new int[numClients];
        for (int i = 0; i < numClients; ++i)
            remaining[i] = numClientLoop;

        Lib.debug('t', "Self test for Communicator");

        Communicator communicator = new Communicator();
        for (int i = 0; i < numClients; ++i) {
            new KThread(new PingPong(communicator, i)).setName("child" + Integer.toString(i)).fork();
        }

        lock.acquire();
        terminate.sleep();
        lock.release();
    }

}