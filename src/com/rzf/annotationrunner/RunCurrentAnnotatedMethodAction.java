package com.rzf.annotationrunner;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 仅运行当前光标所在的方法（该方法需带可执行注解）。
 */
public class RunCurrentAnnotatedMethodAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiMethod method = getRunnableMethodAtCaret(e);
        e.getPresentation().setEnabled(method != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;

        PsiMethod method = getRunnableMethodAtCaret(event);
        if (method == null) return;

        PsiClass psiClass = PsiTreeUtil.getParentOfType(method, PsiClass.class);
        if (psiClass == null) return;

        String className = psiClass.getQualifiedName();
        if (className == null) return;

        boolean isStatic = method.hasModifierProperty(com.intellij.psi.PsiModifier.STATIC);
        RunAnnotatedMethodsAction action = new RunAnnotatedMethodsAction();
        action.runJavaMethod(project, className, method.getName(), isStatic);
    }

    /** 获取光标处可运行的方法；若当前元素在方法内，则返回该方法（需带可执行注解且 public、无参） */
    private static PsiMethod getRunnableMethodAtCaret(AnActionEvent e) {
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (!(psiFile instanceof com.intellij.psi.PsiJavaFile)) return null;

        PsiElement element = e.getData(LangDataKeys.PSI_ELEMENT);
        if (element == null) {
            element = e.getData(CommonDataKeys.PSI_FILE);
        }
        if (element == null) return null;

        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        if (method == null) return null;

        if (!RunAnnotatedMethodsAction.isExecutableMethod(method)) return null;
        if (RunAnnotatedMethodsAction.hasRunnableAnnotation(method) == null) return null;

        return method;
    }
}
