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
    

    private static final Pattern ACTION_PATTERN = Pattern.compile("!(\\w+)\\([^)]*\\)");

    // Client-side state
    private static String noteText = "";
    private static boolean isTyping = false;
    private static boolean isVisible = true;
    private static int cursorPosition = 0;
    private static final int MAX_TEXT_LENGTH = 2000;
    private static boolean[] originalKeyStates = null;
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
    public static String getNoteText() { return noteText; }
    public static void setNoteText(String text) { noteText = text; }
    public static int getCursorPosition() { return cursorPosition; }
    public static void setCursorPosition(int position) { cursorPosition = position; }
    public static boolean isTyping() { return isTyping; }
    public static boolean isVisible() { return isVisible; }
    public static boolean isDragMode() { return isDragMode; }
    public static boolean isWaitingForResponse() { return AIIntegration.isWaitingForResponse(); }
    public static int getWindowWidth() { return windowWidth; }
    public static int getWindowHeight() { return windowHeight; }
    public static int getWindowX() { return windowX; }
    public static int getWindowY() { return windowY; }
    public static void setWindowPosition(int x, int y) { windowX = x; windowY = y; }
    public static int getMaxTextLength() { return MAX_TEXT_LENGTH; }
    
    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (!isVisible) return;
        
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        
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
        if (!isTyping) return;
        
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
        if (!isVisible) return false;
        
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
    
    // Cleanup
    public static void cleanup() {
        AIIntegration.cleanup();
    }


    // PUBLIC METHOD: Main entry point for processing AI responses with actions
    public static String processAIResponse(String response) {
        System.out.println("DEBUG: processAIResponse called with: " + response);
        
        if (response == null || response.trim().isEmpty()) {
            return "No response received";
        }
        
        // First process any actions in the response
        String processedResponse = processActionsInResponse(response);
        
        System.out.println("DEBUG: After processing actions: " + processedResponse);
        
        // Then format the text for display
        return formatResponseText(processedResponse);
    }

    // Method to extract and execute actions from AI response
    private static String processActionsInResponse(String response) {
        System.out.println("DEBUG: Processing actions in response: " + response);
        
        // Look for both square bracket and exclamation mark formats
        Pattern squareBracketPattern = Pattern.compile("\\[[^\\]]+\\]");
        Pattern exclamationPattern = Pattern.compile("!\\w+\\([^)]*\\)");
        
        StringBuilder processedResponse = new StringBuilder();
        String workingResponse = response;
        
        boolean foundActions = false;
        
        // First check for square bracket actions (newer format)
        Matcher squareMatcher = squareBracketPattern.matcher(workingResponse);
        if (squareMatcher.find()) {
            foundActions = true;
            System.out.println("DEBUG: Found square bracket actions, delegating to ActionParser");
            // Let ActionParser handle square bracket actions
            return response; // Return original response, let ActionParser handle it
        }
        
        // Then check for exclamation mark actions (legacy format)
        Matcher exclamationMatcher = exclamationPattern.matcher(workingResponse);
        int lastEnd = 0;
        
        while (exclamationMatcher.find()) {
            foundActions = true;
            // Add text before the action
            processedResponse.append(workingResponse.substring(lastEnd, exclamationMatcher.start()));
            
            String actionCommand = exclamationMatcher.group();
            System.out.println("DEBUG: Found exclamation action command: " + actionCommand);
            
            // Execute the action
            String result = executeAction(actionCommand);
            System.out.println("DEBUG: Action result: " + result);
            
            // Replace the action command with the result
            processedResponse.append(result);
            
            lastEnd = exclamationMatcher.end();
        }
        
        if (!foundActions) {
            System.out.println("DEBUG: No actions found in response");
            return response; // Return original if no actions found
        }
        
        // Add remaining text after last action
        processedResponse.append(workingResponse.substring(lastEnd));
        
        return processedResponse.toString();
    }

    // Method to execute individual action commands
    private static String executeAction(String actionCommand) {
        System.out.println("DEBUG: Executing action: " + actionCommand);
        
        try {
            if (actionCommand.startsWith("!collectBlocks(")) {
                return executeCollectBlocks(actionCommand);
            } else if (actionCommand.startsWith("!moveTowards(")) {
                return executeMoveTowards(actionCommand);
            } else if (actionCommand.startsWith("!lookAt(")) {
                return executeLookAt(actionCommand);
            } else if (actionCommand.startsWith("!placeBlock(")) {
                return executePlaceBlock(actionCommand);
            } else {
                return "[Unknown action: " + actionCommand + "]";
            }
        } catch (Exception e) {
            System.err.println("Error executing action: " + e.getMessage());
            return "[Action failed: " + e.getMessage() + "]";
        }
    }

    // Execute block collection action
    private static String executeCollectBlocks(String actionCommand) {
        System.out.println("DEBUG: Executing collectBlocks: " + actionCommand);
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return "[Cannot collect blocks - no player or world]";
        }
        
        // Parse the command to extract block type and count
        // Format: !collectBlocks("block_name", count)
        Pattern pattern = Pattern.compile("!collectBlocks\\(\"([^\"]+)\",\\s*(\\d+)\\)");
        Matcher matcher = pattern.matcher(actionCommand);
        
        if (!matcher.find()) {
            return "[Invalid collectBlocks format]";
        }
        
        String blockName = matcher.group(1);
        int count = Integer.parseInt(matcher.group(2));
        
        System.out.println("DEBUG: Looking for block: " + blockName + ", count: " + count);
        
        // Find the nearest block of the specified type
        BlockPos targetPos = findNearestBlock(blockName, 10); // Search within 10 blocks
        
        if (targetPos == null) {
            return "[No " + blockName + " found nearby]";
        }
        
        // Simulate mining the block
        boolean success = mineBlock(targetPos);
        
        if (success) {
            return "[Mined " + blockName + " at " + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ() + "]";
        } else {
            return "[Failed to mine " + blockName + "]";
        }
    }

    // Find the nearest block of a specific type, prioritizing the block the player is looking at
    private static BlockPos findNearestBlock(String blockName, int searchRadius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        
        // First, try to get the block the player is looking at
        BlockPos lookingAtPos = getBlockPlayerIsLookingAt();
        if (lookingAtPos != null) {
            BlockState lookingAtState = mc.level.getBlockState(lookingAtPos);
            String lookingAtBlockName = getBlockName(lookingAtState);
            
            System.out.println("DEBUG: Player is looking at: " + lookingAtBlockName + " at " + lookingAtPos);
            
            // If the block they're looking at matches what we're searching for, use that
            if (blockMatches(lookingAtBlockName, blockName)) {
                System.out.println("DEBUG: Found exact block player is looking at!");
                return lookingAtPos;
            }
        }
        
        // If no specific block found or not looking at the right type, search nearby
        BlockPos playerPos = mc.player.blockPosition();
        
        // Convert block name to ResourceLocation for comparison
        ResourceLocation blockId = ResourceLocation.tryParse("minecraft:" + blockName);
        if (blockId == null) {
            blockId = ResourceLocation.tryParse(blockName);
        }
        
        if (blockId == null) {
            System.out.println("DEBUG: Invalid block name: " + blockName);
            return null;
        }
        
        Block targetBlock = BuiltInRegistries.BLOCK.get(blockId);
        if (targetBlock == null) {
            System.out.println("DEBUG: Block not found in registry: " + blockId);
            return null;
        }
        
        System.out.println("DEBUG: Searching for block: " + targetBlock + " around " + playerPos);
        
        // Search in a cube around the player, but prioritize closer blocks
        BlockPos closestPos = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos checkPos = playerPos.offset(x, y, z);
                    BlockState blockState = mc.level.getBlockState(checkPos);
                    
                    if (blockState.getBlock() == targetBlock) {
                        double distance = playerPos.distSqr(checkPos);
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closestPos = checkPos;
                        }
                    }
                }
            }
        }
        
        if (closestPos != null) {
            System.out.println("DEBUG: Found closest target block at: " + closestPos);
        } else {
            System.out.println("DEBUG: No target block found");
        }
        
        return closestPos;
    }

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
    
    // Check if two block names match (with some flexibility)
    private static boolean blockMatches(String actualBlock, String requestedBlock) {
        // Normalize both names
        actualBlock = actualBlock.toLowerCase().replace(" ", "_");
        requestedBlock = requestedBlock.toLowerCase().replace(" ", "_");
        
        // Direct match
        if (actualBlock.equals(requestedBlock)) {
            return true;
        }
        
        // Check if one contains the other
        if (actualBlock.contains(requestedBlock) || requestedBlock.contains(actualBlock)) {
            return true;
        }
        
        // Common variations
        if (actualBlock.equals("oak_log") && (requestedBlock.equals("log") || requestedBlock.equals("oak"))) {
            return true;
        }
        if (actualBlock.equals("stone") && requestedBlock.equals("cobblestone")) {
            return true;
        }
        // Add more variations as needed
        
        return false;
    }

    // Simulate mining a block
    private static boolean mineBlock(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return false;
        }
        
        try {
            System.out.println("DEBUG: Attempting to mine block at: " + pos);
            
            // Use the game mode to break the block
            mc.gameMode.destroyBlock(pos);
            
            System.out.println("DEBUG: Block mining command sent");
            return true;
        } catch (Exception e) {
            System.err.println("Failed to mine block: " + e.getMessage());
            return false;
        }
    }

    // Execute movement action (placeholder)
    private static String executeMoveTowards(String actionCommand) {
        return "[Movement not implemented yet]";
    }

    // Execute look action (placeholder)
    private static String executeLookAt(String actionCommand) {
        return "[Look action not implemented yet]";
    }

    // Execute place block action (placeholder)
    private static String executePlaceBlock(String actionCommand) {
        return "[Place block not implemented yet]";
    }

    // Format response text for display (moved the original formatting logic here)
    private static String formatResponseText(String response) {
        // Split response into sentences for better formatting
        String[] sentences = response.split("(?<=[.!?])\\s+");
        StringBuilder formatted = new StringBuilder();
        
        int lineLength = 0;
        int maxLineLength = (windowWidth - 20) / 6; // Approximate characters per line
        
        for (String sentence : sentences) {
            if (lineLength + sentence.length() > maxLineLength && lineLength > 0) {
                formatted.append("\n");
                lineLength = 0;
            }
            
            if (lineLength > 0) {
                formatted.append(" ");
                lineLength++;
            }
            
            formatted.append(sentence);
            lineLength += sentence.length();
        }
        
        return formatted.toString() + "\n"; // Add newline at the end
    }

    // Legacy method for backwards compatibility (remove the old one)
    private static String formatAIResponse(String response) {
        return processAIResponse(response);
    }

}