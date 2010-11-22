package com.googlecode.jslint4java.eclipse.builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IActionDelegate;

import com.googlecode.jslint4java.eclipse.JSLintLog;

public class ToggleNatureAction implements IActionDelegate {

    private IStructuredSelection selection;

    public void run(IAction action) {
        for (Object obj : selection.toArray()) {
            IProject project = projectFromSelectedItem(obj);
            if (project != null) {
                toggleNature(project);
            }
        }
    }

    /** Convert to a project, or return null. */
    private IProject projectFromSelectedItem(Object obj) {
        if (obj instanceof IProject) {
            return (IProject) obj;
        } else if (obj instanceof IAdaptable) {
            return (IProject) ((IAdaptable) obj).getAdapter(IProject.class);
        } else {
            return null;
        }
    }

    public void selectionChanged(IAction action, ISelection selection) {
        if (selection instanceof IStructuredSelection) {
            this.selection = (IStructuredSelection) selection;
        }
    }

    /**
     * Toggles sample nature on a project
     *
     * @param project
     *            to have sample nature added or removed
     */
    private void toggleNature(IProject project) {
        try {
            IProjectDescription description = project.getDescription();
            List<String> natures = new ArrayList<String>(Arrays.asList(description.getNatureIds()));
            if (natures.contains(JSLintNature.NATURE_ID)) {
                // Remove the nature.
                natures.remove(JSLintNature.NATURE_ID);
            } else {
                // Add the nature.
                natures.add(JSLintNature.NATURE_ID);
            }
            description.setNatureIds(natures.toArray(new String[natures.size()]));
            project.setDescription(description, null);
        } catch (CoreException e) {
            JSLintLog.logError(e);
        }
    }

}
