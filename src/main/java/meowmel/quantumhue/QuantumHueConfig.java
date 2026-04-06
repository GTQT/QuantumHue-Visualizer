package meowmel.quantumhue;

import com.meowmel.quantumhue.Tags;
import net.minecraftforge.common.config.Config;

@Config(modid = Tags.MOD_ID)
public class QuantumHueConfig {

    @Config.Name("Tooltips Color")
    public static TooltipColor tooltips_custom = new TooltipColor();

    @Config.Name("The One Probe Color")
    public static TOPCustomColor top_custom = new TOPCustomColor();

    @Config.Name("Blur Config")
    public static Blur blur = new Blur();

    @Config.Name("Biome Info")
    public static biomeInfo biome_info = new biomeInfo();

    public static class biomeInfo {
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
}
