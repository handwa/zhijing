# Transformer 架构：从张量到概率

这份讲义只讲一件事：**一个前馈网络块里，张量是怎么一层层走过去的，层与层之间靠什么条件才能接上。**

选择这个主题做图谱的第一份语料，是因为它的每一条依赖都能给出具体判据（形状、值域、统计量、梯度、语义），也就能把「以为懂了、其实判断错了」的地方精确地定位出来。

## 1 前馈网络：从张量到概率

一个最小编码块可以写成下面这条链：

```text
Linear → ReLU → Linear → Softmax → CrossEntropy
```

看起来只有五个组件，但每一步都能接不上的地方各不相同：形状对不对、取值范围合不合适、归一化沿哪一维、梯度有没有被截断、含义是不是那个意思。

### 1.1 Linear 层

**Linear** 层做的是仿射变换：`y = x @ W.T + b`。输入 `x` 的形状通常是 `(N, in_features)`，权重 `W` 的形状是 `(out_features, in_features)`，输出是 `(N, out_features)`。它只动最后一维，前面的维度（批次、序列长度）原样保留。

这也是为什么 **batch** 大小不影响参数量：参数只有 `W` 和 `b` 两块，与 `N` 无关。

```yaml
kg:claim
id: clm.arch.ffn.linear-in-dim
statement: Linear 层要求输入张量的最后一维等于权重的输入维度，否则矩阵乘无法进行。
expected: true
status: verified
conditions:
  - 形状前提：x.shape[-1] == W.shape[1]
  - 前导维自由：x 的前导维可以是任意值，不参与约束
evidence: 输入 x 的形状通常是 (N, in_features)，权重 W 的形状是 (out_features, in_features)。
error_kind: shape
error_why: 把 batch 维当成 in_features 是初学者最常见的错点。
edges:
  - to: clm.arch.dep.shape
    type: prerequisite
    kind: shape
    premise: 只有最后一维对齐才能做矩阵乘
    boundary: 输入至少是二维；一维输入会被当成单样本处理
    counterexample: 把 (N, C) 的 C 与 in_features 混为一谈
    weight: 0.92
```

```yaml
kg:claim
id: clm.arch.ffn.linear-param-batch
statement: Linear 层的参数量与 batch 大小无关。
expected: true
conditions:
  - W 的形状只由 in_features 与 out_features 决定
  - 计算量随 batch 线性增长，但参数量不变
evidence: 参数只有 W 和 b 两块，与 N 无关。
error_kind: shape
error_why: 把「计算量随 batch 增长」误当成「参数变多」。
```

### 1.2 ReLU

**ReLU** 是逐元素激活：`max(0, x)`。它不改变张量形状，只把负半轴压成 0。正因为是逐元素操作，它可以接在任何形状之后，不需要额外的形状对齐。

代价在梯度上：输入为负时导数为 0，该神经元的参数在这一步不会更新；如果一层神经元的输入长期全负，就会整片「死掉」。

```yaml
kg:claim
id: clm.arch.ffn.relu-shape
statement: ReLU 是逐元素操作，不改变输入张量的形状。
expected: true
status: mastered
conditions:
  - 逐元素：输出形状与输入完全一致
  - 不引入需要对齐的新维度
evidence: 它不改变张量形状，只把负半轴压成 0。
error_kind: shape
error_why: 误以为激活会改变通道数。
```

```yaml
kg:claim
id: clm.arch.ffn.relu-zero-grad
statement: 输入为负时 ReLU 的梯度为 0，该处参数在这一步不会更新。
expected: true
status: confused
conditions:
  - 仅在 x<=0 的区间梯度为 0
  - 学习率再大也无法把死掉的神经元救回来
evidence: 输入为负时导数为 0，该神经元的参数在这一步不会更新。
misconception: 以为「梯度小」和「梯度为 0」是一回事，于是加大学习率试图救活。
error_kind: gradient
error_why: 梯度为 0 与梯度很小的处置方式完全不同。
edges:
  - to: clm.arch.dep.grad-relu-dead
    type: affects
    kind: gradient
    premise: 死区一旦形成，梯度通道被永久切断
    boundary: 只在输入持续为负时成立
    counterexample: 换成 LeakyReLU 后负半轴仍有小梯度
    weight: 0.88
```

