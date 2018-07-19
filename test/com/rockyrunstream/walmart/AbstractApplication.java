package com.rockyrunstream.walmart;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.rockyrunstream.walmart.impl.VenueService;
import com.rockyrunstream.walmart.impl.model.Venue;
import com.rockyrunstream.walmart.impl.store.ReservationStore;
import com.rockyrunstream.walmart.impl.store.SeatMap;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public abstract class AbstractApplication {
    private static final String ANSI_RESET = "\u001B[0m";
    private final String ANSI_RED = "\u001B[31m";

    @Autowired
    protected ReservationStore store;

    @Autowired
    protected TicketService ticketService;

    @Autowired
    protected VenueService venueService;

    private boolean useColor = false;

    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
        return args -> {
            //1. Process arguments
            if (args.length > 0 && BooleanUtils.toBoolean(args[0])) {
                useColor = true;
            }

            //2. Reduce logs
            final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            final ch.qos.logback.classic.Logger appLogger = loggerContext.getLogger("com.rockyrunstream.walmart");
            appLogger.setLevel(Level.WARN);

            try {
                execute(args);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
            System.exit(0);
        };
    }

    protected abstract void execute(String[] args) throws Exception;


    protected void printHeader(String header) {
        printf("%n%n%n---------------------------------------------------------------- %s --------------------------------------------------------------------------%n%n", header);
    }

    protected void println(String s) {
        System.out.println(s);
    }

    protected void printf(String format, Object...arg) {
        System.out.printf(format, arg);
    }

    protected void printVenue() {
        System.out.println("");
        System.out.println("");
        final SeatMap seatMap = store.getSeatMap();
        final Venue venue = venueService.getVenue();
        venue.updateSeat(seatMap);
        final byte[][] rows = venue.getRows();
        final String reservedSeat = useColor ? " " + ANSI_RED + "@" + ANSI_RESET + " " : " @ ";

        for (byte[] seats : rows) {
            System.out.print("                            | ");
            for (int seat : seats) {
                switch (seat) {
                    case Venue.AVAILABLE :
                        System.out.print(" - "); break;
                    case Venue.PENDING :
                        System.out.print(reservedSeat); break;
                    case Venue.RESERVED :
                        System.out.print(" # "); break;
                    default:
                        System.out.print("&&&"); break;
                }
            }
            System.out.println(" |");
        }
        System.out.println("");
        System.out.println("");
    }

    protected void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
