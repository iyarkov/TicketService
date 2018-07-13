# Ticket Service Coding Challenge

## Requirements
Implement a simple ticket service that facilitates the discovery, temporary hold, and final reservation of seats within a
high-demand performance venue.
For example, see the seating arrangement below.

        ----------[[  STAGE  ]]----------
        ---------------------------------
        sssssssssssssssssssssssssssssssss
        sssssssssssssssssssssssssssssssss
        sssssssssssssssssssssssssssssssss
        sssssssssssssssssssssssssssssssss
        sssssssssssssssssssssssssssssssss
        sssssssssssssssssssssssssssssssss
        sssssssssssssssssssssssssssssssss
        sssssssssssssssssssssssssssssssss
        sssssssssssssssssssssssssssssssss

Ticket Service provides the following functions:
 * Find the number of seats available within the venue
 
**Note: available seats are seats that are neither held nor reserved.**

* Find and hold the best available seats on behalf of a customer

**Note: each ticket hold should expire within a set number of seconds.**

* Reserve and commit a specific group of held seats for a customer

* The ticket service implementation should be written in Java
* The solution and tests should build and execute entirely via the command line using either Maven or Gradle as the build tool
* A README file should be included in your submission that documents your assumptions and includes instructions for building the solution and executing the tests
* Implementation mechanisms such as disk-based storage, a REST API, and a front-end GUI are not required

You will need to implement the following interface. The design of the SeatHold object is entirely up to you.

```java
public interface TicketService {
/**
* The number of seats in the venue that are neither held nor reserved
*
* @return the number of tickets available in the venue
*/
  int numSeatsAvailable();
/**
* Find and hold the best available seats for a customer
*
* @param numSeats the number of seats to find and hold
* @param customerEmail unique identifier for the customer
* @return a SeatHold object identifying the specific seats and related
information
*/
  SeatHold findAndHoldSeats(int numSeats, String customerEmail);
/**
* Commit seats held for a specific customer
*
* @param seatHoldId the seat hold identifier
* @param customerEmail the email address of the customer to which the
seat hold is assigned
* @return a reservation confirmation code
*/
String reserveSeats(int seatHoldId, String customerEmail);
}
```


 