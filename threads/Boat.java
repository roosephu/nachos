package nachos.threads;

import nachos.ag.BoatGrader;
import nachos.machine.Lib;

public class Boat {
    static BoatGrader bg;

    public static void selfTest() {
        BoatGrader b = new BoatGrader();

        int adults = 5, children = 4;

//        System.out.println("\n ***Testing Boats with only 2 children***");
//        begin(0, 5, b);

//	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
//  	begin(1, 2, b);

  	System.out.println("\n ***Testing Boats with " + Integer.toString(children) + " children, " + Integer.toString(adults) + " adults***");
  	begin(adults, children, b);
    }

    public static void begin(int adults, int children, BoatGrader b) {
        // Store the externally generated autograder in a class
        // variable to be accessible by children.
        bg = b;

        // Instantiate global variables here

        // Create threads here. See section 3.4 of the Nachos for Java
        // Walkthrough linked from the projects page.

        for (int i = 1; i <= adults; ++i) {
            KThread t = new KThread(new Runnable() {
                public void run() {
                    AdultItinerary();
                }
            });
            t.setName("Adult-" + Integer.toString(i)).fork();
        }

        for (int i = 1; i <= children; ++i) {
            KThread t = new KThread(new Runnable() {
                public void run() {
                    ChildItinerary();
                }
            });
            t.setName("Child-" + Integer.toString(i)).fork();
        }

        while (true) {
            if (communicator.listen() == adults + children) {
//                finishedTravel = true;
//                molokai.wake();
                break;
            }
        }

        System.out.printf("done\n");
    }

    static void AdultItinerary() {
        bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.
        //DO NOT PUT ANYTHING ABOVE THIS LINE.

	/* This is where you should put your solutions. Make calls
           to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
	*/

        int currentLocation = OAHU;

        lockInfoOahu.acquire();
        numAdultOahu += 1;
        oahu.wake();
        oahu.sleep();

//        lockBoat.acquire();
        while (true) {
            if (currentLocation == OAHU) { // Oahu
                if (numChildMolokai > 0 && boatLocation == OAHU && boatSeats == 2) {
                    numAdultOahu -= 1;
                    bg.AdultRideToMolokai(); // arrival
                    numAdultMolokai += 1;
                    lockInfoOahu.release();

                    boatLocation = MOLOKAI;
                    lockInfoMolokai.acquire();
                    molokai.wakeAll();
                    lockInfoMolokai.release();

                    break;
                } else {
                    oahu.wakeAll();
                    oahu.sleep();
                }
            } else { // Molokai
                break; // never comes back to Oahu
            }
        }


    }

    static void ChildItinerary() {
        bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
        //DO NOT PUT ANYTHING ABOVE THIS LINE.

        int currentLocation = OAHU;

        lockInfoOahu.acquire();
        numChildOahu += 1;
        oahu.wakeAll();
        oahu.sleep();

        while (true) {
            if (currentLocation == OAHU) {
                if (boatLocation == OAHU) {
                    if (boatSeats == 2 && numChildOahu > 1) {
                        boatSeats = 1;
                        bg.ChildRowToMolokai();
                        numChildOahu -= 1;
                        numChildMolokai += 1;
                        oahu.wakeAll();
                        lockInfoOahu.release();

                        currentLocation = MOLOKAI;
                        lockInfoMolokai.acquire();
                        molokai.sleep();
                        boatSeats = 2;
                        boatLocation = MOLOKAI;
                        molokai.wakeAll();
                    } else if (boatSeats == 1) {
                        boatSeats = 0;
                        bg.ChildRideToMolokai();
                        numChildMolokai += 1;
                        numChildOahu -= 1;
                        currentLocation = MOLOKAI;
                        lockInfoOahu.release();

                        lockInfoMolokai.acquire();
                        molokai.wakeAll();
                        molokai.sleep();
                    } else {
//                        oahu.wakeAll();
                        oahu.sleep();
                    }
                } else {
//                    oahu.wakeAll();
                    oahu.sleep();
                }
            } else {
                communicator.speak(numAdultMolokai + numChildMolokai);
//                molokai.sleep();
                if (finishedTravel) {
                    break;
                }

                Lib.debug('t', "boatLocation = " + Integer.toString(boatLocation));
                if (boatLocation == MOLOKAI) {
                    bg.ChildRideToOahu();
                    currentLocation = OAHU;
                    numChildOahu += 1;
                    numChildMolokai -= 1;
                    boatLocation = OAHU;
                    lockInfoMolokai.release();

                    lockInfoOahu.acquire();
                    oahu.wakeAll();
                    oahu.sleep();
                } else {
                    molokai.wake();
                    molokai.sleep();
                }
            }
        }
    }

    static void SampleItinerary() {
        // Please note that this isn't a valid solution (you can't fit
        // all of them on the boat). Please also note that you may not
        // have a single thread calculate a solution and then just play
        // it back at the autograder -- you will be caught.
        System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
        bg.AdultRowToMolokai();
        bg.ChildRideToMolokai();
        bg.AdultRideToMolokai();
        bg.ChildRideToMolokai();
    }

    public static final int OAHU = 1;
    public static final int MOLOKAI = 2;
    public static final int SEA = 3;

    static Lock lockInfoOahu = new Lock();
    static Lock lockInfoMolokai = new Lock();

    static int numChildOahu = 0;
    static int numAdultOahu = 0;
    static int numChildMolokai = 0;
    static int numAdultMolokai = 0;

    static boolean finishedTravel = false;

    static Condition oahu = new Condition(lockInfoOahu);
    static Condition molokai = new Condition(lockInfoMolokai);

    static int boatLocation = OAHU;
    static int boatSeats = 2;
    static Lock lockBoat = new Lock();
    static Condition boat = new Condition(lockBoat);

    static Communicator communicator = new Communicator();
}
