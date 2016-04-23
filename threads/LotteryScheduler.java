package nachos.threads;

import nachos.machine.*;

import java.util.*;

/**
 * A scheduler that chooses threads using a lottery.
 * <p>
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 * <p>
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 * <p>
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */

public class LotteryScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public LotteryScheduler() {
    }

    /**
     * Allocate a new priority thread queue.
     *
     * @param transferPriority <tt>true</tt> if this queue should
     *                         transfer priority from waiting threads
     *                         to the owning thread.
     * @return a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
        Lib.assertTrue(Machine.interrupt().disabled());

        Lib.assertTrue(priority >= priorityMinimum &&
                priority <= priorityMaximum);

//        Lib.debug('x', thread.getName() + " old: " + ThreadedKernel.scheduler.getPriority(thread) + " new: " + priority);
        getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMaximum)
            return false;

        setPriority(thread, priority + 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    public boolean decreasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMinimum)
            return false;

        setPriority(thread, priority - 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param thread the thread whose scheduling state to return.
     * @return the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
        if (thread.schedulingState == null)
            thread.schedulingState = new ThreadState(thread);

        return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
        PriorityQueue(boolean transferPriority) {
            this.transferPriority = transferPriority;
        }

        public void waitForAccess(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            ThreadState threadState = getThreadState(thread);

//            threadState.updateTime();
            totalTicks += 1;
            threadState.startTime = totalTicks;
            Lib.assertTrue(!queue.remove(threadState));
            queue.add(threadState);

            // must run after queue.add
            // otherwise updateEffectivePriority can add it twice (first deletion fails)
            threadState.waitForAccess(this);
        }

        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            ThreadState threadState = getThreadState(thread);
            ownedThread = threadState;
            threadState.acquire(this);
        }

        public KThread nextThread() {
//            Lib.debug('p', "Did you call me?");
            Lib.assertTrue(Machine.interrupt().disabled());

            // The owned thread has finished everything it wants, so he is releasing resources.
            if (ownedThread != null) {
                ownedThread.removeWaitingQueue(this);
                ownedThread = null;
            }

            if (queue.isEmpty()) {
                return null;
            }
//            Lib.assertTrue(false);
            ThreadState threadState = pickNextThread();
            queue.remove(threadState);
            Lib.assertTrue(threadState.waitingFor == this);
            threadState.waitingFor = null;

            ownedThread = threadState;
            threadState.acquire(this);
            Lib.assertTrue(ownedThread.waitingFor != this);

            return threadState.thread;
        }

        /**
         * Return the next thread that <tt>nextThread()</tt> would return,
         * without modifying the state of this queue.
         *
         * @return the next thread that <tt>nextThread()</tt> would
         * return.
         */
        protected ThreadState pickNextThread() {
            // implement me
            if (queue.isEmpty())
                return null;

            int totalTickets = 0;
            for (ThreadState threadState : queue) {
                totalTickets += threadState.getEffectivePriority();
            }
            int luckyTicket = Lib.random(totalTickets);

            ThreadState luckyThreadState = null;
            for (ThreadState threadState : queue) {

                if (luckyTicket < threadState.getEffectivePriority()) {
                    luckyThreadState = threadState;
                    break;
                }
                luckyTicket -= threadState.getEffectivePriority();
            }

            return luckyThreadState;
        }

        public void print() {
            Lib.assertTrue(Machine.interrupt().disabled());
            // implement me (if you want)
        }

        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        public boolean transferPriority;
        public ThreadState ownedThread = null;

        int totalTicks = 0;
        public LinkedList<ThreadState> queue = new LinkedList<>();

    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
        /**
         * Allocate a new <tt>ThreadState</tt> object and associate it with the
         * specified thread.
         *
         * @param thread the thread this state belongs to.
         */
        public ThreadState(KThread thread) {
            this.thread = thread;

//            updateTime();
            setPriority(priorityDefault);
            updateEffectivePriority();
        }