```yaml
kg:claim
id: clm.arch.ffn.relu-differentiable
statement: ReLU 在整个实数域上处处可微。
expected: false
conditions:
  - x=0 处左右导数不相等，导数不存在
  - 工程上取次梯度 0 或 1 只是约定，不是可微性
evidence: 输入为负时导数为 0；x=0 处左右导数分别为 0 与 1。
misconception: 因为有反向传播能跑通，就认为它处处可微。
error_kind: gradient
error_why: 框架能算梯度 ≠ 数学上可微。
edges:
  - to: clm.arch.ffn.relu-zero-grad
    type: contrasts
    kind: gradient
    premise: 两者都谈 0 附近的行为，但一个是「不可微」，一个是「梯度为零」
    boundary: 只在 x=0 这一点上区分
    counterexample: 无
    weight: 0.6
```

### 1.3 Softmax

**Softmax** 把一组实数压成概率分布：`softmax(z)_i = exp(z_i) / sum_j exp(z_j)`。它沿**类别维**归约，输出与输入同形，且每一行求和为 1。

它有一个常被忽略的性质：**整体平移不变**。给所有 logits 加同一个常数，输出完全不变 —— 这也是数值稳定实现「先减最大值」的依据。

```yaml
kg:claim
id: clm.arch.ffn.softmax-sum
statement: Softmax 的输出沿类别维度求和为 1。
expected: true
status: verified
conditions:
  - 求和必须沿类别维（通常是最后一维）
  - 若沿错误维度求和，得到的数不等于 1
evidence: 每一行求和为 1。
error_kind: statistics
error_why: 归约维度选错，形状对得上但语义全错。
edges:
  - to: clm.arch.ffn.softmax-dim
    type: depends_on
    kind: statistics
    premise: 求和为 1 的前提是沿类别维归约
    boundary: 类别维必须是最后一维或显式指定
    counterexample: 对 (N, C) 沿 N 求和得到的是每类的批内占比
    weight: 0.9
```

```yaml
kg:claim
id: clm.arch.ffn.softmax-dim
statement: 多分类场景下 Softmax 沿类别维（通常是最后一维）归一化，而不是 batch 维。
expected: true
conditions:
  - dim 参数默认取最后一维
  - 沿 batch 维归一化会破坏样本之间的独立性
evidence: 它沿类别维归约，输出与输入同形。
error_kind: statistics
error_why: 归一化维度选错，是「形状没问题但结果全错」的典型。
```

```yaml
kg:claim
id: clm.arch.ffn.softmax-shift
statement: 给所有 logits 加上同一个常数，Softmax 的输出不变。
expected: true
conditions:
  - 数学上严格成立，与常数大小无关
  - 数值实现上减去最大值正是利用这一性质防溢出
evidence: 整体平移不变。给所有 logits 加同一个常数，输出完全不变。
error_kind: range
error_why: 误以为加常数会整体抬高概率。
```

### 1.4 交叉熵损失

**CrossEntropy** 接收的是未归一化的 **logits**，内部已经做了 log-softmax。所以正确的用法是「最后一层 Linear 直接出 logits，送进 CrossEntropy」，而不是先 Softmax 再送。

先 Softmax 再送 CrossEntropy，数学上不等于直接送 logits：链式求导后梯度被额外乘了一个 `p(1-p)` 因子，训练会明显变慢，甚至学不动。

