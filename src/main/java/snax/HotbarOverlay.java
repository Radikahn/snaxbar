package snax;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SnaxBarMod.MODID, value = Dist.CLIENT)
public class HotbarOverlay {
    
    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        // Only render on the hotbar overlay
        if (!event.getOverlay().id().toString().equals("minecraft:hotbar")) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        //Assume survival by default (allows rendered objects to clear health bar)
        GameType gameMode = GameType.SURVIVAL;


        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        //calc hotbar positions
        int hotbarX = screenWidth / 2 - 91; // Hotbar starts at center - 91 pixels
        int hotbarY = screenHeight - 22; // 22 pixels from bottom
        
        //Y offset for rendering items on top of hotbar
        int yOffset = 34;

        // Get items from inventory slots 9 and 10
        ItemStack slot9Item = player.getInventory().getItem(9);
        ItemStack slot10Item = player.getInventory().getItem(10);
        


        
        //Check the location in which the items should be rendered


        //first check if they are in creative (health bar covers items if they arent moved up)
        //I don't trust this gameMode.getPlayerMode() so I am going to wrap it up
        try{
            gameMode = Minecraft.getInstance().gameMode.getPlayerMode();
        }
        catch (Exception e){
            System.out.println(e);
        }



        //check current gamemode and if the position of rendered hotbar needs to change
        if(gameMode == GameType.CREATIVE){
            yOffset = yOffset - 6;
        }
        else{
            //if the game mode is changed dynamically within the game
            yOffset = 34;
        }

        if(Config.showOnFirstTwoSlots) {
            if (!slot9Item.isEmpty()) {
                renderSlotOverlay(guiGraphics, hotbarX + 3, hotbarY - yOffset, slot9Item);
            }
            
            if (!slot10Item.isEmpty()) {
                renderSlotOverlay(guiGraphics, hotbarX + 23, hotbarY - yOffset, slot10Item);
            }
        }

        //if default is not true, render on last two
        else{
            if (!slot9Item.isEmpty()) {
                renderSlotOverlay(guiGraphics, hotbarX + 143, hotbarY - yOffset, slot9Item); // Slot 7
            }
            if (!slot10Item.isEmpty()) {
                renderSlotOverlay(guiGraphics, hotbarX + 163, hotbarY - yOffset, slot10Item); // Slot 8
            }
        }
    
    
    
    }
    
    private static void renderSlotOverlay(GuiGraphics guiGraphics, int x, int y, ItemStack itemStack) {
        //testing a background overlay
        // guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, 0x80000000);
        
        // draw a border
        // guiGraphics.fill(x - 2, y - 2, x + 18, y - 1, 0xFFFFFFFF); // Top
        // guiGraphics.fill(x - 2, y + 17, x + 18, y + 18, 0xFFFFFFFF); // Bottom
        // guiGraphics.fill(x - 2, y - 1, x - 1, y + 17, 0xFFFFFFFF); // Left
        // guiGraphics.fill(x + 17, y - 1, x + 18, y + 17, 0xFFFFFFFF); // Right
        
        // Render the item
        guiGraphics.renderItem(itemStack, x, y);
        
        // Added a count of items if count > 1
        if (itemStack.getCount() > 1) {
            guiGraphics.renderItemDecorations(Minecraft.getInstance().font, itemStack, x, y);
        }
    }
}

