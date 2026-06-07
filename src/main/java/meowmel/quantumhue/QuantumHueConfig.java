package meowmel.quantumhue;

import com.meowmel.quantumhue.Tags;
import net.minecraftforge.common.config.Config;

@Config(modid = Tags.MOD_ID)
public class QuantumHueConfig {

    @Config.Name("Tooltips Color")
    public static TooltipColor tooltips_custom = new TooltipColor();

    @Config.Name("The One Probe Color")
    public static TOPCustomColor top_custom = new TOPCustomColor();

    @Config.Name("Tooltip Animation")
    public static TooltipAnimation tooltip_animation = new TooltipAnimation();

    @Config.Name("Tooltip Background")
    public static TooltipBackground tooltip_background = new TooltipBackground();

    @Config.Name("Blur Config")
    public static Blur blur = new Blur();

    @Config.Name("Biome Info")
    public static BiomeInfo biome_info = new BiomeInfo();

    @Config.Name("Smooth Scrolling")
    public static SmoothScrolling smoothScrolling = new SmoothScrolling();

    @Config.Name("Middle Click Highlight")
    public static Highlight highlight = new Highlight();

    public static class SmoothScrolling {
        @Config.Name("Scroll Duration")
        @Config.Comment({
                "滚动动画持续时间（毫秒）",
                "值越大，滚动越慢",
                "范围: 0 ~ 5000"
        })
        @Config.RangeInt(min = 0, max = 5000)
        public int scrollDuration = 600;

        @Config.Name("Scroll Step")
        @Config.Comment({
                "滚动步长（像素）",
                "影响每帧滚动的距离",
                "范围: 0 ~ 100"
        })
        @Config.RangeDouble(min = 0, max = 100)
        public double scrollStep = 19.0;

        @Config.Name("Bounce Back Multiplier")
        @Config.Comment({
                "回弹系数",
                "控制滚动超出边界后的回弹强度",
                "建议值: 0.0 ~ 1.0"
        })
        public double bounceBackMultiplier = 0.24;

        @Config.Name("Unlimit FPS")
        @Config.Comment({
                "在标题画面解除 FPS 限制（默认 Minecraft 限制为 30 FPS）",
                "true: 解除限制",
                "false: 保持原有限制"
        })
        public boolean unlimitFps = true;
    }

    public static class BiomeInfo {
        @Config.Name("Enabled")
        @Config.Comment("Enable/disable biome info display")
        public boolean ENABLED = true;

        @Config.Name("Fade Out")
        @Config.Comment("Fade out text after biome change")
        public boolean FADE_OUT = true;

        @Config.Name("Display Time")
        @Config.Comment("Duration (in ticks) to show text after biome change when fadeOut=true")
        @Config.RangeInt(min = 0)
        public int DISPLAY_TIME = 40;

        @Config.Name("Fade Speed")
        @Config.Comment("Transparency change speed per tick during fade out")
        @Config.RangeInt(min = 1, max = 255)
        public int FADE_SPEED = 8;

        @Config.Name("Scale")
        @Config.Comment("Text scale multiplier (1.0 = default size)")
        @Config.RangeDouble(min = 0.5, max = 5.0)
        public double SCALE = 3;

        @Config.Name("Text Color")
        @Config.Comment("Text color in RGB hex (e.g. FFFFFF = white)")
        public String TEXT_COLOR = "FFFFFF";

        @Config.Name("Text Shadow")
        @Config.Comment("Enable text shadow")
        public boolean TEXT_SHADOW = true;
    }

    public static class Blur {
        @Config.Comment({
                "是否启用模糊效果",
                "true: 启用",
                "false: 禁用"
        })
        @Config.Name("启用模糊效果")
        public boolean enabled = true;

        @Config.Comment({
                "自定义着色器文件",
                "留空使用Minecraft内置的blur.json",
                "如果要使用自定义着色器，请填写路径，如: shaders/post/custom_blur.json",
                "默认: shaders/post/blur.json"
        })
        @Config.Name("自定义着色器")
        public String customShader = "shaders/post/blur.json";
    }

    public static class TooltipColor {
        @Config.Comment("是否启用自定义物品提示框颜色")
        @Config.Name("启用")
        public boolean enabled = true;

        @Config.Comment({
                "背景颜色",
                "格式: 0xAARRGGBB",
                "默认: 0xCC1f1f1f (80% 不透明的深灰色)"
        })
        @Config.RangeInt(min = 0x00000000, max = 0xFFFFFFFF)
        @Config.Name("背景颜色")
        public int backgroundColor = 0xCC1f1f1f;

        @Config.Comment({
                "默认边框颜色",
                "格式: 0xAARRGGBB",
                "默认: 0xFF4b4b4b (不透明灰色)",
                "当【稀有度着色】关闭时使用此颜色"
        })
        @Config.RangeInt(min = 0x00000000, max = 0xFFFFFFFF)
        @Config.Name("边框颜色")
        public int borderColor = 0xFF4b4b4b;

