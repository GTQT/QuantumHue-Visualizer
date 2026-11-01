package meowmel.quantumhue.page;

import com.meowmel.quantumhue.Tags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** 商店页背景音乐：打开时从头播放，关闭时停止。 */
@SideOnly(Side.CLIENT)
public final class ShopMusic {

    private static final ResourceLocation SOUND =
            new ResourceLocation(Tags.MOD_ID, "shop.bgm");

    private static ISound playing;

    private ShopMusic() {}

    public static void play() {
        stop();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getSoundHandler() == null) {
            return;
        }
        playing = new PositionedSoundRecord(SOUND, SoundCategory.MUSIC, 1.0F, 1.0F,
                false, 0, ISound.AttenuationType.NONE, 0.0F, 0.0F, 0.0F);
        mc.getSoundHandler().playSound(playing);
    }

    public static void stop() {
        if (playing == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getSoundHandler() != null) {
            mc.getSoundHandler().stopSound(playing);
        }
        playing = null;
    }
}
