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
     * MONITOR IMPLEMENTATION
     */
    ReentrantLock monitor;
    Condition readOk, writeOk;
    private int numReader, numWriter, waitingWriter;



    public ProductMonitor() {
        available = new LinkedList<>();
        withdrawn = new LinkedList<>();

        // monitor locking implementation
        monitor = new ReentrantLock(true);
        readOk = monitor.newCondition();
        writeOk = monitor.newCondition();
        numReader = numWriter = waitingWriter = 0;
    }

    public void sLock() {
        monitor.lock();

        while (numWriter > 0 || waitingWriter > 0)
            try {
                readOk.await();
            } catch (Exception e) {}

        numReader++;
        readOk.signal();
        monitor.unlock();
    }

    public void sUnlock() {
        monitor.lock();

        numReader--;

        if (numReader == 0)
            writeOk.signal();

        monitor.unlock();
    }

    public void xLock() {
        monitor.lock();

        if (numWriter > 0 || numReader > 0)
            try {
                waitingWriter++;
                writeOk.await();
                waitingWriter--;
            } catch (Exception e) {}

        numWriter++;

        monitor.unlock();
    }

    public void xUnlock() {
        monitor.lock();

        numWriter--;
        readOk.signal();

        if (numReader == 0)
            writeOk.signal();

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
