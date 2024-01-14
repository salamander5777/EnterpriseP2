/*
    Name: Michael Gilday
    Course: CNT 4714 Fall 2023
    Assignment title: Project 2 – Multi-threaded programming in Java
    Date: October 8, 2023

    Class: Main
    Description: The primary initiator for this program. It reads in both theFleetFile.csv and theYardFile.csv files, and
                 assigns the read in values to some constructors (records) which are found in the Train class. Main also
                 initiates the ExecutorService with a defined thread pool of 30, and then executes a train to each
                 necessary thread (MAX 30). The ReentrantLocks are also initiated at this time. Each executed train
                 will be run together in the Train class.

    Class: Train
    Description: The backbone of this program. This class implements the functional interface Runnable which is necessary
                 for the run() function to operate as desired. This class also houses the records containing each Train,
                 the current train in use, and the overall start of the Yard (necessary for routing). This information
                 is used within run() to call functions such as findRoute (used to verify if a desired train route is
                 legitimate) and acquireSwitchLocks(), which is used to see if a train can lock in its route, so that it
                 can leave the yard. This method is rather brute force, with trains constantly vying for each available
                 lock that they need to leave the yard, with trains being forced to give up locks they have already claimed
                 if they come to a standstill in their attempt to move forward. Eventually, through this program, all
                 trains will leave the yard except for trains that contain an illegal route as they cannot move.
*/

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class Main {
    public static void main(String[] args) {
        //Creating the two list that will contain the information from the two csv files.
        List<TrainInfo> fleetFile = new ArrayList<>();
        List<YardConfig> yardFile = new ArrayList<>();

        //Attempting to read in the data from the "theFleetFile.csv" file.
        //Each line in this file contains: The train number, it's inbound track, and it's outbound track.
        try (BufferedReader reader = new BufferedReader(new FileReader("theFleetFile.csv"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");

                int trainNumber = Integer.parseInt(parts[0]);
                int inboundTrack = Integer.parseInt(parts[1]);
                int outboundTrack = Integer.parseInt(parts[2]);
                boolean completedPath = false;
                fleetFile.add(new TrainInfo(trainNumber, inboundTrack, outboundTrack, completedPath));
            }
        } catch (IOException e) {
            //No need to actually handle anything here.
        }

        //Attempting to read in the data from the "theYardFile.csv" file.
        //Each line in this file contains: The inbound track, first switch, second switch, third switch, and outbound track.
        try (BufferedReader reader = new BufferedReader(new FileReader("theYardFile.csv"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                int inboundTrack = Integer.parseInt(parts[0]);
                int firstSwitch = Integer.parseInt(parts[1]);
                int secondSwitch = Integer.parseInt(parts[2]);
                int thirdSwitch = Integer.parseInt(parts[3]);
                int outboundTrack = Integer.parseInt(parts[4]);
                yardFile.add(new YardConfig(inboundTrack, firstSwitch, secondSwitch, thirdSwitch, outboundTrack));
            }
        } catch (IOException e) {
            //No need to actually handle anything here.
        }

        System.out.println("$ $ $ TRAIN MOVEMENT SIMULATION BEGINS........... $ $ $\n");

        //Initiating the ExecutorService with a fixed thread pool of 30, as that's the MAX value of trains for this project.
        //The train Fleet array is also initiated here and will be used by the executor service later once it's filled.
        ExecutorService executorService = Executors.newFixedThreadPool(30);
        ReentrantLock[] switchLocks = initializeSwitchLocks();
        Train[] theFleet = new Train[30];

        //The 'for' loop below is used to pass each Train's information into the TrainInfo record found in 'Train'
        //As well as passing information to the Fleet and passing it into the executor service.
        for (int i = 0; i < fleetFile.size() && i < 30; i++) {
            TrainInfo trainInfo = fleetFile.get(i);
            theFleet[i] = new Train(trainInfo, yardFile, switchLocks);

            executorService.execute(theFleet[i]);
        }

        //Shutting down the executor service and waiting until every threat is terminating, before providing the final system out.
        executorService.shutdown();
        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            //Not actually going to handle anything here.
        }

        System.out.println("\n$ $ $ SIMULATION ENDS $ $ $");
    }

    //This function is used to initiate 11 reentrant locks for use by the trains, but Lock 0 will not actually be used.
    private static ReentrantLock[] initializeSwitchLocks() {
        ReentrantLock[] locks = new ReentrantLock[11];

        for (int i = 0; i < 11; i++) {
            locks[i] = new ReentrantLock();
        }

        return locks;
    }
}

class Train implements Runnable{
    //Creating important variables for reference throughout the Train class, as well as the 'Train' constructor.
    private TrainInfo trainInfo;
    private final List<YardConfig> yardConfig;
    ReentrantLock[] switchLocks;

    //Very important constructor containing information related to the trains, yard, and situation of the Locks.
    public Train(TrainInfo trainInfo, List<YardConfig> yardConfig, ReentrantLock[] switchLocks) {
        this.trainInfo = trainInfo;
        this.yardConfig = yardConfig;
        this.switchLocks = switchLocks;
    }

    @Override
    public void run() {
        //Gathering the current trains number, inbound track, and outbound track, then verifying if the path is found in yardConfig.
        int trainNumber = trainInfo.trainNumber();
        int inboundTrack = trainInfo.inboundTrack();
        int outboundTrack = trainInfo.outboundTrack();
        YardConfig route = findRoute(inboundTrack, outboundTrack);

        //If the route found in theFleetFile is not available in the yard then the train can be removed.
        if(route == null){
            //This will cause the on-hold trains to be noticed first.
            System.out.println("*************"
                    + "\nTrain " + trainNumber + " is on permanent hold and cannot be dispatched."
                    + "\n*************\n");
        }
        else {
            do{
                try {
                    if(!trainInfo.completedPath()){
                        if (acquireSwitchLocks(route)) {
                            //Signaling that the train is clear, allowing us to release the locks that it holds.
                            System.out.println("\nTrain " + trainNumber + ": Clear of yard control."
                                    + "\nTrain " + trainNumber + ": Releasing all switch locks."
                                    + "\nTrain " + trainNumber + ": Unlocks/releases lock on Switch " + route.firstSwitch() + "."
                                    + "\nTrain " + trainNumber + ": Unlocks/releases lock on Switch " + route.secondSwitch() + "."
                                    + "\nTrain " + trainNumber + ": Unlocks/releases lock on Switch " + route.thirdSwitch() + "."
                                    + "\nTrain " + trainNumber + ": Has been dispatched and moves on down the line out of yard control into CTC." + "\n@ @ @ TRAIN " + trainNumber + ": DISPATCHED @ @ @\n");

                            switchLocks[route.firstSwitch()].unlock();
                            switchLocks[route.secondSwitch()].unlock();
                            switchLocks[route.thirdSwitch()].unlock();
                            trainInfo = new TrainInfo(trainNumber, inboundTrack, outboundTrack, true);
                        }
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }while(!trainInfo.completedPath()); //The 'do-while' loop will loop until every valid train has left the yard.
        }
    }

    //The function below iterates through yardConfig to find out if a desired route is available.
    private YardConfig findRoute(int inboundTrack, int outboundTrack) {
        for (YardConfig config : yardConfig) {
            if (config.inboundTrack() == inboundTrack && config.outboundTrack() == outboundTrack) {
                return config; //Proper route found in theYardFile.csv
            }
        }

        return null; //No proper matching route.
    }

    //The function below is used in each train's attempt to acquire locks.
    //If a train fails to get a lock it is made to wait for some time before being able to attempt it again.
    //This is to ensure each train has a fair chance at attempting to get a lock. Once a train has all three locks it will start traveling to leave the yard.
    private boolean acquireSwitchLocks(YardConfig route) throws InterruptedException {
        int firstSwitch = route.firstSwitch();
        int secondSwitch = route.secondSwitch();
        int thirdSwitch = route.thirdSwitch();

        if (switchLocks[firstSwitch].tryLock(200, TimeUnit.MILLISECONDS)) {
            System.out.println("Train " + trainInfo.trainNumber() + " HOLDS LOCK on Switch " + firstSwitch + ".\n");

            if (switchLocks[secondSwitch].tryLock(100, TimeUnit.MILLISECONDS)) {
                System.out.println("Train " + trainInfo.trainNumber() + " HOLDS LOCK on Switch " + secondSwitch + ".\n");

                if (switchLocks[thirdSwitch].tryLock(50, TimeUnit.MILLISECONDS)) {
                    System.out.println("Train " + trainInfo.trainNumber() + " HOLDS LOCK on Switch " + thirdSwitch + ".\n"
                            + "\nTrain " + trainInfo.trainNumber() + ": HOLDS ALL NEEDED SWITCH LOCKS – Train movement begins.\n");

                    try {
                        Thread.sleep(400);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return true;
                } else {
                    System.out.println("Train " + trainInfo.trainNumber() + " UNABLE TO LOCK third required switch: Switch " + thirdSwitch + "."
                            + "\nTrain " + trainInfo.trainNumber() + " Releasing locks on first and second required switches: Switch "
                            + firstSwitch + " and Switch " + secondSwitch + ". Train will wait...");
                    switchLocks[firstSwitch].unlock();
                    switchLocks[secondSwitch].unlock();

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else { //This else means the current train failed to get the third switch, requiring it to give up the two it currently holds.
                System.out.println("Train " + trainInfo.trainNumber() + " UNABLE TO LOCK second required switch: Switch " + secondSwitch + "."
                        + "\nTrain " + trainInfo.trainNumber() + " Releasing lock on first required switch: Switch " + firstSwitch + ". Train will wait...");
                switchLocks[firstSwitch].unlock();

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        } else { //This else means the current train failed to get the second switch, requiring it to give up the first switch it got.
            System.out.println("Train " + trainInfo.trainNumber() + " UNABLE TO LOCK first required switch: Switch " + firstSwitch + ". Train will wait...");

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return false;
    }
}

//The constructor (record) holding the information from "theFleetFile.csv" file.
record TrainInfo(int trainNumber, int inboundTrack, int outboundTrack, boolean completedPath) {
}

//The constructor (record) holding the information from "theYardFile.csv" file.
record YardConfig(int inboundTrack, int firstSwitch, int secondSwitch, int thirdSwitch, int outboundTrack) {
}