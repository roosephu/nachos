package nachos.threads;

import nachos.machine.*;

import java.lang.reflect.Array;
import java.util.*;

/**
 * A scheduler that chooses threads based on their priorities.
 * <p>
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 * <p>
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 * <p>
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
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

            threadState.updateTime();
            priorityQueue.add(threadState);

            // must run after priorityQueue.add
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
            Lib.debug('p', "Did you called me?");
            Lib.assertTrue(Machine.interrupt().disabled());

            // The owned thread has finished everything it wants, so he is releasing resources.
            if (ownedThread != null) {
                ownedThread.removeWaitingQueue(this);
            }

            if (priorityQueue.isEmpty()) {
                ownedThread = null;
                return null;
            }
            ThreadState threadState = priorityQueue.poll();

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
            if (priorityQueue.isEmpty())
                return null;
            return priorityQueue.peek();
        }

        public void print() {
            Lib.assertTrue(Machine.interrupt().disabled());
            // implement me (if you want)
        }

        class Comp implements Comparator<ThreadState> {
            public int compare(ThreadState a, ThreadState b) {
                int diff;
                // None business of transferPriority
//                if (transferPriority) {
                diff = a.getEffectivePriority() - b.getEffectivePriority();
//                } else {
//                    diff = a.getPriority() - b.getPriority();
//                }
                if (diff != 0)
                    return -diff;

                if (a.startTime > b.startTime)
                    return 1;
                if (a.startTime < b.startTime)
                    return -1;
                return 0;
            }
        }

        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        public boolean transferPriority;
        public ThreadState ownedThread;

        public java.util.PriorityQueue<ThreadState> priorityQueue = new java.util.PriorityQueue<>(new Comp());
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

            updateTime();
            setPriority(priorityDefault);
            updateEffectivePriority();
        }

        public void updateTime() {
            startTime = Machine.timer().getTime();
        }

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
            return effectivePriority;
        }

        /**
         * Update effective priority.
         * Use `ownedThread` chain to update all.
         */
        public void updateEffectivePriority() {
            boolean intStatus = Machine.interrupt().disable();

            /**
             * The effective priority may change, so first we need to delete it from the
             * priority queue and after computing its new effective priority, we add it
             * back to the priority queue again.
             */
            if (waitingFor != null)
                Lib.assertTrue(waitingFor.priorityQueue.remove(this));

            // Computing new effective priority
            effectivePriority = priority;
            for (PriorityQueue queue : resourceList) {
                if (queue.transferPriority) {
                    Lib.assertTrue(queue.ownedThread == this);
                    for (ThreadState threadState : queue.priorityQueue) {
                        effectivePriority = Math.max(effectivePriority, threadState.getEffectivePriority());
                    }
                }
            }
//            isValid = true;

            if (waitingFor != null) {
                waitingFor.priorityQueue.add(this);
                // waitingFor.ownedThread may be null (ready queue)
                if (waitingFor.ownedThread != null && waitingFor.transferPriority) {
                    Lib.assertTrue(waitingFor.ownedThread != this);

                    // Note that current thread is donating priority to another priority.
                    waitingFor.ownedThread.updateEffectivePriority();
                }
            }
            Machine.interrupt().restore(intStatus);
        }

        public void removeWaitingQueue(PriorityQueue waitQueue) {
            Lib.assertTrue(resourceList.remove(waitQueue));
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

            // may happen, e.g., ready queue
//             Lib.assertTrue(waitQueue.ownedThread != this);

            // I'm waiting for some resource, thus I must donate my priority to the owner.
            waitingFor = waitQueue;
            if (waitingFor != null && waitingFor.ownedThread != null) // and then update its effective priority
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
            waitingFor = null;
            resourceList.add(waitQueue); // I will own the queue soon
//            waitQueue.ownedThread = this;

            updateEffectivePriority();
//            isValid = false;
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

        protected LinkedList<PriorityQueue> resourceList = new LinkedList<>();
    }
}

class InstructionsGenerator {

    public class Operation {
        int pid;
        int pc;
        int currentPid;
        Runnable runnable;
        boolean report = false;
        String self;

