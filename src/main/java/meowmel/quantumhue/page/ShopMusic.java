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
        // 循环播放：商店是常驻页面，一遍放完就没声、开关还会停在“播放中”，
        // 所以这里直接循环，关闭页面时由 stop() 收掉。
        playing = new PositionedSoundRecord(SOUND, SoundCategory.MUSIC, 1.0F, 1.0F,
                true, 0, ISound.AttenuationType.NONE, 0.0F, 0.0F, 0.0F);
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

    /** 当前是否有一曲在播；标题栏的音乐开关据此决定画“暂停”还是“播放”。 */
    public static boolean isPlaying() {
        return playing != null;
    }

    /**
     * 标题栏音乐开关：正在播就停，停了就从头再放。
     *
     * @return 切换后的状态，{@code true} 表示现在正在播
     */
    public static boolean toggle() {
        if (isPlaying()) {
            stop();
            return false;
        }
        play();
        return isPlaying();
    }
}
