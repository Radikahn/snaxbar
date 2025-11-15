package snax;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SnaxBarMod.MODID, value = Dist.CLIENT)
public class Notes {

    // Client-side state
    private static String noteText = "";
    private static boolean isTyping = false;
    private static boolean isVisible = true;
    private static int cursorPosition = 0;
    private static final int MAX_TEXT_LENGTH = 2000;
    private static long lastAutoSave = 0;
    private static final long AUTO_SAVE_INTERVAL = 30000; // 30 seconds

    // Window sizing and positioning
    private static int windowWidth = 200;
    private static int windowHeight = 300;
    private static final int MIN_WIDTH = 120;
    private static final int MAX_WIDTH = 600;
    private static final int MIN_HEIGHT = 100;
    private static final int MAX_HEIGHT = 800;
    private static final int RESIZE_STEP = 10;
    private static int windowX = -1; // -1 means use default positioning
    private static int windowY = 10;
    private static boolean isDragging = false;
    private static boolean isDragMode = false;
    private static int dragOffsetX = 0;
    private static int dragOffsetY = 0;

    // Getters for other classes
    public static String getNoteText() {
        return noteText;
    }

    public static void setNoteText(String text) {
        noteText = text;
    }

    public static int getCursorPosition() {
        return cursorPosition;
    }

    public static void setCursorPosition(int position) {
        cursorPosition = position;
    }

    public static boolean isTyping() {
        return isTyping;
    }

    public static boolean isVisible() {
        return isVisible;
    }

    public static boolean isDragMode() {
        return isDragMode;
    }

    public static int getWindowWidth() {
        return windowWidth;
    }

    public static int getWindowHeight() {
        return windowHeight;
    }

    public static int getWindowX() {
        return windowX;
    }

    public static int getWindowY() {
        return windowY;
    }

    public static void setWindowPosition(int x, int y) {
        windowX = x;
        windowY = y;
    }

    public static int getMaxTextLength() {
        return MAX_TEXT_LENGTH;
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (!isVisible)
            return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null)
            return;

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Calculate position
        int noteX, noteY;
        if (windowX == -1) {
            noteX = screenWidth - windowWidth - 10;
            noteY = 10;
        } else {
            noteX = Math.max(0, Math.min(windowX, screenWidth - windowWidth));
            noteY = Math.max(0, Math.min(windowY, screenHeight - windowHeight));
        }

