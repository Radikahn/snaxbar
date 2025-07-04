package snax;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private Button positionButton;
    
    public ConfigScreen(Screen parent) {
        super(Component.literal("Snax Bar Configuration"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Title
        int titleY = 40;
        
        // Position toggle button
        String currentSetting = Config.showOnFirstTwoSlots ? "First Two Slots" : "Last Two Slots";
        positionButton = Button.builder(
            Component.literal("Overlay Position: " + currentSetting),
            button -> {
                Config.showOnFirstTwoSlots = !Config.showOnFirstTwoSlots;
                updateButtonText();
            })
            .bounds(width / 2 - 100, titleY + 40, 200, 20)
            .build();
        
        addRenderableWidget(positionButton);
        
        // Done button
        Button doneButton = Button.builder(
            Component.literal("Done"),
            button -> minecraft.setScreen(parent))
            .bounds(width / 2 - 50, titleY + 80, 100, 20)
            .build();
        
        addRenderableWidget(doneButton);
    }
    
    private void updateButtonText() {
        String currentSetting = Config.showOnFirstTwoSlots ? "First Two Slots" : "Last Two Slots";
        positionButton.setMessage(Component.literal("Overlay Position: " + currentSetting));
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw background
        renderBackground(guiGraphics);
        
        // Draw title
        guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        
        // Draw description
        guiGraphics.drawCenteredString(font, 
            Component.literal("Choose where to display the inventory slot previews:"), 
            width / 2, 60, 0xCCCCCC);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}