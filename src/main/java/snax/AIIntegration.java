package snax;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import com.google.gson.Gson;

public class AIIntegration {
    
    // Configuration keys
    private static final String OLLAMA_URL_NBT_KEY = "snax_ollama_url";
    private static final String DEFAULT_MODEL_NBT_KEY = "snax_default_model";
    private static final String PLAYER_NAME_NBT_KEY = "snax_player_name";
    private static final String SERVER_INFO_NBT_KEY = "snax_server_info";
    
    // AI configuration
    private static String OLLAMA_BASE_URL = "http://127.0.0.1:11434";
    private static String DEFAULT_MODEL = "Sweaterdog/Andy-4:latest";
    private static String PLAYER_NAME = "";
    private static String SERVER_INFO = "Minecraft Server";
    
    // AI state
    private static boolean isWaitingForResponse = false;
    private static String currentQuery = "";
    
    // HTTP client
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Gson gson = new Gson();
    
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
    
    // Configuration getters and setters
    public static String getOllamaBaseUrl() {
        return OLLAMA_BASE_URL;
    }

    public static void setOllamaBaseUrl(String url) {
        OLLAMA_BASE_URL = url;
        saveConfiguration();
    }

    public static String getDefaultModel() {
        return DEFAULT_MODEL;
    }

    public static void setDefaultModel(String model) {
        DEFAULT_MODEL = model;
        saveConfiguration();
    }

    public static String getPlayerName() {
        if (PLAYER_NAME.isEmpty() && Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.getName().getString();
        }
        return PLAYER_NAME;
    }

    public static void setPlayerName(String name) {
        PLAYER_NAME = name;
        saveConfiguration();
    }

    public static String getServerInfo() {
        return SERVER_INFO;
    }

    public static void setServerInfo(String info) {
        SERVER_INFO = info;
        saveConfiguration();
    }
    
    public static boolean isWaitingForResponse() {
        return isWaitingForResponse;
    }
    