```yaml
kg:claim
id: clm.arch.ffn.ce-logits
statement: CrossEntropy 期望接收未归一化的 logits，内部已完成 log-softmax。
expected: true
conditions:
  - 输入是 logits，不是概率
  - 内部用 log-sum-exp 稳定实现
evidence: CrossEntropy 接收的是未归一化的 logits，内部已经做了 log-softmax。
error_kind: range
error_why: 把「概率」当成输入，等价于多做了一次归一化。
edges:
  - to: clm.arch.ffn.softmax-shift
    type: depends_on
    kind: range
    premise: log-softmax 的稳定实现依赖平移不变性
    boundary: 仅在实数 logits 上成立
    counterexample: 输入已是概率（0~1）时平移不再等价
    weight: 0.7
```

```yaml
kg:claim
id: clm.arch.ffn.ce-double-softmax
statement: 先做 Softmax 再送 CrossEntropy，与直接送 logits 完全等价。
expected: false
status: confused
conditions:
  - 输出值接近但梯度不同
  - 梯度被额外乘上 p(1-p)，收敛显著变慢
evidence: 先 Softmax 再送 CrossEntropy，数学上不等于直接送 logits。
misconception: 只看 loss 数值接近，就认为两者等价，忽略梯度被压缩。
error_kind: gradient
error_why: 错点不在前向数值，而在反向梯度。
edges:
  - to: clm.arch.ffn.ce-logits
    type: refutes
    kind: gradient
    premise: 多一次 Softmax 就多一层雅可比，梯度被二次压缩
    boundary: 当 logits 数值很大时差异更明显
    counterexample: logits 极小时两者数值几乎一致，容易误判为等价
    weight: 0.95
```

## 2 层与层之间的五种依赖

形状对得上只是最低门槛。真正决定「能不能接」的，是下面五类依赖中的至少一类。判断一条连接是否成立，本质上是在问：**这五条里，哪一条被违反了。**

### 2.1 形状依赖

**张量形状**是最容易检查的一层：上游输出的最后一维，必须等于下游期望的输入维。**Linear** 之后接任意逐元素激活（如 **ReLU**、**GELU**）都不需要额外对齐，因为逐元素操作保持形状。

```yaml
kg:claim
id: clm.arch.dep.shape
statement: 形状依赖只要求上游输出的最后一维等于下游期望的输入维度。
expected: true
conditions:
  - 逐元素操作不改变形状，天然满足
  - 需要改变最后一维的操作（如 Linear）必须显式对齐
evidence: 上游输出的最后一维，必须等于下游期望的输入维度。
error_kind: shape
error_why: 只看总元素个数相等，就以为形状可以接。
```

### 2.2 值域依赖

**值域依赖**问的是输出取值范围是否符合下游假设。**Sigmoid** 输出恒为正且落在 (0,1)，二分类可以直接当概率；但多分类若直接用它，各类概率之和不为 1，还需要额外归一化。

```yaml
kg:claim
id: clm.arch.dep.range-sigmoid
statement: Sigmoid 的输出恒为正且落在 (0,1)，二分类可直接作为概率使用。
expected: true
conditions:
  - 二分类且两类互斥时成立
  - 多分类下各类之和不为 1，不能直接当分布
evidence: Sigmoid 输出恒为正且落在 (0,1)。
error_kind: range
error_why: 把二分类的结论套到多分类上。
```

### 2.3 统计量依赖

**LayerNorm** 与 **BatchNorm** 的差别不在公式，而在**沿哪一维算统计量**：LayerNorm 在特征维上算，与 batch 无关；BatchNorm 在 batch 维上算，batch 太小统计量就不可靠。

```yaml
kg:claim
id: clm.arch.dep.stats-dim
statement: LayerNorm 在特征维上计算均值和方差，与 batch 大小无关。
expected: true
conditions:
  - 归一化维度是最后一维（特征维）
  - 因此单样本也能稳定计算
evidence: LayerNorm 在特征维上算，与 batch 无关。
error_kind: statistics
error_why: 把 LayerNorm 当成「按 batch 求均值」。
```

