package meowmel.quantumhue.wiki.gregtech;

import gregtech.common.metatileentities.MetaTileEntities;

/**
 * EBF（电力高炉 / Electric Blast Furnace）介绍页。
 * <p>实例化时自动注册到 {@link MultiblockBase#documentedMultiblocks}，
 * 由 {@link meowmel.quantumhue.wiki.WikiRegistration#init()} 统一添加到 Wiki。</p>
 */
public class EBFPage extends MultiblockBase {

    public EBFPage() {
        super(MetaTileEntities.ELECTRIC_BLAST_FURNACE);
    }

    @Override
    public String getPageId() {
        return "ebf";
    }

    @Override
    public String getPageTitle() {
        return "电力高炉 (EBF)";
    }

    @Override
    public String getMarkdownContent() {
        return "# " + getPageTitle()
                + "\n\n" + "## 介绍"
                + "\n\n" + "EBF（Electric Blast Furnace，电力高炉）是格雷科技中接触到的第一个多方块机器，它利用电力产生高温，将金属粉末在特定温度下冶炼成锭（或热锭），同时也能用于合金冶炼、特殊材料的制造。相比单方块机器，EBF 能够处理更高温度要求的配方，完成普通熔炉无法胜任的冶炼任务。"
                + "\n\n![multiblock:" + mte.metaTileEntityId + "]"
                + "\n\n" + "## 特殊机制"
                + "\n\n" + "EBF 需要线圈（Coil）来维持高温，不同等级的线圈决定了最高工作温度和能耗效率。"
                + "\n\n" + "EBF 的实际炉温由两部分组成：基础炉温取自线圈本身的温度属性；在此基础上，如果机器的输入电压高于 MV 等级，那么每高出一个电压等级，炉温便额外提升 100K。低于或等于 MV 等级时则不享受此加成。"
                + "\n\n" + "此外，对于炉温超出配方需求温度 900K 以上的配方，在计算超频之前，其能量消耗会先乘以 95%，即降低 5%。该能耗折扣不计入后续的超频计算。"
                + "\n\n" + "对于炉温超出配方需求温度 1800K 以上的配方，超频效率将直接提升 100%。此时每级超频都会变为 4 倍消耗功率、同时实际耗时缩短为原来的四分之一。单次处理耗时最短不会低于 1 游戏刻。"
                ;
    }
}
