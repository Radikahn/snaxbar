package snax;

import net.minecraftforge.client.event.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.event.TickEvent;
import java.util.function.Supplier;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = SnaxBarMod.MODID, value = Dist.CLIENT)
public class Notes {
    
    // Network channel for syncing notes data
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        ResourceLocation.fromNamespaceAndPath(SnaxBarMod.MODID, "notes_channel"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );
    
    // NBT key for storing notes data
    private static final String NOTES_NBT_KEY = "snax_notes_text";
    
    // Client-side state
    private static String noteText = "";
    private static boolean isTyping = false;
    private static boolean isVisible = true; // Controls window visibility
    private static int cursorPosition = 0;
    private static final int MAX_TEXT_LENGTH = 2000; // Increased for AI responses
    private static boolean[] originalKeyStates = null;
    private static long lastAutoSave = 0;
    private static final long AUTO_SAVE_INTERVAL = 30000; // 30 seconds
    private static boolean networkInitialized = false;
    
    // Ollama integration variables
    private static final String OLLAMA_BASE_URL = "http://127.0.0.1:11434"; // Default Ollama URL
    private static final String DEFAULT_MODEL = "llama3:latest"; // Default model, can be changed
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Gson gson = new Gson();
    private static boolean isWaitingForResponse = false;
    private static String currentQuery = "";
    
    // Window sizing variables
    private static int windowWidth = 200; // Increased default width for AI responses
    private static int windowHeight = 300; // Increased default height for AI responses
    private static final int MIN_WIDTH = 120;
    private static final int MAX_WIDTH = 600; // Increased max width
    private static final int MIN_HEIGHT = 100;
    private static final int MAX_HEIGHT = 800; // Increased max height
    private static final int RESIZE_STEP = 10;
    
    // Window positioning variables
    private static int windowX = -1; // -1 means use default positioning
    private static int windowY = 10;
    private static boolean isDragging = false;
    private static boolean isDragMode = false; // NEW: Tracks if drag mode is enabled
    private static int dragOffsetX = 0;
    private static int dragOffsetY = 0;
    
    // Server-side storage for notes
    private static final Map<UUID, String> serverNotes = new HashMap<>();
    
    // Ollama API classes
    public static class OllamaRequest {
        public String model;
        public String prompt;
        public boolean stream = false;
        
        public OllamaRequest(String model, String prompt) {
            this.model = model;
            this.prompt = prompt;
        }
    }
    
    public static class OllamaResponse {
        public String response;
        public boolean done;
        public String error;
    }
    
