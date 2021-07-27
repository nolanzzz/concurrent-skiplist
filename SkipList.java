import java.util.Random;

public final class SkipList {
    static final int MAX_LEVEL = 32;
    static final double Prob = 0.5;
    final Node head = new Node(Integer.MIN_VALUE); // set head to the smallest integer
    final Node tail = new Node(Integer.MAX_VALUE); // set tail to the biggest integer
    
    public SkipList() {
        for (int i = 0; i < head.next.length; i++) {
            head.next[i] = tail; // initialize all layers with only the head and the tail
        }
    }
    
    // generate a random level number that provides a balancing property
    // Top levels are chosen so that the expected number of nodes in each level’s list decreases exponentially
    private int randomLevel() {
        int level = 1;
        for (int i = 0; i < MAX_LEVEL; i++) {
            Random rand = new Random();
            float f = rand.nextFloat();
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
        int layerFound = find(x, preds, succs);
        // Case 1 - element found in the list, unsuccessful add
        if (layerFound != -1) {
            return false;
        }
        // Case 2 - not found in the list, create a new node with a random top-level
        int topLevel = randomLevel();
        Node newNode = new Node(x, topLevel);
        for (int level = 0; level <= topLevel; level++) {
            newNode.next[level] = succs[level];
            preds[level].next[level] = newNode;
        }
        // successful add
        return true;
    }
    
    boolean remove(int x) {
        Node victim = null;
        int topLevel = -1;
        // initialize pred and succ arrays
        Node[] preds = (Node[]) new Node[MAX_LEVEL + 1];
        Node[] succs = (Node[]) new Node[MAX_LEVEL + 1];
        // use find to check if node is in list
        int layerFound = find(x, preds, succs);
        if (layerFound != -1) {
            victim = succs[layerFound];
            topLevel = victim.topLevel;
            // remove links to victim, top-down
            for (int level = topLevel; level >= 0; level--) {
                preds[level].next[level] = victim.next[level];
            }
            // successful delete
            return true;
        } 
        // did not find a matching node, unsuccessful delete
        return false;
    }
    
    boolean contains(int x) {
        Node[] preds = (Node[]) new Node[MAX_LEVEL + 1];
        Node[] succs = (Node[]) new Node[MAX_LEVEL + 1];
        int layerFound = find(x, preds, succs);
        return layerFound != -1;
    }
    
    private static final class Node {
        final int item;
        final int key;
        final Node[] next;
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
    }
    
    public static void main(String [] args) {
        SkipList list = new SkipList();
        int n = 2000000;
        int[] a = new int[n];
        Random rand = new Random();
        long start;
        long end;
        for (int i = 0; i < n; i++) {
            a[i] = rand.nextInt(n);
        }
        
        // test add
        start = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            list.add(a[i]);
        }      
        end = System.currentTimeMillis();
        System.out.println("Java sequential add() " + n + " nodes, time: " + (end - start) / 1000.0 + " s");

        // test contains
        start = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            list.contains(a[i]);
        }      
        end = System.currentTimeMillis();
        System.out.println("Java sequential contains() " + n + " nodes, time: " + (end - start) / 1000.0 + " s");
        
        // test remove
        start = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            list.remove(a[i]);
        }      
        end = System.currentTimeMillis();
        System.out.println("Java sequential remove() " + n + " nodes, time: " + (end - start) / 1000.0 + " s");       
    }
}
