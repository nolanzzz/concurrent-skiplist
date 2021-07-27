using System;
using System.Threading;
using System.Threading.Tasks;

namespace lazyskiplist
{
    class LazySkipListProgram
    {        
        static void Main()
        {
            var list = new LazySkipList();
            int n = 130000;
            int num_threads = 10;
            
            var watch = new System.Diagnostics.Stopwatch();
            
            Thread[] add_threads = new Thread[num_threads];
            Thread[] contains_threads = new Thread[num_threads];
            Thread[] remove_threads = new Thread[num_threads];
            
            // test add
            watch.Start();
            for (int i = 0; i < num_threads; i++) {
                AddThread th = new AddThread(list, n);
                Thread t = new Thread(new ThreadStart(th.Proceed));
                add_threads[i] = t;
                t.Start();
            }
            for (int i = 0; i < num_threads; i++) {
                add_threads[i].Join();
            } 
            watch.Stop();
            Console.WriteLine("Add():"+ n * num_threads +" nodes, time: "+ watch.ElapsedMilliseconds / 1000.0 + " s");
            watch.Reset();
            
            // test contains
            watch.Start();
            for (int i = 0; i < num_threads; i++) {
                ContainsThread th = new ContainsThread(list, n);
                Thread t = new Thread(new ThreadStart(th.Proceed));
                contains_threads[i] = t;
                t.Start();
            }
            for (int i = 0; i < num_threads; i++) {
                contains_threads[i].Join();
            } 
            watch.Stop();
            Console.WriteLine("Contains():"+ n * num_threads +" nodes, time: "+ watch.ElapsedMilliseconds / 1000.0 + " s");
            watch.Reset();
            
            // test remove
            watch.Start();
            for (int i = 0; i < num_threads; i++) {
                RemoveThread th = new RemoveThread(list, n);
                Thread t = new Thread(new ThreadStart(th.Proceed));
                remove_threads[i] = t;
                t.Start();
            }
            for (int i = 0; i < num_threads; i++) {
                remove_threads[i].Join();
            } 
            watch.Stop();
            Console.WriteLine("Remove():"+ n * num_threads +" nodes, time: "+ watch.ElapsedMilliseconds / 1000.0 + " s");
        }
    }
    
    public class AddThread {
        private LazySkipList list;
        private int n;
        public AddThread(LazySkipList l, int num) {
            list = l;
            n = num;
            return;
        }
        public void Proceed() {
            Random rand = new Random();
            for (int i = 0; i < n; i++) {
                list.Add(rand.Next(1, n));
            }
        }
    }
    
    public class RemoveThread {
        private LazySkipList list;
        private int n;
        public RemoveThread(LazySkipList l, int num) {
            list = l;
            n = num;
            return;
        }
        public void Proceed() {
            Random rand = new Random();
            for (int i = 0; i < n; i++) {
                list.Remove(rand.Next(1, n));
            }
        }
    }

    public class ContainsThread {
        private LazySkipList list;
        private int n;
        public ContainsThread(LazySkipList l, int num) {
            list = l;
            n = num;
            return;
        }
        public void Proceed() {
            Random rand = new Random();
            for (int i = 0; i < n; i++) {
                list.Contains(rand.Next(1, n));
            }
        }
    }


    public class LazySkipList {
        const int MAX_LEVEL = 32;
        const double Prob = 0.5;
        Node head = new Node(int.MinValue);
        Node tail = new Node(int.MaxValue);
        
        public LazySkipList() {
            for (int i = 0; i < head.next.Length; i++) {
                head.next[i] = tail; // initialize all layers with only the head and the tail
            }
        }
        
        // generate a random level number that provides a balancing property
        // Top levels are chosen so that the expected number of nodes in each level’s list decreases exponentially
        private int randomLevel() {
            Random random = new Random();
            int level = 1;
            for (int i = 0; i < MAX_LEVEL; i++) {
                double f = random.NextDouble();
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

        public bool Add(int x) {
        // initialize pred and succ arrays
            Node[] preds = new Node[MAX_LEVEL + 1];
            Node[] succs = new Node[MAX_LEVEL + 1];
            // keep trying until returned
            while (true) {
                int layerFound = find(x, preds, succs);
                // Case 1 - element found in the list
                if (layerFound != -1) {
                    // node would be included as the entry in succs on the layer found
                    Node nodeFound = succs[layerFound];
                    if (nodeFound.marked != true) {
                        while (!nodeFound.fullyLinked) {
                            // wait until it's phisically linked to the skiplist
                        }
                        return false;
                    }
                    continue;
                }
                
                // Case 2 - not found in the list
                int highestLocked = -1;
                int topLevel = randomLevel(); // must decrease exponentially
                try {
                    Node pred, succ;
                    bool valid = true;
                    for (int level = 0; valid && (level <= topLevel); level++) {
                        pred = preds[level];
                        succ = succs[level];
                        pred.Lock();
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
                }
                finally
                {
                    // release locks
                    for (int level = 0; level <= highestLocked; level++) {
                        preds[level].Unlock();
                    }
                }
            }
        }

        public bool Remove(int x) {
            Node victim = null; 
            bool isMarked = false; 
            int topLevel = -1;
            Node[] preds = new Node[MAX_LEVEL + 1];
            Node[] succs = new Node[MAX_LEVEL + 1];
            while (true) {
                int layerFound = find(x, preds, succs);
                if (layerFound != -1) {
                    victim = succs[layerFound];
                }
                if (isMarked ||
                    (layerFound != -1 &&
                     victim.fullyLinked &&
                     victim.topLevel == layerFound &&
                     !victim.marked)) {
                    if (!isMarked) {
                        // logical delete
                        topLevel = victim.topLevel;
                        victim.Lock();
                        if (victim.marked) {
                            victim.Unlock();
                            return false;
                        }
                        victim.marked = true;
                        isMarked = true;
                    }
                    // physical delete
                    int highestLocked = -1;
                    try {
                        Node pred; 
                        bool valid = true;
                        for (int level = 0; valid && (level <= topLevel); level++) {
                            pred = preds[level];
                            pred.Lock();
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
                        victim.Unlock();
                        // successful physical delete
                        return true;
                    }
                    finally {
                        // release locks
                        for (int i = 0; i <= highestLocked; i++) {
                            preds[i].Unlock();
                        }
                    }
                }
                else {
                    return false;
                }
            }
        }

        public bool Contains(int x) {
            Node[] preds = new Node[MAX_LEVEL + 1];
            Node[] succs = new Node[MAX_LEVEL + 1];
            int layerFound = find(x, preds, succs);
            // only return true if found in list, fully linked and not marked
            return layerFound != -1 && succs[layerFound].fullyLinked && !succs[layerFound].marked;
        }

        private class Node {
            public Mutex mlock = new Mutex();
            public int item;
            public int key;
            public Node[] next;
            public volatile bool marked = false;
            public volatile bool fullyLinked = false;
            public  int topLevel;
            
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
            
            public void Lock() {
                mlock.WaitOne();
            }
            
            public void Unlock() {
                mlock.ReleaseMutex();
            }
        }
    }
}