```yaml
kg:claim
id: clm.arch.dep.bn-small-batch
statement: BatchNorm 在 batch=1 时依然稳定。
expected: false
conditions:
  - batch=1 时批内方差为 0，归一化退化为减均值
  - 训练方差估计严重失真
evidence: BatchNorm 在 batch 维上算，batch 太小统计量就不可靠。
misconception: 以为 BatchNorm 有自己的滑动统计量兜底，所以任意 batch 都行。
error_kind: statistics
error_why: 混淆「训练时的批内统计量」与「推理时的滑动统计量」。
edges:
  - to: clm.arch.dep.stats-dim
    type: contrasts
    kind: statistics
    premise: 两者公式相同，差别只在归一化的维度
    boundary: 在 batch>=32 时差异不明显
    counterexample: batch=1 时 BatchNorm 方差为 0
    weight: 0.8
```

### 2.4 梯度路径依赖

**梯度路径依赖**问的是梯度能不能从下游回传到上游。**残差连接**给梯度提供了一条恒等捷径，前提是主分支与捷径的形状一致；**ReLU** 的死区则会直接切断梯度通道。

```yaml
kg:claim
id: clm.arch.dep.grad-residual
statement: 残差连接的恒等捷径要求主分支输出与捷径输入形状一致。
expected: true
conditions:
  - 形状一致时可直接相加
  - 不一致时必须加投影（1x1 卷积或 Linear）对齐
evidence: 残差要求主分支与捷径形状一致。
error_kind: shape
error_why: 忽略通道数变化，直接相加导致形状报错或广播错误。
```

```yaml
kg:claim
id: clm.arch.dep.grad-relu-dead
statement: ReLU 的死区会切断该神经元的梯度通道，使其参数不再更新。
expected: true
conditions:
  - 输入长期为负才会形成死区
  - 使用 LeakyReLU 或 GELU 可缓解
evidence: 输入为负时导数为 0。
error_kind: gradient
error_why: 以为是学习率太小，实际是梯度恒为 0。
```

### 2.5 语义依赖

**语义依赖**是最隐蔽的一层：形状对得上、值域也合适，但含义不对。典型例子是「**ReLU** 之后直接接 **Softmax**」—— 形状完全相容，可 ReLU 的输出不是 logits，Softmax 会把「非负但未归一化」的数当成得分来归一化，训练时梯度分布被扭曲。

```yaml
kg:claim
id: clm.arch.dep.semantic
statement: 只要形状能对上，接什么层在语义上都是正确的。
expected: false
conditions:
  - 形状相容只是必要条件，不是充分条件
  - 语义依赖要求下游对输入的统计含义有明确假设
evidence: 形状对得上、值域也合适，但含义不对。
misconception: 用「没报错」当作「接对了」的证据。
error_kind: semantic
error_why: 框架只检查形状，不检查语义。
edges:
  - to: clm.arch.dep.shape
    type: contrasts
    kind: semantic
    premise: 形状依赖可机器检查，语义依赖只能靠人判断
    boundary: 在逐元素激活之间接续时两者恰好一致
    counterexample: ReLU 之后接 Softmax，形状对但语义错
    weight: 0.85
```

```yaml
kg:claim
id: clm.arch.dep.relu-then-softmax
statement: ReLU 之后可以直接接 Softmax 输出类别概率。
expected: false
status: confused
conditions:
  - 形状依赖满足：ReLU 不改变形状，Softmax 接受同形输入
  - 语义依赖不满足：Softmax 期望未归一化 logits，ReLU 输出全非负，负半轴信息已丢
  - 边界：C=1 时 Softmax 恒输出 1，完全没有区分度
evidence: 形状完全相容，可 ReLU 的输出不是 logits。
misconception: 看到形状能跑通、loss 也在降，就认定这条连接成立。
error_kind: semantic
error_why: 错点落在语义依赖，而不是形状或值域。
edges:
  - to: clm.arch.ffn.relu-shape
    type: depends_on
    kind: shape
    premise: 该判断以「ReLU 保持形状」为前提
    boundary: 一旦换成会改变通道数的激活，形状前提就不成立
    counterexample: 换成会 reshape 的激活层
    weight: 0.9
  - to: clm.arch.dep.semantic
    type: refutes
    kind: semantic
    premise: 它是「形状对就语义对」的反例
    boundary: 仅在分类头场景成立
    counterexample: 无
    weight: 0.95
  - to: clm.arch.ffn.softmax-dim
    type: affects
    kind: statistics
    premise: 归一化维度决定「概率」是不是按类别算的
    boundary: 类别维必须是最后一维
    counterexample: 沿 batch 维归一化
    weight: 0.6
```

