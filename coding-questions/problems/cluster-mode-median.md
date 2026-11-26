# Find Cluster Mode and Median

一个很大的整数数据集平均分给 `k` 个 worker，每个 worker 只能看到自己那一份。worker 之间只能通过
`sendAsyncMessage(target, payload)` / `receive()` 传消息。要求不能让任何一个 worker 拿到全部原始数据，
并且网络流量尽量少。结果只由 worker 0 输出。

| 版本 | 要求 | 方法 |
|---|---|---|
| base | 全局众数；次数相同时取较小的值 | `int findMode()` |
| FU1 | 全局中位数；偶数个时取两个中间值的平均 | `double findMedian()` |

`Worker` 的实现题目没给，代码里用一个内存消息网络模拟：每个 worker 一个 `BlockingQueue` 当邮箱，
每个 worker 跑在自己的线程上，所以可以直接运行。

## 思路

- **base：按值分片（shuffle by value）**
  1. 每个 worker 统计本地每个值的次数。
  2. 每个值只归一个 owner 管（`hash(v) % k`），把本地的 `(值, 次数)` 发给对应 owner。
     给每个 owner 都发一条消息，没有数据就发空消息，这样 owner 收满 k 条就知道齐了。
  3. owner 把次数加起来，得到它负责的值的全局精确次数，挑出最好的一个发给 worker 0。
  4. worker 0 从 k 个候选里再挑出最好的。

  原始数据不移动，流量是 O(每个 worker 的不同值个数)。重复越多越省；如果几乎每个值都只出现一次，
  流量会接近原始数据量。可以提的优化：用树状汇总减少 k² 条消息；如果允许近似，可以用 Misra–Gries 或
  Count-Min Sketch 找高频元素。

- **FU1：在值域上二分**
  1. worker 0 先收集每个 worker 的数据个数，算出总数 n。
  2. worker 0 广播一个猜测值 x，每个 worker 回复本地 ≤ x 的数有几个（本地数据只排序一次）。
  3. 值域是 [−10⁹, 10⁹]，大约 31 轮就收敛。偶数个时两个中间位置在同一轮里一起二分。

  流量是 O(k · log 值域)，跟数据规模无关。

## 实现细节

- 消息是异步的，快的 worker 可能已经进入下一阶段，先把下一阶段的消息发过来了。
  `receive(tag)` 按消息前缀（`C|`、`B|`、`Q|` 等）取想要的那一条，提前到的消息先缓存起来。
- 求平均时用 `long` 相加，避免两个 10⁹ 相加时 int 溢出。
