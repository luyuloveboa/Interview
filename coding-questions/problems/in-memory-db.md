# Design In-memory Database with Backup

内存数据库：每条记录用一个字符串 `key` 标识，记录里有若干 `field -> value`，全是字符串。

| 版本 | 新增 |
|---|---|
| base | `setData`、`getData`（缺失返回 `""`）、`deleteData`（删掉返回 `true`，不存在返回 `false`） |
| FU1 | `scanData(key)`、`scanDataByPrefix(key, prefix)`：按字段名字典序，格式 `"field(value)"` |
| FU2 | 所有操作的带时间戳版本 `xxxAt(..., timestamp)`；`setDataAtWithTtl`：字段在 `[timestamp, timestamp + ttl)` 内存在 |
| FU3 | `backup(timestamp)`：保存状态（含剩余 TTL），返回非空且未过期的记录数；`restore(timestamp, timestampToRestore)`：恢复到 `timestampToRestore` 当时或之前最近的一次备份，TTL 从 `timestamp` 重新算 |

## 思路

- **base**：`Map<key, Map<field, value>>`。删掉最后一个字段时把整条记录也删掉。FU3 要数"非空记录"，从一开始就只存非空记录，后面最省事。
- **FU1**：内层换成 `TreeMap`。同一个前缀的字段在排序后是连续的一段，而且从前缀本身开始，
  所以 `tailMap(prefix, true)` 往后走，遇到第一个不以 prefix 开头的就 `break`。空前缀 = 全部，`scanData` 直接复用。
  复杂度 O(log F + 命中数)。
- **FU2**
  - 值换成 `Entry {value, expireAt}`，在 `[设置时间, expireAt)` 内存活，没有 TTL 的 `expireAt = Long.MAX_VALUE`。
  - **惰性过期**：不做后台清理，读 / 删 / 扫描碰到过期的字段时顺手删掉。过期的字段对所有操作都等于不存在，
    所以 `deleteDataAt` 对过期字段返回 `false`（例 2 最后一步）。
  - 覆盖写会连 TTL 一起覆盖：`setDataAt` 覆盖一个带 TTL 的字段后，这个字段就永不过期了；新的 TTL 也可以比原来的短。
  - 旧接口直接转调新接口，时间戳随便填一个固定值（代码里是 0）。题目保证同一个测试不会混用两套接口，
    而旧接口写进去的字段永不过期，所以时间填几都不影响结果。
  - `timestamp + ttl` 用 `long` 算，避免溢出。
- **FU3**
  - `TreeMap<备份时间, 快照>`，恢复时 `floorEntry(timestampToRestore)`。
  - 快照里存**剩余 TTL**（`expireAt - 备份时间`），不存绝对过期时间。恢复时 `expireAt = 恢复时间 + 剩余 TTL`。
    例子：字段在 1 设置、TTL 10（到 11 过期），5 备份时剩 6，10 恢复后到 16 过期。
  - 快照只放备份那一刻还活着的字段，空记录不放；返回值就是快照里的记录数。
  - 恢复是整体替换：备份之后写的东西全部消失。快照本身不会被之后的操作改到（恢复时新建 `Entry`），
    所以同一份备份可以恢复多次。
  - 每次备份是一次全量拷贝，O(总字段数)。面试官问"备份很频繁怎么办"，可以说 copy-on-write / 持久化数据结构，
    把每次备份的代价降到 O(改动量)。

## 题目里有问题的地方

- base 例子最后一行写的是 `{"A": {"c": "F"}}`，应为大写 `"C"`。
- FU2 里 `deleteDataAt` 只说了"key 不存在返回 false"；字段不存在、字段已过期也都返回 `false`（例 2 就是过期的情况）。
- FU2 例 2 的解释里方法名写成 `setDataAtwithTtl`、值写成小写 `"z"`，都是笔误。
- FU3 例子的方法名写成 `setAtWithTtl`，正文是 `setDataAtWithTtl`，测试里用的是后者。
- FU3 说备份要保存"所有记录和字段的剩余 TTL"，但 TTL 只设在字段上，记录本身没有 TTL。
- FU3 没说恢复之后，比被恢复的那次更新的备份还能不能用。代码里所有备份都一直保留，之后照样可以恢复到它们。
