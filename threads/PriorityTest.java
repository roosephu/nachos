package nachos.threads;

import nachos.machine.Lib;
import nachos.machine.Machine;

import javax.security.auth.kerberos.KeyTab;
import java.util.Random;
import java.io.*;
import java.util.Scanner;

/**
 * Created by pty on 16-3-28.
 * Test for priorityScheduler
 */
public class PriorityTest {


    static Random random = ThreadedKernel.random;
    static int numClients = 6;
    static int step = 0;
    static int maxStep = 10000;
    static int maxLock = 10000;
    static int totalStep = 0;
    static Lock[] lock = new Lock[maxLock];
    static String[] instruction = new String[maxStep];
    static int[] pid = new int[maxStep];
    static int[] waitTime = new int[maxStep];
    static int[] initWaitTime = new int[maxStep];
    static int[] current = new int[maxStep];
    static int timeSlice = 2000;

    private static class CatchResource implements Runnable {
        int id;

        public CatchResource(int id1) {
            id = id1;
        }

        public void run() {

            ThreadedKernel.alarm.waitUntil(initWaitTime[id]);
            while (step <= totalStep) {
                //System.err.println(id + " " + step);
                if (pid[step] != id)
                {
                    KThread.yield();
                    continue;
                }
                System.err.println(id + " " + instruction[step].split(" ")[1]);
                int tmpStep = step;
                step++;
                if (instruction[step-1].split(" ")[1].equals("set")) {
                    int priority = Integer.parseInt(instruction[step-1].split(" ")[2]);
                    boolean intStatus = Machine.interrupt().disable();
                    ThreadedKernel.scheduler.setPriority(KThread.currentThread(), priority);
                    Machine.interrupt().restore(intStatus);
                }
                else if (instruction[step-1].split(" ")[1].equals("release"))
                {
                    int numLock = Integer.parseInt(instruction[step-1].split(" ")[2]);
                    Lib.assertTrue(lock[numLock].isHeldByCurrentThread());
                    lock[numLock].release();
                }
                else if (instruction[step-1].split(" ")[1].equals("acquire"))
                {
                    int numLock = Integer.parseInt(instruction[step-1].split(" ")[2]);
                    lock[numLock].acquire();
                    System.err.println(id + " catch lock " + numLock);
                }
                else if (instruction[step-1].split(" ")[1].equals("ask")) {
                    boolean intStatus = Machine.interrupt().disable();
                    int effectivePriority = ThreadedKernel.scheduler.getEffectivePriority(KThread.currentThread());
                    System.err.println("ask result: " + effectivePriority);
                    Machine.interrupt().restore(intStatus);
                }
                ThreadedKernel.alarm.waitUntil(waitTime[tmpStep] - step * timeSlice);
            }
        }
    }
    public static void selfTest() {

        Lib.debug('t', "Self test for PriorityScheduler");

            String input = "0 set 0\n" +
                    "1 set 1\n" +
                    "4 set 4\n" +
                    "0 acquire 0\n"+
                    "1 acquire 0\n" +
                    "4 acquire 0\n" +
                    "0 release 0\n" +
                    "4 release 0\n" +
                    "0 acquire 1\n" +
                    "4 acquire 1\n" +
                    "0 acquire 0\n" +
                    "1 ask\n" +
                    "1 release 0\n" +
                    "0 ask\n" +
                    "0 release 1\n"
                    + "1 ask\n";
            instruction = input.split("\n");
            totalStep = instruction.length - 1;
            for (int i = 0; i < numClients; i++) {
                current[i] = totalStep + 2;
                lock[i] = new Lock();
            }
            for (int i = totalStep; i >= 0; i--)
            {
                pid[i] = Integer.parseInt(instruction[i].split(" ")[0]);
                waitTime[i] = current[pid[i]] * timeSlice;
                current[pid[i]] = i;
            }
            for (int i = 0; i < numClients; i++)
                initWaitTime[i] = current[i] * timeSlice;

            KThread[] kThreads = new KThread[numClients];
            for (int i = 0; i < numClients; ++i) {
                kThreads[i] = new KThread(new CatchResource(i));
                kThreads[i].setName("process" + Integer.toString(i));

                kThreads[i].fork();
            }

            for (int i = 0; i < numClients; ++i)
                kThreads[i].join();
    }
    public static void selfTest1() {
        final Lock mutex = new Lock();
        Random rnd = new Random();

        boolean intStatus = Machine.interrupt().disable();
        for (int i = 0; i < 7; i++) {
            final int priority = rnd.nextInt(7);
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    mutex.acquire();
                    Lib.debug('m', "Priority: " + priority);
                    mutex.release();
                }
            };
            KThread t = new KThread(r);
            ThreadedKernel.scheduler.setPriority(t, priority);
            t.setName("Thread " + i);
            t.fork();
        }
        Machine.interrupt().restore(intStatus);

        ThreadedKernel.alarm.waitUntil(10000);
    }

    public static void selfTest2() {
        final Lock mutex = new Lock();

        Runnable r = new Runnable() {
            @Override
            public void run() {
                // TODO Auto-generated method stub
                mutex.acquire();
            }
        };
        ThreadedKernel.alarm.waitUntil(10000);

        boolean intStatus = Machine.interrupt().disable();
        for (int i = 0; i < 7; i++) {
            final int id = i;
            r = new Runnable() {
                @Override
                public void run() {
                    // TODO Auto-generated method stub
                    mutex.acquire();
                    Lib.debug('m', "Priority: " + id);
                    mutex.release();
                }
            };
            KThread t = new KThread(r);
            ThreadedKernel.scheduler.setPriority(t, i);
            t.setName("Thread " + i);
            t.fork();
        }
        Machine.interrupt().restore(intStatus);

        ThreadedKernel.alarm.waitUntil(10000);
    }

    // explicit test of priority inversion
    public static void selfTest3() {
        final Lock mutex = new Lock();
        boolean intStatus = Machine.interrupt().disable();
        KThread t = new KThread(new Runnable() {
            @Override
            public void run() {
                mutex.acquire();
                int s = 0;


                boolean intStatus = Machine.interrupt().disable();
                ThreadedKernel.scheduler.setPriority(KThread.currentThread(), 0);
                Machine.interrupt().restore(intStatus);
                 ThreadedKernel.alarm.waitUntil(1000);

                for (int i = 0; i < 10; i++) {
                   // ThreadedKernel.alarm.waitUntil(1000);
                    intStatus = Machine.interrupt().disable();
                    int effectivePriority = ThreadedKernel.scheduler.getEffectivePriority(KThread.currentThread());
                    Lib.debug('x', "ask result: " + effectivePriority);
                    Machine.interrupt().restore(intStatus);

                    KThread.yield();
                    Lib.debug('m', "Low is happy " + i);
                }
                mutex.release();
            }
        });
        ThreadedKernel.scheduler.setPriority(t, 0);
        KThread t2 = new KThread(new Runnable() {
            @Override
            public void run() {
                t.join();
                ThreadedKernel.alarm.waitUntil(500);
                mutex.acquire();
                int s = 0;
                for (int i = 0; i < 10; i++) {
                    ThreadedKernel.alarm.waitUntil(1000);
                    Lib.debug('m', "High is happy " + i);
                }
                mutex.release();
            }
        });
        ThreadedKernel.scheduler.setPriority(t2, 2);
        KThread t3 = new KThread(new Runnable() {
            @Override
            public void run() {

                 ThreadedKernel.alarm.waitUntil(1000);

                for (int i = 0; i < 1e9; i++);

                int s = 0;
                for (int i = 0; i < 10; i++) {
                    KThread.yield();
                    Lib.debug('m', "Middle is always happy " + i);
                }
            }
        });
        ThreadedKernel.scheduler.setPriority(t3, 1);
        t.fork();
        t2.fork();
        t3.fork();
        Machine.interrupt().restore(intStatus);
        ThreadedKernel.alarm.waitUntil(1000000);
    }
}