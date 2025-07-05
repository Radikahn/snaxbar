package snax;



import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;


@Mod(SnaxBarMod.MODID)
public class SnaxBarMod {
    public static final String MODID = "snaxbar";

    public SnaxBarMod() {
        MinecraftForge.EVENT_BUS.register(new KeyInputHandler());
        MinecraftForge.EVENT_BUS.register(new HotbarOverlay());
        MinecraftForge.EVENT_BUS.register(new InventoryButtonHandler());
        MinecraftForge.EVENT_BUS.register(new Notes());
    }


}
