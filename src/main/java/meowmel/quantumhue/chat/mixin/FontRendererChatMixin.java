package meowmel.quantumhue.chat.mixin;

import meowmel.quantumhue.chat.EmojiRegistry;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 FontRenderer 知道表情占位符的正确宽度
 *
 * 仅混入 getCharWidth — 不碰 renderUnicodeChar（glyphWidth 保持 0），
 * 这样 FontRenderer 绘制时会跳过占位符（留空），我们在 ChatScreen 中覆盖表情贴图。
 */
@Mixin(value = FontRenderer.class, priority = 500)
public class FontRendererChatMixin {

    @Shadow
    private byte[] glyphWidth;

    /**
     * 对于表情占位符返回 EMoji_SIZE，其余走原版逻辑
     */
    @Inject(method = "getCharWidth", at = @At("HEAD"), cancellable = true)
    private void onGetCharWidth(char character, CallbackInfoReturnable<Integer> cir) {
        if (EmojiRegistry.isPlaceholder(character)) {
            cir.setReturnValue(EmojiRegistry.getEmojiSize());
        }
    }
}
