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
    public String getDescription() {
        return "EBF（Electric Blast Furnace，电力高炉）是格雷科技中接触到的第一个多方块机器。" +
                "它利用电力产生高温，实现矿物粉末的熔炼、合金冶炼以及部分特殊材料的制造。\n\n" +
                "相比单方块熔炉，EBF 的处理速度更快、温度更高，能够处理单方块机器无法完成的配方。" +
                "EBF 需要线圈（Coil）来维持高温，不同等级的线圈决定了最高工作温度和能耗效率。";
    }
}
