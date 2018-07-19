package com.rockyrunstream.walmart;

import com.rockyrunstream.walmart.impl.finder.SimpleSeatFinder;
import com.rockyrunstream.walmart.impl.model.Venue;
import com.rockyrunstream.walmart.impl.store.SeatsCounter;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static com.rockyrunstream.walmart.impl.model.Venue.PENDING;

public class PerformanceTestApplication extends AbstractApplication {

    //Venue parameters
    private static final int NUM_ROWS = 1_000;
    private static final int NUM_SEATS = 200;

    //For multi-thread test
    private static final int NUM_THREADS = 4;

    private static final Logger log = LoggerFactory.getLogger(PerformanceTestApplication.class);

    @Autowired
    private SimpleSeatFinder finder;


    public static void main(String[] args) {
        SpringApplication.run(PerformanceTestApplication.class, args);
    }

    @Override
    protected void execute(String[] args) throws Exception {
        testSeatsFinder();
        testSingleThread();
        testMultipleThreads();
    }

    private void setup(long maxHoldTime) {
        final Venue venue = VenueGenerator.generate(NUM_ROWS, NUM_SEATS);
        venue.setMaxHoldTime(maxHoldTime);
        venueService.setVenue(venue);
        store.drop();
    }


    /**
     * Test how fast is seat selector algorithm
     */
    private void testSeatsFinder() {
        printHeader("Test seats finder's performance");
        final long holdPeriod = 1_000; //Irrelevant for this test
        setup(holdPeriod);

        //Test parameters
        final int iterations = 1000;
        final int numSeats = 25;
        final Venue venue = venueService.getVenue();

        //Let make evey 10th seat taken, so the algorithm will have more segments to deal with and also will
        // have to split seat hold on several segments
        int counter = 0;
        for (byte[] seats : venue.getRows()) {
            for (int i = 0; i < seats.length; i += 10) {
                seats[i] = PENDING;
                counter++;
            }
        }
        venue.setPending(counter);

        //Execute number of iterations to find out performance of the SeatFinder
        final long before = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            finder.find(venue, numSeats);
        }
        final long after = System.currentTimeMillis();
        final long time = after - before;

