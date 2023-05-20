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

    /**
     * Monitor implementation using ReentrantLock
     */
    ReentrantLock monitor;

    /**
     * Condition to keep track of the ability for threads to read into the queues
     */
    Condition readOk;

    /**
     * Condition to keep track of the ability for threads to write into the queues
     */
    Condition writeOk;

    /**
     * Counter to keep track of the number of readers accessing the queues
     */
    private int numReader;

    /**
     * Counter to keep track of the number of writers accessing the queues
     */
    private int numWriter;

    /**
     * Counter to keep track of the number of writers waiting for access to the queues
     */
    private int waitingWriter;



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
        // acquire exclusive lock to access and write to the queue
        xLock();

        // critical section
        for (Item x : cls) {
            if (withdrawn.remove(x))
                available.add(x);
        }

        // release exclusive lock to give others access to both queue
        xUnlock();
    }

    /**
     * Put item from shelf to basket
     *
     * @return
     */
    public Optional<Item> getAvailableItem() {
        Optional<Item> o = Optional.empty();

        // acquire exclusive lock to access and write to the queue
        xLock();

        // critical section
        if (!available.isEmpty()) {
            var obj = available.remove();
            if (obj != null) {
                o = Optional.of(obj);
                withdrawn.add(o.get());
            }
        }

        // release exclusive lock to give others access to both queue
        xUnlock();
        return o;
    }

    /**
     * Put item from basket to shelf
     *
     * @param u
     * @return
     */
    public boolean doShelf(Item u) {
        boolean result = false;

        // acquire exclusive lock to access and write to the queue
        xLock();

        // critical section
        if (withdrawn.remove(u)) {
            available.add(u);
            result = true;
        }

        // release exclusive lock to give others access to both queue
        xUnlock();
        return result;
    }

    /**
     * List all available products left from available queue
     *
     * @return
     */
    public Set<String> getAvailableItems() {
        // acquire shared lock to access and read to the queue
        sLock();

        // critical section
        Set<String> s;
        s = available.stream().map(x -> x.productName).collect(Collectors.toSet());

        // release shared lock once done with reading
        sUnlock();
        return s;
    }

    /**
     * WILL NEED WRITE LOCK
     *
     * @param x
     */
    public void addAvailableProduct(Item x) {
        // acquire exclusive lock to access and write to the queue
        xLock();

        // critical section
        available.add(x);

        // release exclusive lock to give others access to both queue
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

        // acquire exclusive lock to access and write to the queue
        xLock();

        // critical section
        for (var x : toIterate) {
            if (withdrawn.contains(x)) {
                currentlyPurchasable.add(x);
                total_cost += aDouble;
            } else {
                currentlyUnavailable.add(x);
            }
        }

        // release exclusive lock to give others access to both queue
        xUnlock();
        return total_cost;
    }

    /**
     * WILL NEED WRITE LOCK
     *
     * @param toIterate
     */
    public void makeAvailable(List<Item> toIterate) {
        // acquire exclusive lock to access and write to the queue
        xLock();

        // critical section
        for (var x : toIterate) {
            if (withdrawn.remove(x)) {
                available.add(x);
            }
        }

        // release exclusive lock to give others access to both queue
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

        // acquire exclusive lock to access and write to the queue
        xLock();

        // critical section
        for (var x : toIterate) {
            withdrawn.remove(x);
            available.remove(x);
        }
        allEmpty = withdrawn.isEmpty() && available.isEmpty();

        // release exclusive lock to give others access to both queue
        xUnlock();
        return allEmpty;
    }
}
