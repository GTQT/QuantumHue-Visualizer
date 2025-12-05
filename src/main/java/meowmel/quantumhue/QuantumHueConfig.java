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

    public static class Blur {
        @Config.Comment({
                "是否启用模糊效果",
                "true: 启用",
                "false: 禁用"
        })
        @Config.Name("启用模糊效果")
        public boolean enabled = true;

        @Config.Comment({
                "模糊强度（半径）",
                "值越大，模糊效果越强",
                "范围: 1 ~ 20",
                "默认: 8"
        })
        @Config.RangeInt(min = 1, max = 20)
        @Config.Name("模糊强度")
        public int blurRadius = 8;

        @Config.Comment({
                "淡入淡出时间（毫秒）",
                "模糊效果出现和消失的过渡时间",
                "范围: 0 ~ 2000",
                "0: 立即切换",
                "默认: 300"
        })
        @Config.RangeInt(min = 0, max = 2000)
        @Config.Name("淡入时间")
        public int fadeTime = 300;

        @Config.Comment({
                "是否在聊天界面启用模糊",
                "true: 聊天界面也模糊",
                "false: 聊天界面不模糊",
                "默认: true"
        })
        @Config.Name("聊天界面模糊")
        public boolean blurChat = true;

        // ==================== 黑名单设置 ====================
        @Config.Comment({
                "模糊效果黑名单",
                "在此列表中的GUI类名将不会启用模糊效果",
                "每行一个完整的类名",
                "示例: net.minecraft.client.gui.GuiGameOver",
                "默认: []"
        })
        @Config.Name("黑名单")
        public String[] blacklist = {};

        @Config.Comment({
                "模糊效果白名单",
                "仅在此列表中的GUI类名会启用模糊效果",
                "如果为空，则除了黑名单外的所有GUI都启用模糊",
                "每行一个完整的类名",
                "默认: []"
        })
        @Config.Name("白名单（优先级高于黑名单）")
        public String[] whitelist = {};

        // ==================== 视觉效果设置 ====================
        @Config.Comment({
                "背景颜色设置",
                "格式: 0xAARRGGBB",
                "AA: 透明度 (00=完全透明, FF=完全不透明)",
                "RR: 红色分量 (00-FF)",
                "GG: 绿色分量 (00-FF)",
                "BB: 蓝色分量 (00-FF)",
                "默认: 0x66000000 (40% 不透明的黑色)"
        })
        @Config.RangeInt(min = 0x00000000, max = 0xFFFFFFFF)
        @Config.Name("背景颜色")
        public int backgroundColor = 0x66000000;

        @Config.Comment({
                "是否启用动态模糊",
                "true: 模糊效果会根据GUI内容动态调整",
                "false: 使用固定的模糊强度",
                "默认: true"
        })
        @Config.Name("动态模糊")
        public boolean dynamicBlur = true;

        @Config.Comment({
                "性能模式",
                "0: 高质量（消耗更多性能）",
                "1: 平衡（默认）",
                "2: 性能优先（模糊效果较弱）",
                "默认: 1"
        })
        @Config.RangeInt(min = 0, max = 2)
        @Config.Name("性能模式")
        public int performanceMode = 1;

        // ==================== 高级设置 ====================
        @Config.Comment({
                "自定义着色器文件",
                "留空使用Minecraft内置的blur.json",
                "如果要使用自定义着色器，请填写路径，如: shaders/post/custom_blur.json",
                "默认: shaders/post/blur.json"
        })
        @Config.Name("自定义着色器")
        public String customShader = "shaders/post/blur.json";

        @Config.Comment({
                "调试模式",
                "true: 在控制台输出模糊状态信息",
                "false: 静默运行",
                "默认: false"
        })
        @Config.Name("调试模式")
        public boolean debugMode = false;

        @Config.Comment({
                "模糊效果类型",
                "0: 标准模糊（默认）",
                "1: 高斯模糊",
                "2: 运动模糊",
                "3: 径向模糊",
                "注意：除类型0外，其他类型可能需要自定义着色器文件"
        })
        @Config.RangeInt(min = 0, max = 3)
        @Config.Name("模糊类型")
        public int blurType = 0;

        // ==================== 特殊情况处理 ====================
        @Config.Comment({
                "是否在世界加载时禁用模糊",
                "true: 世界加载界面不启用模糊",
                "false: 世界加载界面也启用模糊",
                "默认: true"
        })
        @Config.Name("禁用世界加载模糊")
        public boolean disableOnWorldLoad = true;

        @Config.Comment({
                "是否在F3界面禁用模糊",
                "true: F3调试界面不启用模糊",
                "false: F3调试界面也启用模糊",
                "默认: true"
        })
        @Config.Name("禁用F3模糊")
        public boolean disableOnDebugScreen = true;

        @Config.Comment({
                "是否在GUI切换时重置模糊",
                "true: 每次打开新GUI时重置模糊效果",
                "false: 保持模糊状态直到关闭所有GUI",
                "默认: true"
        })
        @Config.Name("GUI切换重置模糊")
        public boolean resetOnGuiSwitch = true;
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