        NotesRenderer.renderNotesBox(guiGraphics, noteX, noteY, windowWidth, windowHeight,
                isDragMode, isTyping, noteText, cursorPosition);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS && event.getAction() != GLFW.GLFW_REPEAT) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        // Handle window resizing
        if (isVisible && KeyHandler.handleResizeKeys(event.getKey())) {
            event.setResult(Event.Result.DENY);
            return;
        }

        // Handle special function keys
        if (KeyHandler.handleSpecialKeys(event.getKey(), mc)) {
            event.setResult(Event.Result.DENY);
            return;
        }

        // Only process input when typing mode is active
        if (!isTyping)
            return;

        event.setResult(Event.Result.DENY);

        // Handle typing input
        if (KeyHandler.handleTypingKeys(event.getKey())) {
            NetworkManager.saveNotesToServer();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (isTyping) {
            if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.DeathScreen)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (isTyping && event.phase == TickEvent.Phase.START) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen)) {
                mc.setScreen(null);
            }
            KeyHandler.resetKeyStates();
        }

        // Auto-save logic
        if (event.phase == TickEvent.Phase.END) {
            long currentTime = System.currentTimeMillis();
            if (isTyping && currentTime - lastAutoSave > AUTO_SAVE_INTERVAL) {
                NetworkManager.saveNotesToServer();
                lastAutoSave = currentTime;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseInput(InputEvent.MouseButton event) {
        if (isTyping) {
            event.setResult(Event.Result.DENY);
            return;
        }

        if (isDragMode) {
            return; // Let DragModeScreen handle it
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (isTyping) {
            event.setResult(Event.Result.DENY);
        }
    }

    // State management methods
    public static void setTypingMode(boolean typing) {
        isTyping = typing;
        if (typing) {
            if (!NetworkManager.isNetworkInitialized()) {
                NetworkManager.loadNotesLocally();
            }
            cursorPosition = noteText.length();
            KeyHandler.disableKeyBindings();
        } else {
            KeyHandler.enableKeyBindings();
            NetworkManager.saveNotesToServer();
        }
    }

    public static void setDragMode(boolean dragMode) {
        isDragMode = dragMode;
        if (!dragMode && isDragging) {
            isDragging = false;
        }
        if (dragMode && isTyping) {
            setTypingMode(false);
        }
    }

    public static void setVisible(boolean visible) {
        isVisible = visible;
        if (!visible && isTyping) {
            setTypingMode(false);
        }
        if (!visible && isDragMode) {
            setDragMode(false);
        }
    }

    public static void resizeWindow(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_UP:
                windowHeight = Math.max(MIN_HEIGHT, windowHeight - RESIZE_STEP);
                break;
            case GLFW.GLFW_KEY_DOWN:
                windowHeight = Math.min(MAX_HEIGHT, windowHeight + RESIZE_STEP);
                break;
            case GLFW.GLFW_KEY_LEFT:
                windowWidth = Math.min(MAX_WIDTH, windowWidth + RESIZE_STEP);
                break;
            case GLFW.GLFW_KEY_RIGHT:
                windowWidth = Math.max(MIN_WIDTH, windowWidth - RESIZE_STEP);
                break;
        }
    }

    // Text manipulation methods
    public static void insertText(String text) {
        if (noteText.length() + text.length() <= MAX_TEXT_LENGTH) {
            noteText = noteText.substring(0, cursorPosition) + text + noteText.substring(cursorPosition);
            cursorPosition += text.length();
        }
    }

    public static void deleteCharacterBefore() {
        if (cursorPosition > 0) {
            noteText = noteText.substring(0, cursorPosition - 1) + noteText.substring(cursorPosition);
            cursorPosition--;
        }
    }

    public static void deleteCharacterAfter() {
        if (cursorPosition < noteText.length()) {
            noteText = noteText.substring(0, cursorPosition) + noteText.substring(cursorPosition + 1);
        }
    }

    public static void moveCursorLeft() {
        if (cursorPosition > 0) {
            cursorPosition--;
        }
    }

    public static void moveCursorRight() {
        if (cursorPosition < noteText.length()) {
            cursorPosition++;
        }
    }

    public static void moveCursorHome() {
        cursorPosition = 0;
    }

    public static void moveCursorEnd() {
        cursorPosition = noteText.length();
    }

    // Check if mouse is over the notes window
    public static boolean isMouseOverWindow(double mouseX, double mouseY) {
        if (!isVisible)
            return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int noteX, noteY;
        if (windowX == -1) {
            noteX = screenWidth - windowWidth - 10;
            noteY = 10;
        } else {
            noteX = Math.max(0, Math.min(windowX, screenWidth - windowWidth));
            noteY = Math.max(0, Math.min(windowY, screenHeight - windowHeight));
        }

        return mouseX >= noteX && mouseX <= noteX + windowWidth &&
                mouseY >= noteY && mouseY <= noteY + windowHeight;
    }

    // Drag state management
    public static void setDragging(boolean dragging) {
        isDragging = dragging;
    }

    public static boolean isDragging() {
        return isDragging;
    }

    public static void setDragOffset(int x, int y) {
        dragOffsetX = x;
        dragOffsetY = y;
    }

    public static int getDragOffsetX() {
        return dragOffsetX;
    }

    public static int getDragOffsetY() {
        return dragOffsetY;
    }

    // Useful methods that I will attempt to use for base apps
    // Get the block position the player is currently looking at
    private static BlockPos getBlockPlayerIsLookingAt() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }

        // Use Minecraft's built-in block picking logic
        HitResult hitResult = mc.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult) hitResult;
            return blockHitResult.getBlockPos();
        }

        return null;
    }

    // Get a clean block name from a BlockState
    private static String getBlockName(BlockState blockState) {
        return blockState.getBlock().getName().getString().toLowerCase()
                .replace(" ", "_")
                .replace("block_of_", "")
                .replace("_block", "");
    }

}
