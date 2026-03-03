# Annotation Runner 使用流程概述

## 一、安装

1. 构建插件 zip：在 IDEA 中打开本项目 → Gradle → **intellij** → 双击 **buildPlugin**  
   → 产物：`build/distributions/IntelliJ Platform Plugin-1.0-SNAPSHOT.zip`
2. 在 IDEA：**Settings → Plugins** → 齿轮 → **Install Plugin from Disk...** → 选择上述 zip → 重启。

## 二、日常使用流程

```
1. 在 Java 项目中写好带注解的 public 无参方法（可 static）
   ↓
2. 编译项目（Build → Build Project）
   ↓
3. 打开该 Java 文件
   ↓
4. 二选一：
   · 运行文件中全部可运行方法：Tools → Run Annotated Methods（或 Ctrl+Alt+R）
   · 仅运行当前方法：光标置于方法内 → Tools → Run Current Annotated Method（或 Alt+Shift+R）
   ↓
5. 若有多个方法，会弹出列表勾选后再执行
   ↓
6. 在 Run 窗口查看输出
```

## 三、要点

- **可运行条件**：方法带任意可执行注解（如 `@Test`）、`public`、无参；`@Override` / `@Deprecated` 等会被忽略。
- **快捷键**：Ctrl+Alt+R（全文件） / Alt+Shift+R（当前方法）。
- **类路径**：自动包含当前模块及依赖，减少 ClassNotFoundException。
