package com.googlecode.jslint4java.eclipse.builder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import com.googlecode.jslint4java.Issue;
import com.googlecode.jslint4java.JSLint;
import com.googlecode.jslint4java.JSLintResult;
import com.googlecode.jslint4java.eclipse.JSLintLog;
import com.googlecode.jslint4java.eclipse.JSLintPlugin;

public class JSLintBuilder extends IncrementalProjectBuilder {

    class JSLintDeltaVisitor implements IResourceDeltaVisitor {
        private final IProgressMonitor monitor;

        public JSLintDeltaVisitor(IProgressMonitor monitor) {
            this.monitor = monitor;
        }

        /*
         * (non-Javadoc)
         *
         * @see org.eclipse.core.resources.IResourceDeltaVisitor#visit(org.eclipse.core.resources.IResourceDelta)
         */
        public boolean visit(IResourceDelta delta) throws CoreException {
            IResource resource = delta.getResource();
            switch (delta.getKind()) {
            case IResourceDelta.ADDED:
                // handle added resource
                logProgress(monitor, resource);
                checkJavaScript(resource);
                break;
            case IResourceDelta.REMOVED:
                // handle removed resource
                break;
            case IResourceDelta.CHANGED:
                // handle changed resource
                logProgress(monitor, resource);
                checkJavaScript(resource);
                break;
            }
            // return true to continue visiting children.
            return true;
        }
    }

    class JSLintResourceVisitor implements IResourceVisitor {
        private final IProgressMonitor monitor;

        public JSLintResourceVisitor(IProgressMonitor monitor) {
            this.monitor = monitor;
        }

        public boolean visit(IResource resource) {
            logProgress(monitor, resource);
            checkJavaScript(resource);
            // return true to continue visiting children.
            return true;
        }
    }

    // NB! Must match plugin.xml declaration.
    public static final String BUILDER_ID = JSLintPlugin.PLUGIN_ID + ".jsLintBuilder";

    // NB! Must match plugin.xml declaration.
    public static final String MARKER_TYPE = JSLintPlugin.PLUGIN_ID
            + ".javaScriptLintProblem";

    private final JSLintProvider lintProvider = new JSLintProvider();

    public JSLintBuilder() {
        lintProvider.init();
    }

    private void addMarker(IFile file, Issue issue) {
        try {
            IMarker m = file.createMarker(MARKER_TYPE);
            if (m.exists()) {
                m.setAttribute(IMarker.MESSAGE, issue.getReason());
                m.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_WARNING);
                m.setAttribute(IMarker.LINE_NUMBER, issue.getLine());
                m.setAttribute(IMarker.SOURCE_ID, "jslint4java");
            }
            // JSLintLog.logInfo("Added marker for " + issue);
        } catch (CoreException e) {
            JSLintLog.error(e);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.core.internal.events.InternalBuilder#build(int,
     *      java.util.Map, org.eclipse.core.runtime.IProgressMonitor)
     */
    @Override
    protected IProject[] build(final int kind, @SuppressWarnings("rawtypes") Map args,
            IProgressMonitor monitor) throws CoreException {
        ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
            public void run(IProgressMonitor monitor) throws CoreException {
                if (kind == FULL_BUILD) {
                    fullBuild(monitor);
                } else {
                    IResourceDelta delta = getDelta(getProject());
                    if (delta == null) {
                        fullBuild(monitor);
                    } else {
                        incrementalBuild(delta, monitor);
                    }
                }
            }
        }, monitor);
        return null;
    }

    private void checkJavaScript(IResource resource) {
        if (!(resource instanceof IFile)) {
            return;
        }

        IFile file = (IFile) resource;
        if (!file.getName().endsWith(".js")) {
            return;
        }

        // Clear out any existing problems.
        deleteMarkers(file);

        BufferedReader reader = null;
        try {
            JSLint lint = lintProvider.getJsLint();
            // TODO: this should react to changes in the prefs pane instead.
            reader = new BufferedReader(new InputStreamReader(file
                    .getContents(), file.getCharset()));
            JSLintResult result = lint.lint(file.getFullPath().toString(),
                    reader);
            for (Issue issue : result.getIssues()) {
                addMarker(file, issue);
            }
        } catch (IOException e) {
            JSLintLog.error(e);
        } catch (CoreException e) {
            JSLintLog.error(e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    JSLintLog.error(e);
                }
            }
        }
    }

    private void deleteMarkers(IFile file) {
        try {
            file.deleteMarkers(MARKER_TYPE, false, IResource.DEPTH_ZERO);
        } catch (CoreException e) {
            JSLintLog.error(e);
        }
    }

    protected void fullBuild(final IProgressMonitor monitor) throws CoreException {
        try {
            monitor.beginTask("jslint4java", IProgressMonitor.UNKNOWN);
            getProject().accept(new JSLintResourceVisitor(monitor));
        } catch (CoreException e) {
            JSLintLog.error(e);
        } finally {
            monitor.done();
        }
    }

    protected void incrementalBuild(IResourceDelta delta, IProgressMonitor monitor)
            throws CoreException {
        try {
            monitor.beginTask("jslint4java", IProgressMonitor.UNKNOWN);
            delta.accept(new JSLintDeltaVisitor(monitor));
        } finally {
            monitor.done();
        }
    }

    private void logProgress(IProgressMonitor monitor, IResource resource) {
        monitor.subTask("Linting " + resource.getName());
    }
}
