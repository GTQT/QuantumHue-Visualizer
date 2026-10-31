package meowmel.quantumhue.mail;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 一封邮件的详情：正文（已套颜色码）与奖励物品堆。
 *
 * <p>奖励以解析后的 {@link ItemStack} 传输，客户端不需要再读物品注册表；服务端发放时用的是
 * 同一份解析结果，因此显示与实际到手的物品一致。
 */
public class MailDetail {

    public String id = "";
    public String title = "";
    public String sender = "";
    public String date = "";
    public List<String> body = new ArrayList<>();
    public List<ItemStack> rewards = new ArrayList<>();
    public long money;
    public boolean claimed;
    public boolean started = true;
    public boolean expired;

    public boolean active() {
        return started && !expired;
    }

    public boolean claimable() {
        return active() && !claimed;
    }

    public String statusText() {
        if (claimed) {
            return "§7已领取";
        }
        if (expired) {
            return "§c已过期";
        }
        if (!started) {
            return "§e未开始";
        }
        return "§b未领取";
    }
}
