package meowmel.quantumhue.wiki;

import net.minecraft.client.renderer.Tessellator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.scilab.forge.jlatexmath.ParseException;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LaTeX 公式渲染器 — 基于 jlatexmath 库
 *
 * 支持：
 * - 行内公式 $...$（自动缩放对齐文字高度）
 * - 块级公式 $$...$$（居中独立显示）
 *
 * 线程安全：测量/解析可后台执行，纹理上传和渲染需在 GL 线程
 */
public class WikiLatexRenderer {

    public static final WikiLatexRenderer INSTANCE = new WikiLatexRenderer();

    private static final int DEFAULT_FILL_COLOR_ARGB = 0xFFFFFFFF;
    private static final String CALIBRATION_FORMULA = "x";
    private static final int MAX_REF_HEIGHT_ENTRIES = 16;
    private static final Logger LOGGER = LogManager.getLogger("QuantumHue/LaTeX");

    /** 行内公式渲染缩放 */
    public static final float INLINE_SCALE = 22f;
    /** 块级公式渲染缩放 */
    public static final float BLOCK_SCALE = 40f;

    private final ConcurrentHashMap<String, Integer> refHeightCache = new ConcurrentHashMap<>();

    protected WikiLatexRenderer() {}

    /**
     * 校准参考字符高度 — 在给定 sourceScale 下 "x" 字符的像素高度
     */
    public int calibrateRefHeight(float sourceScale) {
        String key = WikiLatexTextureCache.buildScaleKey(sourceScale);
        Integer height = refHeightCache.computeIfAbsent(key, k -> {
            try {
                TeXFormula formula = new TeXFormula(CALIBRATION_FORMULA);
                TeXIcon icon = formula.new TeXIconBuilder()
                        .setStyle(TeXConstants.STYLE_DISPLAY)
                        .setSize(sourceScale)
                        .setFGColor(new Color(DEFAULT_FILL_COLOR_ARGB, true))
                        .build();
                icon.setInsets(new Insets(2, 2, 2, 2));
                int h = icon.getIconHeight();
                return Math.max(1, h);
            } catch (ParseException e) {
                LOGGER.warn("Calibration failed for scale {}", sourceScale, e);
                return 16;
            }
        });
        trimRefHeightCacheIfNeeded();
        return height;
    }

