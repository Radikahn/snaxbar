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
        
    Minecraft mc = Minecraft.getInstance();
    Player player = mc.player;
    if (player == null) return;

    int selectedSlot = player.getInventory().selected;

    if (inventorySlot < 9 || inventorySlot > 35) return;

    var inv = player.getInventory();

    ItemStack fromInv = inv.getItem(inventorySlot);
    ItemStack fromHotbar = inv.getItem(selectedSlot);

    //this swap is just clientside, must also sync server
    inv.setItem(selectedSlot, fromInv);
    inv.setItem(inventorySlot, fromHotbar);

    //sync player state to server (or else selected block state on server and user will collide)
    if (mc.gameMode != null) {

        mc.gameMode.handleInventoryMouseClick(
            player.inventoryMenu.containerId,
            inventorySlot,
            selectedSlot,
            net.minecraft.world.inventory.ClickType.SWAP,
            player
        );
    }

    }
}