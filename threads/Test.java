package nachos.threads;

import java.util.Vector;

import nachos.machine.Lib;
import nachos.machine.Machine;

public class Test {

    public static void selfTest() {
    	//selfTestCondition2();
        //selfTestJoin();
        //selfTestAlarm();
        selfTestPriority1();
        selfTestPriority2();
    }

    private static class PingTest implements Runnable {
        PingTest(int which) {
            this.which = which;
        }

        public void run() {
            for (int i = 0; i < 5; i++) {
                System.out.println("*** thread " + which + " looped "
                        + i + " times");
                KThread.currentThread().yield();
            }
        }

        private int which;
    }

    private static void selfTestJoin() {

        final KThread t1 = new KThread(new PingTest(1));
        final KThread t2 = new KThread(new PingTest(2));
        t1.fork();
        t2.fork();
        t1.join();
        t2.join();
        KThread t3 = new KThread(new Runnable() {
            @Override
            public void run() {
                t1.join();
                t2.join();
                System.out.println("In join test: t3 starts.");
                for (int i = 0; i < 5; i++) {
                	System.out.println("In join test: thread 3 looped "
                			+ i + " times");
                	KThread.currentThread().yield();
                }
            }
        });
        KThread t4 = new KThread(new Runnable() {
            @Override
            public void run() {
                t1.join();
                t2.join();
                System.out.println("In join test: t4 starts.");
                for (int i = 0; i < 5; i++) {
                	System.out.println("In join test: thread 4 looped "
                			+ i + " times");
                	KThread.currentThread().yield();
                }
            }
        });
        t3.fork();
        t4.fork();
        t3.join();
        t4.join();
        System.out.println("End join test.");
    }

    private static class AlarmTest implements Runnable {
        AlarmTest(int which) {
            this.which = which;
        }

        public void run() {
            for (int i = 0; i < 5; i++) {
                System.out.println("In alarm test : thread " + which + " looped "
                        + i + " times");
                ThreadedKernel.alarm.waitUntil(1000);
            }
        }

        private int which;
    }

    private static void selfTestAlarm() {
        KThread t1 = new KThread(new AlarmTest(1));
        t1.fork();
        KThread t2 = new KThread(new PingTest(2));
        KThread t3 = new KThread(new AlarmTest(3));
        t2.fork();
        t3.fork();
        t1.join();
        t2.join();
        t3.join();
        System.out.println("End alarm test.");
    }

    //test by milk buying

    static class Milk {
    	public int num = 0;
    }

    private static void selfTestCondition2() {
        final Lock lock = new Lock();
        final Condition2 condition = new Condition2(lock);
        final Milk milk = new Milk();
        Runnable buyer = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 30; i++) {
                    lock.acquire();
                    while (milk.num != 0) {
                        condition.sleep();
                    }
                    milk.num++;
                    condition.wakeAll();
                    System.out.println("In Condition2 test, buy "+i+"th milk");
                    lock.release();
                }
                System.out.println("In condition2 test, buyer ends.");
            }
        };
        KThread t1 = new KThread(buyer);
        Runnable drinker = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    lock.acquire();
                    while (milk.num == 0) {
                    	condition.wake();
                        condition.sleep();
                    }
                    milk.num--;      
                    System.out.println("In condition2 test, drink "+i+"th milk");
                    lock.release();
                }
                System.out.println("In condition2 test, drinker ends.");
            }       
        };
        KThread t2 = new KThread(drinker);
        KThread t3 = new KThread(drinker);
        KThread t4 = new KThread(drinker);
        t1.fork();
        t2.fork();
        t3.fork();
        t4.fork();
        t1.join();
        t2.join();
        t3.join();
        t4.join();
        System.out.println("End condition test.");
    }
    
    private static class PriorityTest implements Runnable {
    	PriorityTest(int priority) {
            this.priority = priority;
        }

        public void run() {
            for (int i = 0; i < 3; i++) {
                System.out.println("In priority test : thread with priority" + priority + " looped "
                        + i + " times");
                KThread.currentThread().yield();
            }
        }

        private int priority;
    }
    
    private static void selfTestPriority1() {
    	Vector<KThread> threads = new Vector();
    	for(int i = 1; i <= 7; i ++) {
    		threads.add(new KThread(new PriorityTest(i)));
    		boolean intstatus = Machine.interrupt().disable();
    		ThreadedKernel.scheduler.setPriority(threads.get(i-1), i);
    		Machine.interrupt().restore(intstatus);
    	}
    	for(int i = 0; i < 7; i ++) {
    		threads.get(i).fork();
    	}
    	for(int i = 0; i < 7; i ++) {
    		threads.get(i).join();
    	}
    }
    
    private static void selfTestPriority2() {
		boolean intstatus = Machine.interrupt().disable();
    	final Lock lock1 = new Lock();
    	final Lock lock2 = new Lock();
    	
    	Runnable r4 = new Runnable() {
            @Override
            public void run() {
            	System.out.println("In priority test2 : thread4 acquire lock1.");
            	lock1.acquire();
            	System.out.println("In priority test2 : thread4 got lock1.");
                for (int i = 0; i < 3; i++) {
                    System.out.println("In priority test2 : thread4" + " looped "
                            + i + " times");
                    //KThread.currentThread().yield();
                }
            	lock1.release();
            }       
        };
        final KThread t4 = new KThread(r4);
        ThreadedKernel.scheduler.setPriority(t4, 6);
        
       	Runnable r2 = new Runnable() {
            @Override
            public void run() {   	
                for (int i = 0; i < 3; i++) {
                	lock2.acquire();
                    System.out.println("In priority test2 : thread2" + " looped "
                            + i + " times");
                    lock2.release();
                   // KThread.currentThread().yield();
                }
            	
            }       
        };
        final KThread t2 = new KThread(r2);
        ThreadedKernel.scheduler.setPriority(t2, 7);
        
       	Runnable r3 = new Runnable() {
            @Override
            public void run() {   
            	lock2.acquire();
                for (int i = 0; i < 5; i++) {
                    System.out.println("In priority test2 : thread3" + " looped "
                            + i + " times");    
                    if(i==1)
                    	t4.fork();
                    KThread.currentThread().yield();
                    
                }
                lock2.release();
            }       
        };
        final KThread t3 = new KThread(r3);
        ThreadedKernel.scheduler.setPriority(t3, 1);    	
        
        Runnable r1 = new Runnable() {
            @Override
            public void run() {
            	lock1.acquire();
            	System.out.println("In priority test2 : thread1 got lock1.");
                for (int i = 0; i < 5; i++) {
                    System.out.println("In priority test2 : thread1" + " looped "
                            + i + " times");
                    KThread.currentThread().yield();
                }
            	lock1.release();
            }       
        };
        final KThread t1 = new KThread(r1);
        ThreadedKernel.scheduler.setPriority(t1, 1);
        
        Machine.interrupt().restore(intstatus);
        
        t1.fork();       
        t2.fork();
        t3.fork();         
        t1.join();
        t2.join();
        t3.join();
        t4.join();
    }
}