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
        if (allowed_clients.contains(username)) {
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
     * WILL NEED WRITE LOCK
     *
     * @param transaction
     * @return false if the transaction is null or whether that was not created by the system
     */
    boolean logout(Transaction transaction) {
        boolean result = false;
        // TODO: Implement the remaining part!

        // null input check
        if (transaction.getUsername() == null ||
                transaction.getUuid() == null ||
                transaction.getSelf() == null) return result;

        // check if transaction UUID exists
        if (!UUID_to_user.containsKey(transaction.getUuid())) return result;

        // check if user has any item in basket
        if (transaction.getUnmutableBasket().isEmpty()) {
            // if user has an empty basket, they can immediately log out
            transaction.invalidateTransaction();
            result = true;
        }

        // return all items in baskets if user logout without purchasing
        List<Item> returnedItems = transaction.getUnmutableBasket();
        // loop through each item in the list
        for (Item toReturn : returnedItems) {
            // get name of item
            String name = toReturn.productName;
            // get access to the ProductMonitor for each item through available_withdrawn_products
            ProductMonitor pm = available_withdrawn_products.get(name);
            pm.doShelf(toReturn);
        }

        // close transaction once all items have been returned
        transaction.invalidateTransaction();

        return result;
    }

    /**
     * Lists all of the items that were not basketed and that are still on the shelf
     *
     * WILL NEED READ LOCK
     *
     * @param transaction
     * @return
     */
    List<String> getAvailableItems(Transaction transaction) {
        List<String> ls = Collections.emptyList();
        // TODO: Implement the remaining part!

        // redefine ls because Collections.emptyList() gives an immutable (unchangeable) list
        ls = new ArrayList<>();
        if (!this.equals(transaction.getSelf())) return ls;
        //System.out.println("a");

        // check ProductMonitor for each item to see if !available.isEmpty()
        for (Map.Entry<String, ProductMonitor> item : transaction.getSelf().available_withdrawn_products.entrySet()) {
            //System.out.println("b");
            //if (!item.getValue().available.isEmpty()) ls.add(item.getKey());
            ls.addAll(item.getValue().getAvailableItems());
            //System.out.println("d");
        }

        return ls;
    }

    /**
     * If a product can be basketed from the shelf, then a specific instance of the product on the shelf is returned
     *
     * WILL NEED WRITE LOCK
     *
     * @param transaction   User reference
     * @param name          Product name picked from the shelf
     * @return  Whether the item to be basketed is available or not
     */
    Optional<Item> basketProductByName(Transaction transaction, String name) {
        AtomicReference<Optional<Item>> result = new AtomicReference<>(Optional.empty());
        if (transaction.getSelf() == null || (transaction.getUuid() == null)) return result.get();
        // TODO: Implement the remaining part!

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
     * WILL NEED WRITE LOCK
     *
     * @param transaction   Transaction that basketed the object
     * @param object        Object to be reshelved
     * @return  Returns true if the object existed before and if that was basketed by the current thread, returns false otherwise
     */
    boolean shelfProduct(Transaction transaction, Item object) {
        boolean result = false;
        if (transaction.getSelf() == null || (transaction.getUuid() == null)) return false;
        // TODO: Implement the remaining part!

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
        // TODO: Provide a correct concurrent implementation!
        currEmptyItemLock.lock();
        try {
            currentEmptyItem.add("@stop!");
            System.out.println("sent");
        } finally {
            currEmptyItemLock.unlock();
        }
    }

    /**
     * The supplier acknowledges that it was stopped, and updates its internal state.
     * The monitor also receives confirmation
     * @param stopped   Boolean variable from the supplier
     */
    public void supplierStopped(AtomicBoolean stopped) {
        // TODO: Provide a correct concurrent implementation!
        currEmptyItemLock.lock();
        try {
            supplierStopped = true;
            stopped.set(true);
        } finally {
            currEmptyItemLock.unlock();
        }
    }

    /**
     * The supplier invokes this method when it needs to know that a new product shall be made ready available.
     *
     * This method should be blocking (if currentEmptyItem is empty, then this should wait until currentEmptyItem
     * contains at least one element and, in that occasion, then returns the first element being available)
     * @return
     */
    public String getNextMissingItem() {
        // TODO: Provide a correct concurrent implementation!
        String out;
        currEmptyItemLock.lock();
        try {
            supplierStopped = false;
            while (currentEmptyItem.isEmpty()) {
                //System.out.println("empty");
            }
            out = currentEmptyItem.remove();
            //if (currentEmptyItem.size() == 1) stopSupplier();
        } finally {
            currEmptyItemLock.unlock();
        }
        return out;
        //return currentEmptyItem.remove();
    }


    /**
     * This method is invoked by the Supplier to refurbrish the shop of n products of a given time (current item)
     * @param n                 Number of elements to be placed
     * @param currentItem       Type of elements to be placed
     */
    public void refurbishWithItems(int n, String currentItem) {
        // Note: this part of the implementation is completely correct!
        Double cost = productWithCost.get(currentItem);
        if (cost == null) return;
        for (int i = 0; i<n; i++) {
            available_withdrawn_products.get(currentItem).addAvailableProduct(new Item(currentItem, cost, MyUUID.next()));
        }
    }

    /**
     * This operation purchases all the elements available on the basket
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

            // if the user doesn't have enough money to buy all the items
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

            // if the user has enough money to buy all the items
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
                currentEmptyItem.addAll(s);
            }

            // saves the summary of the checkout into result
            result = new BasketResult(currentlyPurchasable, currentlyUnavailable, total_available_money, total_cost, total_available_money - total_cost);
        }

        return result;
    }
}
