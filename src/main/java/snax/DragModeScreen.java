package snax;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class DragModeScreen extends Screen {
    
    public DragModeScreen() {
        super(Component.literal("Drag Mode"));
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Render completely transparent background
        // Don't call super.render() to avoid any default background
        
        // Still allow the notes overlay to render through the normal overlay system
        // The notes window will be rendered by the RenderGuiOverlayEvent
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left mouse button
            if (Notes.isMouseOverWindow(mouseX, mouseY)) {
                // Start dragging - calculate offset from window top-left corner
                Minecraft mc = Minecraft.getInstance();
                int screenWidth = mc.getWindow().getGuiScaledWidth();
                int screenHeight = mc.getWindow().getGuiScaledHeight();
                
                int noteX, noteY;
                if (Notes.getWindowX() == -1) {
                    noteX = screenWidth - Notes.getWindowWidth() - 10;
                    noteY = 10;
                } else {
                    noteX = Math.max(0, Math.min(Notes.getWindowX(), screenWidth - Notes.getWindowWidth()));
                    noteY = Math.max(0, Math.min(Notes.getWindowY(), screenHeight - Notes.getWindowHeight()));
                }
                
                Notes.setDragOffset((int)(mouseX - noteX), (int)(mouseY - noteY));
                Notes.setDragging(true);
                
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (Notes.isDragging() && button == 0) {
            // Update window position based on mouse position minus offset
            int newX = (int)(mouseX - Notes.getDragOffsetX());
            int newY = (int)(mouseY - Notes.getDragOffsetY());
            
            // Clamp to screen bounds
            Minecraft mc = Minecraft.getInstance();
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            newX = Math.max(0, Math.min(newX, screenWidth - Notes.getWindowWidth()));
            newY = Math.max(0, Math.min(newY, screenHeight - Notes.getWindowHeight()));
            
            Notes.setWindowPosition(newX, newY);
            
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (Notes.isDragging() && button == 0) {
            Notes.setDragging(false);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle F8 to exit drag mode
        if (keyCode == GLFW.GLFW_KEY_F8) {
            Notes.setDragMode(false);
            Notes.setDragging(false);
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false; // Don't pause the game
    }
}