## 3 训练稳定性

### 3.1 LayerNorm 与 BatchNorm

两者的公式几乎一样，差别只在统计量的来源。**LayerNorm** 在单样本内部沿特征维归一化，天然适配变长序列和小 batch；**BatchNorm** 依赖批内统计量，推理时改用训练期累积的滑动统计量。

```yaml
kg:claim
id: clm.arch.train.ln-dim
statement: LayerNorm 的归一化统计量在单个样本内部沿特征维计算。
expected: true
conditions:
  - 与 batch 大小无关
  - 序列模型中不必担心变长导致的统计量漂移
evidence: LayerNorm 在单样本内部沿特征维归一化。
error_kind: statistics
error_why: 把 LayerNorm 当成「按 batch 归一化」。
```

```yaml
kg:claim
id: clm.arch.train.bn-infer
statement: BatchNorm 在推理阶段使用训练期累积的滑动统计量，而不是当前 batch 的统计量。
expected: true
conditions:
  - 训练与推理行为不同，切换必须显式调用 eval 模式
  - 忘记切 eval 会导致推理结果随 batch 组成变化
evidence: 推理时改用训练期累积的滑动统计量。
error_kind: statistics
error_why: 忘了切换 eval 模式，是线上精度掉点的常见原因。
```

### 3.2 残差连接

**残差连接**把 `y = F(x) + x` 写进结构里，一方面让梯度有恒等通路，另一方面让网络学「增量」而不是「全量」。

```yaml
kg:claim
id: clm.arch.train.residual-shape
statement: 残差相加前，主分支输出与捷径必须同形状。
expected: true
conditions:
  - 同形状可直接相加
  - 通道数变化时需投影对齐
evidence: 主分支输出与捷径必须同形状。
error_kind: shape
error_why: 通道数改变后忘记加投影。
edges:
  - to: clm.arch.dep.grad-residual
    type: prerequisite
    kind: shape
    premise: 形状一致是梯度能走捷径的前提
    boundary: 仅在恒等捷径下成立
    counterexample: 通道数翻倍但未投影
    weight: 0.9
```

### 3.3 Pre-LN 与 Post-LN

**Pre-LN** 把归一化放在子层之前，残差通路上没有任何归一化，梯度可以无损回传；**Post-LN** 把归一化放在残差相加之后，深层网络训练更依赖 warmup。

```yaml
kg:claim
id: clm.arch.train.pre-ln
statement: 相比 Post-LN，Pre-LN 让残差通路保持无归一化，深层训练更稳定。
expected: true
conditions:
  - 深层（数十层）时差异显著
  - 浅层网络两者差别不大
evidence: Pre-LN 把归一化放在子层之前，残差通路上没有任何归一化。
error_kind: gradient
error_why: 只看单层公式，忽略残差通路上是否夹了归一化。
```

```yaml
kg:claim
id: clm.arch.train.post-ln-deep
statement: Post-LN 在深层网络中不需要 warmup 也能稳定收敛。
expected: false
conditions:
  - 残差通路上夹了归一化，梯度尺度随深度累积
  - 实践中通常需要 warmup 与更小的初始学习率
evidence: Post-LN 把归一化放在残差相加之后，深层网络训练更依赖 warmup。
misconception: 把「浅层能跑通」当成「深层也没问题」。
error_kind: gradient
error_why: 错点在深层梯度尺度，不在单层实现。
edges:
  - to: clm.arch.train.pre-ln
    type: contrasts
    kind: gradient
    premise: 两者唯一差别是归一化相对残差的位置
    boundary: 层数较少时差异可忽略
    counterexample: 40 层 Post-LN 不加 warmup 直接发散
    weight: 0.82
```

