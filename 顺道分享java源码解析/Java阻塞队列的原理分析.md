### Java阻塞队列的原理分析
-----

先看看`BlockingQueue`接口的文档说明：<br/>
1. add：添加元素到队列里，添加成功返回true，由于容量满了添加失败会抛出`IllegalStateException`异常；<br/>
2. offer：添加元素到队列里，添加成功返回true，添加失败返回false；<br/>
3. put：添加元素到队列里，如果容量满了会阻塞直到容量不满；<br/>
4. poll：删除队列头部元素，如果队列为空，返回null。否则返回元素；<br/>
5. remove：基于对象找到对应的元素，并删除。删除成功返回true，否则返回false；<br/>
6. take：删除队列头部元素，如果队列为空，一直阻塞到队列有元素并删除。<br/>

先看一个简单的`ArrayBlockingQueue`，**ArrayBlockingQueue的原理就是使用一个可重入锁和这个锁生成的两个条件对象进行并发控制(classic two-condition algorithm)**。<br/>

#### ArrayBlockingQueue<br/>
ArrayBlockingQueue是一个带有长度的阻塞队列，初始化的时候必须要指定队列长度，且指定长度之后不允许进行修改。<br/>

属性如下：<br/>

```
	/** The queued items item的集合 */
    final Object[] items;

    /** items index for next take, poll, peek or remove 拿数据的索引 */
    int takeIndex;

    /** items index for next put, offer, or add 放数据的索引 */
    int putIndex;

    /** Number of elements in the queue 队列元素的个数 */
    int count;

    /** Main lock guarding all access 可重入的锁 */
    final ReentrantLock lock;

    /** Condition for waiting takes 条件对象 */
    private final Condition notEmpty;

    /** Condition for waiting puts 条件对象 */
    private final Condition notFull;
```

先看一下add方法：<br/>

```
public boolean add(E e) {
	if (offer(e))
		return true;
	else
		throw new IllegalStateException("Queue full");
}
```

offer方法：<br/>

```
public boolean offer(E e) {
	checkNotNull(e);
	final ReentrantLock lock = this.lock;
	lock.lock();
	try {
		if (count == items.length)
			return false;
		else {
			insert(e);
			return true;
		}
	} finally {
		lock.unlock();
	}
}
```

我们可以看到，如果满了返回false，如果没有满调用insert。整个方法是通过可重入锁来锁住的，并且最终释放。接着看一下`insert`方法：<br/>

```
private void insert(E x) {
	items[putIndex] = x; // 元素添加到数组里
	putIndex = inc(putIndex); // 放数据索引+1，当索引满了变成0
	++count; // 元素个数+1
	notEmpty.signal(); // 使用条件对象notEmpty通知
}
```

这里`insert`被调用的时候就会唤醒`notEmpty`上等待的线程进行`take`操作。<br/>

再看一下`put`方法：<br/>

```
public void put(E e) throws InterruptedException {
	checkNotNull(e); // 不允许元素为空
	final ReentrantLock lock = this.lock;
	lock.lockInterruptibly(); // 加锁，保证调用put方法的时候只有1个线程
	try {
		while (count == items.length) // 如果队列满了，阻塞当前线程，while用来防止假唤醒
			notFull.await(); // 线程阻塞并被挂起，同时释放锁
		insert(e); // 调用insert方法
	} finally {
		lock.unlock(); // 释放锁，让其他线程可以调用put方法
	}
}
```

add方法和offer方法不会阻塞线程，put方法如果队列满了会阻塞线程，直到有线程消费了队列里的数据才有可能被唤醒。<br/>

继续看删除数据的相关操作，先看一下poll：<br/>

```
public E poll() {
	final ReentrantLock lock = this.lock;
	lock.lock(); // 加锁，保证调用poll方法的时候只有1个线程
	try {
		return (count == 0) ? null : extract(); // 如果队列里没元素了，返回null，否则调用extract方法
	} finally {
		lock.unlock(); // 释放锁，让其他线程可以调用poll方法
	}
}
```

看看这个`extract`方法（jdk源码的作者的起名水平真的非常高，代码素质好）：<br/>

```
private E extract() {
	final Object[] items = this.items;
	E x = this.<E>cast(items[takeIndex]); // 得到取索引位置上的元素
	items[takeIndex] = null; // 对应取索引上的数据清空
	takeIndex = inc(takeIndex); // 取数据索引+1，当索引满了变成0
	--count; // 元素个数-1
	notFull.signal(); // 使用条件对象notFull通知，原理同上面的insert中
	return x; // 返回元素
}
```

看一下`take`方法：<br/>

```
public E take() throws InterruptedException {
	final ReentrantLock lock = this.lock;
	lock.lockInterruptibly(); // 加锁，保证调用take方法的时候只有1个线程
	try {
		while (count == 0) // 如果队列空，阻塞当前线程，并加入到条件对象notEmpty的等待队列里
			notEmpty.await(); // 线程阻塞并被挂起，同时释放锁
		return extract(); // 调用extract方法
	} finally {
		lock.unlock(); // 释放锁，让其他线程可以调用take方法
	}
}
```

