package meowmel.quantumhue.createworld.mixin;

import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露原版 {@link GuiCreateWorld} 私有字段的 accessor 接口。
 * <p>Accessor interface exposing the private fields of vanilla {@code GuiCreateWorld}.</p>
 * 外部代码把界面实例强转为该接口即可读写状态，无需反射。
 * 注意：接口必须带 {@code @Mixin} 注解并列入 mixins.quantumhue_createworld.json，
 * 否则 Mixin 不会为 accessor 方法生成实现（运行时会炸 AbstractMethodError）。
 */
@Mixin(GuiCreateWorld.class)
public interface IGuiCreateWorldAccess {

    // === World name / 世界名称 ===
    @Accessor("worldName")
    String createWorldUI$getWorldName();

    @Accessor("worldName")
    void createWorldUI$setWorldName(String value);

    // === Game mode / 游戏模式 ===
    @Accessor("gameMode")
    String createWorldUI$getGameMode();

    @Accessor("gameMode")
    void createWorldUI$setGameMode(String value);

    // === Seed / 种子 ===
    @Accessor("worldSeed")
    String createWorldUI$getSeed();

    @Accessor("worldSeed")
    void createWorldUI$setSeed(String value);

    // === World type index / 世界类型索引 ===
    @Accessor("selectedIndex")
    int createWorldUI$getWorldTypeIndex();

    @Accessor("selectedIndex")
    void createWorldUI$setWorldTypeIndex(int value);

    // === Generate structures / 生成建筑 ===
    @Accessor("generateStructuresEnabled")
    boolean createWorldUI$getGenerateStructures();

    @Accessor("generateStructuresEnabled")
    void createWorldUI$setGenerateStructures(boolean value);

    // === Bonus chest / 奖励箱 ===
    @Accessor("bonusChestEnabled")
    boolean createWorldUI$getBonusChest();

    @Accessor("bonusChestEnabled")
    void createWorldUI$setBonusChest(boolean value);

    // === Allow cheats / 允许作弊 ===
    @Accessor("allowCheats")
    boolean createWorldUI$getAllowCheats();

    @Accessor("allowCheats")
    void createWorldUI$setAllowCheats(boolean value);

    // === Hardcore / 硬核模式 ===
    @Accessor("hardCoreMode")
    boolean createWorldUI$getHardcore();

    @Accessor("hardCoreMode")
    void createWorldUI$setHardcore(boolean value);

    // === Parent screen (read-only) / 父界面（只读） ===
    @Accessor("parentScreen")
    GuiScreen createWorldUI$getParentScreen();
}
