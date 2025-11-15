package snax;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SnaxBarMod.MODID, value = Dist.CLIENT)
public class InventoryButtonHandler {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        // Only add button to the player inventory screen
        if (!(event.getScreen() instanceof InventoryScreen)) {
            return;
        }

        InventoryScreen inventoryScreen = (InventoryScreen) event.getScreen();

        // Calculate button position (top-right corner of inventory)
        int screenWidth = inventoryScreen.width;
        int screenHeight = inventoryScreen.height;
        int inventoryLeft = (screenWidth - 176) / 2; // 176 is inventory width
        int inventoryTop = (screenHeight - 166) / 2; // 166 is inventory height

        // Create the main Snax button
        Button snaxButton = Button.builder(
                Component.literal("Snax"),
                button -> {
                    inventoryScreen.getMinecraft().setScreen(new ConfigScreen(inventoryScreen));
                })
                .bounds(inventoryLeft + 180, inventoryTop + 4, 40, 20) // Right side of inventory
                .build();

        event.addListener(snaxButton);
    }
}
