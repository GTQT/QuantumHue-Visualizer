package meowmel.quantumhue.igi;

import meowmel.quantumhue.igi.info.*;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * IGI 正式 HUD 信息注册。
 */
public final class IGIInit {
    private IGIInit() {
    }

    /**
     * 注册左上角 HUD 信息（字号 8）。
     */
    public static void registerDefaults() {
        IGI.register()
                .pos(Alignment.TOP_LEFT)
                .offset(2, 2)
                .size(8)
                .info(TextColor.WHITE, "TPS: ", TextColor.WHITE, new TpsInfo(),
                        TextColor.WHITE, " | MSPT: ", TextColor.WHITE, new MsptInfo(),
                        TextColor.WHITE, " | RAM: ", TextColor.WHITE, new MemoryInfo(), TextColor.RESET)
                .info("")
                .info("")
                .info("")
                .info(new ItemIcon(new ItemStack(Items.NAME_TAG)), " ",
                        TextColor.AQUA, new PlayerNameInfo(), TextColor.RESET,
                        TextColor.WHITE, " FPS: ", TextColor.GREEN, new FpsInfo(), TextColor.RESET," ",
                        new ItemIcon(new ItemStack(Blocks.DAYLIGHT_DETECTOR)),
                        TextColor.WHITE, " 现实时间: ", TextColor.GOLD, new RealTimeInfo(), TextColor.RESET)
                .info(new ItemIcon(new ItemStack(Items.CLOCK)),
                        TextColor.WHITE, " MC日期: ", TextColor.GOLD, new McDateInfo(),
                        TextColor.WHITE, "；时间: ", TextColor.YELLOW, new McTimeFormattedInfo(), TextColor.RESET)
                .info(new ItemIcon(new ItemStack(Blocks.GRASS)),
                        TextColor.WHITE, " 世界: ", TextColor.GREEN, new DimFullInfo(),
                        TextColor.WHITE, " 当前天气: ", TextColor.YELLOW, new WeatherInfo(), TextColor.RESET)
                .info(new ItemIcon(new ItemStack(Blocks.SAPLING, 1, 4)),
                        TextColor.WHITE, " 生物群系: ", TextColor.DARK_AQUA, new BiomeInfo(),
                        TextColor.WHITE, " 温度: ", TextColor.GOLD, new BiomeTempInfo(),
                        TextColor.WHITE, " 湿度: ", TextColor.AQUA, new BiomeHumidityInfo(), TextColor.RESET)
                .info(new ItemIcon(new ItemStack(Items.COMPASS)),
                        TextColor.WHITE, " 区块坐标: X: ", TextColor.WHITE, new ChunkXInfo(),
                        TextColor.WHITE, " Z: ", TextColor.WHITE, new ChunkZInfo(),
                        TextColor.WHITE, " Off: ", TextColor.WHITE, new ChunkOffsetInfo(),
                        TextColor.WHITE, " 面向: ", TextColor.GOLD, new FacingInfo(), TextColor.RESET)
                .info(new ItemIcon(new ItemStack(Blocks.TORCH)),
                        TextColor.WHITE, " 光照等级: ", TextColor.YELLOW, new FootLightInfo(),
                        TextColor.WHITE, "（立足处光照等级: ", TextColor.YELLOW, new EyeLightInfo(),
                        TextColor.WHITE, "）", TextColor.RESET)
                .builder();
    }
}
