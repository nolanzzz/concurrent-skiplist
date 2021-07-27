package main
import "math/rand"
import "fmt"
import "time"
import "sync"
import "reflect"

const MAX_LEVEL int = 32
const Prob float32 = 0.5

func randomLevel() int {
    level := 0
    rand.Seed(time.Now().UnixNano())
    for i := 0; i < MAX_LEVEL; i++ {
        if rand.Float32() <= Prob {
            level++
        } else {
            return level
        }
    }
    return level
}

type Node struct {
    key int
    item int
    top_level int
    next []*Node
    marked bool
    fully_linked bool
    lock sync.RWMutex
}

func newNode(key, item, height int) *Node {
    new_node := Node{
        key: key, 
        item: item,
        top_level: height,
        marked: false,
        fully_linked: false,
        next: make([]*Node, height)}
    return &new_node
}

type LazySkipList struct {
    head  *Node
    tail *Node
    level int
}

func newLazySkipList() LazySkipList {
    newList := LazySkipList{
        head: newNode(-999, -999, MAX_LEVEL), 
        tail: newNode(9999999999, 9999999999, MAX_LEVEL),
        level: 1}
    
    for i := 0; i < MAX_LEVEL; i++ {
        newList.head.next[i] = newList.tail
    }
    
    return newList
}

func (this *LazySkipList) find(key int) (int, []*Node, []*Node) {
    layer_found := -1
    preds := make([]*Node, MAX_LEVEL + 1)
    succs := make([]*Node, MAX_LEVEL + 1)
    pred := this.head
    
    for l := MAX_LEVEL - 1; l >= 0; l-- {
        curr := pred.next[l]
        for key > curr.key {
            pred = curr
            curr = pred.next[l]
        }
        if layer_found == -1 && key == curr.key {
            layer_found = l
        }
        preds[l] = pred
        succs[l] = curr
    }
    return layer_found, preds, succs
}

func (this *LazySkipList) contains(x int) bool {
    layer_found := -1
    layer_found, _, _ = this.find(x)
    return layer_found != -1
}

func (this *LazySkipList) add(x int) bool {
    preds := make([]*Node, MAX_LEVEL)
    succs := make([]*Node, MAX_LEVEL)
    for {
        layer_found := -1
        layer_found, preds, succs = this.find(x)
        if layer_found != -1 {
            node_found := succs[layer_found]
            if !node_found.marked {
                for !node_found.fully_linked {}
                return false
            }
            continue
        }
        highest_locked := -1
        top_level := randomLevel()
        var pred, succ, prev_pred *Node
        valid := true
        for level := 0; valid && (level <= top_level - 1); level++ {
            pred = preds[level]
            succ = succs[level]
            if pred != prev_pred {
                pred.lock.RLock()
                highest_locked = level
                prev_pred = pred
            }
            
            valid = !pred.marked && !succ.marked && pred.next[level] == succ
        }
        if !valid {
            for level := 0; level <= highest_locked - 1; level++ {
                if isLocked(&preds[level].lock) {
                    preds[level].lock.RUnlock()
                }
            }
            continue
        }
        new_node := newNode(x, x, top_level)
        for level := 0; level <= top_level - 1; level++ {
            new_node.next[level] = succs[level]
        }  
        for level := 0; level <= top_level - 1; level++ {
            preds[level].next[level] = new_node
        }
        new_node.fully_linked = true
        for level := 0; level <= highest_locked - 1; level++ {
            if isLocked(&preds[level].lock) {
                preds[level].lock.RUnlock()
            }
        }
        return true
    }
}

func (this *LazySkipList) remove(x int) bool {
    var victim *Node
    is_marked := false
    top_level := -1
    preds := make([]*Node, MAX_LEVEL)
    succs := make([]*Node, MAX_LEVEL)
    for {
        layer_found := -1
        layer_found, preds, succs = this.find(x)
        if layer_found != -1 {
            victim = succs[layer_found]
        }
        if is_marked == true || (layer_found != -1 && victim.fully_linked && victim.top_level == layer_found && !victim.marked) {
            if !is_marked {
                top_level = victim.top_level
                victim.lock.RLock()
                if (victim.marked) {
                    victim.lock.RUnlock()
                    return false
                }
                victim.marked = true
                is_marked = true
            }
            highest_locked := -1
            var pred, succ, prev_pred *Node
            valid := true
            for level := 0; valid && (level <= top_level - 1); level++ {
                pred = preds[level]
                succ = succs[level]
                if pred != prev_pred {
                    pred.lock.RLock()
                    highest_locked = level
                    prev_pred = pred
                }
                valid = !pred.marked && pred.next[level] == succ
            }
            if !valid {
                for level := 0; level <= highest_locked - 1; level++ {
                    if isLocked(&preds[level].lock) {
                        preds[level].lock.RUnlock()
                    }
                }
                continue
            }
            for level := top_level - 1; level >= 0; level-- {
                preds[level].next[level] = victim.next[level]
            }
            if isLocked(&victim.lock) {
                victim.lock.RUnlock()
            }
            for level := 0; level <= highest_locked - 1; level++ {
                if isLocked(&preds[level].lock) {
                    preds[level].lock.RUnlock()
                }
            }
            return true
        } else {
            return false
        }
    }
}

func isLocked(l *sync.RWMutex) bool {
    state := reflect.ValueOf(l).Elem().FieldByName("readerCount").Int()
    return state > 0
}

var a, c, r chan bool

func testAdd(list *LazySkipList, nodes []int) {
    for i := range nodes {
        list.add(i)
    }
    a<-true
}

func testContains(list *LazySkipList, nodes []int) {
//     defer wg.Done()
    for i := range nodes {
        list.contains(i)
    }
    c<-true
}

func testRemove(list *LazySkipList, nodes []int) {
//     defer wg.Done()
    for i := range nodes {
        list.remove(i)
    }
    r<-true
}

/**
testing
**/
func main() {
    a = make(chan bool)
    c = make(chan bool)
    r = make(chan bool)
    list := newLazySkipList()
    num_threads := 100
    n := 130000
    nodes := make([][]int, num_threads)
    rand.Seed(time.Now().UnixNano())
    for i := 0; i < num_threads; i++ {
        nodes[i] = make([]int, n)
    }
    for i := 0; i < num_threads; i++ {
        for j := 0; j < n; j++ {
            nodes[i][j] = rand.Intn(n * num_threads)
        }
    }
    
    start := time.Now()
    for i := 0; i < num_threads; i++ {
        go testAdd(&list, nodes[i])
    }
    for i := 0; i < num_threads; i++ {
        <-a
    }
    elapsed := time.Since(start)
    fmt.Println("Go concurrent add()", n * num_threads, "nodes, time:",  elapsed.Seconds(), "s")
    
    start = time.Now()
    for i := 0; i < num_threads; i++ {
        go testContains(&list, nodes[i])
    }
    for i := 0; i < num_threads; i++ {
        <-c
    }
    elapsed = time.Since(start)
    fmt.Println("Go concurrent contains()", n * num_threads, "nodes, time:",  elapsed.Seconds(), "s")
    
    start = time.Now()
    for i := 0; i < num_threads; i++ {
        go testRemove(&list, nodes[i])
    }
    for i := 0; i < num_threads; i++ {
        <-r
    }
    elapsed = time.Since(start)
    fmt.Println("Go concurrent remove()", n * num_threads, "nodes, time:",  elapsed.Seconds(), "s")
}
