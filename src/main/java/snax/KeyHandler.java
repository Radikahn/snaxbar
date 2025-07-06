package snax;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class KeyHandler {
    
    // Check if control is pressed for resizing
    private static boolean isControlPressed() {
        return GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
               GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }
    
    // Check if shift is pressed for character input
    private static boolean isShiftPressed() {
        return GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
               GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }
    
    // Handle window resizing with Ctrl + Arrow keys
    public static boolean handleResizeKeys(int key) {
        if (!isControlPressed()) return false;
        
        Notes.resizeWindow(key);
        return true;
    }
    
    // Handle special function keys (F7, F8, F9, F10)
    public static boolean handleSpecialKeys(int key, Minecraft mc) {
        switch (key) {
            case GLFW.GLFW_KEY_F8:
                Notes.setDragMode(!Notes.isDragMode());
                
                if (Notes.isDragMode()) {
                    mc.setScreen(new DragModeScreen());
                } else {
                    if (mc.screen instanceof DragModeScreen) {
                        mc.setScreen(null);
                    }
                }
                return true;
                
            case GLFW.GLFW_KEY_F9:
                Notes.setVisible(!Notes.isVisible());
                return true;
                
            case GLFW.GLFW_KEY_F10:
                // Only allow typing if window is visible and not in drag mode
                if (!Notes.isVisible() || Notes.isDragMode()) {
                    return true;
                }
                
                Notes.setTypingMode(!Notes.isTyping());
                
                if (Notes.isTyping()) {
                    // Close any open screen when starting to type
                    if (mc.screen != null) {
                        mc.setScreen(null);
                    }
                }
                return true;
                
            case GLFW.GLFW_KEY_F7:
                mc.setScreen(new SettingsScreen(mc.screen));
                return true;
        }
        return false;
    }
    
    // Handle typing keys when in typing mode
    public static boolean handleTypingKeys(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE:
                Notes.setTypingMode(false);
                return true;
                
            case GLFW.GLFW_KEY_BACKSPACE:
                Notes.deleteCharacterBefore();
                return false;
                
            case GLFW.GLFW_KEY_DELETE:
                Notes.deleteCharacterAfter();
                return false;
                
            case GLFW.GLFW_KEY_LEFT:
                if (!isControlPressed()) {
                    Notes.moveCursorLeft();
                }
                return false;
                
            case GLFW.GLFW_KEY_RIGHT:
                if (!isControlPressed()) {
                    Notes.moveCursorRight();
                }
                return false;
                
            case GLFW.GLFW_KEY_HOME:
                Notes.moveCursorHome();
                return false;
                
            case GLFW.GLFW_KEY_END:
                Notes.moveCursorEnd();
                return false;
                
            case GLFW.GLFW_KEY_ENTER:
                TextProcessor.handleEnterKey();
                return true;
                
            default:
                // Handle character input
                char character = getCharFromKey(key, isShiftPressed());
                if (character != 0) {
                    Notes.insertText(String.valueOf(character));
                    
                    // Check for bullet point formatting
                    if (character == ' ') {
                        TextProcessor.checkBulletPointFormatting();
                    }
                    return false;
                }
                return false;
        }
    }
    
    // Enhanced character conversion with shift support
    private static char getCharFromKey(int key, boolean shiftPressed) {
        // Handle letters
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            char baseChar = (char) ('a' + (key - GLFW.GLFW_KEY_A));
            return shiftPressed ? Character.toUpperCase(baseChar) : baseChar;
        }
        
        // Handle numbers and their shift variants
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            if (shiftPressed) {
                switch (key) {
                    case GLFW.GLFW_KEY_1: return '!';
                    case GLFW.GLFW_KEY_2: return '@';
                    case GLFW.GLFW_KEY_3: return '#';
                    case GLFW.GLFW_KEY_4: return ' ';
                    case GLFW.GLFW_KEY_5: return '%';
                    case GLFW.GLFW_KEY_6: return '^';
                    case GLFW.GLFW_KEY_7: return '&';
                    case GLFW.GLFW_KEY_8: return '*';
                    case GLFW.GLFW_KEY_9: return '(';
                    case GLFW.GLFW_KEY_0: return ')';
                }
            }
            return (char) ('0' + (key - GLFW.GLFW_KEY_0));
        }
        
        // Handle space
        if (key == GLFW.GLFW_KEY_SPACE) {
            return ' ';
        }
        
        // Handle common punctuation with shift variants
        switch (key) {
            case GLFW.GLFW_KEY_PERIOD: return shiftPressed ? '>' : '.';
            case GLFW.GLFW_KEY_COMMA: return shiftPressed ? '<' : ',';
            case GLFW.GLFW_KEY_SEMICOLON: return shiftPressed ? ':' : ';';
            case GLFW.GLFW_KEY_APOSTROPHE: return shiftPressed ? '"' : '\'';
            case GLFW.GLFW_KEY_SLASH: return shiftPressed ? '?' : '/';
            case GLFW.GLFW_KEY_BACKSLASH: return shiftPressed ? '|' : '\\';
            case GLFW.GLFW_KEY_LEFT_BRACKET: return shiftPressed ? '{' : '[';
            case GLFW.GLFW_KEY_RIGHT_BRACKET: return shiftPressed ? '}' : ']';
            case GLFW.GLFW_KEY_EQUAL: return shiftPressed ? '+' : '=';
            case GLFW.GLFW_KEY_MINUS: return shiftPressed ? '_' : '-';
            case GLFW.GLFW_KEY_GRAVE_ACCENT: return shiftPressed ? '~' : '`';
            default: return 0; // Unknown key
        }
    }
    
    // Key binding management
    private static boolean[] originalKeyStates = null;
    
    public static void disableKeyBindings() {
        Minecraft mc = Minecraft.getInstance();
        KeyMapping[] keyMappings = mc.options.keyMappings;
        
        if (originalKeyStates == null) {
            originalKeyStates = new boolean[keyMappings.length];
        }
        
        // Store original states and set all to false
        for (int i = 0; i < keyMappings.length; i++) {
            originalKeyStates[i] = keyMappings[i].isDown();
            keyMappings[i].setDown(false);
        }
    }
    
    public static void enableKeyBindings() {
        if (originalKeyStates == null) return;
        
        Minecraft mc = Minecraft.getInstance();
        KeyMapping[] keyMappings = mc.options.keyMappings;
        
        // Restore original states
        for (int i = 0; i < keyMappings.length && i < originalKeyStates.length; i++) {
            keyMappings[i].setDown(originalKeyStates[i]);
        }
        
        originalKeyStates = null;
    }
    
    public static void resetKeyStates() {
        Minecraft mc = Minecraft.getInstance();
        KeyMapping[] keyMappings = mc.options.keyMappings;
        
        for (KeyMapping keyMapping : keyMappings) {
            keyMapping.setDown(false);
            // Also reset the click count to prevent actions
            while (keyMapping.consumeClick()) {
                // Consume all pending clicks
            }
        }
    }
}