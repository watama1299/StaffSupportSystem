package uk.ncl.CSC8016.jackbergus.coursework.project2.processes;

import java.sql.SQLOutput;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class SupplierLifecycle implements Runnable {

    private final RainforestShop s;

    private volatile boolean hasRetrievedOneProduct;
    private AtomicBoolean stopped;
    private final Random rng;

    public SupplierLifecycle(RainforestShop s) {
        this.s = s;
        this.rng = new Random(0);
        hasRetrievedOneProduct = false;
        stopped = new AtomicBoolean(false);
    }

    public Thread startThread() {
        stopped = new AtomicBoolean(false);
        var t = new Thread(this);
        t.start();
        return t;
    }

    @Override
    public void run() {
        while (true) {
            System.out.println(Thread.currentThread().getName());
            System.out.println(0);
            String product = s.getNextMissingItem();
            System.out.println(1);
            if (product.equals("@stop!")) {
                System.out.println(2);
                s.supplierStopped(stopped);
                System.out.println(3);
                return;
            }
            System.out.println(4);
            hasRetrievedOneProduct = true;
            int howManyItems = this.rng.nextInt(1, 6);
            System.out.println(5);
            s.refurbishWithItems(howManyItems, product);
            System.out.println(6);
        }
    }

    public boolean hasAProductBeenProduced() {
        return hasRetrievedOneProduct;
    }

    public boolean isStopped() {
        return stopped.get();
    }
}
