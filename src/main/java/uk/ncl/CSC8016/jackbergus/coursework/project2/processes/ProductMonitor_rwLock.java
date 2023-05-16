package uk.ncl.CSC8016.jackbergus.coursework.project2.processes;

import uk.ncl.CSC8016.jackbergus.coursework.project2.utils.Item;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;


/**
 * Keeps track of item based on the name. Items of the same name differentiated by unique UUID.
 * Each item with unique UUID is a unique entry inside available and withdrawn.
 */
public class ProductMonitor_rwLock {
    Queue<Item> available;
    Queue<Item> withdrawn;

    /**
     * IMPLEMENT A LOCKING SYSTEM HERE
     */
    ReadWriteLock monitor;
    Lock sLock, xLock;
    private int numReader, numWriter, waitingWriter;



    public ProductMonitor_rwLock() {
        available = new LinkedList<>();
        withdrawn = new LinkedList<>();

        monitor = new ReentrantReadWriteLock(true);
        sLock = monitor.readLock();
        xLock = monitor.writeLock();
    }

    /**
     * WILL NEED WRITE LOCK
     *
     * @param cls
     */
    public void removeItemsFromUnavailability(Collection<Item> cls) {
        xLock.lock();
        try {
            for (Item x : cls) {
                if (withdrawn.remove(x))
                    available.add(x);
            }
        } catch (Exception e) {
            System.out.println(e);
        }  finally {
            xLock.unlock();
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
        xLock.lock();
        Optional<Item> o = Optional.empty();
        try {
            if (!available.isEmpty()) {
                var obj = available.remove();
                if (obj != null) {
                    o = Optional.of(obj);
                    withdrawn.add(o.get());
                }
            }
            return o;
        } catch (Exception e) {
            System.out.println(e);
        }  finally {
            xLock.unlock();
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
        xLock.lock();
        boolean result = false;
        try {
            if (withdrawn.remove(u)) {
                available.add(u);
                result = true;
            }
            return result;
        } catch (Exception e) {
            System.out.println(e);
        }  finally {
            xLock.unlock();
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
        sLock.lock();
        Set<String> s;
        try {
            s = available.stream().map(x -> x.productName).collect(Collectors.toSet());
            return s;
        } catch (Exception e) {
            System.out.println(e);
        }  finally {
            sLock.unlock();
        }
        return null;
    }

    /**
     * WILL NEED WRITE LOCK
     *
     * @param x
     */
    public void addAvailableProduct(Item x) {
        xLock.lock();
        try {
            available.add(x);
        } catch (Exception e) {
            System.out.println(e);
        }  finally {
            xLock.unlock();
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
        xLock.lock();
        try {
            double total_cost = 0.0;
            for (var x : toIterate) {
                if (withdrawn.contains(x)) {
                    currentlyPurchasable.add(x);
                    total_cost += aDouble;
                } else {
                    currentlyUnavailable.add(x);
                }
            }
            return total_cost;
        } catch (Exception e) {
            System.out.println(e);
        }  finally {
            xLock.unlock();
        }
        return 0;
    }

    /**
     * WILL NEED WRITE LOCK
     *
     * @param toIterate
     */
    public void makeAvailable(List<Item> toIterate) {
        xLock.lock();
        try {
            for (var x : toIterate) {
                if (withdrawn.remove(x)) {
                    available.add(x);
                }
            }
        } catch (Exception e) {
            System.out.println(e);
        }  finally {
            xLock.unlock();
        }
    }

    /**
     * WILL NEED WRITE LOCK
     *
     * @param toIterate
     * @return
     */
    public boolean completelyRemove(List<Item> toIterate) {
        xLock.lock();
        try {
            boolean allEmpty;
            for (var x : toIterate) {
                withdrawn.remove(x);
                available.remove(x);
            }
            allEmpty = withdrawn.isEmpty() && available.isEmpty();
            return allEmpty;
        } catch (Exception e) {
            System.out.println(e);
        } finally {
            xLock.unlock();
        }
        return false;
    }
}
