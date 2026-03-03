package com.rzf.annotationrunner;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 运行前选择要执行的方法（多选）
 */
final class MethodSelectDialog extends DialogWrapper {

    private final JBList<RunAnnotatedMethodsAction.MethodSpec> list;
    private final List<RunAnnotatedMethodsAction.MethodSpec> allMethods;

    MethodSelectDialog(Project project, List<RunAnnotatedMethodsAction.MethodSpec> methods) {
        super(project);
        allMethods = methods;
        list = new JBList<>(methods);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        for (int i = 0; i < methods.size(); i++) {
            list.addSelectionInterval(i, i);
        }
        setTitle("选择要运行的方法");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new java.awt.Dimension(400, 200));
        return scroll;
    }

    List<RunAnnotatedMethodsAction.MethodSpec> getSelectedMethods() {
        int[] indices = list.getSelectedIndices();
        List<RunAnnotatedMethodsAction.MethodSpec> selected = new ArrayList<>(indices.length);
        for (int i : indices) {
            selected.add(allMethods.get(i));
        }
        return selected;
    }
}
