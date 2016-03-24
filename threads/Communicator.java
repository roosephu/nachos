package nachos.threads;

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
