package com.antlr.report;

import com.antlr.ApplicationInfo;
import com.antlr.notify.NotifyClientKt;
import com.antlr.plugin.PluginClient;
import com.github.GitHubDeviceAuthApis;
import com.github.Issue;
import com.github.IssueInfo;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.idea.IdeaLogger;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.Consumer;
import com.intellij.xml.util.XmlStringUtil;
import com.ssh.report.StringKt;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static com.ssh.report.StringKt.md5;

public class AnltrErrorReportSubmitter extends ErrorReportSubmitter {
    @Override
    public @NotNull String getReportActionText() {
        return "Report to Developer";
    }

    @Override
    public boolean submit(IdeaLoggingEvent[] events, @Nullable String additionalInfo, @NotNull Component parentComponent, @NotNull Consumer<? super SubmittedReportInfo> consumer) {
        IdeaLoggingEvent ideaLoggingEvent = events[0];
        Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent));
        if (project == null || project.isDisposed() || project.isDefault()) {
            return true;
        }
        new Task.Backgroundable(project, "Submitting...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                buildErrorMsg(project, ideaLoggingEvent, additionalInfo, consumer);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
            }
        }.queue();
        return true;
    }


    private String collectPlugins() {
        List<PluginDescriptor> pluginDescriptors = PluginClient.INSTANCE.collectPlugin();
        if (pluginDescriptors.isEmpty()) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (PluginDescriptor pluginDescriptor : pluginDescriptors) {
            if (!pluginDescriptor.isEnabled()) {
                continue;
            }
            stringBuilder
                    .append(pluginDescriptor.getName())
                    .append("(").append(pluginDescriptor.getPluginId()).append(")")
                    .append(" ").append(pluginDescriptor.getVersion()).append(",");
        }
        if (stringBuilder.toString().endsWith(",")) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        return stringBuilder.toString();
    }

    private void buildErrorMsg(Project project, IdeaLoggingEvent event, @Nullable String additionalInfo,
                               Consumer<? super SubmittedReportInfo> consumer) {
        StringBuilder stringBuilder = new StringBuilder();
        String id = md5(StringKt.title(event.getThrowableText()));
        stringBuilder.append(":warning:_`[Auto Generated Report]-=").append(id).append("=-`_").append("\n");
        stringBuilder.append("## Environments").append('\n');
        stringBuilder.append("> **Plugin version: ").append(ApplicationInfo.getVersion()).append("**").append("\n\n");
        ApplicationInfoEx applicationInfoEx = ApplicationInfoEx.getInstanceEx();
        String edition = ApplicationNamesInfo.getInstance().getEditionName();
        stringBuilder.append(applicationInfoEx.getFullApplicationName()).append("\"")
                .append(edition).append("\"").append("\n");
        stringBuilder.append("Build #").append(applicationInfoEx.getBuild()).append(",").append("built on ").append(DateFormat.getDateInstance(DateFormat.LONG, Locale.US).format(applicationInfoEx.getBuildDate().getTime())).append("\n");
        Properties properties = System.getProperties();
        String javaVersion =
                properties.getProperty("java.runtime.version", properties.getProperty("java.version", "unknown"));
        String arch = properties.getProperty("os.arch", "");
        stringBuilder.append("Runtime version: ").append(javaVersion).append(" ").append(arch).append("\n");
        String vmVersion = properties.getProperty("java.vm.name", "unknown");
        String vmVendor = properties.getProperty("java.vendor", "unknown");
        stringBuilder.append("VM: ").append(vmVersion).append(" by ").append(vmVendor).append("\n");
        stringBuilder.append("Operating system: ").append(SystemInfo.getOsNameAndVersion()).append("\n");
        stringBuilder.append("Last action id: ").append(IdeaLogger.ourLastActionId).append("\n");
        stringBuilder.append("plugins: ").append(collectPlugins()).append("\n");
        if (StringUtils.isNotBlank(additionalInfo)) {
            stringBuilder.append("\n");
            stringBuilder.append("## Additional Info").append("\n");
            stringBuilder.append(additionalInfo).append("\n");
        }
        stringBuilder.append("\n");
        stringBuilder.append("## Stack Trace").append("\n");
        stringBuilder.append("```").append("\n");
        stringBuilder.append(event.getThrowableText()).append("\n");
        stringBuilder.append("```").append("\n");
        GitHubDeviceAuthApis gitHubDeviceAuthApis = new GitHubDeviceAuthApis();
        try {
            IssueInfo.ItemsDTO itemsDTO = gitHubDeviceAuthApis.findIssue("mbtsp/intellij-plugin-v4", id);
            SubmittedReportInfo submittedReportInfo;
            if (itemsDTO != null) {
                submittedReportInfo = new SubmittedReportInfo(itemsDTO.getHtmlUrl(), "Issue#" + itemsDTO.getNumber(), SubmittedReportInfo.SubmissionStatus.DUPLICATE);
            } else {
                String title = "【Plugin submission】";
                title = title + StringKt.title(event.getThrowableText());
                Issue issue = gitHubDeviceAuthApis.issue("https://api.github.com/repos/mbtsp/intellij-plugin-v4/issues", title, stringBuilder.toString());
                submittedReportInfo = new SubmittedReportInfo(issue.getHtmlUrl(), "Issue#" + issue.getNumber(), SubmittedReportInfo.SubmissionStatus.NEW_ISSUE);
            }

            SubmittedReportInfo finalSubmittedReportInfo = submittedReportInfo;
            ApplicationManager.getApplication().invokeLater(() -> {
                consumer.consume(finalSubmittedReportInfo);
                java.util.List<AnAction> anActions = new ArrayList<>();
                anActions.add(new AnAction("View") {
                    @Override
                    public @NotNull ActionUpdateThread getActionUpdateThread() {
                        return ActionUpdateThread.BGT;
                    }

                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        if (StringUtils.isNotBlank(finalSubmittedReportInfo.getURL())) {
                            BrowserUtil.browse(finalSubmittedReportInfo.getURL());
                        }
                    }
                });
                StringBuilder text = new StringBuilder();
                text.append("Report link<b>").append(finalSubmittedReportInfo.getLinkText()).append("</b>");
                if (finalSubmittedReportInfo.getStatus().equals(SubmittedReportInfo.SubmissionStatus.DUPLICATE)) {
                    text.append("【Duplicate】");
                }
                text.append(". ");
                String content = XmlStringUtil.wrapInHtml(text);
                if (StringUtils.isBlank(finalSubmittedReportInfo.getURL())) {
                    NotifyClientKt.notifySuccess(project, "Thanks for the submission", content, new ArrayList<>());
                } else {
                    NotifyClientKt.notifySuccess(project, "Thanks for the submission", content, anActions);
                }

            });

        } catch (Exception e) {
            NotifyClientKt.notify(project, "Report fail", NotificationType.ERROR, e.getMessage(), null);
        }
    }
}
