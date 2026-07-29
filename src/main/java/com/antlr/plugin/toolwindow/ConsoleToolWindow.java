package com.antlr.plugin.toolwindow;

import com.antlr.plugin.Icons;
import com.antlr.plugin.listeners.ConsoleListener;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-project console is stored in project user data to avoid multi-project crosstalk
 * when the ToolWindowFactory instance is shared.
 */
public class ConsoleToolWindow implements ToolWindowFactory {
    public static final Topic<ConsoleListener> TOPIC = new Topic<>(ConsoleListener.class);
    public static final String WINDOW_ID = "Antlr tool Console";
    private static final Key<ConsoleView> CONSOLE_KEY = Key.create("antlr.tool.console.view");
    private static final Key<List<Pair<String, ConsoleViewContentType>>> PENDING_KEY =
            Key.create("antlr.tool.console.pending");

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        TextConsoleBuilderFactory factory = TextConsoleBuilderFactory.getInstance();
        TextConsoleBuilder consoleBuilder = factory.createBuilder(project);
        ConsoleView console = consoleBuilder.getConsole();
        project.putUserData(CONSOLE_KEY, console);
        flushPending(project, console);
        Content content = ContentFactory.getInstance().createContent(console.getComponent(), "", false);
        content.setCloseable(false);
        content.setDisposer(() -> {
            console.dispose();
            if (project.getUserData(CONSOLE_KEY) == console) {
                project.putUserData(CONSOLE_KEY, null);
            }
        });
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public void init(@NotNull ToolWindow toolWindow) {
        toolWindow.setIcon(Icons.getToolWindow());
        Project project = toolWindow.getProject();
        if (!project.isDisposed()) {
            project.getMessageBus().connect(toolWindow.getDisposable()).subscribe(TOPIC, (msg, contentType) -> {
                ConsoleView console = project.getUserData(CONSOLE_KEY);
                if (console != null) {
                    console.print(msg, contentType);
                } else {
                    bufferMessage(project, msg, contentType);
                }
            });
        }
    }

    private static void bufferMessage(Project project, String msg, ConsoleViewContentType contentType) {
        List<Pair<String, ConsoleViewContentType>> pending = project.getUserData(PENDING_KEY);
        if (pending == null) {
            pending = new ArrayList<>();
            project.putUserData(PENDING_KEY, pending);
        }
        synchronized (pending) {
            pending.add(Pair.create(msg, contentType));
        }
    }

    private static void flushPending(Project project, ConsoleView console) {
        List<Pair<String, ConsoleViewContentType>> pending = project.getUserData(PENDING_KEY);
        if (pending == null) {
            return;
        }
        synchronized (pending) {
            for (Pair<String, ConsoleViewContentType> item : pending) {
                console.print(item.first, item.second);
            }
            pending.clear();
        }
        project.putUserData(PENDING_KEY, null);
    }
}
