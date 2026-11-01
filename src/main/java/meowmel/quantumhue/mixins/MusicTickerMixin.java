package meowmel.quantumhue.mixins;

import meowmel.quantumhue.page.ShopMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MusicTicker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 商店背景音乐播放期间，压掉原版背景音乐。
 *
 * <p>两首曲子都是 {@code SoundCategory.MUSIC}，同时播会叠在一起。这里在
 * {@link MusicTicker#update()} 的入口做两件事：
 *
 * <ul>
 *   <li>正在播的原版曲子立刻停掉（{@code currentMusic} 是那个 {@code ISound}，直接交给
 *       {@code SoundHandler#stopSound}）；</li>
 *   <li>整个 tick 直接取消，于是不会挑下一首。{@code timeUntilNextMusic} 保持原样，
 *       商店音乐一停，原版音乐就会照常续上。</li>
 * </ul>
 *
 * <p>只在商店音乐真的在播时才生效，与 {@link ShopMusic#isPlaying()} 状态一致。
 */
@Mixin(MusicTicker.class)
public class MusicTickerMixin {

    private static final Logger LOGGER = LogManager.getLogger("QuantumHue-Music");

    @Shadow
    private ISound currentMusic;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void quantumhue$silenceVanillaMusic(CallbackInfo ci) {
        if (!ShopMusic.isPlaying()) {
            return;
        }
        if (this.currentMusic != null) {
            Minecraft.getMinecraft().getSoundHandler().stopSound(this.currentMusic);
            LOGGER.debug("paused vanilla background music for the shop track");
            this.currentMusic = null;
        }
        ci.cancel();
    }
}