    // Configuration persistence
    private static void saveConfiguration() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            CompoundTag playerData = mc.player.getPersistentData();
            playerData.putString(OLLAMA_URL_NBT_KEY, OLLAMA_BASE_URL);
            playerData.putString(DEFAULT_MODEL_NBT_KEY, DEFAULT_MODEL);
            playerData.putString(PLAYER_NAME_NBT_KEY, PLAYER_NAME);
            playerData.putString(SERVER_INFO_NBT_KEY, SERVER_INFO);
        }
    }

    public static void loadConfiguration() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            CompoundTag playerData = mc.player.getPersistentData();
            if (playerData.contains(OLLAMA_URL_NBT_KEY)) {
                OLLAMA_BASE_URL = playerData.getString(OLLAMA_URL_NBT_KEY);
            }
            if (playerData.contains(DEFAULT_MODEL_NBT_KEY)) {
                DEFAULT_MODEL = playerData.getString(DEFAULT_MODEL_NBT_KEY);
            }
            if (playerData.contains(PLAYER_NAME_NBT_KEY)) {
                PLAYER_NAME = playerData.getString(PLAYER_NAME_NBT_KEY);
            }
            if (playerData.contains(SERVER_INFO_NBT_KEY)) {
                SERVER_INFO = playerData.getString(SERVER_INFO_NBT_KEY);
            }
        }
    }
    
    // Test Ollama connection
    public static void testOllamaConnection(String url, String model) {
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(url + "/api/tags"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                
                HttpResponse<String> response = httpClient.send(httpRequest, 
                        HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return "✓ Connected to Ollama successfully!";
                } else {
                    return "✗ Failed to connect (HTTP " + response.statusCode() + ")";
                }
            } catch (Exception e) {
                return "✗ Connection failed: " + e.getMessage();
            }
        }, executor).thenAccept(result -> {
            Minecraft.getInstance().execute(() -> {
                String testResult = "\n=== Connection Test ===\n" + result + "\n";
                Notes.insertText(testResult);
            });
        });
    }
    
    // Enhanced method to get player context for Andy-4 with control capabilities
    private static String getPlayerContext() {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder context = new StringBuilder();
        
        // Add player identity
        String playerName = getPlayerName();
        if (!playerName.isEmpty()) {
            context.append("You are Andy, an AI assistant who can control ").append(playerName).append(" in Minecraft. ");
        }
        
        // Add server/world context
        if (!SERVER_INFO.isEmpty()) {
            context.append("You are currently playing on: ").append(SERVER_INFO).append(". ");
        }
        
        // Add current game context if available
        if (mc.player != null) {
            Player player = mc.player;
            
            // Get current position
            BlockPos pos = player.blockPosition();
            context.append("You are at coordinates: ").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ()).append(". ");
            
            // Get current dimension
            String dimension = player.level().dimension().location().getPath();
            context.append("You are currently in the ").append(dimension).append(". ");
            
            // Get current biome if possible
            try {
                String biome = player.level().getBiome(pos).unwrapKey()
                        .map(key -> key.location().getPath()).orElse("unknown");
                context.append("The biome around you is ").append(biome.replace("_", " ")).append(". ");
            } catch (Exception e) {
                // Ignore biome detection errors
            }
            
            // Get time of day
            long timeOfDay = player.level().getDayTime() % 24000;
            String timeDescription;
            if (timeOfDay < 6000) {
                timeDescription = "morning";
            } else if (timeOfDay < 12000) {
                timeDescription = "day";
            } else if (timeOfDay < 18000) {
                timeDescription = "evening";
            } else {
                timeDescription = "night";
            }
            context.append("It's currently ").append(timeDescription).append(" in your world. ");
            
            // Add health/hunger status
            float health = player.getHealth();
            int hunger = player.getFoodData().getFoodLevel();
            if (health < 10) {
                context.append("You have low health (").append(String.format("%.1f", health)).append("/20). ");
            }
            if (hunger < 10) {
                context.append("You're getting hungry (").append(hunger).append("/20). ");
            }
            
            // Add current item in hand
            ItemStack heldItem = player.getMainHandItem();
            if (!heldItem.isEmpty()) {
                context.append("You're holding: ").append(heldItem.getDisplayName().getString()).append(". ");
            }
            
            // Add nearby blocks information
            context.append(getNearbyBlocksInfo(player));
        }
        
        // Add control capabilities
        if (PlayerController.isAIControlling()) {
            context.append("You are currently controlling the player. ");
        } else {
            context.append("You can take control of the player by using action commands. ");
        }
        
        context.append("You can perform actions like moving, mining, placing blocks, and interacting with the world. ");
        context.append("Use action commands in square brackets to control the player. ");
        context.append("Respond as Andy, the AI companion who can help by taking direct action in the game. ");
        context.append("Be helpful, friendly, and proactive. When asked to do something, actually do it using action commands.");
        
        return context.toString();
    }
    
    // Get information about nearby blocks
    private static String getNearbyBlocksInfo(Player player) {
        StringBuilder info = new StringBuilder();
        BlockPos playerPos = player.blockPosition();
        
        // Check block below
        BlockPos below = playerPos.below();
        BlockState blockBelow = player.level().getBlockState(below);
        if (!blockBelow.isAir()) {
            info.append("Standing on: ").append(blockBelow.getBlock().getName().getString()).append(". ");
        }
        
        // Check blocks around player
        int interestingBlocks = 0;
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (x == 0 && y == 0 && z == 0) continue; // Skip player position
                    
                    BlockPos checkPos = playerPos.offset(x, y, z);
                    BlockState state = player.level().getBlockState(checkPos);
                    
                    if (!state.isAir() && isInterestingBlock(state)) {
                        if (interestingBlocks < 3) { // Limit to avoid spam
                            info.append("Nearby: ").append(state.getBlock().getName().getString())
                                .append(" at ").append(checkPos.getX()).append(",").append(checkPos.getY()).append(",").append(checkPos.getZ()).append(". ");
                        }
                        interestingBlocks++;
                    }
                }
            }
        }
        
        if (interestingBlocks > 3) {
            info.append("And ").append(interestingBlocks - 3).append(" other nearby blocks. ");
        }
        
        return info.toString();
    }
    
    private static boolean isInterestingBlock(BlockState state) {
        String blockName = state.getBlock().getName().getString().toLowerCase();
        return blockName.contains("ore") || blockName.contains("log") || blockName.contains("diamond") ||
               blockName.contains("iron") || blockName.contains("gold") || blockName.contains("coal") ||
               blockName.contains("chest") || blockName.contains("furnace") || blockName.contains("crafting");
    }
    
    // Enhanced action guide with clearer examples
    private static String getEnhancedActionGuide() {
        return """
            === Andy's Action Commands (VERY IMPORTANT) ===
            
            WHEN THE PLAYER ASKS YOU TO DO SOMETHING, USE THESE COMMANDS:
            
            !collectBlocks("block_name", count) - Mine specific blocks (e.g., !collectBlocks("oak_log", 1))
            !moveTowards("direction", distance) - Move in a direction 
            !lookAt("target") - Look at something
            !placeBlock("block_name", x, y, z) - Place a block
            
            EXAMPLES OF CORRECT RESPONSES:
            Player: "Can you mine that oak log?"
            Andy: "I'll mine it for you! !collectBlocks("oak_log", 1) Done!"
            
            Player: "Mine some stone blocks"  
            Andy: "Mining stone now! !collectBlocks("stone", 5) All mined!"
            
            Player: "Get that diamond ore"
            Andy: "Getting the diamond ore! !collectBlocks("diamond_ore", 1) Got it!"
            
            REMEMBER: Use !commandName("parameter", number) format exactly as shown!
            DO NOT use square brackets [] - use exclamation marks and parentheses!
            """;
    }
    
    // Enhanced method to call Ollama API with Andy-4 context and action capabilities
    private static CompletableFuture<String> callOllamaAPI(String query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Build the enhanced prompt with player context and action guide
                String playerContext = getPlayerContext();
                String actionGuide = getEnhancedActionGuide();
                
                String fullPrompt = playerContext + "\n\n" + actionGuide + "\n\n" +
                        "Player's request: " + query + 
                        "\n\nCRITICAL INSTRUCTIONS:" +
                        "\n- You MUST use the EXACT format shown above with exclamation marks like !collectBlocks()" +
                        "\n- DO NOT use square brackets [] - use exclamation marks and parentheses ()" +
                        "\n- Use the format: !commandName(\"parameter\", number)" +
                        "\n- Example response: 'I'll mine that for you! !collectBlocks(\"oak_log\", 1) Done!'" +
                        "\n- For mining: !collectBlocks(\"block_type\", count)" +
                        "\n- When asked to mine something, identify the block type and use !collectBlocks" +
                        "\n\nRespond as Andy in under 100 words with correct action commands:";

                OllamaRequest request = new OllamaRequest(DEFAULT_MODEL, fullPrompt);
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
    
    // Enhanced AI query handler with better debugging
    public static void handleAIQuery(String query) {
        if (isWaitingForResponse) {
            return; // Prevent multiple simultaneous requests
        }
        
        currentQuery = query;
        isWaitingForResponse = true;
        
        // Add the query to notes with a loading indicator
        String queryLine = "\n> " + query + "\nAndy is thinking...";
        Notes.insertText(queryLine);
        
        // Make the API call
        callOllamaAPI(query).thenAccept(response -> {
            Minecraft.getInstance().execute(() -> {
                // DEBUG: Log the full response
                System.out.println("DEBUG: Full AI Response: " + response);
                
                // Remove the "thinking..." text and replace with actual response
                String noteText = Notes.getNoteText();
                String searchText = "Andy is thinking...";
                int thinkingIndex = noteText.lastIndexOf(searchText);
                if (thinkingIndex != -1) {
                    String beforeThinking = noteText.substring(0, thinkingIndex);
                    String afterThinking = noteText.substring(thinkingIndex + searchText.length());
                    
                    String processedResponse = Notes.processAIResponse(response);

                    String newText = beforeThinking + processedResponse + afterThinking;

                    if (newText.length() <= Notes.getMaxTextLength()) {
                        Notes.setNoteText(newText);
                        Notes.setCursorPosition(beforeThinking.length() + processedResponse.length());
                    } else {
                        // If response is too long, truncate it
                        int maxResponseLength = Notes.getMaxTextLength() - beforeThinking.length() - afterThinking.length();
                        if (maxResponseLength > 0) {
                            processedResponse = processedResponse.substring(0, Math.min(processedResponse.length(), maxResponseLength)) + "...";
                            Notes.setNoteText(beforeThinking + processedResponse + afterThinking);
                            Notes.setCursorPosition(beforeThinking.length() + processedResponse.length());
                        }
                    }
                    NetworkManager.saveNotesToServer();
                    
                }
                isWaitingForResponse = false;
            });
        }).exceptionally(throwable -> {
            Minecraft.getInstance().execute(() -> {
                String noteText = Notes.getNoteText();
                String searchText = "Andy is thinking...";
                int thinkingIndex = noteText.lastIndexOf(searchText);
                if (thinkingIndex != -1) {
                    String beforeThinking = noteText.substring(0, thinkingIndex);
                    String afterThinking = noteText.substring(thinkingIndex + searchText.length());
                    String errorResponse = "Error: Andy couldn't respond - " + throwable.getMessage();
                    Notes.setNoteText(beforeThinking + errorResponse + afterThinking);
                    Notes.setCursorPosition(beforeThinking.length() + errorResponse.length());
                }
                isWaitingForResponse = false;
            });
            return null;
        });
    }
    
    // Format AI response with proper line breaks
    private static String formatAIResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "No response received";
        }
        
        // Clean up action commands from display (they're executed separately)
        String cleanResponse = response.replaceAll("\\[\\w+[^\\]]*\\]", "").trim();
        
        // Split response into sentences for better formatting
        String[] sentences = cleanResponse.split("(?<=[.!?])\\s+");
        StringBuilder formatted = new StringBuilder();
        
        int lineLength = 0;
        int maxLineLength = (Notes.getWindowWidth() - 20) / 6; // Approximate characters per line
        
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
    
    // Enhanced special commands with Andy-4 features - WITH DEBUG
    public static boolean handleSpecialCommand(String command) {
        System.out.println("DEBUG: Checking special command: '" + command + "'");
        
        if (command.equalsIgnoreCase("clear")) {
            System.out.println("DEBUG: Executing clear command");
            Notes.setNoteText("");
            Notes.setCursorPosition(0);
            NetworkManager.saveNotesToServer();
            return true;
        }
        
        if (command.equalsIgnoreCase("context")) {
            System.out.println("DEBUG: Executing context command");
            String context = "\n=== Current Context ===\n" + getPlayerContext() + "\n";
            Notes.insertText(context);
            return true;
        }
        
        if (command.equalsIgnoreCase("actions")) {
            System.out.println("DEBUG: Executing actions command");
            String guide = "\n" + ActionParser.getActionGuide();
            Notes.insertText(guide);
            return true;
        }
        
        if (command.equalsIgnoreCase("stop")) {
            System.out.println("DEBUG: Executing stop command");
            if (PlayerController.isAIControlling()) {
                PlayerController.stopAIControl();
                String message = "\n🛑 Andy has been stopped and released control.\n";
                Notes.insertText(message);
            } else {
                String message = "\n❌ Andy is not currently controlling the player.\n";
                Notes.insertText(message);
            }
            return true;
        }
        
        if (command.startsWith("name ")) {
            System.out.println("DEBUG: Executing name command");
            String newName = command.substring(5).trim();
            if (!newName.isEmpty()) {
                setPlayerName(newName);
                String message = "\n✓ Player name updated to: " + newName + "\n";
                Notes.insertText(message);
            }
            return true;
        }
        
        if (command.startsWith("server ")) {
            System.out.println("DEBUG: Executing server command");
            String newServer = command.substring(7).trim();
            if (!newServer.isEmpty()) {
                setServerInfo(newServer);
                String message = "\n✓ Server context updated to: " + newServer + "\n";
                Notes.insertText(message);
            }
            return true;
        }
        
        if (command.equalsIgnoreCase("help")) {
            System.out.println("DEBUG: Executing help command");
            String helpText = "\n=== Available Commands ===\n" +
                             "/clear - Clear all notes\n" +
                             "/context - Show AI context\n" +
                             "/actions - Show action commands\n" +
                             "/stop - Stop Andy's control\n" +
                             "/test - Test action system\n" +
                             "/manual - Manual action test\n" +
                             "/name <n> - Set player name\n" +
                             "/server <info> - Set server context\n" +
                             "/help - Show this help\n" +
                             "Ask Andy to do anything and he'll control the player!\n";
            Notes.insertText(helpText);
            return true;
        }
        
        if (command.equalsIgnoreCase("test")) {
            System.out.println("DEBUG: Executing test command");
            String message = "\n=== Testing Action System ===\n";
            Notes.insertText(message);
            testBasicAction();
            return true;
        }
        
        if (command.equalsIgnoreCase("manual")) {
            System.out.println("DEBUG: Executing manual test command");
            String message = "\n=== Manual Action Test ===\n";
            Notes.insertText(message);
            manualTestActions();
            return true;
        }
        
        System.out.println("DEBUG: Not a special command, returning false");
        return false; // Not a special command
    }
    
    // Simple test method for debugging
    public static void testBasicAction() {
        System.out.println("Testing basic action execution...");
        String testResponse = "I'll help you! [CONTROL] [MOVE FORWARD 5] [STOP] Done!";
        
        ActionParser.executeActionsFromResponse(testResponse).thenAccept(result -> {
            System.out.println("Test result: " + result);
            Minecraft.getInstance().execute(() -> {
                String message = "\nTest completed! Check console for results.\n";
                Notes.insertText(message);
            });
        });
    }
    
    // Manual test of PlayerController
    public static void manualTestActions() {
        System.out.println("=== MANUAL ACTION TEST START ===");
        
        // Test 1: Check if PlayerController works
        System.out.println("Test 1: Starting AI control");
        PlayerController.startAIControl();
        System.out.println("AI controlling: " + PlayerController.isAIControlling());
        
        // Test 2: Try a simple movement
        System.out.println("Test 2: Testing movement");
        PlayerController.moveForward(10).thenAccept(result -> {
            System.out.println("Movement result: " + result);
            
            // Test 3: Stop control
            System.out.println("Test 3: Stopping control");
            PlayerController.stopAIControl();
            System.out.println("AI controlling after stop: " + PlayerController.isAIControlling());
            
            System.out.println("=== MANUAL ACTION TEST END ===");
            
            // Update notes with results
            Minecraft.getInstance().execute(() -> {
                String message = "\nManual test completed! Check console for detailed results.\n";
                Notes.insertText(message);
            });
        });
    }
    
    // Cleanup
    public static void cleanup() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        // Also cleanup player controller
        PlayerController.stopAIControl();
    }
}