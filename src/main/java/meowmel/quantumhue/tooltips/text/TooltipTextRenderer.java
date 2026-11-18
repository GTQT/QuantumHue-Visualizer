package meowmel.quantumhue.tooltips.text;

import meowmel.quantumhue.QuantumHue;
import meowmel.quantumhue.QuantumHueConfig;
import meowmel.quantumhue.tooltips.TooltipContent;
import meowmel.quantumhue.tooltips.TooltipLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tooltip 字体特效渲染器。
 *
 * <p>逐帧状态全部基于 {@link System#currentTimeMillis()}，不依赖 GL 帧号，
 * 因此同一帧内重复渲染也不会错位。正文稳定解析一次后缓存，稳态只做一次
 * {@code drawString}；只有带 {@code [故障]} 标记的片段在爆发窗口内才会逐字形重绘。</p>
 *
 * <p>特效由行内触发标签决定（见 {@link TooltipTextMarkup}），不匹配任何具体文案。</p>
 */
public class TooltipTextRenderer {

    private static final int CACHE_LIMIT = 96;
    private static final long TOOLTIP_RESTART_GAP_MS = 600L;

    private final Map<String, GlyphLine> cache = new LinkedHashMap<String, GlyphLine>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, GlyphLine> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    /** 每个 Tooltip 独立的打字机时钟（装备对比会同时显示多个 Tooltip）。 */
    private static final class Timing {
        final long start;
        long lastSeen;
        final float charsPerMs;

        Timing(long start, float charsPerMs) {
            this.start = start;
            this.lastSeen = start;
            this.charsPerMs = charsPerMs;
        }
    }

    private final Map<String, Timing> timings = new LinkedHashMap<String, Timing>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Timing> eldest) {
            return size() > 8;
        }
    };

    private FontRenderer font;
    private long frameTime;
    private Timing current;
    private boolean failed;

    /** 每帧开始时调用一次。检测 Tooltip 重新打开/翻页并重置打字机时钟。 */
    public void beginFrame(TooltipContent content, List<TooltipLine> lines, FontRenderer font) {
        if (font == null) return;
        if (this.font != font) {
            this.font = font;
            cache.clear();
        }
        long now = System.currentTimeMillis();
        frameTime = now;

        String key = (content.cacheKey != null ? content.cacheKey : content.itemName)
                + '\u0000' + content.getCurrentPage();
        Timing timing = timings.get(key);
        if (timing == null || now - timing.lastSeen > TOOLTIP_RESTART_GAP_MS) {
            QuantumHueConfig.TooltipTextEffects cfg = config();
            float charsPerMs = 10.0f;
            if (cfg != null && cfg.typewriter && cfg.introDuration > 0) {
                int maxGlyphs = 1;
                for (TooltipLine line : lines) {
                    if (line.type != TooltipLine.Type.TEXT) continue;
                    maxGlyphs = Math.max(maxGlyphs, getLine(line.text).glyphs.size());
                }
                charsPerMs = maxGlyphs / (float) Math.max(1, cfg.introDuration);
            }
            if (charsPerMs <= 0.001f) charsPerMs = 0.05f;
            timing = new Timing(now, charsPerMs);
            timings.put(key, timing);
        }
        timing.lastSeen = now;
        current = timing;
    }

    /**
     * 物品名：始终可见，只做一层同向彩色投影。
     *
     * <p>这里原本调用 {@link #drawGlow} 在 x±1 / y±1 四个方向各画一遍整行——
     * 在 GUI scale 2 下这四处偏移相距 2 个屏幕像素，叠加加法混合后看起来就是
     * 文字重影（尤其描边色是高饱和红/金时）。名字只需要一个清晰的彩色阴影，
     * 光晕留给正文里显式标记的 span。</p>
     */
    public void drawItemName(String text, int x, int y, int color, int borderColor) {
        if (font == null || text == null) return;
        try {
            // 名字不再用"描边色投影"：GT/稀有度的描边色往往高饱和（ZPM 是深红），
            // 1px 偏移的纯色投影和字体重叠后会被看成重影。这里统一走原版阴影，
            // 颜色信息只留给边框/角标。正文里显式标记的 span 发光不受影响。
            font.drawStringWithShadow(text, x, y, color);
        } catch (RuntimeException e) {
            fail(e);
            font.drawStringWithShadow(text, x, y, color);
        }
    }

    /**
     * 正文一行：打字机 + 行内标签指定的发光、高亮条与故障爆发。
     * 没有任何标签的行保持原来的单次绘制路径。
     */
    public void drawBodyLine(TooltipTextLine styled, int x, int y, int lineIndex, int borderColor, int rightBound) {
        if (font == null || styled == null || styled.text.isEmpty()) return;
        QuantumHueConfig.TooltipTextEffects cfg = config();
        if (cfg == null || !cfg.enabled) {
            font.drawStringWithShadow(styled.text, x, y, 0xFFFFFF);
            return;
        }
        try {
            GlyphLine glyphs = getLine(styled.text);
            int total = glyphs.glyphs.size();
            if (total == 0) return;

            int visible = total;
            if (cfg.typewriter && cfg.introDuration > 0) {
                Timing timing = current;
                if (timing == null) {
                    font.drawStringWithShadow(styled.text, x, y, 0xFFFFFF);
                    return;
                }
                long elapsed = frameTime - timing.start - (long) lineIndex * Math.max(0, cfg.lineStagger);
                if (elapsed <= 0L) return;
                visible = (int) (elapsed * timing.charsPerMs);
                if (visible >= total) visible = total;
                if (visible <= 0) return;
            }

            if (!styled.hasEffects()) {
                font.drawStringWithShadow(glyphs.sourcePrefix(visible), x, y, 0xFFFFFF);
                return;
            }

            float pulse = pulse();

            if (cfg.highlight) {
                for (TooltipTextLine.Span span : styled.spans) {
                    if ((span.mask & TooltipTextLine.FX_HIGHLIGHT) != 0) {
                        drawHighlight(glyphs, span, x, y, visible, pulse);
                    }
                }
            }

            if (cfg.glow) {
                for (TooltipTextLine.Span span : styled.spans) {
                    drawSpanGlow(glyphs, span, x, y, visible, borderColor, pulse);
                }
            }

            if (cfg.glitch && styled.hasMask(TooltipTextLine.FX_GLITCH)) {
                float burst = burstIntensity(lineIndex);
                if (burst > 0.02f) {
                    drawGlitched(glyphs, styled, visible, x, y, burst, rightBound);
                    return;
                }
            }

            font.drawStringWithShadow(glyphs.sourcePrefix(visible), x, y, 0xFFFFFF);
        } catch (RuntimeException e) {
            fail(e);
            font.drawStringWithShadow(styled.text, x, y, 0xFFFFFF);
        }
    }

    // ==================== 发光 ====================

    private void drawSpanGlow(GlyphLine glyphs, TooltipTextLine.Span span, int x, int y,
                              int visible, int borderColor, float pulse) {
        int end = Math.min(span.end, visible);
        if (end <= span.start) return;

        boolean highlight = (span.mask & TooltipTextLine.FX_HIGHLIGHT) != 0;
        if (!highlight && (span.mask & TooltipTextLine.FX_GLOW) == 0) return;

        QuantumHueConfig.TooltipTextEffects cfg = config();
        if (cfg == null) return;

        String text = glyphs.plainRange(span.start, end);
        if (text.isEmpty()) return;
        int color = highlight ? cfg.highlightColor : borderColor;
        float strength = (float) cfg.glowStrength * (highlight ? 0.9f : 0.5f);
        int offsetX = glyphs.offsetXForPlain(span.start);
        drawGlow(text, x + offsetX, y, color, strength, pulse);
    }

    private void drawGlow(String text, int x, int y, int color, float strength, float alphaMul) {
        if (strength <= 0.01f || text.isEmpty()) return;

        int alpha = clamp255(255.0f * strength * alphaMul);
        if (alpha < 3) return;
        int argb = (color & 0xFFFFFF) | (alpha << 24);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        try {
            font.drawString(text, x - 1, y, argb, false);
            font.drawString(text, x + 1, y, argb, false);
            font.drawString(text, x, y - 1, argb, false);
            font.drawString(text, x, y + 1, argb, false);
        } finally {
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    // ==================== 高亮条 ====================

    private void drawHighlight(GlyphLine glyphs, TooltipTextLine.Span span, int x, int y,
                               int visible, float pulse) {
        int startGlyph = glyphs.glyphIndexForPlain(span.start);
        int endGlyph = glyphs.glyphIndexForPlain(span.end);
        if (endGlyph <= startGlyph) return;
        if (endGlyph > visible) endGlyph = visible;
        if (endGlyph <= startGlyph) return;

        GlyphLine.Glyph first = glyphs.glyphs.get(startGlyph);
        GlyphLine.Glyph last = glyphs.glyphs.get(endGlyph - 1);
        int x1 = x + first.offsetX - 2;
        int x2 = x + last.offsetX + last.width + 2;

        QuantumHueConfig.TooltipTextEffects cfg = config();
        if (cfg == null) return;
        int argb = cfg.highlightColor;
        int rgb = argb & 0xFFFFFF;
        int baseAlpha = (argb >>> 24) & 0xFF;
        int alpha = (int) (baseAlpha * 0.42f * pulse);
        if (alpha < 3) return;

        // 外层柔光
        Gui.drawRect(x1 - 3, y - 2, x2 + 3, y + 10, clamp255(alpha * 0.35f) << 24 | rgb);
        // 主体色带
        Gui.drawRect(x1, y - 1, x2, y + 9, Math.min(200, alpha) << 24 | rgb);
        // 底部亮线
        Gui.drawRect(x1 + 1, y + 8, x2 - 1, y + 9, clamp255(alpha * 2.2f) << 24 | rgb);
    }

    // ==================== 故障 ====================

    private void drawGlitched(GlyphLine glyphs, TooltipTextLine styled, int visible, int x, int y,
                              float intensity, int rightBound) {
        int seed = styled.text.hashCode();
        int step = intensity > 0.55f ? 2 : 1;
        int ghostAlpha = clamp255(110.0f * intensity);

        if (ghostAlpha > 4) {
            for (TooltipTextLine.Span span : styled.spans) {
                if ((span.mask & TooltipTextLine.FX_GLITCH) == 0) continue;
                int end = Math.min(span.end, visible);
                if (end <= span.start) continue;
                String ghost = glyphs.plainRange(span.start, end);
                if (ghost.isEmpty()) continue;
                int gx = x + glyphs.offsetXForPlain(span.start);

                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                        GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                try {
                    font.drawString(ghost, gx - step, y, (ghostAlpha << 24) | 0xFF2A2A, false);
                    font.drawString(ghost, gx + step, y, (ghostAlpha << 24) | 0x2AFFFF, false);
                } finally {
                    GlStateManager.tryBlendFuncSeparate(
                            GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
                }
            }
        }

        long burst = frameTime / 90L;
        List<int[]> blocks = null;
        for (int i = 0; i < visible; i++) {
            GlyphLine.Glyph glyph = glyphs.glyphs.get(i);
            int mask = styled.maskAt(glyph.plainIndex);
            if ((mask & TooltipTextLine.FX_GLITCH) == 0) {
                font.drawString(glyph.stylePrefix + glyph.text, x + glyph.offsetX, y, glyph.color, true);
                continue;
            }

            int hash = hash(seed, burst, i);
            int mode = hash & 0xFF;
            if (mode < 20) continue; // 缺字

            String text = glyph.text;
            if (mode < 64) {
                text = String.valueOf(randomGlyph(hash));
            }
            int gx = x + glyph.offsetX + ((hash >>> 8) % 3 - 1) * step;
            int gy = y + ((hash >>> 12) % 3 - 1) * step;
            int color = glyph.color;
            int tint = (hash >>> 16) & 15;
            if (tint == 0) {
                color = 0xFF6A4A;
            } else if (tint == 1) {
                color = 0x7FFFFF;
            } else if (tint == 2) {
                color = 0xFFD27A;
            }
            font.drawString(glyph.stylePrefix + text, gx, gy, color, true);

            if (mode > 235 && intensity > 0.35f) {
                if (blocks == null) blocks = new ArrayList<>(4);
                blocks.add(new int[]{gx - 1, gy - 1, 6 + (hash >>> 20 & 9)});
            }
        }

        if (blocks != null) {
            for (int[] block : blocks) {
                int bx = block[0];
                int by = block[1];
                int bw = block[2];
                if (bx + bw > rightBound) bw = rightBound - bx;
                if (bw <= 0) continue;
                Gui.drawRect(bx - 1, by - 1, bx + bw + 1, by + 10, 0x55FF2020);
                Gui.drawRect(bx, by, bx + bw, by + 9, 0xF2FFFFFF);
            }
        }
    }

    /** 0 表示当前没有故障爆发，否则返回 0..1 的强度。 */
    private float burstIntensity(int lineIndex) {
        QuantumHueConfig.TooltipTextEffects cfg = config();
        if (cfg == null || !cfg.glitch) return 0.0f;
        if (current == null) return 0.0f;
        int interval = Math.max(1000, cfg.glitchInterval);
        int duration = Math.max(1, cfg.glitchDuration);
        long age = frameTime - current.start;
        // 打字机还没播完时不要打断入场；每次 Tooltip 重新打开也从 0 计时。
        if (age < cfg.introDuration + 500L) return 0.0f;
        long phase = Math.floorMod(age + 1600L + lineIndex * 137L, interval);
        if (phase >= duration) return 0.0f;
        float p = phase / (float) duration;
        float envelope = Math.min(1.0f, p * 6.0f) * Math.min(1.0f, (1.0f - p) * 4.0f);
        return (float) cfg.glitchIntensity * envelope;
    }

    // ==================== 辅助 ====================

    private float pulse() {
        double phase = (frameTime % 8000L) / 8000.0;
        return (float) (0.86 + 0.14 * Math.sin(phase * Math.PI * 2.0));
    }

    private GlyphLine getLine(String text) {
        GlyphLine line = cache.get(text);
        if (line == null) {
            line = GlyphLine.parse(text, font);
            cache.put(text, line);
        }
        return line;
    }

    private static QuantumHueConfig.TooltipTextEffects config() {
        return QuantumHueConfig.tooltip_text_effects;
    }

    private void fail(RuntimeException e) {
        if (!failed) {
            failed = true;
            QuantumHue.LOGGER.warn("Tooltip 字体特效渲染失败，已回退到原版绘制", e);
        }
    }

    /** 渲染层出现异常后由调用方回退到原版文字绘制。 */
    public boolean isBroken() {
        return failed;
    }

    private static int clamp255(float value) {
        int v = (int) value;
        if (v < 0) return 0;
        return Math.min(v, 255);
    }

    private static int hash(int seed, long burst, int index) {
        int h = seed * 0x9E3779B1 + (int) (burst * 0x85EBCA77L) + index * 0xC2B2AE3D;
        h ^= h >>> 15;
        h *= 0x2545F491;
        h ^= h >>> 13;
        return h;
    }

    private static char randomGlyph(int hash) {
        return GARBAGE[Math.floorMod(hash >>> 3, GARBAGE.length)];
    }

    private static final char[] GARBAGE = {
            '#', '%', '&', '?', '!', '*', '<', '>', '/', '\\', '|', '=', '+', '~'
    };
}