        Operation(int pid) {
            this.pid = pid;
            this.pc = programCounter;
            programCounter += 1;
        }

        Operation acquire(int lock) {
            runnable = new Runnable() {
                @Override
                public void run() {
                    acquireTime[currentPid] = Machine.timer().getTime();
                    locks[lock].acquire();
//                    Lib.debug('z', String.format("process %d got lock %d", currentPid, lock));
                    threadReport(currentPid);
                }
            };
            self = String.format("process %d acquires lock %d", pid, lock);
            return this;
        }

        Operation release(int lock) {
            runnable = new Runnable() {
                @Override
                public void run() {
                    locks[lock].release();
                }
            };
            self = String.format("process %d releases lock %d", pid, lock);
            return this;
        }

        Operation setReport() {
            this.report = true;
            return this;
        }

        Operation setPriority(int id, int priority) {
            runnable = new Runnable() {
                @Override
                public void run() {
                    boolean intStatus = Machine.interrupt().disable();
                    ThreadedKernel.scheduler.setPriority(priority);
                    Machine.interrupt().restore(intStatus);
                }
            };
            self = String.format("process %d is set priority to %d", pid, priority);
            return this;
        }

        public boolean run(boolean shouldReport, int currentPid) {
            if (shouldReport && currentPid != 0) {
                threadReport(currentPid);
            }
            if (currentPid == pid || (pid < 0 && currentPid != 0)) {
                this.currentPid = currentPid;
                Lib.debug('z', String.format("[%d] PC %d on proc %d: %s", Machine.timer().getTime(), this.pc, currentPid, self));
                runnable.run();
            }
            return report;
        }

        @Override
        public String toString() {
            return self;
        }
    }

    static final int numClients = 100;
    static final int numLocks = 50;
    static final long oneSecond = 1000000;

    Random random = ThreadedKernel.random;
    Lock[] locks = new Lock[numLocks + 1];
    LinkedList<Operation> operations = new LinkedList<>();
    long baseTime;
    int programCounter = 0;
    int[] resourceWait = new int[numClients + 1];
    int[] lockHolder = new int[numLocks + 1];
    int[] priorities = new int[numClients + 1];
    long[] acquireTime = new long[numClients + 1];
    int[] effective = new int[numClients + 1];
    LinkedList<Integer> answers = new LinkedList<>();

    void threadReport(int pid) {
        Lib.assertTrue(!answers.isEmpty());

        int truth = answers.pollFirst();
        Lib.debug('z', String.format("Expect %d, got %d\n", truth, pid));
        Lib.assertTrue(truth == pid);
    }

    public InstructionsGenerator() {
        baseTime = Machine.timer().getTime();
        for (int i = 0; i <= numLocks; ++i) {
            locks[i] = new Lock();
            lockHolder[i] = -1;
        }
        for (int i = 1; i <= numClients; ++i) {
            priorities[i] = 1;
            resourceWait[i] = -1;
        }
    }

    boolean willDeadlock(int thread, int lock) {
        for (; lock != -1 && lockHolder[lock] != thread && lockHolder[lock] != -1; ) {
            lock = resourceWait[lockHolder[lock]];
//            Lib.assertTrue(lock != -1);
        }
        return lock == -1 || lockHolder[lock] == thread;
    }

    ArrayList<Integer> getWaitThreads(int lock) {
        ArrayList<Integer> free = new ArrayList<>();
        for (int i = 1; i <= numClients; ++i) {
            if (resourceWait[i] == lock) {
                free.add(i);
            }
        }
        return free;
    }

    class Comp implements Comparator<Integer> {
        public int compare(Integer a, Integer b) {
            if (priorities[a] != priorities[b])
                return priorities[b] - priorities[a];
            return (int) (acquireTime[a] - acquireTime[b]);
        }
    }

    void updateEffectivePriority() {
        for (int i = 1; i <= numClients; ++i)
            effective[i] = priorities[i];
        for (int i = 1; i <= numClients; ++i) {
            int u = i;

            while (resourceWait[u] != -1) {
                int v = lockHolder[resourceWait[u]];
                Lib.assertTrue(v != -1);
                effective[v] = Math.max(effective[v], effective[u]);
                u = v;
            }
        }
    }

