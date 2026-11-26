# coding-questions

五道带 follow-up 的面试题，Java 实现。每道题的每个 follow-up 都在上一个版本的基础上改，
新增 / 改动的代码用注释标出来：`// [FU2 NEW]`、`// [FU2 CHANGED]`。
对比相邻两个版本，就能看到这一步到底改了什么。

| 题目 | 代码 | 题面 + 思路 |
|---|---|---|
| Plant Disease Spread（植物病害扩散，base + FU1–FU4） | `src/plantdisease/{base,fu1,fu2,fu3,fu4}/Solution.java` | [problems/plant-disease.md](problems/plant-disease.md) |
| GPU Credit System（GPU 额度系统，base + FU1） | `src/gpucredit/{base,fu1}/CreditSystem.java` | [problems/gpu-credit.md](problems/gpu-credit.md) |
| Rectangle Board（矩形面板，base + FU1–FU3） | `src/rectangleboard/{base,fu1,fu2,fu3}/RectangleBoard.java` | [problems/rectangle-board.md](problems/rectangle-board.md) |
| Find Cluster Mode and Median（分布式众数 / 中位数，base + FU1） | `src/clustermode/{base,fu1}/Cluster.java` | [problems/cluster-mode-median.md](problems/cluster-mode-median.md) |
| Repair Bootloader Program（修复引导程序，base + FU1） | `src/bootloader/{base,fu1}/Solution.java` | [problems/bootloader.md](problems/bootloader.md) |

## 目录结构

```
coding-questions/
├── src/                     # 解法，每个版本一个 package
│   ├── plantdisease/base … fu4/
│   ├── gpucredit/base, fu1/
│   ├── rectangleboard/base … fu3/
│   ├── clustermode/base, fu1/
│   └── bootloader/base, fu1/
├── test/                    # 测试（只依赖 JDK，不需要 JUnit / Maven）
│   ├── AllTests.java        # 跑全部测试的入口
│   ├── plantdisease/PlantDiseaseTest.java
│   ├── gpucredit/CreditSystemTest.java
│   ├── rectangleboard/RectangleBoardTest.java
│   ├── clustermode/ClusterModeTest.java
│   ├── bootloader/BootloaderTest.java
│   └── testutil/Check.java
├── problems/                # 题面、思路、题目里没说清楚的地方
├── run-tests.sh             # macOS / Linux
├── run-tests.ps1            # Windows PowerShell
└── .vscode/                 # VS Code 配置（源码路径 + 运行配置）
```

测试包括（每道题的每个版本至少 10 个手写 case）：
- 题目里的所有例子；
- 几个专门针对容易犯的 bug 的 case（比如 FU2 恢复后被重复感染、undo 用成了队列）；
- 随机对拍：植物病害每个版本都跟一个慢但显然正确的逐天暴力模拟比；
  矩形面板跟一个"每次修改前存一份完整快照"的参考实现比；
  分布式众数 / 中位数跟直接在整个数组上算的结果比；
  引导程序跟"把每一条 jump/next 都翻转试一遍"的暴力解比。

## 在 VS Code 里运行

1. **装 JDK 17 或更高版本**（推荐 [Temurin JDK 21](https://adoptium.net/)）。
   在终端里跑 `java -version` 和 `javac -version`，两个都要有输出。
2. **装 VS Code 插件** "Extension Pack for Java"（Microsoft 出的）。
3. **用 VS Code 打开 `coding-questions` 这个文件夹**：File → Open Folder，选 `coding-questions`，
   不要选整个 `Interview` 仓库，否则 `.vscode/settings.json` 里的源码路径不会生效。
   第一次打开等右下角 Java 插件加载完（状态栏显示 "Java: Ready"）。
4. **运行测试**，任选一种：
   - **Run and Debug 面板**（左侧栏 ▷ 图标，或 `Ctrl+Shift+D` / `Cmd+Shift+D`）：
     顶部下拉框选 "All tests"，按 F5。也可以只选某一道题的测试。
   - **直接点 Run**：打开 `test/AllTests.java`，点 `main` 方法上方的 "Run"。
   - **用终端**（`` Ctrl+` `` 打开）：
     ```bash
     ./run-tests.sh          # macOS / Linux
     ```
     ```powershell
     .\run-tests.ps1         # Windows PowerShell
     ```
     如果 PowerShell 提示不允许运行脚本，先执行一次
     `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`。

全部通过时最后一行是：

```
ALL PASSED (232 checks)
```

有失败的话会打印 `FAIL <名字>: got ..., expected ...`，退出码为 1。

## 不用脚本，手动编译

```bash
javac -d out $(find src test -name '*.java')
java -cp out AllTests
```

## 想自己练

把某个版本的方法体删掉，自己重写，再跑测试；随机对拍能抓到例子覆盖不到的 bug。