        @Config.Comment({
                "是否根据物品稀有度自动着色边框",
                "true: 启用 (边框颜色根据物品稀有度变化)",
                "false: 禁用 (始终使用默认边框颜色)"
        })
        @Config.Name("稀有度着色")
        public boolean enableRarityColors = true;

        @Config.Comment("List of GUI class names to skip (wildcard support: *Drawer*)")
        @Config.Name("Skipped GUI Patterns")
        public static String[] skippedGuiPatterns = new String[] {
                "*GuiDrawer*"
        };
    }

    public static class TooltipAnimation {
        @Config.Comment({
                "是否启用Tooltip切换动画",
                "当鼠标从不同物品间切换时，Tooltip框会平滑过渡而非瞬间变换",
                "true: 启用平滑过渡",
                "false: 禁用（瞬间切换）"
        })
        @Config.Name("启用")
        public boolean enabled = true;

        @Config.Comment({
                "动画持续时间（毫秒）",
                "值越大，过渡越慢",
                "范围: 50 ~ 500"
        })
        @Config.RangeInt(min = 50, max = 500)
        @Config.Name("动画时长")
        public int duration = 150;
    }

    public static class TooltipBackground {

        @Config.Comment({
                "是否启用Tooltip内部填充和页眉渐变视觉效果",
                " - 内部填充：在背景上叠加一层极淡的边框色",
                " - 页眉渐变：物品名字区域从左到右渐隐的色块",
                "true: 启用",
                "false: 禁用"
        })
        @Config.Name("启用")
        public boolean enabled = true;
    }

    public static class TOPCustomColor {

        @Config.Comment({
                " 【边框颜色】",
                " 格式: 0xAARRGGBB (ARGB 十六进制)",
                " - AA: 透明度 (00=透明, FF=不透明)",
                " - RR: 红色分量 (00-FF)",
                " - GG: 绿色分量 (00-FF)",
                " - BB: 蓝色分量 (00-FF)",
                " 默认值: 0xFF4b4b4b (不透明的深灰色)",
        })
        @Config.RangeInt(min = 0x00000000, max = 0xFFFFFFFF)
        @Config.Name("边框颜色")
        public int borderColor = -16711936;

        @Config.Comment({
                " 【填充颜色】",
                " 格式: 0xAARRGGBB (ARGB 十六进制)",
                " - AA: 透明度 (推荐 88 = 约 53% 不透明)",
                " - RR: 红色分量 (00-FF)",
                " - GG: 绿色分量 (00-FF)",
                " - BB: 蓝色分量 (00-FF)",
                " 默认值: 0x884b4b4b (半透明深灰色)",
        })
        @Config.RangeInt(min = 0x00000000, max = 0xFFFFFFFF)
        @Config.Name("填充颜色")
        public int fillColor = 0xCC000033;

        @Config.Comment({
                " 【边框厚度】",
                " 单位: 像素",
                " 范围: 0-10",
                " - 0: 无边框 (完全透明主题)",
                " - 1: 细边框 (简约风格)",
                " - 2: 标准边框 (默认值)",
                " - 3+: 粗边框 (强调效果)",
        })
        @Config.RangeInt(min = 0, max = 10)
        @Config.Name("边框厚度")
        public int thickness = 1;

        @Config.Comment({
                " 【边框偏移】",
                " 单位: 像素",
                " 范围: 0-20",
                " - 0: 边框紧贴内容 (无间距)",
                " - 1: 轻微内边距 (推荐)",
                " - 2-5: 中等间距",
                " - 5+: 较大间距",
        })
        @Config.RangeInt(min = 0, max = 20)
        @Config.Name("边框偏移")
        public int offset = 1;
    }

    public static class Highlight {
        @Config.Name("Enabled")
        @Config.Comment({
                "启用/禁用手表中键提醒功能",
                "true: 启用",
                "false: 禁用"
        })
        public boolean enabled = true;

        @Config.Name("Duration")
        @Config.Comment({
                "高亮持续显示时间（秒）",
                "范围: 1 ~ 120"
        })
        @Config.RangeInt(min = 1, max = 120)
        public int duration = 10;

        @Config.Name("Block Outline Color")
        @Config.Comment({
                "方块线框颜色 (RGB 十六进制)",
                "默认: 00FF00 (绿色)"
        })
        public int color = 0x00FF00;

        @Config.Name("Entity Outline Color")
        @Config.Comment({
                "生物/实体线框颜色 (RGB 十六进制)",
                "默认: FF0000 (红色)"
        })
        public int entityColor = 0xFF0000;

        @Config.Name("Opacity")
        @Config.Comment({
                "线框透明度",
                "范围: 0 ~ 255, 0=完全透明, 255=完全不透明"
        })
        @Config.RangeInt(min = 0, max = 255)
        public int opacity = 200;

        @Config.Name("Line Width")
        @Config.Comment({
                "线框线条宽度",
                "范围: 0.5 ~ 10"
        })
        @Config.RangeDouble(min = 0.5, max = 10)
        public float lineWidth = 2.0f;
    }
}
