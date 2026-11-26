# AC2 — 修 bug（Meta Structured 风格）

## 先做什么

1. **先自己复现 bug，不要先问 AI**：写出输入 / 预期 / 实际，再写自己的假设。
   提示：`put` 一个已有的 key 时，它的行为和 `get` 应该一致吗？
2. 带着复现结果和假设去问 AI，修完跑全套回归。
3. 练完把 prompt 记录 + AI 输出 + 你的 review 贴回 Interview 对话。

## 怎么跑（Windows + VS Code 也一样）

```powershell
javac SimpleCache.java SimpleCacheTest.java
java SimpleCacheTest
```

先确认 `javac -version` 有输出（需要 JDK 18+；没有的话装一个 Temurin JDK 21）。
回归套件 8 个 case，修好后应该 8/8 PASS，退出码 0。

## 顺带验证你的 Claude Code 环境

这个文件夹正好可以当 smoke test：VS Code 里打开它，让 Claude Code
讲一遍 `SimpleCache` 的数据结构（HashMap + 双向链表），再按上面
的流程做 AC2。如果它改代码前先给你看 diff，说明验收流程是通的。

注意：练习只用这里的代码，不要把 Meta 的代码贴进任何外部 AI。
