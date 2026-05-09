package meowmel.quantumhue.command;

import io.netty.buffer.ByteBuf;
import meowmel.quantumhue.wiki.WikiScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ShowWikiPacket implements IMessage {

    public ShowWikiPacket() {}

    @Override public void fromBytes(ByteBuf buf) {}
    @Override public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<ShowWikiPacket, IMessage> {
        @Override
        public IMessage onMessage(ShowWikiPacket message, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(WikiScreen::open);
            return null;
        }
    }
}