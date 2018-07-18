package com.rockyrunstream.walmart;

import com.rockyrunstream.walmart.impl.finder.SimpleSeatFinder;
import com.rockyrunstream.walmart.impl.model.Row;
import com.rockyrunstream.walmart.impl.model.Venue;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class PerformanceTestApplication extends AbstractApplication {

    //Venue parameters
    private static final int NUM_ROWS = 100;
    private static final int NUM_SEATS = 20;

    //For multi-thread test
    private static final int NUM_THREADS = 5;

    private static final Logger log = LoggerFactory.getLogger(PerformanceTestApplication.class);

    @Autowired
    private SimpleSeatFinder finder;


    public static void main(String[] args) {
        SpringApplication.run(PerformanceTestApplication.class, args);
    }

    @Override
    protected void execute(String[] args) throws Exception {
        //testSeatsFinder();
        //testSingleThread();
        testMultipleThreads();
    }

    private void setup(long holdPeriod) {
        final Venue venue = VenueGenerator.generate(NUM_ROWS, NUM_SEATS);
        configuration.configure(venue, null, holdPeriod);
    }


    private void testSeatsFinder() {
        printHeader("Test seats finder's performance");
        final long holdPeriod = 1_000; //Irrelevant for this test
        setup(holdPeriod);

        //Test parameters
        final int iterations = 1000;
        final int numSeats = 25;
        final Venue venue = store.getVenue();

        //Let make evey 10th seat taken, so the algorithm will have more segments to deal with and also will
        // have to split seat hold on several segments
        int counter = 0;
        for (Row row : venue.getRows()) {
            final byte[] seats = row.getSeats();
            for (int i = 0; i < seats.length; i += 10) {
                seats[i] = Row.PENDING;
                counter++;
            }
        }
        venue.setAvailable(venue.getCapacity() - counter);

        //Execute number of iterations to find out performance of the SeatFinder
        final long before = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            finder.find(venue, numSeats);
        }
        final long after = System.currentTimeMillis();
        final long time = after - before;
        printf("%n%n Iterations: %d, time: %dms, avg time %dms %n", iterations, time, time / iterations);
    }


    private void testSingleThread() {
        printHeader("Test single-thread performance");
        final long holdPeriod = 1_000_000; //That disables scheduler
        setup(holdPeriod);

        //Test parameters
        final Worker worker = new Worker("testSingleThread");
        worker.run();

        final Venue venue = store.getVenue();
        int capacity = venue.getCapacity();
        int averageHoldSize = capacity/worker.getSuccess();
        printf("%n%n seatHolds: %d, avg holdSize %d, time: %dms, avg time %dms %n", worker.getIterationCount(), averageHoldSize, worker.getTime(), worker.getTime() / worker.getIterationCount());
        if (worker.errors.size() > 0) {
            log.error("OOOPS, something went wrong");
            throw new RuntimeException("Server failed during single thread run");
        }
    }

    private void testMultipleThreads() {
        System.out.printf("%n%n Test multi-thread performance %n");
        final long holdPeriod = 100;
        setup(holdPeriod);

        //1. Prepare signals
        final CountDownLatch startSignal = new CountDownLatch(1);
        final CountDownLatch doneSignal = new CountDownLatch(NUM_THREADS);

        //2. Prepare and start workers
        final List<Worker> workers = new ArrayList<>(NUM_THREADS);
        final long before = System.currentTimeMillis();
        for (int i = 0; i < NUM_THREADS; ++i) {
            final String name = "Worker " + i;
            final Worker worker = new Worker(name);
            workers.add(worker);
            new Thread(() -> {
                try {
                    startSignal.await();
                    worker.run();
                } catch (InterruptedException ex) {
                    log.error(ex.getMessage(), ex);
                } finally {
                    doneSignal.countDown();
                }
            }, name).start();

        }

        //3. Start!
        startSignal.countDown();

        //4. Wait
        try {
            doneSignal.await();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        final long time = System.currentTimeMillis() - before;

        //5. Count stats
        final Worker aggregator = new Worker("aggregator");
        workers.forEach(aggregator::agregateStat);

        final Venue venue = store.getVenue();
        final int capacity = venue.getCapacity();
        final int averageHoldSize = capacity/aggregator.getSuccess();
        int errorCount = 0;
        for (Map.Entry<Class<? extends ServiceException>, MutableInt> keyValue : aggregator.errors.entrySet()) {
            errorCount += keyValue.getValue().toInteger();
        }
        final double successErrorRatio = (double) aggregator.getSuccess() / (double) errorCount;

        //6. Print
        printf("%n%n Threads: %d, seatHolds: %d, avg holdSize %d, time: %dms, total: %d, avg time %dms, success %d, errors %d, success/error ratio %f%n",
                NUM_THREADS, aggregator.getSuccess(), averageHoldSize, time, aggregator.getIterationCount(), time / aggregator.getIterationCount(), aggregator.getSuccess(), errorCount, successErrorRatio);
        printf("errors: %n");
        aggregator.errors.forEach((k, v) -> {
            printf("%s -> %d%n", k.getSimpleName(), v.getValue());
        });

        if (NUM_ROWS * NUM_SEATS < 10_000) {
            printVenue();
        }
    }

    class Worker implements Runnable {

        private final int MAX_NUM_SEATS = 5;
        private final int PAUSE_MS = 1000;

        private int success;
        private Map<Class<? extends ServiceException>, MutableInt> errors = new HashMap<>();

        private long time;
        private int iterationCount = 0;

        private final String name;

        Worker(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            final Random random = new Random();
            int availableAndPending = Integer.MAX_VALUE;
            final long before = System.currentTimeMillis();
            while (availableAndPending > 0) {
                iterationCount++;
                try {
                    final int available = ticketService.numSeatsAvailable();
                    if (available > 0) {
                        int numSeats = (int) Math.abs(Math.round(random.nextGaussian() * MAX_NUM_SEATS)) + 1;
                        numSeats = Math.min(numSeats, available);

                        final SeatHold seatHold = ticketService.findAndHoldSeats(numSeats, name);

                        ticketService.reserveSeats(seatHold.getId(), name);
                        success++;
                    } else {
                        pause(PAUSE_MS);
                    }
                } catch (OptimisticLockException | DataExpired e) {
                    errors.computeIfAbsent(e.getClass(), k -> new MutableInt(0)).increment();
                } catch (NoSeatsAvailable e) {
                    pause(PAUSE_MS);
                } catch (ServiceException e) {
                    log.error("Unexpected exception {}", e.toString()); //Do not print stacktrace
                    errors.computeIfAbsent(e.getClass(), k -> new MutableInt(0)).increment();
                }
                final Venue venue = store.getVenue();
                availableAndPending = 0;
                for (Row row : venue.getRows()) {
                    for (byte seat : row.getSeats()) {
                        if (seat != Row.RESERVED) {
                            availableAndPending++;
                        }
                    }
                }
                if (getIterationCount() % 10 == 0) {
                    final int reserved = venue.getCapacity() - availableAndPending;
                    printf("%s still alive, total %d, success %d, errors: %d, reserved %d of %d%n", name, iterationCount, success, errors.size(), reserved, venue.getCapacity());
                }
            }

            time = System.currentTimeMillis() - before;
        }

        long getTime() {
            return time;
        }

        int getSuccess() {
            return success;
        }

        int getIterationCount() {
            return iterationCount;
        }

        void agregateStat(Worker another) {
            success += another.success;
            time += another.time;
            iterationCount += another.iterationCount;
            another.errors.forEach((k, v) -> errors.computeIfAbsent(k, newK -> new MutableInt(0)).add(v.getValue()));
        }

    }

}
