package com.rzf.annotationrunner;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
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

        List<String> toRun = new ArrayList<>();
        for (PsiClass psiClass : javaFile.getClasses()) {
            for (PsiMethod method : psiClass.getMethods()) {
                if (!isExecutableMethod(method)) continue;
                String runnable = hasRunnableAnnotation(method);
                if (runnable != null) {
                    String className = psiClass.getQualifiedName();
                    if (className != null) {
                        toRun.add(className + "#" + method.getName());
                    }
                }
            }
        }

        if (toRun.isEmpty()) {
            Messages.showMessageDialog(project,
                    "当前文件中没有可运行的方法。\n请确保方法带有 @RunTest 或其它可执行注解，且为 public、非 static、无参。",
                    "Annotation Runner",
                    Messages.getInformationIcon());
            return;
        }

        for (String spec : toRun) {
            int i = spec.lastIndexOf('#');
            String className = spec.substring(0, i);
            String methodName = spec.substring(i + 1);
            runJavaMethod(project, className, methodName);
        }
    }

    private String hasRunnableAnnotation(PsiMethod method) {
        for (PsiAnnotation a : method.getModifierList().getAnnotations()) {
            String q = a.getQualifiedName();
            if (q != null && !SKIP_ANNOTATIONS.contains(q)) {
                return q;
            }
        }
        return null;
    }

    private boolean isExecutableMethod(PsiMethod method) {
        return method.hasModifierProperty(PsiModifier.PUBLIC)
                && !method.hasModifierProperty(PsiModifier.STATIC)
                && method.getParameterList().isEmpty();
    }

    private void runJavaMethod(Project project, String className, String methodName) {
        try {
            Module module = findModuleWithClass(project, className);
            if (module == null) {
                String fallback = detectOutputPath(project);
                if (fallback != null) {
                    runWithClassLoader(className, methodName, fallback);
                } else {
                    Messages.showMessageDialog(project, "未找到模块或编译输出目录，请先编译项目。", "Annotation Runner", Messages.getWarningIcon());
                }
                return;
            }

            String outputPath = CompilerPaths.getModuleOutputPath(module, false);
            if (outputPath == null) {
                outputPath = detectOutputPath(project);
            }
            if (outputPath != null) {
                runWithClassLoader(className, methodName, outputPath);
            } else {
                Messages.showMessageDialog(project, "无法确定模块输出路径，请先编译项目。", "Annotation Runner", Messages.getWarningIcon());
            }
        } catch (Exception e) {
            Messages.showMessageDialog(project, "执行失败: " + className + "." + methodName + "\n" + e.getMessage(), "Annotation Runner", Messages.getErrorIcon());
        }
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

    private void runWithClassLoader(String className, String methodName, String outputPath) throws Exception {
        URLClassLoader loader = new URLClassLoader(new URL[]{Paths.get(outputPath).toUri().toURL()});
        Class<?> clazz = loader.loadClass(className);
        Object instance = clazz.getDeclaredConstructor().newInstance();
        Method method = clazz.getDeclaredMethod(methodName);
        method.setAccessible(true);

        System.out.println("[Annotation Runner] ▶ " + className + "." + methodName);
        Object result = method.invoke(instance);
        System.out.println("[Annotation Runner] 结果: " + (result != null ? result : "void"));
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
}
