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

    @Config.Name("Equipment Comparison")
    public static EquipmentComparison equipmentComparison = new EquipmentComparison();

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

        @Config.Comment({
                "切换物品时物品图标的果冻弹出动画",
                "图标会从略小尺寸弹性弹出、微量过冲后回弹稳定",
                "true: 启用",
                "false: 禁用"
        })
        @Config.Name("图标弹出动画")
        public boolean icon_pop_enabled = true;

        @Config.Comment({
                "图标弹出动画持续时间（毫秒）",
                "值越大，动画越慢",
                "范围: 100 ~ 1000"
        })
        @Config.RangeInt(min = 100, max = 1000)
        @Config.Name("图标弹出时长")
        public int icon_pop_duration = 800;

        @Config.Comment({
                "图标弹出力度",
                "控制弹性的强弱程度",
                "0.0 = 无弹性效果",
                "1.0 = 最强弹性",
                "范围: 0.0 ~ 1.0"
        })
        @Config.RangeDouble(min = 0.0, max = 1.0)
        @Config.Name("图标弹出力度")
        public double icon_pop_strength = 1.0;
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
        public int duration = 5;

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

    @Config.Name("Chat")
    public static ChatConfig chat = new ChatConfig();

    public static class ChatConfig {
        @Config.Name("Panel Width %")
        @Config.Comment({ "聊天面板占屏幕宽度的百分比", "范围: 30 ~ 90" })
        @Config.RangeInt(min = 30, max = 90)
        public int panelWidthPercent = 65;

        @Config.Name("Panel Max Width")
        @Config.Comment({ "面板最大宽度（像素）", "范围: 200 ~ 1920" })
        @Config.RangeInt(min = 200, max = 1920)
        public int panelMaxWidth = 700;

        @Config.Name("Panel Min Width")
        @Config.Comment({ "面板最小宽度（像素）", "范围: 200 ~ 1000" })
        @Config.RangeInt(min = 200, max = 1000)
        public int panelMinWidth = 300;

        @Config.Name("Sidebar Width")
        @Config.Comment({ "侧边栏宽度（像素）", "范围: 80 ~ 250" })
        @Config.RangeInt(min = 80, max = 250)
        public int sidebarWidth = 115;

        @Config.Name("Accent Color")
        @Config.Comment({ "主题强调色 (RRGGBB 十六进制)", "用于高亮、边框、名字等", "默认: 6688CC (蓝)" })
        public String accentColor = "6688CC";

        @Config.Name("Background Color")
        @Config.Comment({ "面板底色 (RRGGBB 十六进制)", "默认: 0E0E16 (深蓝黑)" })
        public String bgColor = "0E0E16";

        @Config.Name("Own Bubble Color")
        @Config.Comment({ "自己消息气泡颜色 (RRGGBB 十六进制)", "默认: 181830" })
        public String ownBubbleColor = "181830";

        @Config.Name("Other Bubble Color")
        @Config.Comment({ "他人消息气泡颜色 (RRGGBB 十六进制)", "默认: 0E0E22" })
        public String otherBubbleColor = "0E0E22";

        @Config.Name("Text Light")
        @Config.Comment({ "主文字颜色 (RRGGBB 十六进制)", "默认: DDDDDD" })
        public String textLight = "DDDDDD";

        @Config.Name("Text Dim")
        @Config.Comment({ "次要文字颜色 (RRGGBB 十六进制)", "默认: 999999" })
        public String textDim = "999999";
    }

    public static class EquipmentComparison {
        @Config.Name("Enabled")
        @Config.Comment({
                "启用/禁用装备对比功能",
                "鼠标悬停在可装备物品上时，同时显示已装备物品的Tooltip进行对比",
                "true: 启用",
                "false: 禁用"
        })
        public boolean enabled = true;

        @Config.Name("Default On")
        @Config.Comment({
                "默认显示对比（按下快捷键隐藏而非显示）",
                "true: 默认显示对比，按键时隐藏",
                "false: 默认隐藏对比，按键时显示"
        })
        public boolean defaultOn = false;

        @Config.Name("Strict Mode")
        @Config.Comment({
                "严格模式：只比较相同物品类型",
                "例如：剑只能与剑对比，不能与斧对比",
                "true: 启用严格模式",
                "false: 关闭严格模式"
        })
        public boolean strict = false;

        @Config.Name("Blacklist")
        @Config.Comment({
                "不参与对比的物品注册名列表",
                "格式: modid:item_name",
                "例如: minecraft:stick"
        })
        public String[] blacklist = new String[0];

        @Config.Name("Badge Text")
        @Config.Comment({
                "对比徽章显示的文字",
                "留空则使用默认文字\"已装备\""
        })
        public String badgeText = "";

        @Config.Name("Override Badge Text")
        @Config.Comment({
                "是否使用自定义徽章文字",
                "true: 使用 badgeText 中的自定义文字",
                "false: 使用内置默认文字\"已装备\""
        })
        public boolean overrideBadgeText = false;

        @Config.Name("Badge Text Color")
        @Config.Comment({
                "徽章文字颜色",
                "格式: 0xAARRGGBB",
                "默认: 0xFFFFFFFF (白色)"
        })
        @Config.RangeInt(min = 0x00000000, max = 0xFFFFFFFF)
        public int badgeTextColor = 0xFFFFFFFF;

        @Config.Name("Badge Background Color")
        @Config.Comment({
                "徽章背景颜色",
                "格式: 0xAARRGGBB",
                "默认: 0xCC1f1f1f"
        })
        @Config.RangeInt(min = 0x00000000, max = 0xFFFFFFFF)
        public int badgeBackgroundColor = 0xCC1f1f1f;

        @Config.Name("Badge Border Start Color")
        @Config.Comment({
                "徽章上/左边框颜色",
                "格式: 0xAARRGGBB",
                "默认: 0xFF4b4b4b"
        })
        @Config.RangeInt(min = 0x00000000, max = 0xFFFFFFFF)
        public int badgeBorderStartColor = 0xFF4b4b4b;

        @Config.Name("Badge Border End Color")
        @Config.Comment({
                "徽章下/右边框颜色",
                "格式: 0xAARRGGBB",
                "默认: 0xFF4b4b4b"
        })
        @Config.RangeInt(min = 0x00000000, max = 0xFFFFFFFF)
        public int badgeBorderEndColor = 0xFF4b4b4b;
    }

    /**
     * 创建世界界面现代化（移植自 CreateWorldUI）
     */
    @Config.Name("Create World UI")
    public static CreateWorld createWorld = new CreateWorld();

    public static class CreateWorld {

        @Config.Name("Modern White Tab Text")
        @Config.Comment({
                "顶部标签页文字使用白色",
                "需要 vintagefix + 现代纹理资源包(modernity/mc-new-textures)才生效",
                "false: 悬停/选中时文字为黄色"
        })
        public boolean topTabCharatorModernWhite = false;

        @Config.Name("Enable Gamerule Editor")
        @Config.Comment({
                "启用游戏规则编辑器",
                "在创建世界界面的\"更多\"标签页中显示入口按钮"
        })
        public boolean gameruleEdit = true;

        @Config.Name("In-Game Gamerule Editor")
        @Config.Comment({
                "启用游戏内游戏规则编辑器",
                "允许使用 /gameruleEditor 命令在游戏内打开编辑器"
        })
        public boolean igGameruleEdit = false;

        @Config.Name("Enable Other More Tab Button")
        @Config.Comment({
                "显示未使用的现代功能按钮",
                "在\"更多\"标签页中显示实验性功能和数据包占位按钮"
        })
        public boolean enableOtherMoreTabButton = false;

        @Config.Name("Show World Name Placeholder")
        @Config.Comment({
                "世界名称输入框为空时显示占位提示文字"
        })
        public boolean showWorldNamePlaceHolder = false;

        @Config.Name("Disable Create Button When World Name Is Blank")
        @Config.Comment({
                "世界名称为空时禁用创建按钮"
        })
        public boolean disableCreateButtonWhenWNIsBlank = false;

        @Config.Name("Enable Difficulty Lock Button")
        @Config.Comment({
                "在难度选择旁显示难度锁定按钮"
        })
        public boolean enableLock = false;

        @Config.Name("Enable Reload Button")
        @Config.Comment({
                "在游戏规则编辑器中显示重置按钮"
        })
        public boolean enableResetButton = false;

        @Config.Name("Highlight Changed Rules in Chat")
        @Config.Comment({
                "聊天栏通知中修改的规则名用黄色高亮显示"
        })
        public boolean changedRulesInChatHighLighted = false;

        @Config.Name("Highlight Modified Rules in GUI")
        @Config.Comment({
                "游戏规则编辑器中修改过的规则名用黄色高亮显示"
        })
        public boolean highlightModifiedRulesInGUI = true;
    }
}
