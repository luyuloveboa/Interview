# Design Banking System

带时间戳的银行系统，时间戳严格递增。

| 版本 | 新增 |
|---|---|
| base | `createAccount`、`deposit`、`transfer`（返回转出方的新余额，失败返回 −1） |
| FU1 | `topSpenders(n)`：按转出总额降序、账户 ID 升序，格式 `"id(总额)"` |
| FU2 | `pay`：扣款并在 24 小时（86,400,000 ms）后返还 2%（向下取整）；`getPaymentStatus` |
| FU3 | `mergeAccounts(id1, id2)`：把 id2 的一切并入 id1 并删除 id2；`getBalance(timeAt)`：历史余额 |

## 思路

- **base**：`Map<id, Account>`。所有余额变化都走同一个 `add()`，后面的 follow-up 主要就是改这里。
- **FU1**：`Account` 加一个 `outgoing` 字段，转出时累加；查询时排序取前 n 个。
- **FU2**
  - 到期时间 = 付款时间 + 固定的 24 小时，而时间戳递增，所以待返现的队列天然按到期时间排好，用 FIFO 就行，不需要堆。
  - **每个操作第一行先调用 `processCashbacks(timestamp)`**，把到期时间 ≤ 当前时间的返现全部入账，
    满足"同一时间戳先返现、再处理操作"。
  - 2% 向下取整写成 `amount / 50`，避免 `amount * 2` 溢出。
  - `pay` 算 outgoing，返现不算。payment ID 只按成功的付款计数。
- **FU3**
  - 每笔 payment 记一个"当前所属账户"；merge 时把 id2 的 payment 都改成 id1，待返现的钱就会进 id1，
    `getPaymentStatus(id2, ...)` 也会返回 `""`。
  - 历史余额：每个账户 ID 一个 `TreeMap<时间, 余额>`，每次余额变化都写一条，用 `floorEntry(timeAt)` 查。
    merge 时给 id2 写一条 `-1`，表示从这一刻起不存在。历史按 ID 存、不放在 `Account` 里，账户删除后历史还在。
  - 返现入账时记在**到期时间**上，不是处理它的那次操作的时间，所以 `getBalance` 查到期那一刻也是对的。

## 题目里有问题的地方

- FU1 例子里三次 `transfer` 的返回值（3500 / 2500 / 500）跟它自己的存款对不上
  （account3 存了 3000，转出 500 后应该是 2500）。正确值是 2500 / 1500 / 2500，测试里用的是正确值。
- FU1 例子的 Output 一行写的是 `["account2(1500)", ...]`，跟它自己的解释（和算出来的结果）
  `["account1(2500)", "account3(1500)", "account2(0)"]` 不一致。以解释为准。
- 例子里方法名有时写成 `topSenders`，题目正文是 `topSpenders`。
- 被 merge 掉的账户 ID 以后能不能重新创建，题目没说。代码允许重新创建，历史余额也能正确区分中间不存在的那段时间。
