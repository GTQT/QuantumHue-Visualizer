package meowmel.quantumhue.chat.mixin;

import meowmel.quantumhue.chat.ChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截 GuiChat 打开 → 聊天(T)走自定义界面，命令(/)走原版
 *
 * 原版按 T → GuiChat("")   → 替换为 ChatScreen
 * 原版按 / → GuiChat("/")  → 放行，原版处理命令输入
 */
@Mixin(value = Minecraft.class, priority = 500)
public class MinecraftChatMixin {

    @Inject(method = "displayGuiScreen", at = @At("HEAD"), cancellable = true)
    private void onDisplayGuiScreen(GuiScreen screen, CallbackInfo ci) {
        if (screen instanceof GuiChat && !(screen instanceof ChatScreen)) {
            String initial = ((GuiChatAccessor) screen).getDefaultInputFieldText();
            // 命令输入 → 原版处理
            if (initial != null && initial.startsWith("/")) return;
            // 聊天输入 → 替换为我们的界面
            ci.cancel();
            Minecraft.getMinecraft().displayGuiScreen(new ChatScreen(initial));
        }
    }
}
