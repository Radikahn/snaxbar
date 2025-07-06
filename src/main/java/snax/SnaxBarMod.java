package snax;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod(SnaxBarMod.MODID)
public class SnaxBarMod {
    public static final String MODID = "snaxbar";

    public SnaxBarMod() {
        // Register event handlers
        MinecraftForge.EVENT_BUS.register(new KeyInputHandler());
        MinecraftForge.EVENT_BUS.register(new HotbarOverlay());
        MinecraftForge.EVENT_BUS.register(new InventoryButtonHandler());
        MinecraftForge.EVENT_BUS.register(Notes.class);
        MinecraftForge.EVENT_BUS.register(NetworkManager.CommonEvents.class);
        
        // Register for mod setup event
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(final FMLCommonSetupEvent event) {
        // Initialize network
        NetworkManager.initNetwork();
        
        // Initialize PlayerController (if needed)
        // PlayerController should work without explicit initialization
    }
}