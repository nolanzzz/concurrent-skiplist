package main
import "math/rand"
import "fmt"
import "time"

const MAX_LEVEL int = 32
const Prob float32 = 0.5

func randomLevel() int {
    level := 1
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

type SkipList struct {
    head  *Node
    tail *Node
    level int
}

type Node struct {
    key int
    item int
    top_level int
    next []*Node
}

func newNode(key, item, height int) *Node {
    new_node := Node{
        key: key, 
        item: item,
        top_level: height,
        next: make([]*Node, height)}
    return &new_node
}

func newSkipList() SkipList {
    newList := SkipList{
        head: newNode(-999, -999, MAX_LEVEL), 
        tail: newNode(9999999999, 9999999999, MAX_LEVEL),
        level: 1}
    
    for i := 0; i < MAX_LEVEL; i++ {
        newList.head.next[i] = newList.tail
    }
    
    return newList
}

func (this *SkipList) find(key int) (int, []*Node, []*Node) {
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

func (this *SkipList) contains(x int) bool {
    layer_found := -1
    layer_found, _, _ = this.find(x)
    return layer_found != -1
}

func (this *SkipList) add(x int) bool {
    preds := make([]*Node, MAX_LEVEL)
    succs := make([]*Node, MAX_LEVEL)
    layer_found := -1
    layer_found, preds, succs = this.find(x)
    
    if layer_found != -1 {
        return false
    }
    top_level := randomLevel()
    new_node := newNode(x, x, top_level)
    for i := 0; i <= top_level - 1; i++ {
        new_node.next[i] = succs[i]
        preds[i].next[i] = new_node
    }
    return true
}

func (this *SkipList) remove(x int) bool {
    top_level := -1
    preds := make([]*Node, MAX_LEVEL)
    succs := make([]*Node, MAX_LEVEL)
    layer_found := -1
    layer_found, preds, succs = this.find(x)
    if layer_found != -1 {
        victim := succs[layer_found]
        top_level = victim.top_level
        for i := top_level - 1; i >=0; i-- {
            preds[i].next[i] = victim.next[i]
        }
        return true
    }
    return false
}

func main() {
    list := newSkipList()
    n := 1000000
    nodes := make([]int, n)
    
    rand.Seed(time.Now().UnixNano())
    for i := 0; i < n; i++ {
        nodes[i] = rand.Intn(100000)
    }
    start := time.Now()
    for i := 0; i < n; i++ {
        list.add(nodes[i])
    }
    elapsed := time.Since(start)
    fmt.Println("Go sequential add()", n, "nodes, time:",  elapsed.Seconds(), "s")
    
    start = time.Now()
    for i := 0; i < n; i++ {
        list.contains(nodes[i])
    }
    elapsed = time.Since(start)
    fmt.Println("Go sequential contains()", n, "nodes, time:",  elapsed.Seconds(), "s")
    
    start = time.Now()
    for i := 0; i < n; i++ {
        list.remove(nodes[i])
    }
    elapsed = time.Since(start)
    fmt.Println("Go sequential remove()", n, "nodes, time:",  elapsed.Seconds(), "s")
}
