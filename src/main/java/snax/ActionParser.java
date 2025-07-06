package snax;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.List;

public class ActionParser {
    
    // Action patterns for parsing AI responses
    private static final Pattern MOVE_PATTERN = Pattern.compile("\\[MOVE\\s+(FORWARD|BACKWARD|LEFT|RIGHT)\\s+(\\d+)\\]");
    private static final Pattern LOOK_PATTERN = Pattern.compile("\\[LOOK\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)\\]");
    private static final Pattern LOOK_AT_PATTERN = Pattern.compile("\\[LOOK_AT\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\]");
    private static final Pattern JUMP_PATTERN = Pattern.compile("\\[JUMP\\]");
    private static final Pattern SNEAK_PATTERN = Pattern.compile("\\[SNEAK\\s+(\\d+)\\]");
    private static final Pattern ATTACK_PATTERN = Pattern.compile("\\[ATTACK\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\]");
    private static final Pattern MINE_PATTERN = Pattern.compile("\\[MINE\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\]");
    private static final Pattern PLACE_PATTERN = Pattern.compile("\\[PLACE\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\]");
    private static final Pattern USE_ITEM_PATTERN = Pattern.compile("\\[USE_ITEM\\]");
    private static final Pattern SELECT_SLOT_PATTERN = Pattern.compile("\\[SELECT_SLOT\\s+(\\d+)\\]");
    private static final Pattern DROP_ITEM_PATTERN = Pattern.compile("\\[DROP_ITEM\\]");
    private static final Pattern COLLECT_ITEMS_PATTERN = Pattern.compile("\\[COLLECT_ITEMS\\s+(\\d+(?:\\.\\d+)?)\\]");
    private static final Pattern WALK_TO_PATTERN = Pattern.compile("\\[WALK_TO\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)\\]");
    private static final Pattern FIND_ENTITY_PATTERN = Pattern.compile("\\[FIND\\s+(\\w+)\\s+(\\d+(?:\\.\\d+)?)\\]");
    private static final Pattern INTERACT_ENTITY_PATTERN = Pattern.compile("\\[INTERACT\\s+(\\w+)\\s+(\\d+(?:\\.\\d+)?)\\]");
    private static final Pattern STOP_PATTERN = Pattern.compile("\\[STOP\\]");
    
    // Check if response contains action commands
    public static boolean containsActionCommands(String response) {
        return response.contains("[MOVE") || response.contains("[LOOK") || 
               response.contains("[JUMP]") || response.contains("[MINE") ||
               response.contains("[PLACE") || response.contains("[ATTACK") ||
               response.contains("[WALK_TO") || response.contains("[COLLECT") ||
               response.contains("[CONTROL]") || response.contains("[USE_ITEM") ||
               response.contains("[SELECT_SLOT") || response.contains("[DROP_ITEM") ||
               response.contains("[FIND") || response.contains("[INTERACT") ||
               response.contains("[SNEAK");
    }
    
    // Execute all actions found in AI response
    public static CompletableFuture<String> executeActionsFromResponse(String aiResponse) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder result = new StringBuilder();
            boolean hasActions = false;
            
            System.out.println("DEBUG: Checking response for actions: " + aiResponse);
            
            // Check if AI wants to take control
            if (aiResponse.contains("[CONTROL]") || containsActionCommands(aiResponse)) {
                System.out.println("DEBUG: Found action commands, starting control");
                
                if (!PlayerController.isAIControlling()) {
                    PlayerController.startAIControl();
                    result.append("🎮 Andy is now controlling the player!\n");
                    hasActions = true;
                }
                
                // Parse and execute each action
                String actionResults = executeAllActions(aiResponse);
                result.append(actionResults);
                if (!actionResults.isEmpty()) {
                    hasActions = true;
                }
            }
            
