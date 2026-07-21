package meowmel.quantumhue.chat.mixin;

import net.minecraft.client.gui.GuiChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor for GuiChat private members
 */
@Mixin(GuiChat.class)
public interface GuiChatAccessor {

    @Accessor("defaultInputFieldText")
    String getDefaultInputFieldText();

    @Accessor("defaultInputFieldText")
    void setDefaultInputFieldText(String text);

}
