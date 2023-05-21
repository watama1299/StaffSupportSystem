package uk.ncl.CSC8016.jackbergus.coursework.project2.processes;

import uk.ncl.CSC8016.jackbergus.coursework.project2.utils.BasketResult;
import uk.ncl.CSC8016.jackbergus.coursework.project2.utils.Item;
import uk.ncl.CSC8016.jackbergus.coursework.project2.utils.MyUUID;
import uk.ncl.CSC8016.jackbergus.slides.semaphores.scheduler.Pair;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class RainforestShop {

    /// For correctly implementing the server, pelase consider that

    /**
     * Boolean to keep track of pessimistic or optimistic transaction
     */
    private final boolean isGlobalLock;

    /**
     * Boolean to keep track of whether supplier is running or has stopped
     */
    private boolean supplierStopped;

    /**
     * A set of String to keep track of clients that are allowed to perform transaction
     */
    private Set<String> allowed_clients;

    /**
     * Maps the user unique IDs (UUID object) with their names (String object)
     */
    public HashMap<UUID, String> UUID_to_user;

    /**
     * Maps the product name (String object) with its ProductMonitor counterpart
     */
    private volatile HashMap<String, ProductMonitor> available_withdrawn_products;

    /**
     * Maps the product name (String object) with its item price (Double object)
     */
    private HashMap<String, Double> productWithCost = new HashMap<>();

    /**
     * Queue that keeps track of product names that has been purchased and needs to be restocked
     */
    private volatile Queue<String> currentEmptyItem;

    /**
     * Reentrant lock for volatile queue currentEmptyItem
     */
    ReentrantLock currEmptyItemLock;

    /**
     * Condition used
     */
    Condition notEmpty;


    public boolean isGlobalLock() {
        return isGlobalLock;
    }

    /**
     * Please replace this string with your student ID, so to ease the marking process
     * @return  Your student id!
     */
    public String studentId() {
        return "220480721";
    }


    /**
     *
     * @param client_ids                Collection of registered client names that can set up the communication
     * @param available_products        Map associating each product name to its cost and the initial number of available items on the shop
     * @param isGlobalLock              Might be used (but not strictly required) To remark whether your solution uses a
     *                                  pessimistic transaction (isGlobalLock=true) or an optimistic opne (isGlobalLock=false)
     */
    public RainforestShop(Collection<String> client_ids,
                          Map<String, Pair<Double, Integer>> available_products,
                          boolean isGlobalLock) {
        // initialise supplier as not running
        supplierStopped = true;

        // initialise queue of Items as LinkedBlockingQueue -> concurrent Java datastructure
        currentEmptyItem = new LinkedBlockingQueue<>();

        // lock for currentEmptyItem
        currEmptyItemLock = new ReentrantLock(true);

        // condition for supplier to trigger
        notEmpty = currEmptyItemLock.newCondition();

        // setting globallock for pessimistic or optimistic transaction
        this.isGlobalLock = isGlobalLock;

        // initialise client list as hashset
        allowed_clients = new HashSet<>();

        // populating client list using the input parameter
        if (client_ids != null) allowed_clients.addAll(client_ids);

        // initialise product list as hashmap
        this.available_withdrawn_products = new HashMap<>();

        // initialise user id map as hashmap
        UUID_to_user = new HashMap<>();

        // populating product list using the input parameter
        if (available_products != null) for (var x : available_products.entrySet()) {
            // going through the input hashmap parameter
            if (x.getKey().equals("@stop!")) continue;

            // put product list with its associated cost in hashmap
            productWithCost.put(x.getKey(), x.getValue().key);

            // create new productmonitor for each new different type of products from input
            var p = new ProductMonitor();
            for (int i = 0; i<x.getValue().value; i++) {
                // MyUUID is used to keep track of the number of products left
                p.addAvailableProduct(new Item(x.getKey(), x.getValue().key, MyUUID.next()));
            }
            // putting products into the hashmap; key: product name, value: product monitor
            this.available_withdrawn_products.put(x.getKey(), p);
        }
    }


    /**
     * Performing an user log-in. To generate a transaction ID, please use the customary Java method
     * 
     * UUID uuid = UUID.randomUUID();
     * 
     * @param username      Username that wants to login
     *
     * @return A non-empty transaction if the user is logged in for the first time, and he hasn't other instances of itself running at the same time
     *         In all the other cases, thus including the ones where the user is not registered, this returns an empty transaction
     *
     */
    public Optional<Transaction> login(String username) {
        Optional<Transaction> result = Optional.empty();

        // Before logging in, ensures that the username input is a valid and existing user in the
        // rainforest shop hashmap
        if (allowed_clients.contains(username)) {
            // Generate a random userID upon logging in. It will help with the same user
            // logging in using 2 different transaction instances
            UUID uuid = UUID.randomUUID();
            UUID_to_user.put(uuid, username);
            result = Optional.of(new Transaction(this, username, uuid));
        }
        return result;
    }


    /**
     * This method should be accessible only to the transaction and not to the public!
     * Logs out the client iff. there was a transaction that was started with a given UUID and that was associated to
     * a given user
     *
     * @param transaction
     * @return false if the transaction is null or whether that was not created by the system
     */
    boolean logout(Transaction transaction) {
        boolean result = false;

        // null input check
        if (transaction.getUsername() == null ||
                transaction.getUuid() == null ||
                transaction.getSelf() == null) return result;

        // check if transaction UUID is listed in the rainforest shop
        if (!UUID_to_user.containsKey(transaction.getUuid())) return result;

        String user = transaction.getUsername();

        // check if user has any item in basket
        if (transaction.getUnmutableBasket().isEmpty()) {
            // if user has an empty basket, the system can immediately log the user out
            transaction.invalidateTransaction();
            result = true;
        }

        // re-shelf all items in basket if the user logs out without purchasing
        List<Item> returnedItems = transaction.getUnmutableBasket();
        // loop through each item in the list
        for (Item toReturn : returnedItems) {
            // get name of item
            String name = toReturn.productName;
            // get access to the ProductMonitor for each item through available_withdrawn_products
            ProductMonitor pm = available_withdrawn_products.get(name);
            // re-shelf the product/item
            System.out.println("Returning [" + toReturn.productName + ", ID: " + toReturn.id + "] to shelf");
            pm.doShelf(toReturn);
        }

        // close transaction once all items have been returned
        transaction.invalidateTransaction();
        System.out.println(user + " successfully logged out!");

        return result;
    }


    /**
     * Lists all of the items that were not basketed and that are still on the shelf
     *
     * @param transaction
     * @return
     */
    List<String> getAvailableItems(Transaction transaction) {
        List<String> ls = Collections.emptyList();

        // redefine ls because Collections.emptyList() gives an immutable (unchangeable) list
        ls = new ArrayList<>();
        if (!this.equals(transaction.getSelf())) return ls;
        //System.out.println("a");

        // check ProductMonitor for each item to see if !available.isEmpty()
        for (Map.Entry<String, ProductMonitor> item : transaction.getSelf().available_withdrawn_products.entrySet()) {
            //if (!item.getValue().available.isEmpty()) ls.add(item.getKey());
            ls.addAll(item.getValue().getAvailableItems());
        }

        return ls;
    }


    /**
     * If a product can be basketed from the shelf, then a specific instance of the product on the shelf is returned
     *
     * @param transaction   User reference
     * @param name          Product name picked from the shelf
     * @return  Whether the item to be basketed is available or not
     */
    Optional<Item> basketProductByName(Transaction transaction, String name) {
        AtomicReference<Optional<Item>> result = new AtomicReference<>(Optional.empty());
        if (transaction.getSelf() == null || (transaction.getUuid() == null)) return result.get();

        // check to see if name exists in available_withdrawn_products.keySet()
        if (!transaction.getSelf().available_withdrawn_products.containsKey(name))
            return result.get();

        // access ProductMonitor of the product and perform getAvailableItem()
        ProductMonitor pm = transaction.getSelf().available_withdrawn_products.get(name);
        result.set(pm.getAvailableItem());

        return result.get();
    }


    /**
     * If the current transaction has withdrawn one of the objects from the shelf and put it inside its basket,
     * then the transaction shall be also able to replace the object back where it was (on its shelf)
     *
     * @param transaction   Transaction that basketed the object
     * @param object        Object to be reshelved
     * @return  Returns true if the object existed before and if that was basketed by the current thread, returns false otherwise
     */
    boolean shelfProduct(Transaction transaction, Item object) {
        boolean result = false;
        if (transaction.getSelf() == null || (transaction.getUuid() == null)) return false;

        // check to see if object exists in current transaction basket
        if (!transaction.getUnmutableBasket().contains(object)) return result;

        // check to see if object string name exists in available_withdrawn_products.keySet()
        if (!transaction.getSelf().available_withdrawn_products.containsKey(object.productName))
            return result;

        // use doShelf to return object back. get the ProductMonitor from transaction.getSelf().
        String objectName = object.productName;
        ProductMonitor pm = transaction.getSelf().available_withdrawn_products.get(objectName);
        result = pm.doShelf(object);

        return result;
    }


    /**
     * Stops the food supplier by sending a specific message. Please observe that no product shall be named @stop!
     */
    public void stopSupplier() {
        //System.out.println("Attempting to stop supplier...");

        // acquire lock to access queue
        // ensures no other thread will access the queue whilst current thread is accessing it
        currEmptyItemLock.lock();

        // critical section
        try {
            currentEmptyItem.add("@stop!");
            System.out.println("@stop! triggered");

            // Once the @stop! flag has been added to the queue, send a signal to
            // the supplier thread to wake up from waiting and continue its process.
            // This is especially useful when there is no items to be refurbished but
            // the supplier thread needs to be ended regardless.
            notEmpty.signal();
        } finally {
            // release lock to allow others access to queue
            currEmptyItemLock.unlock();
        }
    }

    /**
     * The supplier acknowledges that it was stopped, and updates its internal state.
     * The monitor also receives confirmation
     *
     * @param stopped   Boolean variable from the supplier
     */
    public void supplierStopped(AtomicBoolean stopped) {
        System.out.println("Stopping supplier thread");

        // acquire lock to access the queue
        // ensures no other thread will access the queue whilst current thread is accessing it
        currEmptyItemLock.lock();

        // critical section
        try {
            supplierStopped = true;
            stopped.set(true);
        } finally {
            // release lock to allow others access to queue
            currEmptyItemLock.unlock();
        }
        System.out.println("Supplier thread has been stopped");
    }

    /**
     * The supplier invokes this method when it needs to know that a new product shall be made ready available.
     * <p>
     * This method should be blocking (if currentEmptyItem is empty, then this should wait until currentEmptyItem
     * contains at least one element and, in that occasion, then returns the first element being available)
     * <p>
     * TODO: Comment/Explanation
     * <p>
     * For the blocking implementation, the await signal pair has been used. When the Supplier thread enters
     * the while loop to check if the queue is empty or not, while it's empty it the await condition will be
     * called which will let the thread wait until a signal is called. Doing this will also implicitly release
     * the lock that the Supplier thread had while accessing the queue so that the Client thread can obtain
     * the lock to be able to add the items to be refurbished to the queue as and when necessary. Releasing
     * the lock will also allow the RainforestShop to add a @stop! to the queue.
     * <p>
     * Once the queue is filled by either the basketCheckout or stopSupplier methods, a signal will be called
     * for the condition allowing the Supplier thread to run and continue its process. It will break out of
     * its while loop and return the String object that was obtained from the queue.
     *
     * @return      the name of the missing item that will need to be refurbished
     */
    public String getNextMissingItem() {
        // initialise output String object
        String out;

        // acquire lock to access the queue
        // ensures no other thread will access the queue whilst current thread is accessing it
        currEmptyItemLock.lock();
        try {
            supplierStopped = false;

            while (currentEmptyItem.isEmpty()) {
                System.out.println("\ncurrentEmptyItem queue is currently empty");
//                // Implementation below uses sleep instead of awake and signal pair
//                try {
//                    // unlock lock to let access to write into the queue
//                    currEmptyItemLock.unlock();
//
//                    // make supplier thread sleep for a very brief moment
//                    System.out.println("Make supplier thread sleep");
//                    Thread.sleep(1);
//                    System.out.println("Supplier thread awakes");
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    // once awake, supplier thread acquires lock again
//                    currEmptyItemLock.lock();
//                }

                notEmpty.await();
                System.out.println("Found item to refurbish");
            }

            // If the next String on the queue is an item, then display the item to be refurbished
            if (currentEmptyItem.peek() != "@stop!" && currentEmptyItem.peek() != null)
                System.out.println("Item to be refurbished: [" + currentEmptyItem.peek() + "]");

            // get value from the queue
            out = currentEmptyItem.remove();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // release lock once supplier thread has gotten the item to refurbish
            currEmptyItemLock.unlock();
        }

        return out;
    }


    /**
     * This method is invoked by the Supplier to refurbish the shop of n products of a given time (current item)
     *
     * @param n                 Number of elements to be placed
     * @param currentItem       Type of elements to be placed
     */
    public void refurbishWithItems(int n, String currentItem) {
        // Note: this part of the implementation is completely correct!
        System.out.println("Refurbishing [" + currentItem + "] amount: [" + n + "]");
        Double cost = productWithCost.get(currentItem);
        if (cost == null) return;
        for (int i = 0; i<n; i++) {
            available_withdrawn_products.get(currentItem).addAvailableProduct(new Item(currentItem, cost, MyUUID.next()));
        }
        System.out.println("Refurbishing successful!");
    }

    /**
     * This operation purchases all the elements available on the basket.
     * <p>
     * A small code snippet has been added at the end of the checkout algorithm. The condition signal has been
     * added to send a signal to the waiting Supplier thread that an item has been added to the queue that
     * needs to be refurbished.
     *
     * @param transaction               Transaction containing the current withdrawn elements from the shelf (and therefore basketed)
     * @param total_available_money     How much money can the client spend at maximum
     * @return
     */
    public BasketResult basketCheckout(Transaction transaction, double total_available_money) {
        // Note: this part of the implementation is completely correct!

        // initialise result of checkout
        BasketResult result = null;

        // make sure user in transaction is an existing user in the rainforestshop user base
        if (UUID_to_user.getOrDefault(transaction.getUuid(), "").equals(transaction.getUsername())) {

            // get list of items from user's basket
            var b = transaction.getUnmutableBasket();

            // initialise some values
            double total_cost = (0.0);
            List<Item> currentlyPurchasable = new ArrayList<>();
            List<Item> currentlyUnavailable = new ArrayList<>();

            // loop through the user's list of items
            // groups products of same name together via map -> key: product name, value: list of items with same name
            // sum up the total cost of all the items in the user's basket
            // while populating the arrays of items currentlypurchasable and currentunavailable
            for (Map.Entry<String, List<Item>> entry : b.stream().collect(Collectors.groupingBy(x -> x.productName)).entrySet()) {
                String k = entry.getKey();
                List<Item> v = entry.getValue();
                total_cost += available_withdrawn_products.get(k).updatePurchase(productWithCost.get(k), v, currentlyPurchasable, currentlyUnavailable);
            }

            // if the user doesn't have enough money to buy all the items:
            // 1) make all the items in the user's basket become available again for all users
            // 2) clear out the temporary arraylists
            if ((total_cost > total_available_money)) {
                for (Map.Entry<String, List<Item>> entry : b.stream().collect(Collectors.groupingBy(x -> x.productName)).entrySet()) {
                    String k = entry.getKey();
                    List<Item> v = entry.getValue();
                    available_withdrawn_products.get(k).makeAvailable(v);
                }
                currentlyUnavailable.clear();
                currentlyPurchasable.clear();
                total_cost = (0.0);
            }

            // if the user has enough money to buy all the items:
            // 1) complete the purchase and remove the items
            // 2) if a product has been completely purchased off the shelf, put product name to Set s
            // 3) put all product names in s into currentEmptyItem
            else
            {
                Set<String> s = new HashSet<>();
                for (Map.Entry<String, List<Item>> entry : b.stream().collect(Collectors.groupingBy(x -> x.productName)).entrySet()) {
                    String k = entry.getKey();
                    List<Item> v = entry.getValue();
                    if (available_withdrawn_products.get(k).completelyRemove(v))
                        s.add(k);
                }

                // acquire lock to be able to access the queue
                currEmptyItemLock.lock();
                try {
                    // critical section
                    currentEmptyItem.addAll(s);

                    // send signal to the condition to awaken the Supplier thread waiting
                    notEmpty.signal();
                } finally {
                    // release lock once process is done
                    currEmptyItemLock.unlock();
                }
            }

            // saves the summary of the checkout into result
            result = new BasketResult(currentlyPurchasable, currentlyUnavailable, total_available_money, total_cost, total_available_money - total_cost);
        }

        return result;
    }
}