        //Print results
        printf("%n%n    Results: %n");
        printf(" Venue size: %dx%d %n", NUM_ROWS, NUM_SEATS);
        printf(" Number of iterations: %d %n", iterations);
        printf(" Total time, %dms %n", time);
        printf(" Average time per iteration %dms %n", time / iterations);
    }


    /**
     * Test how fast is a single reservation can be made. Test all the methods in 1 thread until the venue is full
     */
    private void testSingleThread() {
        //1. Prepare venue
        printHeader("Test single-thread performance");
        final long holdPeriod = 1_000_000; //That disables scheduler
        setup(holdPeriod);

        //2. Execute test
        final Worker worker = new Worker("testSingleThread");
        worker.run();

        //3. Check for errors
        if (worker.errors.size() > 0) {
            log.error("OOOPS, something went wrong, {}", worker.errors);
            throw new RuntimeException("Server failed during single thread run");
        }
        checkConsistency();

        //4. Compute stats
        final int reservations = worker.getSuccess();
        long time = worker.getTime();

        //5. Print results
        printf("%n%n    Results: %n");
        printf(" Venue size: %dx%d %n", NUM_ROWS, NUM_SEATS);
        printf(" Reservations: %d %n", reservations);
        printf(" Total time, %d sec %n", time / 1000);
        printf(" Average time per iteration %dms %n", time / reservations);
    }

    private void checkConsistency() {
        //Debug output, only for small venues
        if (NUM_ROWS * NUM_SEATS < 10_000) {
            printVenue();
        }
        if (!store.isConsistent()) {
            throw new RuntimeException("Store contains double-booked seats");
        }
    }

    /**
     * X thread plus a cleaner are trying to fill the venue.
     * As result:
     * - Whole venue must be filled
     * - No errors except OptimisticLockException and DataExpired
     * - Performance characteristics must be measured
     * - Errors rate must be measured
     */
    private void testMultipleThreads() {
        //1. Prepare venue
        printHeader("Test multi-thread performance");
        final long holdPeriod = 100;
        setup(holdPeriod);

        //2. Prepare signals
        final CountDownLatch startSignal = new CountDownLatch(1);
        final CountDownLatch doneSignal = new CountDownLatch(NUM_THREADS);

        //3. Prepare and start workers
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

        //4. Start!
        startSignal.countDown();

        //5. Wait
        try {
            doneSignal.await();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        final long time = System.currentTimeMillis() - before;

        //6. Aggregate results
        final Worker aggregator = new Worker("aggregator");
        workers.forEach(aggregator::agregateStat);

        //7. Check for errors
        checkConsistency();
        final Set<Class<? extends ServiceException>> exceptions = new HashSet<>();
        exceptions.addAll(aggregator.errors.keySet());
        exceptions.remove(OptimisticLockException.class);
        exceptions.remove(DataExpired.class);
        if (aggregator.errors.isEmpty()) {
            printf("%n No errors! %n");
        } else {
            printf("%n Errors: %n");
            aggregator.errors.forEach((k, v) -> {
                printf("%s -> %d%n", k.getSimpleName(), v.getValue());
            });
        }
        if (!exceptions.isEmpty()) {
            //Ooops - test failed!
            log.error("Unexpected errors {}", exceptions);
            throw new RuntimeException();
        }

        //8. Compute statistics
        final int iterations = aggregator.getIterationCount();
        final long throughput = ((long) iterations * 60_000L / time);

        int errorCount = aggregator.getErrorsCount();

        //9. Print
        printf("%n%n    Results: %n");
        printf(" Venue size: %dx%d %n", NUM_ROWS, NUM_SEATS);
        printf(" Number of thread: %d %n", NUM_THREADS);
        printf(" Iterations: %d %n", iterations);
        printf(" Time: %d sec %n", time / 1000);
        printf(" Throughput, %d per minute %n", throughput);
        printf(" Average time per iteration %d ms %n", aggregator.getAvgTime());
        printf(" Reservations, total %d ms %n", aggregator.getSuccess());
        printf(" Errors, total %d %n", errorCount);
        if (errorCount > 0) {
            final double successErrorRatio = (double) aggregator.getSuccess() / (double) errorCount;
            printf(" Success to error ration, %f %n", successErrorRatio);
        }
    }

    class Worker implements Runnable {

        private final int MAX_NUM_SEATS = 5;
        private final int PAUSE_MS = 1000;

        private int success;
        private Map<Class<? extends ServiceException>, MutableInt> errors = new HashMap<>();

        private long time;
        private int iterationCount = 0;

        private List<Long> times = new ArrayList<>();
        private List<Integer> iterations = new ArrayList<>();
        private long avgTime;

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
                        if (seatHold.getSeats().size() != numSeats) {
                            log.error("Invalid hold size, expecting {}, result {}", numSeats, seatHold.getSeats().size());
                            throw new RuntimeException("Invalid result");
                        }

                        ticketService.reserveSeats(seatHold.getId(), name);
                        success++;
                    } else {
                        log.debug("No seats available - pause");
                        pause(PAUSE_MS);
                    }
                } catch (OptimisticLockException | DataExpired e) {
                    log.debug(e.getMessage());
                    errors.computeIfAbsent(e.getClass(), k -> new MutableInt(0)).increment();
                } catch (NoSeatsAvailable e) {
                    log.debug(e.getMessage());
                    pause(PAUSE_MS);
                } catch (ServiceException e) {
                    log.error("Unexpected exception {}", e.toString()); //Do not print stacktrace
                    errors.computeIfAbsent(e.getClass(), k -> new MutableInt(0)).increment();
                }
                final SeatsCounter counter = store.countReserved();
                final Venue venue = venueService.getVenue();
                availableAndPending = venue.getCapacity() - counter.getReserved();
                if (getIterationCount() % 1000 == 0) {
                    final int available = venue.getCapacity() - counter.getTotal();
                    final int errorsCount = getErrorsCount();
                    printf("%s still alive, iteration %d, success %d, errors: %d, reserved %d of %d%n", name, iterationCount, success, errorsCount, available, venue.getCapacity());
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
            times.add(another.time);
            iterations.add(another.iterationCount);
        }

        long getAvgTime() {
            avgTime = 0;
            for (int i = 0; i < times.size(); i++) {
                avgTime += times.get(i)/iterations.get(i);
            }
            avgTime = avgTime / times.size();
            return avgTime;
        }

        int getErrorsCount() {
            int errorCount = 0;
            for (Map.Entry<Class<? extends ServiceException>, MutableInt> keyValue : errors.entrySet()) {
                errorCount += keyValue.getValue().toInteger();
            }
            return errorCount;
        }
    }

}
