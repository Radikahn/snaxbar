package snax;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = SnaxBarMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class KeybindHandler {

    public static final KeyMapping SWAP_SLOT_20 = new KeyMapping(
        "Snax Hotbar Swap Key 1", GLFW.GLFW_KEY_Z, "key.categories.inventory"
    );

    public static final KeyMapping SWAP_SLOT_21 = new KeyMapping(
        "Snax Hotbar Swap Key 2", GLFW.GLFW_KEY_X, "key.categories.inventory"
    );

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SWAP_SLOT_20);
        event.register(SWAP_SLOT_21);
    }
}
