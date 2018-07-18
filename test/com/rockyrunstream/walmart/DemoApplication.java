package com.rockyrunstream.walmart;

import com.rockyrunstream.walmart.impl.model.Venue;
import org.springframework.boot.SpringApplication;

import java.io.IOException;
import java.util.Random;

public class DemoApplication extends AbstractApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Override
    protected void execute(String[] args) throws IOException {
        //1. Setup demo venue. Seats at center are better then at the edges
        final Venue venue = VenueGenerator.generate(20, 10);
        final double[][] seatValues = new double[][] {
                {1D, 1D, 1D, 1D, 1D, 1D, 1D, 1D, 1D, 1D},
                {1D, 2D, 2D, 2D, 2D, 2D, 2D, 2D, 2D, 1D},
                {2D, 2D, 2D, 2D, 3D, 3D, 2D, 2D, 1D, 1D},
                {2D, 2D, 2D, 3D, 3D, 3D, 3D, 2D, 2D, 2D},
                {2D, 2D, 3D, 3D, 4D, 4D, 3D, 3D, 2D, 2D},
                {2D, 3D, 3D, 4D, 4D, 4D, 4D, 3D, 3D, 2D},
                {3D, 3D, 4D, 4D, 5D, 5D, 4D, 4D, 3D, 3D},
                {3D, 4D, 4D, 5D, 5D, 5D, 5D, 4D, 4D, 3D},
                {4D, 4D, 5D, 5D, 5D, 5D, 5D, 5D, 4D, 4D},
                {4D, 4D, 5D, 5D, 5D, 5D, 5D, 5D, 4D, 4D},
                {4D, 4D, 5D, 5D, 5D, 5D, 5D, 5D, 4D, 4D},
                {4D, 4D, 5D, 5D, 5D, 5D, 5D, 5D, 4D, 4D},
                {4D, 4D, 5D, 5D, 5D, 5D, 5D, 5D, 4D, 4D},
                {3D, 4D, 4D, 5D, 5D, 5D, 5D, 4D, 4D, 3D},
                {3D, 4D, 4D, 5D, 5D, 5D, 5D, 4D, 4D, 3D},
                {2D, 3D, 3D, 4D, 4D, 4D, 4D, 3D, 3D, 2D},
                {2D, 3D, 3D, 4D, 4D, 4D, 4D, 3D, 3D, 2D},
                {2D, 3D, 3D, 4D, 4D, 4D, 4D, 3D, 3D, 2D},
                {1D, 2D, 2D, 2D, 2D, 2D, 2D, 2D, 2D, 1D},
                {1D, 2D, 2D, 2D, 2D, 2D, 2D, 2D, 2D, 1D}
        };
        final long holdPeriod = 1_000;
        configuration.configure(venue, seatValues, holdPeriod);


        //2. Banner
        printHeader(" Ticker Reservation Service -- DemoApplication");
        println("Venue schema, available seats printed as _, pending as @ and reserved as #");
        println("");
        printf("Available seats: %d%n", ticketService.numSeatsAvailable());
        printVenue();

        //3. Demo
        step1();
        step2();
        step3();
        step4();
        step5();
    }

    private void step1() throws IOException {
        printHeader("Scenario 1");
        println("Reservation for first customer");
        final SeatHold seatHold = ticketService.findAndHoldSeats(3, "customer1");
        printf("Hold 3 seats - %s%n", seatHold);
        printVenue();

        final String confirmationCode = ticketService.reserveSeats(seatHold.getId(), "customer1");
        printf("Seats are reserved, confirmation code %s%n", confirmationCode);
        printVenue();
    }

    private void step2() {
        printHeader("Scenario 2");
        println("Now pretend that there are several parties trying to reserve a seat simultaneously");

        final SeatHold seatHold1 = ticketService.findAndHoldSeats(5, "customer1");
        printf("Hold 1 - %s%n", seatHold1);
        printVenue();

        final SeatHold seatHold2 = ticketService.findAndHoldSeats(2, "customer2");
        printf("Hold 2 - %s%n", seatHold2);
        printVenue();

        final SeatHold seatHold3 = ticketService.findAndHoldSeats(3, "customer3");
        printf("Hold 3 - %s%n", seatHold3);
        printVenue();

        final String confirmationCode2 = ticketService.reserveSeats(seatHold2.getId(), "customer2");
        printf("Confirmation 2 %s%n", confirmationCode2);
        printVenue();

        final String confirmationCode1 = ticketService.reserveSeats(seatHold1.getId(), "customer1");
        printf("Confirmation 1 %s%n", confirmationCode1);
        printVenue();

        final String confirmationCode3 = ticketService.reserveSeats(seatHold3.getId(), "customer3");
        printf("Confirmation 3 %s%n", confirmationCode3);
        printVenue();
    }

    private void step3() throws IOException {
        printHeader("Scenario 3");
        println("Let hold expired, make reservation and wait for two seconds");
        final SeatHold seatHold = ticketService.findAndHoldSeats(2, "customer4");
        printf("Hold seats - %s%n", seatHold);
        printVenue();

        println("Sleep 2 seconds to let the reservation expire and be collected by cleaner");
        pause(2000L);
        try {
            ticketService.reserveSeats(seatHold.getId(), "customer4");
        } catch (DataExpired e) {
            printf("Oops, reservation expired %s%n", e.getMessage());
            printVenue();
        }

    }

    private void step4() {
        printHeader("Scenario 4");
        println("What if order is too big?");

        final SeatHold seatHold = ticketService.findAndHoldSeats(22, "bug group");
        printf("seatHold %s%n", seatHold);
        printVenue();

        final String confirmationCode = ticketService.reserveSeats(seatHold.getId(), "bug group");
        printf("Seats are reserved, confirmation code %s%n", confirmationCode);
        printVenue();
    }
    private void step5() {
        printHeader("Scenario 5");
        println("Fill the rest of the venue");
        int available;
        Random random = new Random();
        while ((available = ticketService.numSeatsAvailable()) > 0) {
            int numSeats = random.nextInt(12) + 1;
            numSeats = Math.min(numSeats, available);
            final SeatHold seatHold = ticketService.findAndHoldSeats(numSeats, "xyz");
            printVenue();
            ticketService.reserveSeats(seatHold.getId(), "xyz");
        }
        printVenue();
    }
}