再看一下`remove`方法：<br/>

```
public boolean remove(Object o) {
	if (o == null) return false;
	final Object[] items = this.items;
	final ReentrantLock lock = this.lock;
	lock.lock(); // 加锁，保证调用remove方法的时候只有1个线程
	try {
		for (int i = takeIndex, k = count; k > 0; i = inc(i), k--) { // 遍历元素
			if (o.equals(items[i])) { // 两个对象相等的话
				removeAt(i); // 调用removeAt方法
				return true; // 删除成功，返回true
			}
		}
		return false; // 删除成功，返回false
	} finally {
		lock.unlock(); // 释放锁，让其他线程可以调用remove方法
	}
}
```

再看一下`removeAt`方法，这个方法反而比较有价值：<br/>

```
private void removeAt(int i) {
	final Object[] items = this.items;
	if (i == takeIndex) { 
		// 如果要删除数据的索引是取索引位置，直接删除取索引位置上的数据，然后取索引+1即可
		items[takeIndex] = null;
		takeIndex = inc(takeIndex);
	} else { 
		// 如果要删除数据的索引不是取索引位置，移动元素元素，更新取索引和放索引的值
		for (;;) {
			int nexti = inc(i);
			if (nexti != putIndex) {
				items[i] = items[nexti];
				i = nexti;
			} else {
				items[i] = null;
				putIndex = i;
				break;
			}
		}
	}
	--count; // 元素个数-1
	notFull.signal(); // 使用条件对象notFull通知
}
```

#### LinkedBlockingQueue<br/>
`LinkedBlockingQueue`是一个使用链表完成队列操作的阻塞队列。**链表是单向链表，而不是双向链表**。

看一下属性：<br/>

```
	/** The capacity bound, or Integer.MAX_VALUE if none 容量大小 */
	private final int capacity;

    /** Current number of elements 元素个数，因为有2个锁，存在竞态条件，使用AtomicInteger */
    private final AtomicInteger count = new AtomicInteger(0);

    /**
     * Head of linked list.
     * Invariant: head.item == null
     * 头结点
     */
    private transient Node<E> head;

    /**
     * Tail of linked list.
     * Invariant: last.next == null
     * 尾节点
     */
    private transient Node<E> last;

    /** Lock held by take, poll, etc 取元素的锁 */
    private final ReentrantLock takeLock = new ReentrantLock();

    /** Wait queue for waiting takes 取元素的条件对象 */
    private final Condition notEmpty = takeLock.newCondition();

    /** Lock held by put, offer, etc 放元素的锁 */
    private final ReentrantLock putLock = new ReentrantLock();

    /** Wait queue for waiting puts 放元素的条件对象 */
    private final Condition notFull = putLock.newCondition();
```

`ArrayBlockingQueue`只有1个锁，添加数据和删除数据的时候只能有1个被执行，不允许并行执行。<br/>

而`LinkedBlockingQueue`有2个锁，放元素锁和取元素锁，添加数据和删除数据是可以并行进行的，当然添加数据和删除数据的时候只能有1个线程各自执行。<br/>

`add`方法内部调用`offer`方法：<br/>

```
public boolean offer(E e) {
	if (e == null) throw new NullPointerException(); // 不允许空元素
	final AtomicInteger count = this.count;
	if (count.get() == capacity) // 如果容量满了，返回false
		return false;
	int c = -1;
	Node<E> node = new Node(e); // 容量没满，以新元素构造节点
	final ReentrantLock putLock = this.putLock;
	putLock.lock(); // 放锁加锁，保证调用offer方法的时候只有1个线程
	try {
		// 再次判断容量是否已满，因为可能取元素锁在进行消费数据，没满的话继续执行
		if (count.get() < capacity) { 
			enqueue(node); // 节点添加到链表尾部
			c = count.getAndIncrement(); // 元素个数+1
			if (c + 1 < capacity) // 如果容量还没满
				notFull.signal(); // 在放锁的条件对象notFull上唤醒正在等待的线程，表示可以再次往队列里面加数据
		}
	} finally {
		putLock.unlock(); // 释放放锁，让其他线程可以调用offer方法
	}
	// 由于存在放元素锁和取元素锁，这里可能取元素锁一直在消费数据，count会变化。这里的if条件表示如果队列中还有1条数据
	if (c == 0) 
		// 在拿锁的条件对象notEmpty上唤醒正在等待的1个线程，表示队列里还有1条数据，可以进行消费
        signalNotEmpty(); 
	return c >= 0; // 添加成功返回true，否则返回false
}
```

`put`方法：<br/>