    private void trimRefHeightCacheIfNeeded() {
        if (refHeightCache.size() <= MAX_REF_HEIGHT_ENTRIES) {
            return;
        }
        for (Map.Entry<String, Integer> entry : refHeightCache.entrySet()) {
            if (refHeightCache.size() <= MAX_REF_HEIGHT_ENTRIES) {
                return;
            }
            refHeightCache.remove(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 预处理公式：剥离 Markdown 颜色标记 [#RRGGBB] 和 [/]。
     *
     * <p>jlatexmath 不支持通过 Markdown 颜色标记来局部着色。
     * 要在公式中实现彩色效果，请将颜色标记放在 {@code $...$} 外部：</p>
     * <pre>
     * 错误：$[#FF5555]E[/] = [#55AAFF]mc^2[/]$
     * 正确：[#FF5555]$E$[/] = [#55AAFF]$mc^2$[/]
     * </pre>
     */
    static String preprocessFormula(String formula) {
        if (formula == null || formula.isEmpty()) return formula;

        // 剥离 [#RRGGBB] 和 [/]，保留公式其余部分不变
        String result = formula.replaceAll("\\[#[0-9A-Fa-f]{6}\\]", "");
        result = result.replaceAll("\\[/\\]", "");
        return result;
    }

    /**
     * 测量公式尺寸 {@code [widthPx, heightPx, depthPx]}，失败返回 null
     * 线程安全
     */
    public int[] measureSize(String formula, int fillColorArgb, float sourceScale) {
        if (formula == null || formula.isEmpty()) {
            return null;
        }
        if (WikiLatexTextureCache.INSTANCE.hasFailed(formula)) {
            return null;
        }

        String sizeKey = WikiLatexTextureCache.buildSizeCacheKey(formula, sourceScale);
        int[] cached = WikiLatexTextureCache.INSTANCE.getSize(sizeKey);
        if (cached != null) {
            return cached;
        }

        try {
            String processed = preprocessFormula(formula);
            TeXFormula texFormula = new TeXFormula(processed);
            TeXIcon icon = texFormula.new TeXIconBuilder()
                    .setStyle(TeXConstants.STYLE_DISPLAY)
                    .setSize(sourceScale)
                    .setFGColor(new Color(fillColorArgb, true))
                    .build();
            icon.setInsets(new Insets(2, 2, 2, 2));
            int w = icon.getIconWidth();
            int h = icon.getIconHeight();
            int d = getIconDepthPx(icon);
            WikiLatexTextureCache.INSTANCE.putSize(sizeKey, w, h, d);
            return new int[]{w, h, d};
        } catch (ParseException e) {
            LOGGER.warn("Parse error measuring '{}': {}", formula, e.getMessage());
            WikiLatexTextureCache.INSTANCE.markFailed(formula, e.getMessage());
            return null;
        } catch (Exception e) {
            LOGGER.warn("Unexpected error measuring '{}': {}", formula, e.getMessage(), e);
            WikiLatexTextureCache.INSTANCE.markFailed(formula, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 获取（或创建并缓存）公式的 OpenGL 纹理
     * 必须在 GL 线程调用
     *
     * @return [textureId, widthPx, heightPx] 或 null
     */
    public int[] getOrCreateTexture(String formula, int fillColorArgb, float sourceScale) {
        if (formula == null || formula.isEmpty()) {
            return null;
        }
        if (WikiLatexTextureCache.INSTANCE.hasFailed(formula)) {
            return null;
        }

        String texKey = WikiLatexTextureCache.buildTextureCacheKey(formula, fillColorArgb, sourceScale);
        int[] cached = WikiLatexTextureCache.INSTANCE.getTexture(texKey);
        if (cached != null) {
            return cached;
        }

        try {
            String processed = preprocessFormula(formula);
            TeXFormula texFormula = new TeXFormula(processed);
            TeXIcon icon = texFormula.new TeXIconBuilder()
                    .setStyle(TeXConstants.STYLE_DISPLAY)
                    .setSize(sourceScale)
                    .setFGColor(new Color(fillColorArgb, true))
                    .build();
            icon.setInsets(new Insets(2, 2, 2, 2));
            icon.setForeground(new Color(fillColorArgb, true));

            BufferedImage image = renderToImage(icon);
            int w = image.getWidth();
            int h = image.getHeight();

            int textureId = uploadToGL(image, w, h);
            WikiLatexTextureCache.INSTANCE.putTexture(texKey, textureId, w, h);

            String sizeKey = WikiLatexTextureCache.buildSizeCacheKey(formula, sourceScale);
            int d = getIconDepthPx(icon);
            WikiLatexTextureCache.INSTANCE.putSize(sizeKey, w, h, d);

            return new int[]{textureId, w, h};
        } catch (ParseException e) {
            LOGGER.warn("Parse error rendering '{}': {}", formula, e.getMessage());
            WikiLatexTextureCache.INSTANCE.markFailed(formula, e.getMessage());
            return null;
        } catch (Exception e) {
            LOGGER.warn("Unexpected error rendering '{}': {}", formula, e.getMessage(), e);
            WikiLatexTextureCache.INSTANCE.markFailed(formula,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 渲染预先创建的纹理为四边面
     * 必须在 GL 线程调用
     */
    public void renderLatex(int x, int y, int displayW, int displayH, int textureId) {
        GL11.glPushAttrib(GL11.GL_TEXTURE_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1f, 1f, 1f, 1f);

            Tessellator tess = Tessellator.getInstance();
            tess.getBuffer().begin(GL11.GL_QUADS, net.minecraft.client.renderer.vertex.DefaultVertexFormats.POSITION_TEX);
            tess.getBuffer().pos(x, y + displayH, 0).tex(0.0, 1.0).endVertex();
            tess.getBuffer().pos(x + displayW, y + displayH, 0).tex(1.0, 1.0).endVertex();
            tess.getBuffer().pos(x + displayW, y, 0).tex(1.0, 0.0).endVertex();
            tess.getBuffer().pos(x, y, 0).tex(0.0, 0.0).endVertex();
            Tessellator.getInstance().draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static int getIconDepthPx(TeXIcon icon) {
        return Math.max(0, (int) Math.ceil(icon.getTrueIconDepth()));
    }

    private BufferedImage renderToImage(TeXIcon icon) {
        int w = icon.getIconWidth();
        int h = icon.getIconHeight();

        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(0, 0, w, h);

            icon.paintIcon(null, g, 0, 0);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static int uploadToGL(BufferedImage image, int w, int h) {
        int[] pixels = new int[w * h];
        image.getRGB(0, 0, w, h, pixels, 0, w);

        ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 4);
        for (int pixel : pixels) {
            buffer.put((byte) ((pixel >> 16) & 0xFF));
            buffer.put((byte) ((pixel >> 8) & 0xFF));
            buffer.put((byte) (pixel & 0xFF));
            buffer.put((byte) ((pixel >> 24) & 0xFF));
        }
        buffer.flip();

        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return textureId;
    }
}
