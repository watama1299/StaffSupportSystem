package uk.ncl.CSC8016.jackbergus.coursework.project2.processes;

import uk.ncl.CSC8016.jackbergus.coursework.project2.utils.Item;

import java.util.*;
import java.util.concurrent.locks.*;
import java.util.stream.Collectors;


/**
 * Keeps track of item based on the name. Items of the same name differentiated by unique UUID.
 * Each item with unique UUID is a unique entry inside available and withdrawn.
 * <p>
 * This ProductMonitor implements a monitor behaviour by applying a ReentrantReadWriteLock for
 * any and all operations that will access and modify either or both the Queues of Item. By using
 * ReentrantReadWriteLock, the handling of the access of reader and writer threads can be done
 * implicitly by the class itself instead of having to program this behaviour. By setting the
 * fairness flag for the ReentrantReadWriteLock to be true, it will ensure that competing reader
 * and writer threads are handled by a FIFO basis. This will help to alleviate Starvation between
 * the reader and writer threads.
 *
 */
public class ProductMonitor {
    Queue<Item> available;
    Queue<Item> withdrawn;

    /**
     * UNUSED
     * <p>
     * Monitor implementation using ReentrantLock
     */
    ReentrantLock monitor;

    /**
     * UNUSED
     * <p>
     * Condition to keep track of the ability for threads to read into the queues
     */
    Condition readOk;

    /**
     * UNUSED
     * <p>
     * Condition to keep track of the ability for threads to write into the queues
     */
    Condition writeOk;

    /**
     * UNUSED
     * <p>
     * Counter to keep track of the number of readers accessing the queues
     */
    private int numReader;

    /**
     * UNUSED
     * <p>
     * Counter to keep track of the number of writers accessing the queues
     */
    private int numWriter;

    /**
     * UNUSED
     * <p>
     * Counter to keep track of the number of writers waiting for access to the queues
     */
    private int waitingWriter;

    /**
     * Monitor implementation using ReadWriteLock
     */
    ReadWriteLock rwMonitor;

    /**
     * Lock that will handle the reader threads
     */
    Lock rLock;

    /**
     * Lock that will handle the writer threads
     */
    Lock wLock;



    /**
     * Fair monitor implementation, taken from lecture slide 05-Monitors
     */
    public ProductMonitor() {
        available = new LinkedList<>();
        withdrawn = new LinkedList<>();

//        // UNUSED
//        // monitor locking implementation
//        monitor = new ReentrantLock(true);
//        readOk = monitor.newCondition();
//        writeOk = monitor.newCondition();
//        numReader = numWriter = waitingWriter = 0;

        // monitor implementation using reentrant readwritelock
        rwMonitor = new ReentrantReadWriteLock(true);
        rLock = rwMonitor.readLock();
        wLock = rwMonitor.writeLock();

    }

    /**
     * UNUSED
     * <p>
     * Shared lock locking implementation, taken from lecture slide 05-Monitors
     */
    public void sLock() {
        // obtain monitor lock
        monitor.lock();

        // check if there are any existing writers in process or waiting
        // if there are, put thread to sleep and wait until no more writers in process or waiting
        while (numWriter > 0 || waitingWriter > 0)
            try {
                readOk.await();
            } catch (Exception e) {}

        // add one to the number of readers currently in process
        numReader++;
        // signal to other readers to awake and begin process
        readOk.signal();
        // unlock monitor lock
        monitor.unlock();
    }

    /**
     * UNUSED
     * <p>
     * Shared lock unlocking implementation, taken from lecture slide 05-Monitors
     */
    public void sUnlock() {
        // obtain monitor lock
        monitor.lock();

        // decrease readers count
        numReader--;

        // if there are no more readers, then wake any writer threads waiting
        if (numReader == 0)
            writeOk.signal();

        // unlock monitor lock
        monitor.unlock();
    }

    /**
     * UNUSED
     * <p>
     * Exclusive lock locking implementation, taken from lecture slide 05-Monitors
     */
    public void xLock() {
        // obtain monitor lock
        monitor.lock();

        // if there are currently any active writers or any active reader
        // add to waiting writer counter and go to sleep
        // once awaken, reduce waiting writer counter and add to writer counter
        if (numWriter > 0 || numReader > 0)
            try {
                waitingWriter++;
                writeOk.await();
                waitingWriter--;
            } catch (Exception e) {}
        numWriter++;

        // unlock monitor lock
        monitor.unlock();
    }