## 4 注意力机制

### 4.1 缩放点积注意力

**缩放点积注意力**是 `softmax(QK^T / sqrt(d_k)) V`。除以 `sqrt(d_k)` 不是装饰：点积的方差随 `d_k` 线性增长，不缩放会让 Softmax 进入饱和区，梯度接近 0。

```yaml
kg:claim
id: clm.arch.attn.scale
statement: 除以 sqrt(d_k) 是为了控制点积方差，避免 Softmax 进入饱和区。
expected: true
conditions:
  - 假设 q、k 各分量独立且方差为 1，点积方差约为 d_k
  - d_k 很小时不缩放影响有限
evidence: 点积的方差随 d_k 线性增长。
error_kind: statistics
error_why: 把缩放当成「调参技巧」，不知道它对应方差量级。
```

```yaml
kg:claim
id: clm.arch.attn.no-scale
statement: 不除以 sqrt(d_k)，注意力分布只会更尖锐，模型效果更好。
expected: false
conditions:
  - 分布更尖锐 → Softmax 饱和 → 梯度接近 0
  - d_k 越大问题越严重
evidence: 不缩放会让 Softmax 进入饱和区，梯度接近 0。
misconception: 把「注意力更集中」当成「注意力更准」。
error_kind: statistics
error_why: 尖锐不等于准确，饱和会让梯度消失。
edges:
  - to: clm.arch.attn.scale
    type: refutes
    kind: statistics
    premise: 同一现象（更尖锐）的两种解释，只有一种对应可训练性
    boundary: 仅在 d_k 较大时成立
    counterexample: d_k=8 时几乎看不出差别
    weight: 0.9
```

### 4.2 掩码

**因果掩码**必须在 Softmax **之前**把未来位置置为 `-inf`。先 Softmax 再乘 0 不等价：归一化时未来位置已经参与了分母，概率质量被摊薄，等于让模型偷看了答案。

```yaml
kg:claim
id: clm.arch.attn.mask-before-softmax
statement: 因果掩码必须在 Softmax 之前把被屏蔽位置的得分置为 -inf。
expected: true
conditions:
  - 掩码要参与归一化的分母
  - 用 -inf 而不是 0，才能让 exp 后严格为 0
evidence: 必须在 Softmax 之前把未来位置置为 -inf。
error_kind: statistics
error_why: 以为「乘 0 就等于屏蔽」。
```

```yaml
kg:claim
id: clm.arch.attn.mask-after
statement: 先做 Softmax，再把未来位置乘 0，与先掩码效果相同。
expected: false
conditions:
  - 乘 0 之前，未来位置已经占据了分母的概率质量
  - 归一化被污染，剩余概率之和小于 1
evidence: 先 Softmax 再乘 0 不等价。
misconception: 只检查「未来位置输出是不是 0」，不检查概率之和。
error_kind: statistics
error_why: 错点在归一化分母，不在输出值本身。
edges:
  - to: clm.arch.attn.mask-before-softmax
    type: contrasts
    kind: statistics
    premise: 两者输出在屏蔽位置都接近 0，差别在分母
    boundary: 序列长度>1 时才有差别
    counterexample: 序列长度 1 时两者完全一致
    weight: 0.92
```

### 4.3 多头注意力

**多头注意力**把 `d_model` 切成 `h` 份并行做注意力，每个头在自己的子空间里学一种关系模式，最后拼接再做一次线性投影。各头之间是**子空间划分**关系，不是独立模型。

