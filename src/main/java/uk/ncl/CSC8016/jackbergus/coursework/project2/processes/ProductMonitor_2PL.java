package uk.ncl.CSC8016.jackbergus.coursework.project2.processes;

import uk.ncl.CSC8016.jackbergus.coursework.project2.utils.Item;

import java.util.*;
import java.util.concurrent.locks.*;
import java.util.stream.Collectors;


/**
 * Keeps track of item based on the name. Items of the same name differentiated by unique UUID.
 * Each item with unique UUID is a unique entry inside available and withdrawn.
 */
public class ProductMonitor_2PL {
    Queue<Item> available;
    Queue<Item> withdrawn;

    /**
     * IMPLEMENT A LOCKING SYSTEM HERE
     */
    ReadWriteLock availableLock, withdrawnLock;
    Lock readAL, writeAL, readWL, writeWL;

    /**
     * MONITOR IMPLEMENTATION
     */
    private int numReader, numWriter, waitingWriter;
    ReentrantLock monitor;
    Condition okToRead, okToWrite;



    public ProductMonitor_2PL() {
        available = new LinkedList<>();
        withdrawn = new LinkedList<>();

        // locks for available
        availableLock = new ReentrantReadWriteLock(true);
        readAL = availableLock.readLock();
        writeAL = availableLock.writeLock();
        // need to implement conditions for the writelocks

        // locks for withdrawn
        withdrawnLock = new ReentrantReadWriteLock(true);
        readWL = withdrawnLock.readLock();
        writeWL = withdrawnLock.writeLock();
        // need to implement conditions for the writelocks

        // monitor locking implementation
        monitor = new ReentrantLock(true);
        okToRead = monitor.newCondition();
        okToWrite = monitor.newCondition();
        waitingWriter = 0;
    }

    /**
     * WILL NEED WRITE LOCK
     *
     * @param cls
     */
    public void removeItemsFromUnavailability(Collection<Item> cls) {
        // acquire writelock for withdrawn
        writeWL.lock();
        try {
            for (Item x : cls) {
                if (withdrawn.remove(x)) {
                    // acquire writelock for available
                    writeAL.lock();
                    try {
                        available.add(x);
                    } finally {
                        // unlock writelock for available
                        writeAL.unlock();
                    }
                }
            }
        } finally {
            // unlock writelock for withdrawn
            writeWL.unlock();
        }
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
        // acquire writelock to modify available in the future
        writeAL.lock();
        try {
            if (!available.isEmpty()) {
                var obj = available.remove();
                if (obj != null) {
                    o = Optional.of(obj);
                    // acquire writelock for withdrawn
                    writeWL.lock();
                    try {
                        withdrawn.add(o.get());
                    } finally {
                        // unlock writelock for withdrawn
                        writeWL.unlock();
                    }
                }
            }
        } finally {
            writeAL.unlock();
        }
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
        // acquire writelock for withdrawn
        writeWL.lock();
        try {
            if (withdrawn.remove(u)) {
                // acquire writelock for available
                writeAL.lock();
                try {
                    available.add(u);
                    result = true;
                } finally {
                    // unlock writelock for available
                    writeAL.unlock();
                }
            }
        } finally {
            // unlock writelock for withdrawn
            writeWL.unlock();
        }
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
        Set<String> s;
        readAL.lock();
        try {
            s = available.stream().map(x -> x.productName).collect(Collectors.toSet());
        } finally {
            readAL.unlock();
        }
        return s;
    }

    /**
     * WILL NEED WRITE LOCK
     *
     * @param x
     */
    public void addAvailableProduct(Item x) {
        //
        writeAL.lock();
        try {
            available.add(x);
        } finally {
            writeAL.unlock();
        }
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
        writeWL.lock();
        try {
            for (var x : toIterate) {
                if (withdrawn.contains(x)) {
                    currentlyPurchasable.add(x);
                    total_cost += aDouble;
                } else {
                    currentlyUnavailable.add(x);
                }
            }
        } finally {
            writeWL.unlock();
        }
        return total_cost;
    }

    /**
     * WILL NEED WRITE LOCK
     *
     * @param toIterate
     */
    public void makeAvailable(List<Item> toIterate) {
        // acquire writelock for withdrawn
        writeWL.lock();
        try {
            for (var x : toIterate) {
                if (withdrawn.remove(x)) {
                    // acquire writelock for available
                    writeAL.lock();
                    try {
                        available.add(x);
                    } finally {
                        // unlock writelock for available
                        writeAL.unlock();
                    }
                }
            }
        } finally {
            // unlock writelock for withdrawn
            writeWL.unlock();
        }
    }

    /**
     * WILL NEED WRITE LOCK
     *
     * @param toIterate
     * @return
     */
    public boolean completelyRemove(List<Item> toIterate) {
        boolean allEmpty;
        // acquire writelock for both available and withdrawn
        writeAL.lock(); writeWL.lock();
        try {
            for (var x : toIterate) {
                withdrawn.remove(x);
                available.remove(x);
            }
            allEmpty = withdrawn.isEmpty() && available.isEmpty();
        } finally {
            writeAL.unlock(); writeWL.unlock();
        }
        return allEmpty;
    }
}
