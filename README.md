# Ticket Service Coding Challenge

A very simple homework project.

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

## Analysis

According to Wikipedia https://en.wikipedia.org/wiki/List_of_sports_venues_by_capacity the biggest venue in 
the world has capacity 257K seats. 

The interface does not provide ability to select particular seats. 

The seating arrangement is very simplified. A venue can consists from multiple zones, each zone might unique 
parameters, such as geometry, price, availability for disabled, etc. A zone shape is not necessarily a rectangle, 
it could be a sectors or a trapezoids. 

Meaning of **best available seats on behalf of a customer** is hard to formalize. A numeric value could be assigned
to every seat at the venue, a price is a good starting initial value. For a group mutual position of seats in the group 
is also important, all seats in a one batch in the same row is probably the best option. 
 
Brut force approach by comparing all possible combinations of seats for group of K seat will take O(n!/(n-k)!) where n 
number of all available seats and k is number of requested seats.  

## Assumption

For the sake of this exercise I assume that
 * Each seat in the venue has assigned a number - seat value 
 * A venue consists of number of rows, each row consists of number of seats. Number of seats in each row could be 
 different
 * Uninterrupted sequence of seats in the same row is ideal for a customer. Or, a reservation that consists of minimal
  number of uninterrupted sequence of seats in the same row has best value
 * The is no particular order or priority the service must process requests according to. If two requests for the last 
   few seat comes at the same time then one must fail and one must complete successfully   
 * The algorithm must find best value for current customer, not best in average, or best for venue owner   

## Implemented seat search algorithm

* Start by iterating of every row and every seat in the venue and find all free segments
* Put a requested number of seats into the processing queue
* While queue is not empty, for each number in the queue
    * Iterate over each free segment 
    * If requested number of seats fits into the segment - find a best position
    * Put best segment and position into result queue
    * If there is no segment big enough - divide the number by two and put all parts in to the queue

## Implemented storage

* Only in-memory storage is implemented
* Implementation uses optimistic locks to prevent overbooking
* Each reserved or hold seat cold be written into store only once, any collision will cause "rollback" for whole operation
* Every reservation has unique transaction number, optimistic locks for reservation update based on this TN
* The store uses read-write lock to ensure consistency of data

## Build

The project uses gradle. Commands:

To build the project use
```shell
gradle clean test
```

## Algorithm Demo

There is a main class that demonstrates how the algorithm selects seats for reservation.
It executes several scenarios and print venue state into console after each step. 
There is a delay at the scenario 3 to demonstrate reservation expiration logic.
At the last step the app fills the whole venue requesting holds of random size until the venue is full

To run demo app
```shell
 gradle demo
```
or if your console supports ANSI colors
```shell
 gradle demo -Pcolor=true
```

## Performance and integration test

There is another main class to test performance. It contains 3 scenarios.
**Note** it takes about 15 minutes on my machine to execute all 3 scenarios

* Scenario 1 tests how fast the finder algorithm is
* Scenario 2 tests what is max performance of the service when there are only 1 thread
* Scenario 3 tests multi-thread performance. All threads are using the service non-stop, significant amount of 
OptimisticLockExceptions is expected.

Testes on my MacBook Pro (Retina, 15-inch, Mid 2015), 2.2 GHz Intel Core i7 against 200K venue. Execution examples:

```text
finder's performance results:
 Venue size: 1000x200
 Number of iterations: 1000
 Total time, 7034ms
 Average time per iteration 7ms
```

```text
single-thread performance results:
  Venue size: 1000x200
  Reservations: 40133
  Total time, 275 sec
  Average time per iteration 6ms

```


```text
multi-thread performance results

 Errors:
OptimisticLockException -> 8455
DataExpired -> 4


    Results:
 Venue size: 1000x200
 Number of thread: 4
 Iterations: 48407
 Time: 563 sec
 Throughput, 5151 per minute
 Average time per iteration 46 ms
 Reservations, total 39945 ms
 Errors, total 8459
 Success to error ration, 4.722189
```

 