# Rectangle Board

`width x height` 的面板，矩形可以重叠，后加的在上面。id 从 1 开始递增，永远不复用。

| 版本 | 新增 |
|---|---|
| base | `addRectangle`、`removeRectangle(id)`、`getAt(x, y)` |
| FU1 | `undo()`：撤销最近一次成功的 add / remove。失败的 remove 和 `getAt` 不进 history |
| FU2 | `removeAt(x, y)`：删掉这个格子最上面的那个矩形，算一个可撤销的操作 |
| FU3 | `batchRemove(x, y)`：删掉覆盖这个格子的所有矩形，整个调用算一个可撤销的操作 |

## 思路

- 覆盖顺序就是 id 的顺序。`rects` 永久保存所有矩形，`TreeSet<Integer> active` 记录哪些 id 还有效。
  `getAt` 从最大的 id 往下找第一个覆盖这个格子的矩形。被删的矩形恢复后，自然回到原来的位置。
- **FU1**：`Deque<List<Integer>> history`，每个操作记录它改动了哪些 id。undo 时把这些 id 的状态翻转。
  因为 history 是栈，撤销某个操作的时候，在它之后的操作都已经撤销了，这些 id 一定还是它当时留下的样子。
  一定要用 `push`，不能用 `add`：`ArrayDeque.add` 加到队尾，`pop` 从队头取，就变成队列了。
- **FU2**：`getAt` + `removeRectangle`，history 自动记录，其他代码不用改。
- **FU3**：先收集所有覆盖这个格子的 id，一次性删掉，再 push 一条 history（一个 id 列表）。`undo` 不用改。
  不能循环调用 `removeRectangle`，否则一次 undo 只能恢复其中一个。
