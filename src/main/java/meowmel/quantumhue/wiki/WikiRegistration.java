package meowmel.quantumhue.wiki;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.items.MetaItems;
import gregtech.common.metatileentities.MetaTileEntities;
import meowmel.quantumhue.wiki.gregtech.EBFPage;
import meowmel.quantumhue.wiki.gregtech.MultiblockBase;
import net.minecraft.item.ItemStack;

import java.util.Map;

import static net.minecraftforge.fml.common.Loader.isModLoaded;

/**
 * Wiki 代码注册集中入口。
 * 所有通过代码注册的分类和页面在此统一管理，
 * 在 {@link net.minecraftforge.fml.common.event.FMLInitializationEvent} 阶段调用。
 */
public final class WikiRegistration {

    private static final String WELCOME_GTQT_CONTENT =
            "# 欢迎来到GTQT\n\n" +
            "![image:quantumhue:wiki/bar.png:400:133]\n\n" +
            "## 背景故事\n\n" +
            "在漫长的战争之中，泛银河系格雷科技有限公司如一抹血红残阳，即将走向它不可挽回的终局。过去，多元宇宙曾经全部被公司收入囊中，而如今反过来，多元宇宙的管理员们正驱动蜂群，绞杀每一寸公司的领地。管理员通过扶持\"齿轮神教\"，一个自我增殖、自我驱动的恐怖人工智能网络来对公司发动反扑，而公司以往无往不利的硅岩核能武器在无处不在的渗透下脆弱不堪，甚至根本无法开火……情势已危如累卵。\n\n" +
            "除去外患，公司还有内忧。自从初代格雷有限公司董事长\"伟大的格雷格\"在\"星际之门五号\"产品发布会上消失以来，公司的经营业绩每况愈下，庞大的商业帝国近乎分崩离析，一个个星区随之黯淡。尽管在后继者总经理德利姆·马斯特的指挥下，公司勉强维持着正利润，但是公司决策层的产品开发思路愈发保守，市场不断萎缩，数值泡沫不断滋生，以至于旗舰产品\"星际之门五号\"居然陷入产能不足的困境中……一切的一切都在宣告着，全银河系最伟大的公司大势已去。\n\n" +
            "公司内，一批有识之士决定采取火种计划保存实力。他们将最为优秀的公司基因和公司的科技记忆芯片打入自愿奉献的员工体内，随即让他们冬眠，冬眠舱被秘密发送到各星区。计划负责人认为，在格雷公司即将毁灭的当下，守住公司的传承是唯一的出路，他们希望格雷公司能够在非常遥远的未来重生。\n\n" +
            "一万年过去了。沧海桑田……格雷科技有限公司已经是一段遗忘的古老历史，此时此刻，火种计划中一个几近损毁的降落仓已经偏离了预定航向整整8度。它本不可能完成自己的任务，但是在命运的恶作剧之下，最终还是碰巧降落在一个美丽的蓝色星球……\n\n" +
            "## 简介\n\n" +
            "格雷：量子跃迁(GregTech:QuantumTransition,GTQT)是一款围绕GTCEm,GTQTCore，GregicalityMultiblocks等格雷科技相关模组展开，配合Thaumcraft，Botania，Advanced Rocketry等主力内容建构模组，Enderio，Forestry等其他辅助科技模组联动魔改的大型魔改合包。GTQT整合包以科研为核心发展线索，以工业化多方块机器为支柱，以基础化工为引擎，力求为玩家创造独特的、前所未有的游戏体验。为此，团队开发了GTQTCore(核心内容模组)，GTQTSpace(太空附属），Drtech（特色工具和机器），Pollution(大型魔法附属联动)，GregTinker_CEU(匠魂附属)，Botania_CEU(植物魔法附属)等一系列自研模组，和各大主流模组进行交互，并创造出更多功能独特、机制新颖的多方块机器。现在，GTQT整合包现有的内容已完全颠覆了格雷科技原本的既有路线，每个电压内容拓展至原有的3-5倍之多，同时难度保持基本不变。我们精心制作了新的任务书线路（每章任务数量平均100+！），并致力于使用更多精细化的魔改提高游戏手感，使得格雷科技的新手和老手均能寻得乐趣。";

    private WikiRegistration() {}
    
    /** 注册所有通过代码定义的 Wiki 内容 */
    public static void init() {
        if (!isModLoaded("gregtech")) return;
    
        // ===== 主线攻略 =====
        WikiRegistry.builder()
                .category(new WikiCategoryBuilder("GTQT主线攻略", () -> MetaTileEntities.FUSION_REACTOR[0].getStackForm())
                        .page(new WikiPageBuilder("welcome_to_gtqt", "欢迎来到GTQT", () -> MetaItems.WETWARE_MAINFRAME_UHV.getStackForm())
                                .content(WELCOME_GTQT_CONTENT)))
                .register();
    
        // ===== 核心内容（多方块攻略） =====
        // 1. 实例化所有多方块页面 —— 自动注册到 MultiblockBase.documentedMultiblocks
        new EBFPage();
    
        // 2. 统一添加到分类
        WikiCategoryBuilder coreCat = new WikiCategoryBuilder(
                "多方块教学",
                () -> MetaItems.MULTIBLOCK_BUILDER.getStackForm());
    
        for (Map.Entry<MetaTileEntity, MultiblockBase> entry : MultiblockBase.getDocumentedMultiblocks().entrySet()) {
            MultiblockBase mb = entry.getValue();
            coreCat.page(new WikiPageBuilder(mb.getPageId(), mb.getPageTitle(),
                    () -> mb.getMetaTileEntity().getStackForm())
                    .content(mb.getMarkdownContent()));
        }
    
        WikiRegistry.builder().category(coreCat).register();
    }
}