    void generateOperation() {
        ArrayList<Integer> free = getWaitThreads(-1);
        while (true) {
            int b = random.nextInt(4);
            if (b == 0) { // link
                if (free.isEmpty())
                    continue;

                int u = free.get(random.nextInt(free.size()));
                Lib.assertTrue(resourceWait[u] == -1);
                for (int i = 1; i <= 100; ++i) {
                    int lock = random.nextInt(numLocks) + 1;
                    if (!willDeadlock(u, lock)) {
                        operations.add(new Operation(u).acquire(lock));
                        if (lockHolder[lock] == -1) {
                            lockHolder[lock] = u;
                            answers.addLast(u);
                        } else {
                            resourceWait[u] = lock;
                        }
                        return;
                    }
                }
            } else if (b == 100) { // run
                operations.add(new Operation(0).acquire(0));
                operations.add(new Operation(-1).acquire(0));
                operations.add(new Operation(0).release(0).setReport());
                operations.add(new Operation(-1).release(0));

                updateEffectivePriority();
                free.sort(new Comp());
                for (int v : free)
                    answers.addLast(v);
                break;
            } else if (b == 2) { // release
                int u = free.get(random.nextInt(free.size()));
                if (resourceWait[u] != -1)
                    continue;

                ArrayList<Integer> holdingLocks = new ArrayList<>();
                for (int i = 1; i <= numLocks; ++i)
                    if (lockHolder[i] == u)
                        holdingLocks.add(i);
                if (holdingLocks.isEmpty())
                    continue;

                int lock = holdingLocks.get(random.nextInt(holdingLocks.size()));

                operations.add(new Operation(u).release(lock));
                Lib.assertTrue(lockHolder[lock] == u);

                updateEffectivePriority();

                free = getWaitThreads(lock);
                if (free.isEmpty()) {
                    lockHolder[lock] = -1;
                } else {
                    free.sort(new Comp());
                    int newHolder = free.get(0);
                    lockHolder[lock] = newHolder;
                    resourceWait[newHolder] = -1;
                    answers.addLast(newHolder);
                }
                break;
            } else if (b == 3) { // set priority
                if (random.nextInt(10) != 0)
                    continue;
                int u = random.nextInt(numClients) + 1;
                int p = random.nextInt(8);
                operations.add(new Operation(0).setPriority(u, p));
                priorities[u] = p;
                break;
            }
        }
    }

    public Operation nextOperation() {
        long currentTime = Machine.timer().getTime();

        if (currentTime / oneSecond != baseTime / oneSecond) {
            baseTime = currentTime;
            operations.removeFirst();
        }
        if (operations.isEmpty())
            generateOperation();
        return operations.getFirst();
    }
}

class UniversalSchedulerTest {
    static InstructionsGenerator generator;

    static long toNextSecond() {
        long cur = Machine.timer().getTime();
        return (cur / generator.oneSecond + 1) * generator.oneSecond - cur;
    }

    static class Client implements Runnable {
        int pid;
        boolean shouldReport = false;

        Client(int pid) {
            this.pid = pid;
        }

        public void run() {
            while (true) {
                InstructionsGenerator.Operation op = generator.nextOperation();
                shouldReport = op.run(shouldReport, pid);
                ThreadedKernel.alarm.waitUntil(toNextSecond());
            }
        }
    }

    static void selfTest() {
        generator = new InstructionsGenerator();
//        Lib.debug('x', "before one second");
//        ThreadedKernel.alarm.waitUntil(generator.oneSecond);
//        Lib.debug('x', "after one second");

        KThread[] threads = new KThread[generator.numClients + 1];
        for (int i = 0; i <= generator.numClients; ++i) {
            threads[i] = new KThread(new Client(i)).setName("P" + i);
        }
        for (int i = 0; i <= generator.numClients; ++i) {
            threads[i].fork();
        }
        for (int i = 0; i <= generator.numClients; ++i) {
            threads[i].join();
        }
        Lib.debug('z', "finished");
    }
}
