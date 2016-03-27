package nachos.threads;

import nachos.machine.Lib;

public class Test {
	
	public static void selfTest() {
		selfTestJoin();
		selfTestAlarm();
		selfTestCondition2();
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
	 
	/**
     * Tests whether join is working.
     */
    public static void selfTestJoin() {

        Runnable playload=new Runnable(){
			@Override
			public void run(){
				for (int i=0;i<10;i++){
					ThreadedKernel.alarm.waitUntil(1000);
	                System.out.println("*** thread " + " looped "
	                        + i + " times");
				}
				Lib.debug('m',"child dead");
			}
		};
		KThread t1=new KThread(new PingTest(1));
		t1.fork();
		t1.join();
		Lib.debug('m',"parent returned");
		KThread t2=new KThread(new PingTest(2));
		KThread t3=new KThread(new PingTest(3));
		t2.fork();
		t3.fork();
		t2.join();
		t3.join();
		Lib.debug('m',"parent returned");
		final KThread t4=new KThread(new PingTest(4));
		
		//what if one thread is joined by multiple threads?
		KThread t5=new KThread(new Runnable(){
			@Override
			public void run(){
				t4.join();
				Lib.debug('m',"another child returned");
			}
		});
		t4.fork();
		t5.fork();
		t4.join();
		t5.join();
		Lib.debug('m',"parent returned");

    }
    
    private static class AlarmTest implements Runnable {
        AlarmTest(int which) {
            this.which = which;
        }

        public void run() {
            for (int i = 0; i < 5; i++) {   	
                System.out.println("*** In alarm test : thread " + which + " looped "
                        + i + " times");
                ThreadedKernel.alarm.waitUntil(1000);
            }
        }

        private int which;
    }
    
    public static void selfTestAlarm() {
		KThread t1=new KThread(new AlarmTest(1));
		t1.fork();
		KThread t2=new KThread(new PingTest(2));
		KThread t3=new KThread(new AlarmTest(3));
		t2.fork();
		t3.fork();
		t1.join();
		t2.join();
		t3.join();
    }
    
	//test by milk buying
	
    static class Milk {
    	public volatile int num = 0;
    }
    
	public static void selfTestCondition2(){
		final Lock lock=new Lock();
		final Condition2 condition=new Condition2(lock);
		final Milk milk = new Milk();
		KThread t1=new KThread(new Runnable(){
			@Override
			public void run(){
				for (int i=0;i<10;i++){
					lock.acquire();
					Lib.debug('t',"In Condition2 test: try to buy milk");
					while (milk.num != 0){
						condition.sleep();
					}
					if(milk.num == 0) {
						milk.num ++;
						condition.wake();		
					}
					lock.release();
				}
			}
		});
		Runnable r_drinker=new Runnable(){
			@Override
			public void run(){
				for (int i=0;i<10;i++){
					lock.acquire();
					while (milk.num == 0){
						condition.sleep();
					}
					milk.num--;
					Lib.debug('t',"In Condition2 test: drink a milk");
					lock.release();
				}
			}
		};
		KThread t2=new KThread(r_drinker);
		t1.fork();
		t2.fork();
		t1.join();
		t2.join();
	}
}