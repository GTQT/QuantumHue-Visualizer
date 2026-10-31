package meowmel.quantumhue.market;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.UUID;

/**
 * 一条离线收益：卖家不在线时成交的货款先记在这里，登录时统一结算。
 *
 * <p>同时保存纯文本与 JSON 组件两种形态：前者是兜底，后者保留翻译组件（例如物品名），
 * 结算消息因此仍然是可本地化的，而不是一条拼死的字符串。
 */
public class OfflineEarning {

    public UUID id;
    public UUID playerUUID;
    public long amount;
    public String source;
    private String sourceComponentJson = "";
    public long createTime;

    public OfflineEarning(UUID playerUUID, long amount, String source) {
        this.id = UUID.randomUUID();
        this.playerUUID = playerUUID;
        this.amount = amount;
        this.source = source;
        this.createTime = System.currentTimeMillis();
    }

    public OfflineEarning(UUID playerUUID, long amount, ITextComponent source) {
        this(playerUUID, amount, source.getUnformattedText());
        this.sourceComponentJson = ITextComponent.Serializer.componentToJson(source);
    }

    public OfflineEarning(NBTTagCompound nbt) {
        this.id = UUID.fromString(nbt.getString("id"));
        this.playerUUID = UUID.fromString(nbt.getString("player"));
        this.amount = nbt.getLong("amount");
        this.source = nbt.getString("source");
        this.sourceComponentJson = nbt.getString("sourceComponent");
        this.createTime = nbt.getLong("time");
    }

    public ITextComponent sourceComponent() {
        if (!sourceComponentJson.isEmpty()) {
            try {
                ITextComponent component = ITextComponent.Serializer.jsonToComponent(sourceComponentJson);
                if (component != null) {
                    return component;
                }
            } catch (com.google.gson.JsonParseException invalid) {
                meowmel.quantumhue.QuantumHue.LOGGER.warn("[Shop] 无法解析离线收益来源 {}", id, invalid);
            }
        }
        return new TextComponentString(source == null ? "" : source);
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("id", id.toString());
        nbt.setString("player", playerUUID.toString());
        nbt.setLong("amount", amount);
        nbt.setString("source", source == null ? "" : source);
        if (!sourceComponentJson.isEmpty()) {
            nbt.setString("sourceComponent", sourceComponentJson);
        }
        nbt.setLong("time", createTime);
        return nbt;
    }
}
