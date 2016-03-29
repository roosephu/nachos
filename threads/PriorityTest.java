package nachos.threads;

import nachos.machine.Lib;
import nachos.machine.Machine;

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
                System.err.println(id + " " + step);
                if (pid[step] != id) continue;
                System.err.println(instruction[step].split(" ")[1]);
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
                ThreadedKernel.alarm.waitUntil(waitTime[step-1]);
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
                    "0 release 0\n"+
                    "4 release 0\n";
            instruction = input.split("\n");
            totalStep = instruction.length - 1;
            for (int i = 0; i < numClients; i++) {
                current[i] = totalStep + 1;
                lock[i] = new Lock();
            }
            for (int i = totalStep; i >= 0; i--)
            {
                pid[i] = Integer.parseInt(instruction[i].split(" ")[0]);
                waitTime[i] = (current[pid[i]] - i) * timeSlice;
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
}
