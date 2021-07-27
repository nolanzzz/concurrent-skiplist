using System;
using System.Threading;
using System.Threading.Tasks;

namespace skiplist
{
    class SkipListProgram
    {
        static void Main()
        {
            var list = new SkipList();
            int n = 1000000;
            int[] a = new int[n];
            
            Random rand = new Random();
            var watch = new System.Diagnostics.Stopwatch();

            for (int i = 0; i < n; i++) {
                a[i] = rand.Next(1, n);
            }
        
            // test add
            watch.Start();
            for (int i = 0; i < n; i++) {
                list.Add(a[i]);
            }      
            watch.Stop();
            Console.WriteLine("Add():"+ n +" nodes, time: "+ watch.ElapsedMilliseconds / 1000 + " s");
            
            watch.Reset();
            // test contains
            watch.Start();
            for (int i = 0; i < n; i++) {
                list.Contains(a[i]);
            }      
            watch.Stop();
            Console.WriteLine("Contains():"+ n +" nodes, time: "+ watch.ElapsedMilliseconds / 1000 + " s");
            
            watch.Reset();
            // test remove
            watch.Start();
            for (int i = 0; i < n; i++) {
                list.Remove(a[i]);
            }      
            watch.Stop();
            Console.WriteLine("Remove():"+ n +" nodes, time: "+ watch.ElapsedMilliseconds / 1000 + " s");
        }
    }

    public class SkipList {
        const int MAX_LEVEL = 32;
        const double Prob = 0.5;
        Node head = new Node(int.MinValue);
        Node tail = new Node(int.MaxValue);
        
        public SkipList() {
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
            int layerFound = find(x, preds, succs);
            // Case 1 - element found in the list
            if (layerFound != -1) {
                return false;
            }

            // Case 2 - not found in the list
            int topLevel = randomLevel(); // must decrease exponentially
            Node newNode = new Node(x, topLevel);
            for (int level = 0; level <= topLevel; level++) {
                newNode.next[level] = succs[level];
                preds[level].next[level] = newNode;
            } 
            // successful add
            return true;
        }

        public bool Remove(int x) {
            Node victim = null;  
            int topLevel = -1;
            // initialize pred and succ arrays
            Node[] preds = new Node[MAX_LEVEL + 1];
            Node[] succs = new Node[MAX_LEVEL + 1];
            int layerFound = find(x, preds, succs);
            if (layerFound != -1) {
                victim = succs[layerFound];
                topLevel = victim.topLevel;   
                // physically remove link to victim, top-down
                for (int level = topLevel; level >= 0; level--) {
                    preds[level].next[level] = victim.next[level];
                }
                // successful delete
                return true;
            }
            // did not find a matching node, unsuccessful delete
            return false;
        }

        public bool Contains(int x) {
            Node[] preds = new Node[MAX_LEVEL + 1];
            Node[] succs = new Node[MAX_LEVEL + 1];
            int layerFound = find(x, preds, succs);
            return layerFound != -1;
        }

        private class Node {
            public int item;
            public int key;
            public Node[] next;
            public int topLevel;
            
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
    }
}