    /**
     * UNUSED
     * <p>
     * Exclusive lock unlocking implementation, taken from lecture slide 05-Monitors
     */
    public void xUnlock() {
        // obtain monitor lock
        monitor.lock();

        // decrease writer counter
        numWriter--;
        // assumes that there will only be a single writer
        // awake any sleeping reader threads
        readOk.signal();

        // if there are no readers, then awake any sleeping writer threads
        if (numReader == 0)
            writeOk.signal();

        // unlock monitor lock
        monitor.unlock();
    }

    /**
     * Returns those Items which were previously unavailable to become available again
     *
     * @param cls       Items to be re-flagged as available again
     */
    public void removeItemsFromUnavailability(Collection<Item> cls) {
        // acquire exclusive lock to access and write to the queue
        wLock.lock();
        try {
            // critical section
            for (Item x : cls) {
                if (withdrawn.remove(x))
                    available.add(x);
            }
        } finally {
            // release exclusive lock to give others access to both queue
            wLock.unlock();
        }
//        // UNUSED
//        // acquire exclusive lock to access and write to the queue
//        xLock();
//
//        // critical section
//        for (Item x : cls) {
//            if (withdrawn.remove(x))
//                available.add(x);
//        }
//
//        // release exclusive lock to give others access to both queue
//        xUnlock();
    }

    /**
     * Put item from shelf to basket
     *
     * @return      the Item which is still available to be put in the basket
     */
    public Optional<Item> getAvailableItem() {
        Optional<Item> o = Optional.empty();

        // acquire exclusive lock to access and write to the queue
        wLock.lock();

        try {
            // critical section
            if (!available.isEmpty()) {
                var obj = available.remove();
                if (obj != null) {
                    o = Optional.of(obj);
                    withdrawn.add(o.get());
                }
            }
        } finally {
            // release exclusive lock to give others access to both queue
            wLock.unlock();
        }
        return o;
//        // UNUSED
//        Optional<Item> o = Optional.empty();
//
//        // acquire exclusive lock to access and write to the queue
//        xLock();
//
//        // critical section
//        if (!available.isEmpty()) {
//            var obj = available.remove();
//            if (obj != null) {
//                o = Optional.of(obj);
//                withdrawn.add(o.get());
//            }
//        }
//
//        // release exclusive lock to give others access to both queue
//        xUnlock();
//        return o;
    }

    /**
     * Put item from basket to shelf
     *
     * @param u         Item to be re-shelved
     * @return          true if successful, false if otherwise
     */
    public boolean doShelf(Item u) {
        boolean result = false;

        // acquire exclusive lock to access and write to the queue
        wLock.lock();

        try {
            // critical section
            if (withdrawn.remove(u)) {
                available.add(u);
                result = true;
            }
        } finally {
            // release exclusive lock to give others access to both queue
            wLock.unlock();
        }

        return result;
//        // UNUSED
//        boolean result = false;
//
//        // acquire exclusive lock to access and write to the queue
//        xLock();
//
//        // critical section
//        if (withdrawn.remove(u)) {
//            available.add(u);
//            result = true;
//        }
//
//        // release exclusive lock to give others access to both queue
//        xUnlock();
//        return result;
    }

    /**
     * List all available products left from available queue
     *
     * @return          the item's name. Shows that this item is still available in the shelves
     */
    public Set<String> getAvailableItems() {
        // acquire shared lock to access and read to the queue
        Set<String> s;

        // acquire shared lock for reading data
        rLock.lock();
        try {
            // critical section
            s = available.stream().map(x -> x.productName).collect(Collectors.toSet());
        } finally {
            // release shared lock once done with reading
            rLock.unlock();
        }

        return s;
//        // UNUSED
//        // acquire shared lock to access and read to the queue
//        sLock();
//
//        // critical section
//        Set<String> s;
//        s = available.stream().map(x -> x.productName).collect(Collectors.toSet());
//
//        // release shared lock once done with reading
//        sUnlock();
//        return s;
    }