```yaml
kg:claim
id: clm.arch.attn.mha-concat
statement: 多头注意力的各头输出先拼接，再经过一次线性投影。
expected: true
conditions:
  - 拼接后的维度回到 d_model
  - 投影矩阵负责融合各头信息
evidence: 最后拼接再做一次线性投影。
error_kind: shape
error_why: 忘记最后的投影，维度对不上或信息无法融合。
```

```yaml
kg:claim
id: clm.arch.attn.mha-heads-independent
statement: 多头注意力中各个头是完全独立的模型，互不共享输入。
expected: false
conditions:
  - 各头共享同一份输入，只是投影矩阵不同
  - 头数增加不改变总参数量级（d_model 被切分）
evidence: 把 d_model 切成 h 份并行做注意力。
misconception: 把头当成多个独立小模型，以为参数量随头数翻倍。
error_kind: shape
error_why: 混淆「切分子空间」与「复制模型」。
edges:
  - to: clm.arch.attn.mha-concat
    type: refutes
    kind: shape
    premise: 拼接维度回到 d_model，说明总量并未翻倍
    boundary: 仅在 d_model 被均分时成立
    counterexample: 各头用独立全维投影时参数量确实翻倍
    weight: 0.75
```

### 4.4 位置编码

自注意力对输入顺序是**置换等变**的：把 token 打乱，输出只是跟着打乱，模型本身感知不到「谁在前」。因此必须额外注入位置信息，这就是**位置编码**存在的原因。

```yaml
kg:claim
id: clm.arch.attn.pos-encoding-needed
statement: 纯自注意力对位置不敏感，必须额外注入位置信息才能区分顺序。
expected: true
conditions:
  - 自注意力是置换等变的
  - 位置编码可以是绝对式、相对式或 RoPE，形式不影响结论
evidence: 自注意力对输入顺序是置换等变的。
error_kind: semantic
error_why: 以为「注意力看得到所有 token」就等于「知道顺序」。
```

## 5 常见误区速查

下面这张表是判断题形式的速查清单，用于快速自测。第三条「边界」说明什么情况下结论会翻转 —— 这一列就是深度图谱里「条件」的来源。

```yaml
kg:claim
id: clm.arch.s5.boundary
statement: 速查表里的判断句只在写明的边界内成立，脱离边界直接套用会判错。
expected: true
conditions:
  - 每条判断句都附一条边界说明：什么情况下结论翻转
  - 把「成立」当成无条件成立，等于丢掉了这一列
evidence: 第三条「边界」说明什么情况下结论会翻转 —— 这一列就是深度图谱里「条件」的来源。
error_kind: semantic
error_why: 把「结论成立」直接当成「无条件成立」，边界那一列被当成了注解而不是条件。
explain: 成立。速查表把每条判断句与它的边界并列写出来，就是为了说明「成立」是有前提的：BatchNorm 那条要求显式切到 eval，交叉熵那条要求损失内部做了 log-softmax，前提一变结论就翻转。
```

### 5.1 判断题表

| 判断句 | 是否成立 | 边界 |
|---|---|---|
| ReLU 在整个实数域上处处可微 | 不成立 | x=0 处左右导数不等 |
| Dropout 在推理阶段仍需随机置零 | 不成立 | 推理阶段关闭，训练阶段才随机置零 |
| BatchNorm 在推理阶段使用训练期累积的滑动统计量 | 成立 | 必须显式切换到 eval 模式 |
| 交叉熵对 logits 的梯度等于 softmax 输出减 one-hot | 成立 | 仅当损失内部做 log-softmax 时成立 |
| Adam 优化器不需要设置学习率 | 不成立 | 仍需学习率，只是自适应调整每个参数的步长 |
| Softmax 的输出可以直接当作模型置信度使用 | 不成立 | 未校准的概率分布会系统性偏高 |

## 6 小结

判断一条连接能不能成立，按顺序问五个问题：形状对不对、值域合不合适、统计量沿哪一维、梯度有没有通路、含义是不是那个意思。前四个能用代码检查，第五个只能靠人判断 —— 而绝大多数「看起来能跑但结果不对」的问题，都出在第五个。
