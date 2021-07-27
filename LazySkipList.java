import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Random;

public final class LazySkipList {
    static final int MAX_LEVEL = 32;
    static final double Prob = 0.5;
    final Node head = new Node(Integer.MIN_VALUE); // set head to the smallest integer
    final Node tail = new Node(Integer.MAX_VALUE); // set tail to the biggest integer
    
    public LazySkipList() {
        for (int i = 0; i < head.next.length; i++) {
            head.next[i] = tail; // initialize all layers with only the head and the tail
        }
    }
    
    // generate a random level number that provides a balancing property
    // Top levels are chosen so that the expected number of nodes in each level’s list decreases exponentially
    private int randomLevel() {
        int level = 1;
        for (int i = 0; i < MAX_LEVEL; i++) {
            float f = ThreadLocalRandom.current().nextFloat();
            if (f <= Prob) {
                level++;
            }
            else {
                return level;
            }
        }
        return level;
    }
    
    int find(int x, Node[] preds, Node[] succs) {
        int key = x;
        // will return -1 if not found in any layer
        int layerFound = -1;
        // start traversal from the head sentinel
        Node pred = head;
        for (int level = MAX_LEVEL; level >= 0; level--) {
            Node curr = pred.next[level];
            // compare key with curr.key until hit tail, i.e. a MAX_VALUE
            while (key > curr.key) {
                pred = curr;
                curr = pred.next[level];
            }
            if (layerFound == -1 && key == curr.key) {
                layerFound = level;
            }
            preds[level] = pred;
            succs[level] = curr;
        }
        return layerFound;
    }
    
    boolean add(int x) {
        // initialize pred and succ arrays
        Node[] preds = (Node[]) new Node[MAX_LEVEL + 1];
        Node[] succs = (Node[]) new Node[MAX_LEVEL + 1];
        // keep trying until returned
        while (true) {
            int layerFound = find(x, preds, succs);
            // Case 1 - element found in the list
            if (layerFound != -1) {
                // node would be included as the entry in succs on the layer found
                Node nodeFound = succs[layerFound];
                if (nodeFound.marked != true) {
                    while (nodeFound.fullyLinked != true) {
                        // wait until it's phisically linked to the skiplist
                    }
                    // unsuccessful add
                    return false;
                }
                continue;
            }
            
            // Case 2 - not found in the list
            int highestLocked = -1;
            int topLevel = randomLevel();
            try {
                Node pred, succ;
                boolean valid = true;
                // validate and lock every predecessor in the path, bottom-up
                for (int level = 0; valid && (level <= topLevel); level++) {
                    pred = preds[level];
                    succ = succs[level];
                    pred.lock.lock();
                    highestLocked = level;
                    valid = !pred.marked && !succ.marked && pred.next[level] == succ;
                }
                if (!valid) {
                    continue;
                }
                // create a new node with a random top-level
                Node newNode = new Node(x, topLevel);
                // Question: why using two seperate iterations?
                for (int level = 0; level <= topLevel; level++) {
                    newNode.next[level] = succs[level];
                }
                for (int level = 0; level <= topLevel; level++) {
                    preds[level].next[level] = newNode;
                }
                // successful add
                newNode.fullyLinked = true;
                return true;
            } finally {
                // release locks
                for (int level = 0; level <= highestLocked; level++) {
                    preds[level].lock.unlock();
                }
            }
        }
    }
    
    boolean remove(int x) {
        Node victim = null;
        boolean isMarked = false;
        int topLevel = -1;
        // initialize pred and succ arrays
        Node[] preds = (Node[]) new Node[MAX_LEVEL + 1];
        Node[] succs = (Node[]) new Node[MAX_LEVEL + 1];
        while (true) {
            // use find to check if node is in list
            int layerFound = find(x, preds, succs);
            if (layerFound != -1) {
                victim = succs[layerFound];
            }
            if (isMarked ||
                (layerFound != -1 && // found in list
                 victim.fullyLinked && // fully linked
                 victim.topLevel == layerFound && // at its top level
                 !victim.marked) // not marked already
               ) {
                // ready to delete
                if (!isMarked) { // not marked yet
                    // Logical delete
                    topLevel = victim.topLevel;
                    victim.lock.lock();
                    // validate after lock that it's still not marked
                    if (victim.marked) {
                        victim.lock.unlock();
                        // already deleted, unsuccessful delete
                        return false;
                    }
                    // successful delete
                    victim.marked = true;
                    isMarked = true;
                }
                int highestLocked = -1;
                // Physical delete
                try {
                    Node pred;
                    boolean valid = true;
                    // validate and lock every predecessor in the path, bottom-up
                    for (int level = 0; valid && (level <= topLevel); level++) {
                        pred = preds[level];
                        pred.lock.lock();
                        highestLocked = level;
                        valid = !pred.marked && pred.next[level] == victim;
                    }
                    if (!valid) {
                        continue;
                    }
                    // physically remove link to victim, top-down
                    for (int level = topLevel; level >= 0; level--) {
                        preds[level].next[level] = victim.next[level];
                    }
                    victim.lock.unlock();
                    // successful physical delete
                    return true;
                } finally {
                    // release locks
                    for (int level = 0; level <= highestLocked; level++) {
                        preds[level].unlock();
                    }
                }
            } else {
                // find did not find a matching node, or not valid (see above conditions)
                // unsuccessful delete
                return false;
            }
        }
    }
    
