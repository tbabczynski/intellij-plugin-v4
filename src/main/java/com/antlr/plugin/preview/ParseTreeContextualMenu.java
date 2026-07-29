package com.antlr.plugin.preview;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.apache.commons.lang3.StringUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Contextual menu for exporting the parse tree preview (PNG/JPG/SVG without Batik).
 */
class ParseTreeContextualMenu {

    static void showPopupMenu(ParseTreeGraphView parseTreeViewer, MouseEvent event) {
        JPopupMenu menu = new JPopupMenu();

        menu.add(createExportMenuItem(parseTreeViewer, "Export to image (white background)", false));
        menu.add(createExportMenuItem(parseTreeViewer, "Export to image (transparent background)", true));

        menu.show(parseTreeViewer, event.getX(), event.getY());
    }

    private static JMenuItem createExportMenuItem(ParseTreeGraphView parseTreeViewer, String label, boolean useTransparentBackground) {
        JMenuItem item = new JMenuItem(label);
        boolean isMacNativSaveDialog = SystemInfo.isMac && Registry.is("ide.mac.native.save.dialog");

        item.addActionListener(event -> {
            String[] extensions = useTransparentBackground ? new String[]{"png", "svg"} : new String[]{"png", "jpg", "svg"};
            FileSaverDescriptor descriptor = new FileSaverDescriptor("Export Image To", "Choose the destination file", extensions);
            FileSaverDialog dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, (Project) null);

            String fileName = "parseTree" + (isMacNativSaveDialog ? ".png" : "");
            VirtualFileWrapper vf = dialog.save((VirtualFile) null, fileName);

            if (vf == null) {
                return;
            }

            File file = vf.getFile();
            String imageFormat = FileUtilRt.getExtension(file.getName());
            if (StringUtils.isBlank(imageFormat)) {
                imageFormat = "png";
            }

            if ("svg".equalsIgnoreCase(imageFormat)) {
                exportToSvg(parseTreeViewer, file, useTransparentBackground);
            } else {
                exportToImage(parseTreeViewer, file, useTransparentBackground, imageFormat);
            }
        });

        return item;
    }

    private static void exportToImage(ParseTreeGraphView parseTreeViewer, File file, boolean useTransparentBackground, String imageFormat) {
        Dimension size = parseTreeViewer.getPreferredSize();
        int width = Math.max(1, size.width);
        int height = Math.max(1, size.height);
        int imageType = useTransparentBackground ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage bi = UIUtil.createImage(width, height, imageType);
        Graphics graphics = bi.getGraphics();

        if (!useTransparentBackground) {
            graphics.setColor(JBColor.WHITE);
            graphics.fillRect(0, 0, width, height);
        }

        // Paint into preferred-size buffer so export matches full tree, not clipped viewport
        parseTreeViewer.setSize(width, height);
        parseTreeViewer.paint(graphics);
        graphics.dispose();

        try {
            if (!ImageIO.write(bi, imageFormat, file)) {
                Notification notification = new Notification(
                        "antlr.new.notify.group",
                        "Error while exporting parse tree to file " + file.getAbsolutePath(),
                        "unknown format '" + imageFormat + "'?",
                        NotificationType.WARNING
                );
                Notifications.Bus.notify(notification);
            }
        } catch (IOException e) {
            Logger.getInstance(ParseTreeContextualMenu.class)
                    .error("Error while exporting parse tree to file " + file.getAbsolutePath(), e);
        }
    }

    private static void exportToSvg(ParseTreeGraphView parseTreeViewer, File file, boolean useTransparentBackground) {
        try {
            parseTreeViewer.exportSvg(file, useTransparentBackground);
        } catch (IOException e) {
            Logger.getInstance(ParseTreeContextualMenu.class)
                    .error("Error while exporting parse tree to SVG file " + file.getAbsolutePath(), e);
        }
    }
}
