package com.rzf.annotationrunner;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class RunAnnotatedMethodsAction extends AnAction {

    /** 不执行这些注解（仅标记用） */
    private static final Set<String> SKIP_ANNOTATIONS = Set.of(
            "java.lang.Override",
            "java.lang.Deprecated",
            "java.lang.SuppressWarnings",
            "java.lang.SafeVarargs",
            "java.lang.FunctionalInterface"
    );

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabled(project != null && psiFile instanceof PsiJavaFile);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;

        PsiFile psiFile = event.getData(CommonDataKeys.PSI_FILE);
        if (!(psiFile instanceof PsiJavaFile javaFile)) {
            Messages.showMessageDialog(project, "请先打开一个 Java 文件再运行。", "Annotation Runner", Messages.getInformationIcon());
            return;
        }

        List<MethodSpec> toRun = collectRunnableMethods(javaFile);
        if (toRun.isEmpty()) {
            Messages.showMessageDialog(project,
                    "当前文件中没有可运行的方法。\n请确保方法带有可执行注解，且为 public、无参（可 static）。",
                    "Annotation Runner",
                    Messages.getInformationIcon());
            return;
        }

        // 多个方法时弹出选择对话框
        if (toRun.size() > 1) {
            MethodSelectDialog dialog = new MethodSelectDialog(project, toRun);
            if (!dialog.showAndGet()) return;
            toRun = dialog.getSelectedMethods();
            if (toRun.isEmpty()) return;
        }

        for (MethodSpec spec : toRun) {
            runJavaMethod(project, spec.className, spec.methodName, spec.isStatic);
        }
    }

    /** 收集文件中所有可运行方法（含内部类） */
    static List<MethodSpec> collectRunnableMethods(PsiJavaFile javaFile) {
        List<MethodSpec> out = new ArrayList<>();
        for (PsiClass psiClass : collectAllClasses(javaFile)) {
            String className = psiClass.getQualifiedName();
            if (className == null) continue;
            for (PsiMethod method : psiClass.getMethods()) {
                if (!isExecutableMethod(method)) continue;
                String ann = hasRunnableAnnotation(method);
                if (ann != null) {
                    out.add(new MethodSpec(className, method.getName(), method.hasModifierProperty(PsiModifier.STATIC)));
                }
            }
        }
        return out;
    }

    /** 递归收集顶层类与内部类 */
    private static List<PsiClass> collectAllClasses(PsiJavaFile javaFile) {
        List<PsiClass> out = new ArrayList<>();
        for (PsiClass c : javaFile.getClasses()) {
            collectClassesRecursive(c, out);
        }
        return out;
    }

    private static void collectClassesRecursive(PsiClass psiClass, List<PsiClass> out) {
        out.add(psiClass);
        for (PsiClass inner : psiClass.getInnerClasses()) {
            collectClassesRecursive(inner, out);
        }
    }

    static String hasRunnableAnnotation(PsiMethod method) {
        for (PsiAnnotation a : method.getModifierList().getAnnotations()) {
            String q = a.getQualifiedName();
            if (q != null && !SKIP_ANNOTATIONS.contains(q)) {
                return q;
            }
        }
        return null;
    }

    /** 可执行：public、无参；允许 static */
    static boolean isExecutableMethod(PsiMethod method) {
        return method.hasModifierProperty(PsiModifier.PUBLIC)
                && method.getParameterList().isEmpty();
    }

    void runJavaMethod(Project project, String className, String methodName, boolean isStatic) {
        try {
            Module module = findModuleWithClass(project, className);
            if (module == null) {
                String fallback = detectOutputPath(project);
                if (fallback != null) {
                    runWithClassLoader(className, methodName, isStatic, Collections.singletonList(Paths.get(fallback).toUri().toURL()));
                } else {
                    Messages.showMessageDialog(project, "未找到模块或编译输出目录，请先编译项目。", "Annotation Runner", Messages.getWarningIcon());
                }
                return;
            }

            List<URL> classpath = buildClasspath(module);
            if (classpath.isEmpty()) {
                String out = CompilerPaths.getModuleOutputPath(module, false);
                if (out != null) classpath.add(Paths.get(out).toUri().toURL());
            }
            if (!classpath.isEmpty()) {
                runWithClassLoader(className, methodName, isStatic, classpath);
            } else {
                Messages.showMessageDialog(project, "无法确定模块输出路径，请先编译项目。", "Annotation Runner", Messages.getWarningIcon());
            }
        } catch (Exception e) {
            Messages.showMessageDialog(project, "执行失败: " + className + "." + methodName + "\n" + e.getMessage(), "Annotation Runner", Messages.getErrorIcon());
        }
    }

    /** 构建模块及其依赖的类路径（减少 ClassNotFoundException） */
    private List<URL> buildClasspath(Module module) {
        List<URL> urls = new ArrayList<>();
        try {
            String out = CompilerPaths.getModuleOutputPath(module, false);
            if (out != null) urls.add(Paths.get(out).toUri().toURL());
            for (VirtualFile root : OrderEnumerator.orderEntries(module).compileOnly().recursively().getClassesRoots()) {
                String path = root.getPath();
                if (path != null && !path.isEmpty()) {
                    urls.add(Paths.get(path).toUri().toURL());
                }
            }
        } catch (Exception ignored) { }
        return urls;
    }

    private Module findModuleWithClass(Project project, String className) {
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            String out = CompilerPaths.getModuleOutputPath(module, false);
            if (out != null) {
                String relativePath = className.replace('.', '/') + ".class";
                if (new File(out, relativePath).exists()) {
                    return module;
                }
            }
        }
        return null;
    }

    private void runWithClassLoader(String className, String methodName, boolean isStatic, List<URL> classpath) throws Exception {
        URL[] urls = classpath.toArray(new URL[0]);
        try (URLClassLoader loader = new URLClassLoader(urls)) {
            Class<?> clazz = loader.loadClass(className);
            Method method = clazz.getDeclaredMethod(methodName);
            method.setAccessible(true);

            System.out.println("[Annotation Runner] ▶ " + className + "." + methodName);
            Object instance = isStatic ? null : clazz.getDeclaredConstructor().newInstance();
            Object result = method.invoke(instance);
            System.out.println("[Annotation Runner] 结果: " + (result != null ? result : "void"));
        }
    }

    private String detectOutputPath(Project project) {
        String base = project.getBasePath();
        if (base == null) return null;
        String[] possible = {
                base + "/out/production/" + project.getName(),
                base + "/target/classes",
                base + "/build/classes/java/main"
        };
        for (String path : possible) {
            if (new File(path).exists()) {
                return path;
            }
        }
        return possible[0];
    }

    static final class MethodSpec {
        final String className;
        final String methodName;
        final boolean isStatic;

        MethodSpec(String className, String methodName, boolean isStatic) {
            this.className = className;
            this.methodName = methodName;
            this.isStatic = isStatic;
        }

        String displayText() {
            return className + "." + methodName + (isStatic ? " (static)" : "");
        }

        @Override
        public String toString() {
            return displayText();
        }
    }
}