//        public void updateTime() {
//            startTime = Machine.timer().getTime();
//        }

        /**
         * Return the priority of the associated thread.
         *
         * @return the priority of the associated thread.
         */
        public int getPriority() {
            return priority;
        }

        /**
         * Return the effective priority of the associated thread.
         *
         * @return the effective priority of the associated thread.
         */
        public int getEffectivePriority() {
//            effectivePriority = priority;
//            for (PriorityQueue queue : resourceList) {
//                if (queue.transferPriority) {
//                    Lib.assertTrue(queue.ownedThread == this);
//                    for (ThreadState threadState : queue.queue) {
//                        effectivePriority = Math.max(effectivePriority, threadState.getEffectivePriority());
//                    }
//                }
//            }

            return effectivePriority;
        }

        /**
         * Update effective priority.
         * Use `ownedThread` chain to update all.
         */
        int depth = 0;

        public void updateEffectivePriority() {
            depth += 1;
            Lib.assertTrue(depth <= 1000);

            if (!isUpdating) {
                isUpdating = true;
                boolean intStatus = Machine.interrupt().disable();

                int oldSize = 0;
                if (waitingFor != null)
                    oldSize = waitingFor.queue.size();

                /**
                 * The effective priority may change, so first we need to delete it from the
                 * priority queue and after computing its new effective priority, we add it
                 * back to the priority queue again.
                 */
                if (waitingFor != null)
                    Lib.assertTrue(waitingFor.queue.remove(this));

                // Computing new effective priority
                int oldEffectivePriority = effectivePriority;
                effectivePriority = priority;
                for (PriorityQueue queue : resourceList) {
                    if (queue.transferPriority) {
                        Lib.assertTrue(queue.ownedThread == this);
                        for (ThreadState threadState : queue.queue) {
                            effectivePriority += threadState.getEffectivePriority();
                        }
                    }
                }
//            isValid = true;

                int newSize = 0;
                if (waitingFor != null) {
                    waitingFor.queue.add(this);
                    newSize = waitingFor.queue.size();

                    // waitingFor.ownedThread may be null (ready queue)
                    if (waitingFor.ownedThread != null && waitingFor.transferPriority &&
                            oldEffectivePriority != effectivePriority) {
                        Lib.assertTrue(waitingFor.ownedThread != this);

                        // Note that current thread is donating priority to another priority.
                        waitingFor.ownedThread.updateEffectivePriority();
                    }
                }
                Lib.assertTrue(newSize == oldSize);
                Machine.interrupt().restore(intStatus);
                isUpdating = false;
            }
            depth -= 1;
        }

        public void removeWaitingQueue(PriorityQueue waitQueue) {
            Lib.assertTrue(resourceList.remove(waitQueue));
            updateEffectivePriority();
        }

        /**
         * Set the priority of the associated thread to the specified value.
         *
         * @param priority the new priority.
         */
        public void setPriority(int priority) {
            if (this.priority == priority)
                return;

            this.priority = priority;
            updateEffectivePriority();
        }

        /**
         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
         * the associated thread) is invoked on the specified priority queue.
         * The associated thread is therefore waiting for access to the
         * resource guarded by <tt>waitQueue</tt>. This method is only called
         * if the associated thread cannot immediately obtain access.
         *
         * @param waitQueue the queue that the associated thread is
         *                  now waiting on.
         * @see nachos.threads.ThreadQueue#waitForAccess
         */

        public void waitForAccess(PriorityQueue waitQueue) {
//            resourceList.add(waitQueue);
            Lib.debug('P', "process " + thread.getName() + " waiting for access " + waitQueue.toString());
            Lib.assertTrue(waitingFor == null);

            // may happen, e.g., ready queue when yielding
//            Lib.assertTrue(waitQueue.ownedThread != this);
            if (waitQueue.ownedThread == this && waitQueue.transferPriority) {
//                Lib.debug('x', "wait queue " + waitQueue);
                Lib.assertTrue(false);
            }

            // I'm waiting for some resource, thus I must donate my priority to the owner.
            waitingFor = waitQueue;
            if (waitingFor.ownedThread != null && waitingFor.transferPriority) // and then update its effective priority
                waitingFor.ownedThread.updateEffectivePriority();
//            setInvalid();
        }

        /**
         * Called when one of threads which donate to it changes its priority.
         * We know which thread it's waiting for (waitingFor.ownedThread).
         */
//        public void setInvalid() {
//            isValid = false;
//
//            if (waitingFor.transferPriority == true && waitingFor.ownedThread != null) {
//                waitingFor.ownedThread.setInvalid();
//            }
//        }

        /**
         * Called when the associated thread has acquired access to whatever is
         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
         * <tt>thread</tt> is the associated thread), or as a result of
         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
         *
         * @see nachos.threads.ThreadQueue#acquire
         * @see nachos.threads.ThreadQueue#nextThread
         */
        public void acquire(PriorityQueue waitQueue) {
            Lib.debug('P', "process " + thread.getName() + " acquiring " + waitQueue.toString());

            /**
             * The following assertion may not hold since:
             * when you want some resources and no one is holding it,
             * you can get it directly and won't be called waitForAccess
             */
//            Lib.assertTrue(waitingFor == waitQueue);

            /**
             * I've gotten the resource, therefore I'm waiting for nothing (at least
             * for now). And all the threads waiting for this resource must donate
             * their priority to me.
             */
            Lib.assertTrue(!resourceList.remove(waitQueue));
            resourceList.add(waitQueue); // I will own the queue soon
//            waitQueue.ownedThread = this;

            updateEffectivePriority();
//            isValid = false;
        }

        @Override
        public String toString() {
            return String.format("Process %s [priority = %d, effective = %d, time = %d] ",
                    thread.getName(), priority, effectivePriority, startTime);
        }

        /**
         * The thread with which this object is associated.
         */
        protected KThread thread;
        /**
         * The priority of the associated thread.
         */
        protected int priority;
        protected int effectivePriority = priorityMinimum;
        protected boolean isValid; // do we need to update effective priority?
        protected PriorityQueue waitingFor = null;
        protected long startTime;
        protected boolean isUpdating;

        protected LinkedList<PriorityQueue> resourceList = new LinkedList<>();
    }
}