```
public void put(E e) throws InterruptedException {
	if (e == null) throw new NullPointerException(); // 不允许空元素
	int c = -1;
	Node<E> node = new Node(e); // 以新元素构造节点
	final ReentrantLock putLock = this.putLock;
	final AtomicInteger count = this.count;
	putLock.lockInterruptibly(); // 放锁加锁，保证调用put方法的时候只有1个线程
	try {
		while (count.get() == capacity) { // 如果容量满了
			notFull.await(); // 阻塞并挂起当前线程
		}
		enqueue(node); // 节点添加到链表尾部
		c = count.getAndIncrement(); // 元素个数+1
		if (c + 1 < capacity) // 如果容量还没满
			// 在放锁的条件对象notFull上唤醒正在等待的线程，表示可以再次往队列里面加数据了，队列还没满
			notFull.signal();
	} finally {
		putLock.unlock(); // 释放放锁，让其他线程可以调用put方法
	}
	// 由于存在放锁和拿锁，这里可能拿锁一直在消费数据，count会变化。这里的if条件表示如果队列中还有1条数据
	if (c == 0)
		// 在拿锁的条件对象notEmpty上唤醒正在等待的1个线程，表示队列里还有1条数据，可以进行消费
		signalNotEmpty();
}
```

`ArrayBlockingQueue`中放入数据阻塞的时候，需要消费数据才能唤醒。<br/>

而***`LinkedBlockingQueue`中放入数据阻塞的时候，因为它内部有2个锁，可以并行执行放入数据和消费数据，不仅在消费数据的时候进行唤醒插入阻塞的线程，同时在插入的时候如果容量还没满，也会唤醒插入阻塞的线程***。<br/>

`poll`方法：<br/>

```
public E poll() {
	final AtomicInteger count = this.count;
	if (count.get() == 0) // 如果元素个数为0
		return null; // 返回null
	E x = null;
	int c = -1;
	final ReentrantLock takeLock = this.takeLock;
	takeLock.lock(); // 拿锁加锁，保证调用poll方法的时候只有1个线程
	try {
		if (count.get() > 0) { // 判断队列里是否还有数据
			x = dequeue(); // 删除头结点
			c = count.getAndDecrement(); // 元素个数-1
			if (c > 1) // 如果队列里还有元素
				// 在拿锁的条件对象notEmpty上唤醒正在等待的线程，表示队列里还有数据，可以再次消费
				notEmpty.signal();
        }
    } finally {
        takeLock.unlock(); // 释放拿锁，让其他线程可以调用poll方法
    }
    // 由于存在放锁和拿锁，这里可能放锁一直在添加数据，count会变化。这里的if条件表示如果队列中还可以再插入数据
    if (c == capacity)
		// 在放锁的条件对象notFull上唤醒正在等待的1个线程，表示队列里还能再次添加数据
		signalNotFull(); 
	return x;
}
```

`take`方法：<br/>

```
public E take() throws InterruptedException {
	E x;
	int c = -1;
	final AtomicInteger count = this.count;
	final ReentrantLock takeLock = this.takeLock;
	takeLock.lockInterruptibly(); // 拿锁加锁，保证调用take方法的时候只有1个线程
	try {
		while (count.get() == 0) { // 如果队列里已经没有元素了
			notEmpty.await(); // 阻塞并挂起当前线程
		}
		x = dequeue(); // 删除头结点
		c = count.getAndDecrement(); // 元素个数-1
		if (c > 1) // 如果队列里还有元素
			// 在拿锁的条件对象notEmpty上唤醒正在等待的线程，表示队列里还有数据，可以再次消费
			notEmpty.signal(); 
	} finally {
		takeLock.unlock(); // 释放拿锁，让其他线程可以调用take方法
	}
	// 由于存在放锁和拿锁，这里可能放锁一直在添加数据，count会变化。这里的if条件表示如果队列中还可以再插入数据
	if (c == capacity) 
		// 在放锁的条件对象notFull上唤醒正在等待的1个线程，表示队列里还能再次添加数据
		signalNotFull(); 
	return x;
}
```

`remove`方法：<br/>

```
public boolean remove(Object o) {
	if (o == null) return false;
	fullyLock(); // remove操作要移动的位置不固定，2个锁都需要加锁
	try {
		for (Node<E> trail = head, p = trail.next; // 从链表头结点开始遍历
			p != null;
			trail = p, p = p.next) {
			if (o.equals(p.item)) { // 判断是否找到对象
				unlink(p, trail); // 修改节点的链接信息，同时调用notFull的signal方法
				return true;
			}
		}
		return false;
	} finally {
		fullyUnlock(); // 2个锁解锁
	}
}
```

`LinkedBlockingQueue`的take方法对于没数据的情况下会阻塞，poll方法删除链表头结点，remove方法删除指定的对象。<br/>

**需要注意的是remove方法由于要删除的数据的位置不确定，需要2个锁同时加锁。**<br/>
