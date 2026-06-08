package meowmel.quantumhue.wiki.gregtech;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.GTUtility;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多方块 Wiki 页面基类。
 * <p>每个子类代表一个多方块的介绍页面，实例化时自动注册到 {@link #documentedMultiblocks}。
 * 然后在 {@link meowmel.quantumhue.wiki.WikiRegistration#init()} 中统一遍历、生成页面。</p>
 * <pre>
 * public class EBFPage extends MultiblockBase {
 *     public EBFPage() {
 *         super(MetaTileEntities.ELECTRIC_BLAST_FURNACE[0]);
 *     }
 *     public String getPageId()       { return "ebf"; }
 *     public String getPageTitle()    { return "电力高炉"; }
 *     public String getDescription()  { return "EBF 是..."; }
 * }
 * </pre>
 */
public abstract class MultiblockBase {

    /** MTE → 页面实例 映射（按插入顺序） */
    protected static final Map<MetaTileEntity, MultiblockBase> documentedMultiblocks = new LinkedHashMap<>();

    /** MTE ID → 页面实例 映射（用于 ItemStack 快速查找） */
    private static final Map<String, MultiblockBase> documentedMultiblocksByMteId = new LinkedHashMap<>();

    protected final MetaTileEntity mte;

    protected MultiblockBase(MetaTileEntity mte) {
        this.mte = mte;
        documentedMultiblocks.put(mte, this);
        documentedMultiblocksByMteId.put(mte.metaTileEntityId.toString(), this);
    }

    /** Wiki 页面 ID（必须唯一） */
    public abstract String getPageId();

    /** Wiki 页面标题 */
    public abstract String getPageTitle();

    /** 介绍文本（不含 Markdown 标题等外层格式） */
    public abstract String getDescription();

    /** 生成完整的 Markdown 正文 */
    public String getMarkdownContent() {
        return "# " + getPageTitle() + "\n\n" + getDescription();
    }

    public MetaTileEntity getMetaTileEntity() {
        return mte;
    }

    /**
     * 获取多方块控制器实例。
     * 用于 {@link MultiblockPreviewRenderer} 构建 3D 预览。
     */
    public MultiblockControllerBase getController() {
        return (MultiblockControllerBase) mte;
    }

    public static Map<MetaTileEntity, MultiblockBase> getDocumentedMultiblocks() {
        return Collections.unmodifiableMap(documentedMultiblocks);
    }

    /**
     * 根据 ItemStack 查找对应的多方块 Wiki 页面。
     *
     * @return 对应的 MultiblockBase 实例，如果不是已记录的多方块则返回 null
     */
    public static MultiblockBase getDocumentedMultiblockFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        MetaTileEntity mte = GTUtility.getMetaTileEntity(stack);
        if (mte != null) {
            return documentedMultiblocksByMteId.get(mte.metaTileEntityId.toString());
        }
        return null;
    }
}