    // Method to call Ollama API
    private static CompletableFuture<String> callOllamaAPI(String query) {
        return CompletableFuture.supplyAsync(() -> {
            try {

                String machineQuery = query + " respond in less than 100 words, be concise";

                OllamaRequest request = new OllamaRequest(DEFAULT_MODEL, machineQuery);
                String jsonBody = gson.toJson(request);
                
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(OLLAMA_BASE_URL + "/api/generate"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();
                
                HttpResponse<String> response = httpClient.send(httpRequest, 
                        HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    OllamaResponse ollamaResponse = gson.fromJson(response.body(), OllamaResponse.class);
                    if (ollamaResponse.error != null) {
                        return "Error: " + ollamaResponse.error;
                    }
                    return ollamaResponse.response != null ? ollamaResponse.response.trim() : "No response received";
                } else {
                    return "HTTP Error: " + response.statusCode();
                }
            } catch (Exception e) {
                return "Connection Error: " + e.getMessage();
            }
        }, executor);
    }
    
    // Method to handle AI query
    private static void handleAIQuery(String query) {
        if (isWaitingForResponse) {
            return; // Prevent multiple simultaneous requests
        }
        
        currentQuery = query;
        isWaitingForResponse = true;
        
        // Add the query to notes with a loading indicator
        String queryLine = "\n> " + query + "\nThinking...";
        if (noteText.length() + queryLine.length() <= MAX_TEXT_LENGTH) {
            noteText = noteText.substring(0, cursorPosition) + queryLine + noteText.substring(cursorPosition);
            cursorPosition += queryLine.length();
        }
        
        // Make the API call
        callOllamaAPI(query).thenAccept(response -> {
            // This runs on a different thread, so we need to be careful
            Minecraft.getInstance().execute(() -> {
                // Remove the "Thinking..." text and replace with actual response
                String searchText = "Thinking...";
                int thinkingIndex = noteText.lastIndexOf(searchText);
                if (thinkingIndex != -1) {
                    String beforeThinking = noteText.substring(0, thinkingIndex);
                    String afterThinking = noteText.substring(thinkingIndex + searchText.length());
                    
                    // Format the response with proper line breaks
                    String formattedResponse = formatAIResponse(response);
                    String newText = beforeThinking + formattedResponse + afterThinking;
                    
                    if (newText.length() <= MAX_TEXT_LENGTH) {
                        noteText = newText;
                        cursorPosition = beforeThinking.length() + formattedResponse.length();
                    } else {
                        // If response is too long, truncate it
                        int maxResponseLength = MAX_TEXT_LENGTH - beforeThinking.length() - afterThinking.length();
                        if (maxResponseLength > 0) {
                            formattedResponse = formattedResponse.substring(0, Math.min(formattedResponse.length(), maxResponseLength)) + "...";
                            noteText = beforeThinking + formattedResponse + afterThinking;
                            cursorPosition = beforeThinking.length() + formattedResponse.length();
                        }
                    }
                }
                isWaitingForResponse = false;
                saveNotesToServer(); // Auto-save after AI response
            });
        }).exceptionally(throwable -> {
            // Handle errors
            Minecraft.getInstance().execute(() -> {
                String searchText = "Thinking...";
                int thinkingIndex = noteText.lastIndexOf(searchText);
                if (thinkingIndex != -1) {
                    String beforeThinking = noteText.substring(0, thinkingIndex);
                    String afterThinking = noteText.substring(thinkingIndex + searchText.length());
                    String errorResponse = "Error: Failed to get AI response";
                    noteText = beforeThinking + errorResponse + afterThinking;
                    cursorPosition = beforeThinking.length() + errorResponse.length();
                }
                isWaitingForResponse = false;
            });
            return null;
        });
    }
    
    // Format AI response with proper line breaks and indentation
    private static String formatAIResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "No response received";
        }
        
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
    
    // Check if the current line starts with "/"
    private static boolean isAIQueryLine() {
        if (noteText.isEmpty()) return false;
        
        // Find the start of the current line
        int lineStart = getLineStart(cursorPosition);
        return lineStart < noteText.length() && noteText.charAt(lineStart) == '/';
    }
    
    // Extract AI query from the current line
    private static String extractAIQuery() {
        int lineStart = getLineStart(cursorPosition);
        int lineEnd = getLineEnd(cursorPosition);
        
        if (lineStart < noteText.length() && noteText.charAt(lineStart) == '/') {
            String line = noteText.substring(lineStart + 1, lineEnd); // Skip the '/' character
            return line.trim();
        }
        
        return "";
    }
    
    // Get the end position of the current line
    private static int getLineEnd(int position) {
        int lineEnd = noteText.length();
        for (int i = position; i < noteText.length(); i++) {
            if (noteText.charAt(i) == '\n') {
                lineEnd = i;
                break;
            }
        }
        return lineEnd;
    }
    
    // Remove the current AI query line
    private static void removeCurrentAIQueryLine() {
        int lineStart = getLineStart(cursorPosition);
        int lineEnd = getLineEnd(cursorPosition);
        
        // Include the newline character if it exists
        if (lineEnd < noteText.length() && noteText.charAt(lineEnd) == '\n') {
            lineEnd++;
        }
        
        noteText = noteText.substring(0, lineStart) + noteText.substring(lineEnd);
        cursorPosition = lineStart;
    }
    
    // Transparent screen for drag mode
    public static class DragModeScreen extends net.minecraft.client.gui.screens.Screen {
        