            // Check if AI wants to stop control
            if (aiResponse.contains("[STOP]") || aiResponse.contains("[RELEASE]")) {
                System.out.println("DEBUG: Found stop command");
                if (PlayerController.isAIControlling()) {
                    PlayerController.stopAIControl();
                    result.append("🛑 Andy has released control.\n");
                    hasActions = true;
                }
            }
            
            String finalResult = hasActions ? result.toString() : "";
            System.out.println("DEBUG: Final action result: " + finalResult);
            return finalResult;
        });
    }
    
    private static String executeAllActions(String response) {
        StringBuilder result = new StringBuilder();
        
        try {
            System.out.println("DEBUG: Executing actions from: " + response);
            
            // Movement actions
            Matcher moveMatcher = MOVE_PATTERN.matcher(response);
            while (moveMatcher.find()) {
                String direction = moveMatcher.group(1);
                int ticks = Integer.parseInt(moveMatcher.group(2));
                System.out.println("DEBUG: Found movement: " + direction + " for " + ticks + " ticks");
                result.append(executeMovement(direction, ticks));
            }
            
            // Look actions
            Matcher lookMatcher = LOOK_PATTERN.matcher(response);
            while (lookMatcher.find()) {
                double yaw = Double.parseDouble(lookMatcher.group(1));
                double pitch = Double.parseDouble(lookMatcher.group(2));
                System.out.println("DEBUG: Found look command: yaw=" + yaw + ", pitch=" + pitch);
                PlayerController.lookAt(yaw, pitch).join();
                result.append("👁️ Looked to yaw: ").append(yaw).append(", pitch: ").append(pitch).append("\n");
            }
            
            // Look at block
            Matcher lookAtMatcher = LOOK_AT_PATTERN.matcher(response);
            while (lookAtMatcher.find()) {
                int x = Integer.parseInt(lookAtMatcher.group(1));
                int y = Integer.parseInt(lookAtMatcher.group(2));
                int z = Integer.parseInt(lookAtMatcher.group(3));
                System.out.println("DEBUG: Found look at block: " + x + "," + y + "," + z);
                PlayerController.lookAtBlock(new BlockPos(x, y, z)).join();
                result.append("👀 Looked at block: ").append(x).append(", ").append(y).append(", ").append(z).append("\n");
            }
            
            // Jump actions
            if (JUMP_PATTERN.matcher(response).find()) {
                System.out.println("DEBUG: Found jump command");
                PlayerController.jump().join();
                result.append("🦘 Jumped!\n");
            }
            
            // Sneak actions
            Matcher sneakMatcher = SNEAK_PATTERN.matcher(response);
            while (sneakMatcher.find()) {
                int ticks = Integer.parseInt(sneakMatcher.group(1));
                System.out.println("DEBUG: Found sneak command for " + ticks + " ticks");
                PlayerController.sneak(ticks).join();
                result.append("🤫 Sneaked for ").append(ticks).append(" ticks\n");
            }
            
            // Mining actions
            Matcher mineMatcher = MINE_PATTERN.matcher(response);
            while (mineMatcher.find()) {
                int x = Integer.parseInt(mineMatcher.group(1));
                int y = Integer.parseInt(mineMatcher.group(2));
                int z = Integer.parseInt(mineMatcher.group(3));
                System.out.println("DEBUG: Found mine command: " + x + "," + y + "," + z);
                BlockPos pos = new BlockPos(x, y, z);
                PlayerController.mineBlock(pos).join();
                result.append("⛏️ Mined block at: ").append(x).append(", ").append(y).append(", ").append(z).append("\n");
            }
            
            // Place actions
            Matcher placeMatcher = PLACE_PATTERN.matcher(response);
            while (placeMatcher.find()) {
                int x = Integer.parseInt(placeMatcher.group(1));
                int y = Integer.parseInt(placeMatcher.group(2));
                int z = Integer.parseInt(placeMatcher.group(3));
                System.out.println("DEBUG: Found place command: " + x + "," + y + "," + z);
                BlockPos pos = new BlockPos(x, y, z);
                PlayerController.placeBlock(pos).join();
                result.append("🧱 Placed block at: ").append(x).append(", ").append(y).append(", ").append(z).append("\n");
            }
            
            // Attack actions
            Matcher attackMatcher = ATTACK_PATTERN.matcher(response);
            while (attackMatcher.find()) {
                int x = Integer.parseInt(attackMatcher.group(1));
                int y = Integer.parseInt(attackMatcher.group(2));
                int z = Integer.parseInt(attackMatcher.group(3));
                System.out.println("DEBUG: Found attack command: " + x + "," + y + "," + z);
                BlockPos pos = new BlockPos(x, y, z);
                PlayerController.attackBlock(pos).join();
                result.append("⚔️ Attacked block at: ").append(x).append(", ").append(y).append(", ").append(z).append("\n");
            }
            
            // Use item actions
            if (USE_ITEM_PATTERN.matcher(response).find()) {
                System.out.println("DEBUG: Found use item command");
                PlayerController.useItem().join();
                result.append("🔧 Used item in hand\n");
            }
            
            // Select hotbar slot
            Matcher selectMatcher = SELECT_SLOT_PATTERN.matcher(response);
            while (selectMatcher.find()) {
                int slot = Integer.parseInt(selectMatcher.group(1));
                System.out.println("DEBUG: Found select slot command: " + slot);
                PlayerController.selectHotbarSlot(slot).join();
                result.append("📦 Selected hotbar slot: ").append(slot).append("\n");
            }
            
            // Drop item
            if (DROP_ITEM_PATTERN.matcher(response).find()) {
                System.out.println("DEBUG: Found drop item command");
                PlayerController.dropItem().join();
                result.append("📤 Dropped item\n");
            }
            
            // Collect items
            Matcher collectMatcher = COLLECT_ITEMS_PATTERN.matcher(response);
            while (collectMatcher.find()) {
                double radius = Double.parseDouble(collectMatcher.group(1));
                System.out.println("DEBUG: Found collect items command: radius=" + radius);
                PlayerController.collectNearbyItems(radius).join();
                result.append("🧲 Collected items within radius: ").append(radius).append("\n");
            }
            
            // Walk to position
            Matcher walkMatcher = WALK_TO_PATTERN.matcher(response);
            while (walkMatcher.find()) {
                double x = Double.parseDouble(walkMatcher.group(1));
                double y = Double.parseDouble(walkMatcher.group(2));
                double z = Double.parseDouble(walkMatcher.group(3));
                System.out.println("DEBUG: Found walk to command: " + x + "," + y + "," + z);
                Vec3 targetPos = new Vec3(x, y, z);
                PlayerController.walkToPosition(targetPos, 1.0).join();
                result.append("🚶 Walked to position: ").append(x).append(", ").append(y).append(", ").append(z).append("\n");
            }
            
            // Find entity
            Matcher findMatcher = FIND_ENTITY_PATTERN.matcher(response);
            while (findMatcher.find()) {
                String entityType = findMatcher.group(1);
                double radius = Double.parseDouble(findMatcher.group(2));
                System.out.println("DEBUG: Found find entity command: " + entityType + " radius=" + radius);
                Entity entity = findEntityByName(entityType, radius);
                if (entity != null) {
                    result.append("🔍 Found ").append(entityType).append(" at: ")
                          .append(entity.getX()).append(", ").append(entity.getY()).append(", ").append(entity.getZ()).append("\n");
                } else {
                    result.append("❌ No ").append(entityType).append(" found within radius ").append(radius).append("\n");
                }
            }
            
            // Interact with entity
            Matcher interactMatcher = INTERACT_ENTITY_PATTERN.matcher(response);
            while (interactMatcher.find()) {
                String entityType = interactMatcher.group(1);
                double radius = Double.parseDouble(interactMatcher.group(2));
                System.out.println("DEBUG: Found interact entity command: " + entityType + " radius=" + radius);
                Entity entity = findEntityByName(entityType, radius);
                if (entity != null) {
                    PlayerController.interactWithEntity(entity).join();
                    result.append("🤝 Interacted with ").append(entityType).append("\n");
                } else {
                    result.append("❌ No ").append(entityType).append(" found to interact with\n");
                }
            }
            
        } catch (Exception e) {
            System.out.println("DEBUG: Error executing actions: " + e.getMessage());
            e.printStackTrace();
            result.append("❌ Error executing action: ").append(e.getMessage()).append("\n");
        }
        
        System.out.println("DEBUG: executeAllActions result: " + result.toString());
        return result.toString();
    }
    
    private static String executeMovement(String direction, int ticks) {
        try {
            System.out.println("DEBUG: Executing movement: " + direction + " for " + ticks + " ticks");
            switch (direction.toUpperCase()) {
                case "FORWARD":
                    PlayerController.moveForward(ticks).join();
                    break;
                case "BACKWARD":
                    PlayerController.moveBackward(ticks).join();
                    break;
                case "LEFT":
                    PlayerController.strafeLeft(ticks).join();
                    break;
                case "RIGHT":
                    PlayerController.strafeRight(ticks).join();
                    break;
            }
            return "🏃 Moved " + direction.toLowerCase() + " for " + ticks + " ticks\n";
        } catch (Exception e) {
            System.out.println("DEBUG: Movement error: " + e.getMessage());
            return "❌ Failed to move " + direction.toLowerCase() + ": " + e.getMessage() + "\n";
        }
    }
    
    private static Entity findEntityByName(String entityType, double radius) {
        try {
            switch (entityType.toLowerCase()) {
                case "animal":
                case "animals":
                    return PlayerController.findNearestEntity(Animal.class, radius).join();
                case "monster":
                case "mob":
                case "hostile":
                    return PlayerController.findNearestEntity(Monster.class, radius).join();
                case "item":
                case "items":
                    return PlayerController.findNearestEntity(ItemEntity.class, radius).join();
                default:
                    // Try to find specific entity types
                    return PlayerController.findNearestEntity(Entity.class, radius).join();
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Error finding entity: " + e.getMessage());
            return null;
        }
    }
    
    // Generate action suggestions for AI
    public static String getActionGuide() {
        return """
            === Andy's Action Commands ===
            
            Movement:
            [MOVE FORWARD 20] - Move forward for 20 ticks
            [MOVE BACKWARD 10] - Move backward for 10 ticks  
            [MOVE LEFT 15] - Strafe left for 15 ticks
            [MOVE RIGHT 15] - Strafe right for 15 ticks
            [JUMP] - Jump once
            [SNEAK 10] - Sneak for 10 ticks
            
            Looking:
            [LOOK 90 0] - Look to yaw 90, pitch 0
            [LOOK_AT 100 64 200] - Look at block position
            
            Block Interaction:
            [MINE 100 64 200] - Mine block at coordinates
            [PLACE 100 65 200] - Place block at coordinates
            [ATTACK 100 64 200] - Attack/break block
            
            Items:
            [USE_ITEM] - Use item in hand
            [SELECT_SLOT 2] - Select hotbar slot (0-8)
            [DROP_ITEM] - Drop one item
            
            Advanced:
            [WALK_TO 100.5 64.0 200.5] - Walk to exact position
            [COLLECT_ITEMS 5.0] - Collect items within 5 block radius
            [FIND animal 10] - Find nearest animal within 10 blocks
            [INTERACT animal 5] - Interact with nearest animal within 5 blocks
            
            Control:
            [CONTROL] - Start taking control of player
            [STOP] - Stop controlling and release control
            
            Example: "I'll collect those items for you! [CONTROL] [COLLECT_ITEMS 10] [STOP]"
            """;
    }
}