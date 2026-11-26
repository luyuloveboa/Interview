# GPU Credit System

每个 grant 有 `id`、`amount`，在 `[startTime, expirationTime)` 内有效，不同 grant 可以在时间上重叠。
事件会乱序到达；`getBalance(t)` 要基于目前已知的所有事件来算。

| 版本 | `subtract(amount, t)` 的含义 | 什么时候返回 −1 |
|---|---|---|
| base | 只在 t 这一刻扣掉，不影响之后的时间 | t 时刻的余额 < 0 |
| FU1 | 真正消耗 grant，优先从最早过期的有效 grant 里扣 | 任何 ≤ t 的扣除没有被完全覆盖 |

## 思路

- 写入时只记录：`grants` 列表，加上 `TreeMap<timestamp, amount>` 存扣除。查询时根据已知事件现场计算，
  这样晚到的事件自然会被算进去。
- **base**：t 时刻还有效的 grant 总额，减去恰好在 t 时刻的扣除。
- **FU1**：`grants` 插入时按到期时间排序；查询时用 `subs.headMap(t, true)` 按时间顺序重放所有扣除，
  每次扣除从前往后扫，先扣最早过期的。之后再把 t 时刻还有效的 grant 的剩余量加起来。
  每次查询 O(S·G)；所有时间都在 [0, 1000] 内，完全够用。