        public DragModeScreen() {
            super(net.minecraft.network.chat.Component.literal("Drag Mode"));
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
                if (isMouseOverWindow(mouseX, mouseY)) {
                    // Start dragging - calculate offset from window top-left corner
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
                    
                    dragOffsetX = (int)(mouseX - noteX);
                    dragOffsetY = (int)(mouseY - noteY);
                    isDragging = true;
                    
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (isDragging && button == 0) {
                // Update window position based on mouse position minus offset
                windowX = (int)(mouseX - dragOffsetX);
                windowY = (int)(mouseY - dragOffsetY);
                
                // Clamp to screen bounds
                Minecraft mc = Minecraft.getInstance();
                int screenWidth = mc.getWindow().getGuiScaledWidth();
                int screenHeight = mc.getWindow().getGuiScaledHeight();
                windowX = Math.max(0, Math.min(windowX, screenWidth - windowWidth));
                windowY = Math.max(0, Math.min(windowY, screenHeight - windowHeight));
                
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        
        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (isDragging && button == 0) {
                isDragging = false;
                return true;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }
        
        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            // Handle F8 to exit drag mode
            if (keyCode == GLFW.GLFW_KEY_F8) {
                isDragMode = false;
                isDragging = false;
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
    
    // Initialize network packets
    public static void initNetwork() {
        if (!networkInitialized) {
            int packetId = 0;
            INSTANCE.registerMessage(packetId++, NotesUpdatePacket.class, NotesUpdatePacket::encode, NotesUpdatePacket::decode, NotesUpdatePacket::handle);
            INSTANCE.registerMessage(packetId++, NotesRequestPacket.class, NotesRequestPacket::encode, NotesRequestPacket::decode, NotesRequestPacket::handle);
            networkInitialized = true;
        }
    }
    
    // Server-side event handlers
    @Mod.EventBusSubscriber(modid = SnaxBarMod.MODID, value = Dist.DEDICATED_SERVER)
    public static class ServerEvents {
        
        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                // Load notes from persistent data
                CompoundTag playerData = serverPlayer.getPersistentData();
                String savedNotes = playerData.getString(NOTES_NBT_KEY);
                
                // Store in server cache
                serverNotes.put(serverPlayer.getUUID(), savedNotes);
                
                // Send to client if network is initialized
                if (networkInitialized) {
                    try {
                        INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new NotesUpdatePacket(savedNotes));
                    } catch (Exception e) {
                        System.err.println("Failed to send notes to client: " + e.getMessage());
                    }
                }
            }
        }
        
        @SubscribeEvent
        public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                // Save notes to persistent data
                String notes = serverNotes.get(serverPlayer.getUUID());
                if (notes != null) {
                    CompoundTag playerData = serverPlayer.getPersistentData();
                    playerData.putString(NOTES_NBT_KEY, notes);
                }
                // Remove from cache
                serverNotes.remove(serverPlayer.getUUID());
            }
        }
    }
    
    // Common event handlers (both client and server)
    @Mod.EventBusSubscriber(modid = SnaxBarMod.MODID)
    public static class CommonEvents {
        
        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                // Load notes from persistent data
                CompoundTag playerData = serverPlayer.getPersistentData();
                String savedNotes = playerData.getString(NOTES_NBT_KEY);
                
                // Store in server cache
                serverNotes.put(serverPlayer.getUUID(), savedNotes);
                
