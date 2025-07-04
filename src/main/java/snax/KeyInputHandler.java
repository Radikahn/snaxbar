package snax;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SnaxBarMod.MODID, value = Dist.CLIENT)
public class KeyInputHandler {
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (KeybindHandler.SWAP_SLOT_20.consumeClick()) {
            swapWithSelected(9);
        }
        if (KeybindHandler.SWAP_SLOT_21.consumeClick()) {
            swapWithSelected(10);
        }
    }
    
    private static void swapWithSelected(int inventorySlot) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        
        // Get the currently selected hotbar slot
        int selectedSlot = player.getInventory().selected;
        
        // Validate inventory slot range (slots 9-35 are the main inventory)
        if (inventorySlot < 9 || inventorySlot > 35) return;
        
        var inv = player.getInventory();
        ItemStack fromInv = inv.getItem(inventorySlot);
        ItemStack fromHotbar = inv.getItem(selectedSlot);
        
        inv.setItem(selectedSlot, fromInv);
        inv.setItem(inventorySlot, fromHotbar);
    }
}