    /**
     * This method is invoked by a RainforestShop object upon its creation to populate the shelves of the
     * shop. It is also used by the Supplier thread when refurbishing the shelves with items.
     *
     *
     * @param x         Item to be added back to the shelves
     */
    public void addAvailableProduct(Item x) {
        // acquire exclusive lock to access and write to the queue
        wLock.lock();

        try {
            // critical section
            available.add(x);
        } finally {
            // release exclusive lock to give others access to both queue
            wLock.unlock();
        }
//        // UNUSED
//        // acquire exclusive lock to access and write to the queue
//        xLock();
//
//        // critical section
//        available.add(x);
//
//        // release exclusive lock to give others access to both queue
//        xUnlock();
    }

    /**
     * This method is invoked by the Client thread through the basketCheckout method in the RainforestShop.
     * It is used to get the total cost of a single item type, i.e. if the user has multiple items with the
     * same product name, the method will total up all these items.
     *
     * @param aDouble                   the price of the Item
     * @param toIterate                 the list of Items that the user has in their basket
     * @param currentlyPurchasable      list of Items that keeps track whether an item is still available to be
     *                                  purchased and checked out
     * @param currentlyUnavailable      list of Items that keeps track of the items which is no longer available
     *                                  to be purchased and checked out by the user
     * @return                          total cost of a singular item type within the user's basket
     */
    public double updatePurchase(Double aDouble,
                                 List<Item> toIterate,
                                 List<Item> currentlyPurchasable,
                                 List<Item> currentlyUnavailable) {
        double total_cost = 0.0;

        // acquire exclusive lock to access and write to the queue
        wLock.lock();

        try {
            // critical section
            for (var x : toIterate) {
                if (withdrawn.contains(x)) {
                    currentlyPurchasable.add(x);
                    total_cost += aDouble;
                } else {
                    currentlyUnavailable.add(x);
                }
            }
        } finally {
            // release exclusive lock to give others access to both queue
            wLock.unlock();
        }

        return total_cost;
//        // UNUSED
//        double total_cost = 0.0;
//
//        // acquire exclusive lock to access and write to the queue
//        xLock();
//
//        // critical section
//        for (var x : toIterate) {
//            if (withdrawn.contains(x)) {
//                currentlyPurchasable.add(x);
//                total_cost += aDouble;
//            } else {
//                currentlyUnavailable.add(x);
//            }
//        }
//
//        // release exclusive lock to give others access to both queue
//        xUnlock();
//        return total_cost;
    }

    /**
     * This method is invoked by the Client thread through the basketCheckout method in the RainforestShop.
     * It is invoked when a user does not have enough money to purchase all the items in their basket, so
     * they are all returned to the shelf.
     *
     * @param toIterate     a list of Items that the user has purchased
     */
    public void makeAvailable(List<Item> toIterate) {
        // acquire exclusive lock to access and write to the queue
        wLock.lock();

        try {
            // critical section
            for (var x : toIterate) {
                if (withdrawn.remove(x)) {
                    available.add(x);
                }
            }
        } finally {
            // release exclusive lock to give others access to both queue
            wLock.unlock();
        }
//        // UNUSED
//        // acquire exclusive lock to access and write to the queue
//        xLock();
//
//        // critical section
//        for (var x : toIterate) {
//            if (withdrawn.remove(x)) {
//                available.add(x);
//            }
//        }
//
//        // release exclusive lock to give others access to both queue
//        xUnlock();
    }

    /**
     * This method is invoked by the Client thread through the basketCheckout method in the RainforestShop.
     * It is invoked to ensure that once the users purchases an item, the item can no longer be re-shelved.
     *
     * @param toIterate     a list of Items that the user has purchased
     * @return              true if method successfully remove the item from the ProductMonitor
     */
    public boolean completelyRemove(List<Item> toIterate) {
        boolean allEmpty;

        // acquire exclusive lock to access and write to the queue
        wLock.lock();

        try {
            // critical section
            for (var x : toIterate) {
                withdrawn.remove(x);
                available.remove(x);
            }
            allEmpty = withdrawn.isEmpty() && available.isEmpty();
        } finally {
            // release exclusive lock to give others access to both queue
            wLock.unlock();
        }

        return allEmpty;
//        // UNUSED
//        boolean allEmpty;
//
//        // acquire exclusive lock to access and write to the queue
//        xLock();
//
//        // critical section
//        for (var x : toIterate) {
//            withdrawn.remove(x);
//            available.remove(x);
//        }
//        allEmpty = withdrawn.isEmpty() && available.isEmpty();
//
//        // release exclusive lock to give others access to both queue
//        xUnlock();
//        return allEmpty;
    }
}
