# Annotation Runner

在 IntelliJ IDEA 中扫描并运行带有注解的 Java 方法（如 `@RunTest`），无需写 main 或单元测试。

- **菜单**：**Tools → Run Annotated Methods**
- **要求**：方法为 `public`、非 `static`、无参，且带有任意可执行注解（如 `@Test`、自定义 `@RunTest`；`@Override`、`@Deprecated` 等会被忽略）。

---

## 构建可安装的插件包

### 方式一：在 IntelliJ IDEA 中构建（推荐）

1. 用 **IntelliJ IDEA** 打开本项目（打开包含 `build.gradle` 的目录）。
2. 右侧打开 **Gradle** 工具窗口。
3. 展开 **IntelliJ Platform Plugin** → **intellij**，双击 **buildPlugin**。
4. 构建完成后，插件 zip 位于：
   ```
   build/distributions/IntelliJ Platform Plugin-1.0-SNAPSHOT.zip
   ```

### 方式二：命令行构建

若已安装 [Gradle](https://gradle.org/install/)：

```bash
cd "IntelliJ Platform Plugin"
gradle buildPlugin
```

生成的 zip 同样在 `build/distributions/` 下。

---

## 安装插件

1. 打开 **IntelliJ IDEA**。
2. **File → Settings**（Windows/Linux）或 **IntelliJ IDEA → Settings**（macOS）。
3. 左侧选择 **Plugins**，点击齿轮图标，选 **Install Plugin from Disk...**。
4. 选择上面构建得到的 zip 文件（如 `IntelliJ Platform Plugin-1.0-SNAPSHOT.zip`）。
5. 重启 IDEA。

---

## 使用方式

1. 在任意 Java 项目中，给要运行的方法加上**任意注解**（例如 JUnit 的 `@Test`、或自定义的 `@RunTest`）：
   ```java
   import org.junit.Test;  // 或自定义注解

   public class MyClass {
       @Test
       public void myMethod() {
           System.out.println("Hello from Annotation Runner!");
       }
   }
   ```
   `@Override`、`@Deprecated`、`@SuppressWarnings` 等仅用于标记的注解会被自动跳过，不会执行。

2. 确保方法为 **public、非 static、无参**，且项目已编译（Build → Build Project）。

3. 在编辑器中打开该 Java 文件，菜单选择 **Tools → Run Annotated Methods**。

4. 插件会依次执行当前文件中所有符合条件的方法，输出在 **Run** 窗口。

---

## 技术说明

- **适用版本**：IntelliJ IDEA 2023.2 (232) ～ 2024.1.x (241.*)。
- **运行逻辑**：从当前模块或项目编译输出目录加载类，通过反射创建实例并调用方法。