    boolean contains(int x) {
        Node[] preds = (Node[]) new Node[MAX_LEVEL + 1];
        Node[] succs = (Node[]) new Node[MAX_LEVEL + 1];
        int layerFound = find(x, preds, succs);
        // only return true if found in list, fully linked and not marked
        return layerFound != -1 && succs[layerFound].fullyLinked && !succs[layerFound].marked;
    }
    
    private static final class Node {
        final Lock lock = new ReentrantLock();
        final int item;
        final int key;
        final Node[] next;
        // only a unmarked, fullyLinked node is considered a part of the list
        volatile boolean marked = false;
        volatile boolean fullyLinked = false;
        private int topLevel;
        
        // sentinel node constructor (head and tail)
        public Node(int key) { 
            this.item = key;
            this.key = key;
            this.next = new Node[MAX_LEVEL + 1];
            this.topLevel = MAX_LEVEL;
        }
        
        public Node(int x, int height) {
            this.item = x;
            this.key = x;
            this.next = new Node[height + 1];
            this.topLevel = height;
        }
        
        public void lock() {
            lock.lock();
        }
        
        public void unlock() {
            lock.unlock();
        }
    }
        
    public static void main(String [] args) throws InterruptedException {        
        LazySkipList list = new LazySkipList();
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        long start, end;  
        
        int n = 20000;
        int num_threads = 200;        
              
        AddThread[] add_threads = new AddThread[num_threads];
        ContainsThread[] contains_threads = new ContainsThread[num_threads];
        RemoveThread[] remove_threads = new RemoveThread[num_threads];
        
        int[][] number_lists = new int[num_threads][n];
        
        for (int i = 0; i < num_threads; i++) {
            for (int j = 0; j < n; j++) {
                number_lists[i][j] = rand.nextInt(0,n);
            }
        }
        
        // test add
        start = System.currentTimeMillis();
        for (int i = 0; i < num_threads; i++) {
            add_threads[i] = new AddThread(list, number_lists[i]);
        }
        for (int i = 0; i < num_threads; i++) {
            add_threads[i].start();
        }
        try {
            for (int i = 0; i < num_threads; i++) {
                add_threads[i].join();
            }
        } catch (Exception e) {}
        end = System.currentTimeMillis();
        System.out.println("Java concurrent add() " + (n * num_threads) + " nodes with " + num_threads + " threads, time: " + (end - start) / 1000.0 + " s");
    
        // test contains
        start = System.currentTimeMillis();
        for (int i = 0; i < num_threads; i++) {
            contains_threads[i] = new ContainsThread(list, number_lists[i]);
        }
        for (int i = 0; i < num_threads; i++) {
            contains_threads[i].start();
        }
        try {
            for (int i = 0; i < num_threads; i++) {
                contains_threads[i].join();
            }
        } catch (Exception e) {}
        end = System.currentTimeMillis();
        System.out.println("Java concurrent contains() " + (n * num_threads) + " nodes with " + num_threads + " threads, time: " + (end - start) / 1000.0 + " s");
    
        // test remove
        start = System.currentTimeMillis();
        for (int i = 0; i < num_threads; i++) {
            remove_threads[i] = new RemoveThread(list, number_lists[i]);
        }
        for (int i = 0; i < num_threads; i++) {
            remove_threads[i].start();
        }
        try {
            for (int i = 0; i < num_threads; i++) {
                remove_threads[i].join();
            }
        } catch (Exception e) {}
        end = System.currentTimeMillis();
        System.out.println("Java concurrent remove() " + (n * num_threads) + " nodes with " + num_threads + " threads, time: " + (end - start) / 1000.0 + " s");
    }
}

class AddThread extends Thread {
    LazySkipList list;
    int[] nodes;
    public AddThread(LazySkipList list, int[] nodes) {
        this.list = list;
        this.nodes = nodes;
    }
    public void run() {
        for (int i = 0; i < nodes.length; i++) {
            list.add(nodes[i]);
        }    
    }
}

class ContainsThread extends Thread {
    LazySkipList list;
    int[] nodes;
    public ContainsThread(LazySkipList list, int[] nodes) {
        this.list = list;
        this.nodes = nodes;
    }
    public void run() {
        for (int i = 0; i < nodes.length; i++) {
            list.contains(nodes[i]);
        }    
    }
}

class RemoveThread extends Thread {
    LazySkipList list;
    int[] nodes;
    public RemoveThread(LazySkipList list, int[] nodes) {
        this.list = list;
        this.nodes = nodes;
    }
    public void run() {
        for (int i = 0; i < nodes.length; i++) {
            list.remove(nodes[i]);
        }    
    }
}
