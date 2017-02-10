前言部分：
    1.concurentHashmap在jdk8以前，使用的是分段锁的设计理念来完成并发控制，1.8进行了一次非常大幅度的改版，原因有下：
    （1）.synchronized关键字 -- 1.8对synchronized的优化已经足够好，以至于不需要用segment来增强并发
    （2）.类似于hashmap在1.8的增强，concurenthashmap也在出现hash冲突过多的时候采用红黑树的方式来降低query的时间
    2.这里为什么分析transfer方法呢：
    （1）.transfer方法是concurenthashmap里最复杂的方法
    （2）.transfer方法在1.8的时候也进行了增强，在出现多线程问题的时候，如果让我设计，我的第一反应是锁住当前map，transfer之后再修改，那么1.8里怎么做的呢，非常非常非常牛逼，它直接把另外一个线程拉进来一起帮着做transfer，
    而控制transfer的就是concurenthashmap中的新数据结构，ForwardingNode。

    其实这里我还有一些地方没有彻底搞明白，也借鉴了一下网上其他人对源码的解读，综合了一下我自己的理解，重要的部分都用中文做了注释，有兴趣的可以研究一下源码以及相关概念，concurenthashmap和hashmap在1.8的改变真的是非常非常大。

/**
 * Moves and/or copies the nodes in each bin to new table. See
 * above for explanation.
 */
private final void transfer(Node<K,V>[] tab, Node<K,V>[] nextTab) {
    int n = tab.length, stride;
    if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE)
        stride = MIN_TRANSFER_STRIDE; // subdivide range
    if (nextTab == null) {            // initiating
        try {
            //构造一个nextTable对象 它的容量是原来的两倍
            Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n << 1];
            nextTab = nt;
        } catch (Throwable ex) {      // try to cope with OOME
            sizeCtl = Integer.MAX_VALUE;
            return;
        }
        nextTable = nextTab;
        //下一个表索引 + 1 在调整时
        transferIndex = n;
    }
    int nextn = nextTab.length;
    ForwardingNode<K,V> fwd = new ForwardingNode<K,V>(nextTab); //构造一个连节点指针 用于标志位
    boolean advance = true; //并发扩容的关键属性 如果等于true 说明这个节点已经处理过
    boolean finishing = false; // to ensure sweep before committing nextTab

    //这里是一个无限循环，直到处理结束或者(sc - 2) != resizeStamp(n) << RESIZE_STAMP_SHIFT条件达成 TODO <--这个条件干吗用的
    for (int i = 0, bound = 0;;) {
        Node<K,V> f; int fh;
        //这个while循环体的作用就是在控制i，不断的i--，用来循环整个table
        while (advance) {
            int nextIndex, nextBound;
            if (--i >= bound || finishing)
                advance = false;
            else if ((nextIndex = transferIndex) <= 0) {
                i = -1;
                advance = false;
            } else if (U.compareAndSwapInt(this, TRANSFERINDEX, nextIndex,nextBound = (nextIndex > stride ? nextIndex - stride : 0))) {
                bound = nextBound;
                i = nextIndex - 1;
                advance = false;
            }
        }
        //nextn就是下一个table的长度，n是旧表的长度，i则是每一个元素的位置
        if (i < 0 || i >= n || i + n >= nextn) {
            int sc;
            //如果所有的节点都已经完成复制工作  就把nextTable赋值给table 清空临时对象nextTable
            if (finishing) {
                nextTable = null;
                table = nextTab;
                sizeCtl = (n << 1) - (n >>> 1);//扩容阈值设置为原来容量的1.5倍  依然相当于现在容量的0.75倍
                return;
            }
            if (U.compareAndSwapInt(this, SIZECTL, sc = sizeCtl, sc - 1)) {
                //什么时候会在这里返回？？？
                if ((sc - 2) != resizeStamp(n) << RESIZE_STAMP_SHIFT) {
                    return;
                }
                finishing = advance = true;
                i = n; // recheck before commit
            }
        } else if ((f = tabAt(tab, i)) == null) {
            //如果遍历到的节点为空 则放入ForwardingNode指针
            advance = casTabAt(tab, i, null, fwd);
        } else if ((fh = f.hash) == MOVED) {
            //如果遍历到ForwardingNode节点  说明这个点已经被处理过了 直接跳过  这里是控制并发扩容的核心
            advance = true; // already processed
        } else {
            synchronized (f) {
                if (tabAt(tab, i) == f) {
                    Node<K,V> ln, hn;
                    //如果fh>=0 证明这是一个Node节点
                    if (fh >= 0) {
                        //以下的部分在完成的工作是构造两个链表
                        int runBit = fh & n;
                        Node<K,V> lastRun = f;
                        for (Node<K,V> p = f.next; p != null; p = p.next) {
                            int b = p.hash & n;
                            if (b != runBit) {
                                runBit = b;
                                lastRun = p;
                            }
                        }
                        if (runBit == 0) {
                            ln = lastRun;
                            hn = null;
                        } else {
                            hn = lastRun;
                            ln = null;
                        }
                        for (Node<K,V> p = f; p != lastRun; p = p.next) {
                            int ph = p.hash; K pk = p.key; V pv = p.val;
                            if ((ph & n) == 0)
                                ln = new Node<K,V>(ph, pk, pv, ln);
                            else
                                hn = new Node<K,V>(ph, pk, pv, hn);
                        }
                        //在nextTable的i位置上插入一个链表
                        setTabAt(nextTab, i, ln);
                        //这是在干吗没看懂- -
                        setTabAt(nextTab, i + n, hn);
                        //在table的i位置上插入forwardNode节点  表示已经处理过该节点
                        setTabAt(tab, i, fwd);
                        //设置advance为true 返回到上面的while循环中 就可以执行i--操作
                        advance = true;
                    }
                    //对TreeBin对象进行处理  与上面的过程类似
                    else if (f instanceof TreeBin) {
                        TreeBin<K,V> t = (TreeBin<K,V>)f;
                        TreeNode<K,V> lo = null, loTail = null;
                        TreeNode<K,V> hi = null, hiTail = null;
                        int lc = 0, hc = 0;
                        for (Node<K,V> e = t.first; e != null; e = e.next) {
                            int h = e.hash;
                            TreeNode<K,V> p = new TreeNode<K,V>
                                (h, e.key, e.val, null, null);
                            if ((h & n) == 0) {
                                if ((p.prev = loTail) == null)
                                    lo = p;
                                else
                                    loTail.next = p;
                                loTail = p;
                                ++lc;
                            }
                            else {
                                if ((p.prev = hiTail) == null)
                                    hi = p;
                                else
                                    hiTail.next = p;
                                hiTail = p;
                                ++hc;
                            }
                        }
                        //如果扩容后已经不再需要tree的结构 反向转换为链表结构
                        ln = (lc <= UNTREEIFY_THRESHOLD) ? untreeify(lo) :
                            (hc != 0) ? new TreeBin<K,V>(lo) : t;
                        hn = (hc <= UNTREEIFY_THRESHOLD) ? untreeify(hi) :
                            (lc != 0) ? new TreeBin<K,V>(hi) : t;
                        setTabAt(nextTab, i, ln);
                        setTabAt(nextTab, i + n, hn);
                        setTabAt(tab, i, fwd);
                        advance = true;
                    }
                }
            }
        }
        // end if
    }
}