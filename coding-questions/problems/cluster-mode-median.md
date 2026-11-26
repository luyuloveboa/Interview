# Find Cluster Mode and Median

一个很大的整数数据集平均分给 `k` 个 worker，每个 worker 只能看到自己那一份。worker 之间只能通过
`sendAsyncMessage(target, payload)` / `receive()` 传消息。要求不能让任何一个 worker 拿到全部原始数据，
并且网络流量尽量少。结果只由 worker 0 输出。

| 版本 | 要求 | 方法 |
|---|---|---|
| base | 全局众数；次数相同时取较小的值 | `int findMode()` |
| FU1 | 全局中位数；偶数个时取两个中间值的平均 | `double findMedian()` |

代码用的是面试环境给的 harness（`Cluster` 的构造函数、邮箱、`sendAsyncMessage`、`receive` 原样保留）：
- `receive()` **不阻塞**：邮箱空了返回 `""`。
- 所有 worker 跑在**同一个线程**里，由 `Cluster` 依次调用。

所以解法按阶段写：`Cluster` 先让所有 worker 执行完第 1 阶段（只发消息），再让所有 worker 执行第 2 阶段
（把这一阶段该收的消息收完），依此类推。阻塞等待或多线程的写法在这个环境里会卡死。

## 思路

- **base：按值分片（shuffle by value）**
  1. 每个 worker 统计本地每个值的次数。
  2. 每个值只归一个 owner 管（`floorMod(v, k)`）。每个 worker 给每个 owner **只发一条打包好的消息**
     `"F v:c v:c ..."`，没有数据就发空的 `"F"`。这样 owner 收满 k 条就知道齐了，不需要额外的 DONE 消息。
  3. owner 把次数加起来，得到它负责的值的全局精确次数，挑出最好的一个发给 worker 0。
  4. worker 0 从 k 个候选里再挑出最好的。

  原始数据不移动，流量是 O(每个 worker 的不同值个数)。重复越多越省；如果几乎每个值都只出现一次，
  流量会接近原始数据量。可以提的优化：用树状汇总减少 k² 条消息；如果允许近似，可以用 Misra–Gries 或
  Count-Min Sketch 找高频元素。

- **FU1：在值域上二分**
  1. worker 0 先收集每个 worker 的数据个数，算出总数 n。
  2. 每一轮三个阶段：worker 0 广播两个猜测值 → 每个 worker 回复本地 ≤ 猜测值的个数（本地数据只排序一次）
     → worker 0 汇总，移动二分的上下界。二分的状态放在 worker 0 里，`Cluster` 只负责调度阶段。
  3. 值域是 [−10⁹, 10⁹]，大约 31 轮就收敛。偶数个时两个中间位置在同一轮里一起二分。

  流量是 O(k · log 值域)，跟数据规模无关。

## 实现细节

- `receive()` 读到 `""` 就说明阶段顺序写错了（某条消息还没发）。`next()` 在这种情况下直接报错，
  否则 `while` 循环会一直空转，表现成超时。
- 只用 `Worker` 自己的 `sendAsyncMessage` / `receive()` 通信，不要直接调用 `cluster.*`。
- 为了测试网络用量，在 `Cluster` 上加了一个 `messagesSent()`，其他 harness 代码没动。
- 求平均时用 `long` 相加，避免两个 10⁹ 相加时 int 溢出。