                // Send to client if network is initialized
                if (networkInitialized) {
                    try {
                        INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new NotesUpdatePacket(savedNotes));
                    } catch (Exception e) {
                        System.err.println("Failed to send notes to client: " + e.getMessage());
                    }
                }
            }
        }
        
        @SubscribeEvent
        public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                // Save notes to persistent data
                String notes = serverNotes.get(serverPlayer.getUUID());
                if (notes != null) {
                    CompoundTag playerData = serverPlayer.getPersistentData();
                    playerData.putString(NOTES_NBT_KEY, notes);
                }
                // Remove from cache
                serverNotes.remove(serverPlayer.getUUID());
            }
        }
    }
    
    // Packet for updating notes on client
    public static class NotesUpdatePacket {
        private final String notesText;
        
        public NotesUpdatePacket(String notesText) {
            this.notesText = notesText != null ? notesText : "";
        }
        
        public static void encode(NotesUpdatePacket packet, FriendlyByteBuf buffer) {
            buffer.writeUtf(packet.notesText);
        }
        
        public static NotesUpdatePacket decode(FriendlyByteBuf buffer) {
            return new NotesUpdatePacket(buffer.readUtf());
        }
        
        public static void handle(NotesUpdatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                // Update client-side notes text
                noteText = packet.notesText;
                cursorPosition = Math.min(cursorPosition, noteText.length());
            });
            context.setPacketHandled(true);
        }
    }
    
    // Packet for requesting notes save on server
    public static class NotesRequestPacket {
        private final String notesText;
        
        public NotesRequestPacket(String notesText) {
            this.notesText = notesText != null ? notesText : "";
        }
        
        public static void encode(NotesRequestPacket packet, FriendlyByteBuf buffer) {
            buffer.writeUtf(packet.notesText);
        }
        
        public static NotesRequestPacket decode(FriendlyByteBuf buffer) {
            return new NotesRequestPacket(buffer.readUtf());
        }
        
        public static void handle(NotesRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null) {
                    // Save notes to server cache and persistent data
                    serverNotes.put(player.getUUID(), packet.notesText);
                    CompoundTag playerData = player.getPersistentData();
                    playerData.putString(NOTES_NBT_KEY, packet.notesText);
                }
            });
            context.setPacketHandled(true);
        }
    }
    
    // Method to save notes to server
    private static void saveNotesToServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null && networkInitialized) {
            try {
                INSTANCE.sendToServer(new NotesRequestPacket(noteText));
            } catch (Exception e) {
                System.err.println("Failed to save notes to server: " + e.getMessage());
                // Fallback: save locally for single player
                if (mc.hasSingleplayerServer()) {
                    saveNotesLocally();
                }
            }
        } else {
            // Fallback for single player or when network isn't available
            saveNotesLocally();
        }
    }
    
    // Fallback method for local saving
    private static void saveNotesLocally() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            CompoundTag playerData = mc.player.getPersistentData();
            playerData.putString(NOTES_NBT_KEY, noteText);
        }
    }
    
    // Method to load notes locally
    private static void loadNotesLocally() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            CompoundTag playerData = mc.player.getPersistentData();
            String savedNotes = playerData.getString(NOTES_NBT_KEY);
            if (savedNotes != null && !savedNotes.isEmpty()) {
                noteText = savedNotes;
                cursorPosition = Math.min(cursorPosition, noteText.length());
            }
        }
    }
    
    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        // Only render if visible
        if (!isVisible) return;
        
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        
        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        // Calculate position - use custom position if set, otherwise default to top right
        int noteX, noteY;
        if (windowX == -1) {
            // Default position: top right corner
            noteX = screenWidth - windowWidth - 10;
            noteY = 10;
        } else {
            // Use custom position, but clamp to screen bounds
            noteX = Math.max(0, Math.min(windowX, screenWidth - windowWidth));
            noteY = Math.max(0, Math.min(windowY, screenHeight - windowHeight));
        }
        
        renderNotesBox(guiGraphics, noteX, noteY);
    }
    
    // Helper method to check if cursor is at the beginning of a line
    private static boolean isAtBeginningOfLine() {
        if (cursorPosition == 0) return true;
        
        // Check if the character before cursor is a newline
        return noteText.charAt(cursorPosition - 1) == '\n';
    }
    
    // Helper method to format bullet points
    private static void formatBulletPoint() {
        if (noteText.length() + 1 > MAX_TEXT_LENGTH) return; // Not enough space
        
        // Replace "- " with "• " (bullet character)
        String beforeCursor = noteText.substring(0, cursorPosition);
        String afterCursor = noteText.substring(cursorPosition);
        
        // Remove the "- " and add "• "
        if (beforeCursor.endsWith("- ")) {
            beforeCursor = beforeCursor.substring(0, beforeCursor.length() - 2) + "• ";
            noteText = beforeCursor + afterCursor;
            // Cursor position stays the same since we replaced 2 chars with 2 chars
        }
    }
    
    // Helper method to handle Enter key in bullet points and AI queries
    private static void handleEnterInBulletPoint() {
        // Check if this is an AI query line (starts with "/")
        if (isAIQueryLine()) {
            String query = extractAIQuery();
            if (!query.isEmpty() && !isWaitingForResponse) {
                // Remove the query line and process the AI request
                removeCurrentAIQueryLine();
                handleAIQuery(query);
                return;
            }
        }
        
        if (noteText.length() + 3 > MAX_TEXT_LENGTH) return; // Not enough space for newline + bullet
        
        // Check if current line starts with a bullet point
        int lineStart = getLineStart(cursorPosition);
        if (lineStart < noteText.length() && noteText.charAt(lineStart) == '•') {
            // Add newline and new bullet point
            noteText = noteText.substring(0, cursorPosition) + "\n• " + noteText.substring(cursorPosition);
            cursorPosition += 3; // Move cursor after the new bullet point
        } else {
            // Regular newline
            noteText = noteText.substring(0, cursorPosition) + "\n" + noteText.substring(cursorPosition);
            cursorPosition++;
        }
    }
    
    // Helper method to get the start position of the current line
    private static int getLineStart(int position) {
        int lineStart = 0;
        for (int i = position - 1; i >= 0; i--) {
            if (noteText.charAt(i) == '\n') {
                lineStart = i + 1;
                break;
            }
        }
        return lineStart;
    }
    
    // Check if control is pressed for resizing
    private static boolean isControlPressed() {
        return GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
               GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }
    
    // Handle window resizing with Ctrl + Arrow keys
    private static boolean handleResizeKeys(int key) {
        if (!isControlPressed()) return false;
        
        switch (key) {
            case GLFW.GLFW_KEY_UP:
                windowHeight = Math.max(MIN_HEIGHT, windowHeight - RESIZE_STEP);
                return true;
            case GLFW.GLFW_KEY_DOWN:
                windowHeight = Math.min(MAX_HEIGHT, windowHeight + RESIZE_STEP);
                return true;
            case GLFW.GLFW_KEY_LEFT:
                windowWidth = Math.min(MAX_WIDTH, windowWidth + RESIZE_STEP);
                return true;
            case GLFW.GLFW_KEY_RIGHT:
                windowWidth = Math.max(MIN_WIDTH, windowWidth - RESIZE_STEP);
                return true;
        }
        return false;
    }
    
    // Check if shift is pressed for character input
    private static boolean isShiftPressed() {
        return GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
               GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }
    
    // Check if mouse is over the notes window
    private static boolean isMouseOverWindow(double mouseX, double mouseY) {
        if (!isVisible) return false;
        
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        // Calculate window position
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
    
    // Handle key input with proper event cancellation
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS && event.getAction() != GLFW.GLFW_REPEAT) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        
        // Handle window resizing with Ctrl + Arrow keys (works even when not typing)
        if (isVisible && handleResizeKeys(event.getKey())) {
            event.setResult(Event.Result.DENY);
            return;
        }
        
        // Toggle drag mode with F8 key
        if (event.getKey() == GLFW.GLFW_KEY_F8) {
            isDragMode = !isDragMode;
            
            // If we're exiting drag mode while dragging, stop dragging
            if (!isDragMode && isDragging) {
                isDragging = false;
            }
            
            // If we're entering drag mode while typing, exit typing mode
            if (isDragMode && isTyping) {
                isTyping = false;
                enableKeyBindings();
                saveNotesToServer();
            }
            
            // Open or close the transparent drag screen
            if (isDragMode) {
                mc.setScreen(new DragModeScreen());
            } else {
                if (mc.screen instanceof DragModeScreen) {
                    mc.setScreen(null);
                }
            }
            
            event.setResult(Event.Result.DENY);
            return;
        }
        
        // Toggle window visibility with F9 key
        if (event.getKey() == GLFW.GLFW_KEY_F9) {
            isVisible = !isVisible;
            
            // If we're hiding the window while typing, exit typing mode
            if (!isVisible && isTyping) {
                isTyping = false;
                enableKeyBindings();
                saveNotesToServer();
            }
            
            // If we're hiding the window while in drag mode, exit drag mode
            if (!isVisible && isDragMode) {
                isDragMode = false;
                isDragging = false;
            }
            
            // Set the event result to deny further processing
            event.setResult(Event.Result.DENY);
            return;
        }
        
        // Toggle typing mode with F10 key
        if (event.getKey() == GLFW.GLFW_KEY_F10) {
            // Only allow typing if window is visible and not in drag mode
            if (!isVisible || isDragMode) {
                // Set the event result to deny further processing
                event.setResult(Event.Result.DENY);
                return;
            }
            
            isTyping = !isTyping;
            
            if (isTyping) {
                // Load notes when starting to type
                if (!networkInitialized) {
                    loadNotesLocally();
                }
                cursorPosition = noteText.length();
                // Close any open screen when starting to type
                if (mc.screen != null) {
                    mc.setScreen(null);
                }
                // Disable key bindings
                disableKeyBindings();
            } else {
                // Re-enable key bindings and save notes
                enableKeyBindings();
                saveNotesToServer();
            }
            
            // Set the event result to deny further processing
            event.setResult(Event.Result.DENY);
            return;
        }
        
        // Only process input when typing mode is active
        if (!isTyping) return;
        
        // Cancel the event to prevent game from processing it
        event.setResult(Event.Result.DENY);
        
        int key = event.getKey();
        
        // Handle special keys
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            isTyping = false;
            enableKeyBindings();
            saveNotesToServer();
            return;
        }
        
        if (key == GLFW.GLFW_KEY_BACKSPACE && cursorPosition > 0) {
            noteText = noteText.substring(0, cursorPosition - 1) + noteText.substring(cursorPosition);
            cursorPosition--;
            return;
        }
        
        if (key == GLFW.GLFW_KEY_DELETE && cursorPosition < noteText.length()) {
            noteText = noteText.substring(0, cursorPosition) + noteText.substring(cursorPosition + 1);
            return;
        }
        
        if (key == GLFW.GLFW_KEY_LEFT && cursorPosition > 0) {
            // Don't move cursor if Ctrl is pressed (for resizing)
            if (!isControlPressed()) {
                cursorPosition--;
            }
            return;
        }
        
        if (key == GLFW.GLFW_KEY_RIGHT && cursorPosition < noteText.length()) {
            // Don't move cursor if Ctrl is pressed (for resizing)
            if (!isControlPressed()) {
                cursorPosition++;
            }
            return;
        }
        
        if (key == GLFW.GLFW_KEY_HOME) {
            cursorPosition = 0;
            return;
        }
        
        if (key == GLFW.GLFW_KEY_END) {
            cursorPosition = noteText.length();
            return;
        }
        
        if (key == GLFW.GLFW_KEY_ENTER) {
            // Handle Enter key with bullet point formatting and AI queries
            handleEnterInBulletPoint();
            return;
        }
        
        // Handle character input
        char character = getCharFromKey(key, isShiftPressed());
        if (character != 0 && noteText.length() < MAX_TEXT_LENGTH) {
            noteText = noteText.substring(0, cursorPosition) + character + noteText.substring(cursorPosition);
            cursorPosition++;
            
            // Check for bullet point formatting (dash followed by space)
            if (character == ' ' && cursorPosition >= 2 && 
                noteText.charAt(cursorPosition - 2) == '-') {
                // Check if dash is at beginning of line or at start of text
                int dashPosition = cursorPosition - 2;
                if (dashPosition == 0 || noteText.charAt(dashPosition - 1) == '\n') {
                    formatBulletPoint();
                }
            }
        }
    }
    
    // Prevent chat screen from opening while typing
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (isTyping) {
            // Block all screens while typing (except death screen for safety)
            if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.DeathScreen)) {
                event.setCanceled(true);
            }
        }
    }
    
    // Client tick event for screen blocking, key reset, auto-save
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (isTyping && event.phase == TickEvent.Phase.START) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen)) {
                mc.setScreen(null);
            }
            // Continuously reset key states while typing
            resetKeyStates();
        }
        
        // Auto-save logic
        if (event.phase == TickEvent.Phase.END) {
            long currentTime = System.currentTimeMillis();
            if (isTyping && currentTime - lastAutoSave > AUTO_SAVE_INTERVAL) {
                saveNotesToServer();
                lastAutoSave = currentTime;
            }
        }
    }
    
    // Disable key bindings to prevent player movement
    private static void disableKeyBindings() {
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
    
    // Re-enable key bindings
    private static void enableKeyBindings() {
        if (originalKeyStates == null) return;
        
        Minecraft mc = Minecraft.getInstance();
        KeyMapping[] keyMappings = mc.options.keyMappings;
        
        // Restore original states
        for (int i = 0; i < keyMappings.length && i < originalKeyStates.length; i++) {
            keyMappings[i].setDown(originalKeyStates[i]);
        }
        
        originalKeyStates = null;
    }
    
    // Reset all key states to prevent movement and actions
    private static void resetKeyStates() {
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
    
    // Handle mouse input to prevent clicking while typing
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseInput(InputEvent.MouseButton event) {
        if (isTyping) {
            event.setResult(Event.Result.DENY);
            return;
        }
        
        // Don't handle mouse input here when in drag mode with screen open
        // Let the DragModeScreen handle it instead
        if (isDragMode) {
            return;
        }
    }
    
    // Handle mouse scrolling to prevent inventory scrolling while typing
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (isTyping) {
            event.setResult(Event.Result.DENY);
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
    
    private static void renderNotesBox(GuiGraphics guiGraphics, int x, int y) {
        // Use dynamic width and height
        int width = windowWidth;
        int height = windowHeight;
        
        // Render a low-opacity background overlay
        int backgroundColor = 0x10000000; // Very transparent background
        guiGraphics.fill(x, y, x + width, y + height, backgroundColor);
        
        // Add a border around the rectangle
        int borderColor;
        if (isDragMode) {
            borderColor = 0xFFFF0000; // Red when in drag mode
        } else if (isTyping) {
            borderColor = 0xFF00FF00; // Green when typing
        } else if (isWaitingForResponse) {
            borderColor = 0xFFFFFF00; // Yellow when waiting for AI response
        } else {
            borderColor = 0xFFFFFFFF; // White otherwise
        }
        guiGraphics.fill(x - 1, y - 1, x + width + 1, y, borderColor); // Top border
        guiGraphics.fill(x - 1, y + height, x + width + 1, y + height + 1, borderColor); // Bottom border
        guiGraphics.fill(x - 1, y, x, y + height, borderColor); // Left border
        guiGraphics.fill(x + width, y, x + width + 1, y + height, borderColor); // Right border
        
        // Render the text
        if (!noteText.isEmpty() || isTyping) {
            Minecraft mc = Minecraft.getInstance();
            
            // Split text into lines that fit in the box
            String[] lines = wrapText(noteText, width - 6, mc.font);
            
            // Render each line with different colors for different content types
            int maxLines = (height - 10) / 10; // Calculate max lines based on height
            for (int i = 0; i < lines.length && i < maxLines; i++) {
                String line = lines[i];
                int color = 0xFFFFFFFF; // Default white
                
                // Color AI queries differently
                if (line.trim().startsWith("/")) {
                    color = 0xFF00FFFF; // Cyan for AI queries
                } else if (line.trim().startsWith(">")) {
                    color = 0xFF90EE90; // Light green for AI query display
                } else if (line.trim().equals("Thinking...")) {
                    color = 0xFFFFFF00; // Yellow for thinking indicator
                }
                
                guiGraphics.drawString(mc.font, line, x + 3, y + 3 + (i * 10), color);
            }
            
            // Render cursor when typing
            if (isTyping && shouldShowCursor()) {
                int[] cursorPos = getCursorScreenPosition(x + 3, y + 3, width - 6, mc.font);
                guiGraphics.fill(cursorPos[0], cursorPos[1], cursorPos[0] + 1, cursorPos[1] + 9, 0xFFFFFFFF);
            }
        }
        
        // Show instruction text when not typing
        if (!isTyping && noteText.isEmpty()) {
            if (isDragMode) {
                guiGraphics.drawString(Minecraft.getInstance().font, "Drag mode: Click & drag", x + 3, y + 3, 0xFFFF0000);
                guiGraphics.drawString(Minecraft.getInstance().font, "F8 to exit", x + 3, y + 13, 0xFFFF0000);
            } else {
                guiGraphics.drawString(Minecraft.getInstance().font, "F10 to edit", x + 3, y + 3, 0xFFAAAAAA);
                guiGraphics.drawString(Minecraft.getInstance().font, "F8 to move", x + 3, y + 13, 0xFFAAAAAA);
                guiGraphics.drawString(Minecraft.getInstance().font, "F9 to hide", x + 3, y + 23, 0xFFAAAAAA);
                guiGraphics.drawString(Minecraft.getInstance().font, "Type /question for AI", x + 3, y + 33, 0xFF00FFFF);
            }
        }
    }
    
    private static String[] wrapText(String text, int maxWidth, net.minecraft.client.gui.Font font) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        
        // First, split by manual line breaks (Enter key presses)
        String[] manualLines = text.split("\n", -1); // -1 to preserve empty strings
        
        for (String manualLine : manualLines) {
            if (manualLine.isEmpty()) {
                lines.add(""); // Add empty line for manual line breaks
                continue;
            }
            
            String[] words = manualLine.split(" ");
            String currentLine = "";
            
            for (String word : words) {
                String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
                if (font.width(testLine) <= maxWidth) {
                    currentLine = testLine;
                } else {
                    if (!currentLine.isEmpty()) {
                        lines.add(currentLine);
                        currentLine = word;
                    } else {
                        // Word is too long, break it into characters
                        for (int i = 0; i < word.length(); i++) {
                            char c = word.charAt(i);
                            if (font.width(currentLine + c) <= maxWidth) {
                                currentLine += c;
                            } else {
                                if (!currentLine.isEmpty()) {
                                    lines.add(currentLine);
                                    currentLine = String.valueOf(c);
                                } else {
                                    // Single character is too wide, add it anyway
                                    lines.add(String.valueOf(c));
                                }
                            }
                        }
                    }
                }
            }
            
            if (!currentLine.isEmpty()) {
                lines.add(currentLine);
            }
        }
        
        return lines.toArray(new String[0]);
    }
    
    private static int[] getCursorScreenPosition(int startX, int startY, int maxWidth, net.minecraft.client.gui.Font font) {
        String textToCursor = noteText.substring(0, cursorPosition);
        String[] lines = wrapText(textToCursor, maxWidth, font);
        
        int lineIndex = Math.max(0, lines.length - 1);
        String currentLine = lines.length > 0 ? lines[lineIndex] : "";
        
        // Handle the case where cursor is at the end of a line that ends with \n
        if (textToCursor.endsWith("\n")) {
            lineIndex++;
            currentLine = "";
        }
        
        int x = startX + font.width(currentLine);
        int y = startY + (lineIndex * 10);
        
        return new int[]{x, y};
    }
    
    private static boolean shouldShowCursor() {
        return (System.currentTimeMillis() / 500) % 2 == 0; // Blink every 500ms
    }
    
    // Cleanup method to shut down executor when mod is unloaded
    public static void cleanup() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}