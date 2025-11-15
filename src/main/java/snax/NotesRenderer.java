package snax;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class NotesRenderer {

    public static void renderNotesBox(GuiGraphics guiGraphics, int x, int y, int width, int height,
            boolean isDragMode, boolean isTyping, String noteText, int cursorPosition) {

        // Render a low-opacity background overlay
        int backgroundColor = 0x10000000; // Very transparent background
        guiGraphics.fill(x, y, x + width, y + height, backgroundColor);

        // Add a border around the rectangle
        int borderColor;
        if (isDragMode) {
            borderColor = 0xFFFF0000; // Red when in drag mode
        } else if (isTyping) {
            borderColor = 0xFF00FF00; // Green when typing
        }

        else {
            borderColor = 0xFFFFFFFF; // White otherwise
        }

        guiGraphics.fill(x - 1, y - 1, x + width + 1, y, borderColor); // Top border
        guiGraphics.fill(x - 1, y + height, x + width + 1, y + height + 1, borderColor); // Bottom border
        guiGraphics.fill(x - 1, y, x, y + height, borderColor); // Left border
        guiGraphics.fill(x + width, y, x + width + 1, y + height, borderColor); // Right border

        // Render the text
        if (!noteText.isEmpty() || isTyping) {
            Minecraft mc = Minecraft.getInstance();

            // Split text into lines that fit in the box
            String[] lines = wrapText(noteText, width - 6, mc.font);

            // Render each line with different colors for different content types
            int maxLines = (height - 10) / 10; // Calculate max lines based on height
            for (int i = 0; i < lines.length && i < maxLines; i++) {
                String line = lines[i];
                int color = getLineColor(line);

                guiGraphics.drawString(mc.font, line, x + 3, y + 3 + (i * 10), color);
            }

            // Render cursor when typing
            if (isTyping && shouldShowCursor()) {
                int[] cursorPos = getCursorScreenPosition(x + 3, y + 3, width - 6, mc.font, noteText, cursorPosition);
                guiGraphics.fill(cursorPos[0], cursorPos[1], cursorPos[0] + 1, cursorPos[1] + 9, 0xFFFFFFFF);
            }
        }

        // Show instruction text when not typing
        if (!isTyping && noteText.isEmpty()) {
            renderInstructionText(guiGraphics, x, y, isDragMode);
        }
    }

    private static int getLineColor(String line) {
        if (line.trim().startsWith("/clear")) {
            return 0xFFFF6B6B; // Light red for clear command
        }

        return 0xFFFFFFFF; // Default white
    }

    private static void renderInstructionText(GuiGraphics guiGraphics, int x, int y, boolean isDragMode) {
        Minecraft mc = Minecraft.getInstance();

        if (isDragMode) {
            guiGraphics.drawString(mc.font, "Drag mode: Click & drag", x + 3, y + 3, 0xFFFF0000);
            guiGraphics.drawString(mc.font, "F8 to exit", x + 3, y + 13, 0xFFFF0000);
        } else {
            guiGraphics.drawString(mc.font, "F10 to edit", x + 3, y + 3, 0xFFAAAAAA);
            guiGraphics.drawString(mc.font, "F8 to move", x + 3, y + 13, 0xFFAAAAAA);
            guiGraphics.drawString(mc.font, "F9 to hide", x + 3, y + 23, 0xFFAAAAAA);
            guiGraphics.drawString(mc.font, "Type /clear to reset", x + 3, y + 33, 0xFFFF6B6B);
        }
    }

    private static String[] wrapText(String text, int maxWidth, net.minecraft.client.gui.Font font) {
        java.util.List<String> lines = new java.util.ArrayList<>();

        // First, split by manual line breaks (Enter key presses)
        String[] manualLines = text.split("\n", -1); // -1 to preserve empty strings

        for (String manualLine : manualLines) {
            if (manualLine.isEmpty()) {
                lines.add(""); // Add empty line for manual line breaks
                continue;
            }

            String[] words = manualLine.split(" ");
            String currentLine = "";

            for (String word : words) {
                String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
                if (font.width(testLine) <= maxWidth) {
                    currentLine = testLine;
                } else {
                    if (!currentLine.isEmpty()) {
                        lines.add(currentLine);
                        currentLine = word;
                    } else {
                        // Word is too long, break it into characters
                        for (int i = 0; i < word.length(); i++) {
                            char c = word.charAt(i);
                            if (font.width(currentLine + c) <= maxWidth) {
                                currentLine += c;
                            } else {
                                if (!currentLine.isEmpty()) {
                                    lines.add(currentLine);
                                    currentLine = String.valueOf(c);
                                } else {
                                    // Single character is too wide, add it anyway
                                    lines.add(String.valueOf(c));
                                }
                            }
                        }
                    }
                }
            }

            if (!currentLine.isEmpty()) {
                lines.add(currentLine);
            }
        }

        return lines.toArray(new String[0]);
    }

    private static int[] getCursorScreenPosition(int startX, int startY, int maxWidth,
            net.minecraft.client.gui.Font font,
            String noteText, int cursorPosition) {
        String textToCursor = noteText.substring(0, cursorPosition);
        String[] lines = wrapText(textToCursor, maxWidth, font);

        int lineIndex = Math.max(0, lines.length - 1);
        String currentLine = lines.length > 0 ? lines[lineIndex] : "";

        // Handle the case where cursor is at the end of a line that ends with \n
        if (textToCursor.endsWith("\n")) {
            lineIndex++;
            currentLine = "";
        }

        int x = startX + font.width(currentLine);
        int y = startY + (lineIndex * 10);

        return new int[] { x, y };
    }

    private static boolean shouldShowCursor() {
        return (System.currentTimeMillis() / 500) % 2 == 0; // Blink every 500ms
    }
}
