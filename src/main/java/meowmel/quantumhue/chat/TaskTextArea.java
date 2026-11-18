package meowmel.quantumhue.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.12.2 没有多行输入框，这里补一个够用的：回车换行、方向键、退格/删除、Ctrl+V、点击定位光标。
 * 不做选区，任务板的说明/检查项/评论都不需要富文本编辑。
 */
final class TaskTextArea {

    private static final int LINE_H = 10;

    private final List<Line> lines = new ArrayList<>();

    private int x;
    private int y;
    private int width;
    private int height;
    private int maxLength = 500;
    private int cursor;
    private int scrollLine;
    private boolean focused;
    private String text = "";

    void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(1, width);
        this.height = Math.max(LINE_H, height);
        layout();
    }

    void setMaxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        if (text.length() > this.maxLength) {
            text = text.substring(0, this.maxLength);
            cursor = Math.min(cursor, text.length());
        }
    }

    String getText() {
        return text;
    }

    void setText(String value) {
        text = value == null ? "" : value;
        if (text.length() > maxLength) {
            text = text.substring(0, maxLength);
        }
        cursor = text.length();
        scrollLine = 0;
        layout();
    }

    void setFocused(boolean focused) {
        this.focused = focused;
    }

    boolean isFocused() {
        return focused;
    }

    void draw(String placeholder) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        layout();
        if (text.isEmpty()) {
            font.drawStringWithShadow(placeholder == null ? "" : placeholder,
                    x + 2, y + 2, ChatColors.TEXT_SECONDARY);
            if (focused) {
                drawCaret(font, x + 2, y + 2);
            }
            return;
        }
        int visible = Math.max(1, height / LINE_H);
        int end = Math.min(lines.size(), scrollLine + visible);
        for (int i = scrollLine; i < end; i++) {
            Line line = lines.get(i);
            int ly = y + 2 + (i - scrollLine) * LINE_H;
            font.drawStringWithShadow(line.text, x + 2, ly, ChatColors.TEXT_PRIMARY);
            if (focused && cursor >= line.start && cursor <= line.end) {
                String before = text.substring(line.start, Math.min(cursor, line.end));
                int cx = x + 2 + font.getStringWidth(before);
                drawCaret(font, cx, ly);
            }
        }
    }

    private void drawCaret(FontRenderer font, int cx, int cy) {
        if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
            net.minecraft.client.gui.Gui.drawRect(cx, cy - 1, cx + 1, cy + font.FONT_HEIGHT, ChatColors.ACCENT);
        }
    }

    boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }
        focused = true;
        layout();
        int visible = Math.max(1, height / LINE_H);
        int lineIndex = scrollLine + (mouseY - y - 2) / LINE_H;
        if (lineIndex < 0) {
            lineIndex = 0;
        }
        if (lineIndex >= lines.size()) {
            lineIndex = Math.max(0, lines.size() - 1);
        }
        if (lines.isEmpty()) {
            cursor = 0;
            return true;
        }
        Line line = lines.get(lineIndex);
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String part = font.trimStringToWidth(text.substring(line.start, line.end),
                Math.max(0, mouseX - x - 2));
        cursor = line.start + part.length();
        ensureCursorVisible();
        return true;
    }

    boolean keyTyped(char typedChar, int keyCode) {
        if (!focused) {
            return false;
        }
        if (GuiScreen.isCtrlKeyDown() && keyCode == Keyboard.KEY_V) {
            insert(GuiScreen.getClipboardString());
            return true;
        }
        switch (keyCode) {
            case Keyboard.KEY_BACK:
                if (cursor > 0) {
                    text = text.substring(0, cursor - 1) + text.substring(cursor);
                    cursor--;
                }
                layout();
                ensureCursorVisible();
                return true;
            case Keyboard.KEY_DELETE:
                if (cursor < text.length()) {
                    text = text.substring(0, cursor) + text.substring(cursor + 1);
                }
                layout();
                ensureCursorVisible();
                return true;
            case Keyboard.KEY_LEFT:
                if (cursor > 0) cursor--;
                ensureCursorVisible();
                return true;
            case Keyboard.KEY_RIGHT:
                if (cursor < text.length()) cursor++;
                ensureCursorVisible();
                return true;
            case Keyboard.KEY_HOME:
                cursor = lineStart(cursor);
                ensureCursorVisible();
                return true;
            case Keyboard.KEY_END:
                cursor = lineEnd(cursor);
                ensureCursorVisible();
                return true;
            case Keyboard.KEY_UP:
                moveVertical(-1);
                return true;
            case Keyboard.KEY_DOWN:
                moveVertical(1);
                return true;
            case Keyboard.KEY_RETURN:
                insert("\n");
                return true;
            default:
                break;
        }
        if (typedChar >= 32 && typedChar != 127) {
            insert(String.valueOf(typedChar));
            return true;
        }
        return false;
    }

    private void insert(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        String cleaned = value.replace("\r", "").replace('\t', ' ');
        int room = maxLength - text.length();
        if (room <= 0) {
            return;
        }
        if (cleaned.length() > room) {
            cleaned = cleaned.substring(0, room);
        }
        text = text.substring(0, cursor) + cleaned + text.substring(cursor);
        cursor += cleaned.length();
        layout();
        ensureCursorVisible();
    }

    private int lineStart(int index) {
        layout();
        for (Line line : lines) {
            if (index >= line.start && index <= line.end) {
                return line.start;
            }
        }
        return 0;
    }

    private int lineEnd(int index) {
        layout();
        for (Line line : lines) {
            if (index >= line.start && index <= line.end) {
                return line.end;
            }
        }
        return text.length();
    }

    private void moveVertical(int delta) {
        layout();
        int current = 0;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (cursor >= line.start && cursor <= line.end) {
                current = i;
                break;
            }
        }
        int target = Math.max(0, Math.min(lines.size() - 1, current + delta));
        Line line = lines.get(target);
        int column = cursor - lines.get(current).start;
        cursor = Math.min(line.end, line.start + Math.max(0, column));
        ensureCursorVisible();
    }

    private void ensureCursorVisible() {
        layout();
        int visible = Math.max(1, height / LINE_H);
        int current = 0;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (cursor >= line.start && cursor <= line.end) {
                current = i;
                break;
            }
        }
        if (current < scrollLine) {
            scrollLine = current;
        } else if (current >= scrollLine + visible) {
            scrollLine = current - visible + 1;
        }
        scrollLine = Math.max(0, Math.min(scrollLine, Math.max(0, lines.size() - visible)));
    }

    private void layout() {
        lines.clear();
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int maxWidth = Math.max(1, width - 4);
        int paragraphStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            boolean end = i == text.length();
            if (!end && text.charAt(i) != '\n') {
                continue;
            }
            int paragraphEnd = i;
            if (paragraphStart == paragraphEnd) {
                lines.add(new Line(paragraphStart, paragraphEnd, ""));
            } else {
                int start = paragraphStart;
                int lineStart = start;
                int lastSpace = -1;
                int w = 0;
                for (int j = start; j < paragraphEnd; j++) {
                    char c = text.charAt(j);
                    int cw = font.getCharWidth(c);
                    if (c == ' ') {
                        lastSpace = j;
                    }
                    if (w + cw > maxWidth && j > lineStart) {
                        int breakAt = lastSpace > lineStart ? lastSpace + 1 : j;
                        lines.add(new Line(lineStart, breakAt, text.substring(lineStart, breakAt)));
                        lineStart = breakAt;
                        w = 0;
                        lastSpace = -1;
                        j = breakAt - 1;
                        continue;
                    }
                    w += cw;
                }
                lines.add(new Line(lineStart, paragraphEnd, text.substring(lineStart, paragraphEnd)));
            }
            paragraphStart = i + 1;
        }
        if (lines.isEmpty()) {
            lines.add(new Line(0, 0, ""));
        }
    }

    private static final class Line {
        final int start;
        final int end;
        final String text;

        Line(int start, int end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }
}
