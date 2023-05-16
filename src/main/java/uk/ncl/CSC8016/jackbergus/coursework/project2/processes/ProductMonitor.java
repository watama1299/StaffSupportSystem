package uk.ncl.CSC8016.jackbergus.coursework.project2.processes;

import uk.ncl.CSC8016.jackbergus.coursework.project2.utils.Item;

import java.util.*;
import java.util.concurrent.locks.*;
import java.util.stream.Collectors;


/**
 * Keeps track of item based on the name. Items of the same name differentiated by unique UUID.
 * Each item with unique UUID is a unique entry inside available and withdrawn.
 *
 */
public class ProductMonitor {
    Queue<Item> available;
    Queue<Item> withdrawn;


    ReentrantLock monitor;
    Condition readOk, writeOk;
    private int numReader, numWriter, waitingWriter;


    /**
     * Fair monitor implementation, taken from lecture slide 05-Monitors
     */
    public ProductMonitor() {
        available = new LinkedList<>();
        withdrawn = new LinkedList<>();

        // monitor locking implementation
        monitor = new ReentrantLock(true);
        readOk = monitor.newCondition();
        writeOk = monitor.newCondition();
        numReader = numWriter = waitingWriter = 0;
    }

    /**
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
     * WILL NEED WRITE LOCK
     *
     * @param cls
     */
    public void removeItemsFromUnavailability(Collection<Item> cls) {
        xLock();
        for (Item x : cls) {
            if (withdrawn.remove(x))
                available.add(x);
        }
        xUnlock();
    }

    /**
     * Put item from shelf to basket
     *
     * WILL NEED READ WRITE LOCK
     *
     * @return
     */
    public Optional<Item> getAvailableItem() {
        Optional<Item> o = Optional.empty();
        xLock();
        if (!available.isEmpty()) {
            var obj = available.remove();
            if (obj != null) {
                o = Optional.of(obj);
                withdrawn.add(o.get());
            }
        }
        xUnlock();
        return o;
    }

    /**
     * Put item from basket to shelf
     *
     * WILL NEED WRITE LOCK
     *
     * @param u
     * @return
     */
    public boolean doShelf(Item u) {
        boolean result = false;
        xLock();
        if (withdrawn.remove(u)) {
            available.add(u);
            result = true;
        }
        xUnlock();
        return result;
    }

    /**
     * List all available products left from available queue
     *
     * WILL NEED READ LOCK
     *
     * @return
     */
    public Set<String> getAvailableItems() {
        sLock();
        Set<String> s;
        s = available.stream().map(x -> x.productName).collect(Collectors.toSet());
        sUnlock();
        return s;
    }

    /**
     * WILL NEED WRITE LOCK
     *
     * @param x
     */
    public void addAvailableProduct(Item x) {
        xLock();
        available.add(x);
        xUnlock();
    }

    /**
     * WILL NEED WRITE LOCK
     *
     * @param aDouble
     * @param toIterate
     * @param currentlyPurchasable
     * @param currentlyUnavailable
     * @return
     */
    public double updatePurchase(Double aDouble,
                                 List<Item> toIterate,
                                 List<Item> currentlyPurchasable,
                                 List<Item> currentlyUnavailable) {
        double total_cost = 0.0;
        xLock();
        for (var x : toIterate) {
            if (withdrawn.contains(x)) {
                currentlyPurchasable.add(x);
                total_cost += aDouble;
            } else {
                currentlyUnavailable.add(x);
            }
        }
        xUnlock();
        return total_cost;
    }

    /**
     * WILL NEED WRITE LOCK
     *
     * @param toIterate
     */
    public void makeAvailable(List<Item> toIterate) {
        xLock();
        for (var x : toIterate) {
            if (withdrawn.remove(x)) {
                available.add(x);
            }
        }
        xUnlock();
    }

    /**
     * WILL NEED WRITE LOCK
     *
     * @param toIterate
     * @return
     */
    public boolean completelyRemove(List<Item> toIterate) {
        boolean allEmpty;
        xLock();
        for (var x : toIterate) {
            withdrawn.remove(x);
            available.remove(x);
        }
        allEmpty = withdrawn.isEmpty() && available.isEmpty();
        xUnlock();
        return allEmpty;
    }
}
