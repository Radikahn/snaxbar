package snax;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerController {
    
    private static final AtomicBoolean isControlling = new AtomicBoolean(false);
    private static final AtomicBoolean stopExecution = new AtomicBoolean(false);
    private static CompletableFuture<Void> currentTask = null;
    
    // Control state
    public static boolean isAIControlling() {
        return isControlling.get();
    }
    
    public static void stopAIControl() {
        stopExecution.set(true);
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        isControlling.set(false);
        releaseAllControls();
    }
    
    public static void startAIControl() {
        isControlling.set(true);
        stopExecution.set(false);
    }
    
    // Basic movement actions
    public static CompletableFuture<Boolean> moveForward(int ticks) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return false;
            
            for (int i = 0; i < ticks && !stopExecution.get(); i++) {
                mc.options.keyUp.setDown(true);
                try {
                    Thread.sleep(50); // 50ms per tick
                } catch (InterruptedException e) {
                    break;
                }
            }
            mc.options.keyUp.setDown(false);
            return true;
        });
    }
    
    public static CompletableFuture<Boolean> moveBackward(int ticks) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return false;
            
            for (int i = 0; i < ticks && !stopExecution.get(); i++) {
                mc.options.keyDown.setDown(true);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                }
            }
            mc.options.keyDown.setDown(false);
            return true;
        });
    }
    
    public static CompletableFuture<Boolean> strafeLeft(int ticks) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return false;
            
            for (int i = 0; i < ticks && !stopExecution.get(); i++) {
                mc.options.keyLeft.setDown(true);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                }
            }
            mc.options.keyLeft.setDown(false);
            return true;
        });
    }
    
    public static CompletableFuture<Boolean> strafeRight(int ticks) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return false;
            
            for (int i = 0; i < ticks && !stopExecution.get(); i++) {
                mc.options.keyRight.setDown(true);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                }
            }
            mc.options.keyRight.setDown(false);
            return true;
        });
    }
    
    public static CompletableFuture<Boolean> jump() {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return false;
            
            mc.options.keyJump.setDown(true);
            try {
                Thread.sleep(100); // Short jump
            } catch (InterruptedException e) {
                // Ignore
            }
            mc.options.keyJump.setDown(false);
            return true;
        });
    }
    
    public static CompletableFuture<Boolean> sneak(int ticks) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return false;
            
            for (int i = 0; i < ticks && !stopExecution.get(); i++) {
                mc.options.keyShift.setDown(true);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                }
            }
            mc.options.keyShift.setDown(false);
            return true;
        });
    }
    
    // Looking/rotation actions
    public static CompletableFuture<Boolean> lookAt(double yaw, double pitch) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return false;
            
            player.setYRot((float) yaw);
            player.setXRot((float) pitch);
            return true;
        });
    }
    
    public static CompletableFuture<Boolean> lookAtBlock(BlockPos pos) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return false;
            
            Vec3 playerPos = player.getEyePosition();
            Vec3 blockCenter = Vec3.atCenterOf(pos);
            Vec3 direction = blockCenter.subtract(playerPos).normalize();
            
            double yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
            double pitch = Math.toDegrees(-Math.asin(direction.y));
            
            player.setYRot((float) yaw);
            player.setXRot((float) pitch);
            return true;
        });
    }
    
    public static CompletableFuture<Boolean> lookAtEntity(Entity entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || entity == null) return false;
            
            Vec3 playerPos = player.getEyePosition();
            Vec3 entityPos = entity.getEyePosition();
            Vec3 direction = entityPos.subtract(playerPos).normalize();
            
            double yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
            double pitch = Math.toDegrees(-Math.asin(direction.y));
            
            player.setYRot((float) yaw);
            player.setXRot((float) pitch);
            return true;
        });
    }
    
    // Interaction actions
    public static CompletableFuture<Boolean> attackBlock(BlockPos pos) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.gameMode == null) return false;
            
            // Look at the block first
            lookAtBlock(pos).join();
            
            // Start attacking
            Direction direction = getDirectionToBlock(pos);
            BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(pos), direction, pos, false);
            
            mc.gameMode.startDestroyBlock(pos, direction);
            
            // Continue attacking until block is broken or we're stopped
            while (!stopExecution.get() && mc.level.getBlockState(pos).getBlock() != net.minecraft.world.level.block.Blocks.AIR) {
                mc.gameMode.continueDestroyBlock(pos, direction);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                }
            }
            
            mc.gameMode.stopDestroyBlock();
            return true;
        });
    }
    
    public static CompletableFuture<Boolean> rightClickBlock(BlockPos pos) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.gameMode == null) return false;
            
            // Look at the block first
            lookAtBlock(pos).join();
            
            Direction direction = getDirectionToBlock(pos);
            BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(pos), direction, pos, false);
            
            InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
            return result != InteractionResult.FAIL;
        });
    }
    
    public static CompletableFuture<Boolean> attackEntity(Entity entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.gameMode == null || entity == null) return false;
            
            // Look at the entity first
            lookAtEntity(entity).join();
            
            mc.gameMode.attack(mc.player, entity);
            return true;
        });
    }
    
    public static CompletableFuture<Boolean> interactWithEntity(Entity entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.gameMode == null || entity == null) return false;
            
            // Look at the entity first
            lookAtEntity(entity).join();
            
            // Create an EntityHitResult for the interaction
            Vec3 hitPos = entity.getEyePosition();
            EntityHitResult hitResult = new EntityHitResult(entity, hitPos);
            
            InteractionResult result = mc.gameMode.interactAt(mc.player, entity, hitResult, InteractionHand.MAIN_HAND);
            return result != InteractionResult.FAIL;
        });
    }
    
    // Item/inventory actions
    public static CompletableFuture<Boolean> selectHotbarSlot(int slot) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || slot < 0 || slot > 8) return false;
            
            mc.player.getInventory().selected = slot;
            return true;
        });
    }
    
    public static CompletableFuture<Boolean> useItem() {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.gameMode == null) return false;
            
            InteractionResult result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            return result != InteractionResult.FAIL;
        });
    }
    
    public static CompletableFuture<Boolean> dropItem() {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return false;
            
            mc.player.drop(false); // Drop one item
            return true;
        });
    }
    
    public static CompletableFuture<Boolean> dropStack() {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return false;
            
            mc.player.drop(true); // Drop entire stack
            return true;
        });
    }
    
    // Utility methods
    private static Direction getDirectionToBlock(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return Direction.NORTH;
        
        Vec3 playerPos = mc.player.getEyePosition();
        Vec3 blockCenter = Vec3.atCenterOf(pos);
        Vec3 direction = blockCenter.subtract(playerPos);
        
        // Find the closest face
        if (Math.abs(direction.x) > Math.abs(direction.y) && Math.abs(direction.x) > Math.abs(direction.z)) {
            return direction.x > 0 ? Direction.EAST : Direction.WEST;
        } else if (Math.abs(direction.y) > Math.abs(direction.z)) {
            return direction.y > 0 ? Direction.UP : Direction.DOWN;
        } else {
            return direction.z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }
    
    private static void releaseAllControls() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(false);
            mc.options.keyLeft.setDown(false);
            mc.options.keyRight.setDown(false);
            mc.options.keyJump.setDown(false);
            mc.options.keyShift.setDown(false);
        }
    }
    
    // High-level action sequences
    public static CompletableFuture<Boolean> walkToPosition(Vec3 targetPos, double tolerance) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return false;
            
            while (!stopExecution.get()) {
                Vec3 currentPos = player.position();
                double distance = currentPos.distanceTo(targetPos);
                
                if (distance <= tolerance) {
                    break; // Reached destination
                }
                
                // Calculate direction to target
                Vec3 direction = targetPos.subtract(currentPos).normalize();
                double yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
                
                // Look towards target
                player.setYRot((float) yaw);
                
                // Move forward
                mc.options.keyUp.setDown(true);
                
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
            
            mc.options.keyUp.setDown(false);
            return true;
        });
    }
    
    public static CompletableFuture<Boolean> collectNearbyItems(double radius) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return false;
            
            Vec3 playerPos = player.position();
            AABB searchArea = new AABB(playerPos.subtract(radius, radius, radius), 
                                      playerPos.add(radius, radius, radius));
            
            List<ItemEntity> items = mc.level.getEntitiesOfClass(ItemEntity.class, searchArea);
            
            for (ItemEntity item : items) {
                if (stopExecution.get()) break;
                
                // Walk to item
                Vec3 itemPos = item.position();
                walkToPosition(itemPos, 1.0).join();
                
                // Wait a moment for pickup
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
            
            return true;
        });
    }
    
    // Find and interact with nearest entity of type
    public static CompletableFuture<Entity> findNearestEntity(Class<? extends Entity> entityType, double radius) {
        return CompletableFuture.supplyAsync(() -> {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null) return null;
            
            Vec3 playerPos = player.position();
            AABB searchArea = new AABB(playerPos.subtract(radius, radius, radius), 
                                      playerPos.add(radius, radius, radius));
            
            List<? extends Entity> entities = mc.level.getEntitiesOfClass(entityType, searchArea);
            
            Entity nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            
            for (Entity entity : entities) {
                double distance = playerPos.distanceTo(entity.position());
                if (distance < nearestDistance) {
                    nearest = entity;
                    nearestDistance = distance;
                }
            }
            
            return nearest;
        });
    }
    
    // Complex actions
    public static CompletableFuture<Boolean> mineBlock(BlockPos pos) {
        CompletableFuture<Boolean> miningTask = CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            // First, look at the block
            lookAtBlock(pos).join();
            
            // Then attack it until it breaks
            attackBlock(pos).join();
            
            return true;
        });
        
        // Store as Void task for cancellation purposes
        currentTask = miningTask.thenAccept(result -> {
            // This converts to CompletableFuture<Void>
        });
        
        return miningTask;
    }
    
    public static CompletableFuture<Boolean> placeBlock(BlockPos pos) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isControlling.get()) return false;
            
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || mc.gameMode == null) return false;
            
            // Find a suitable adjacent position to place from
            BlockPos placeFrom = findAdjacentAirBlock(pos);
            if (placeFrom == null) return false;
            
            // Move to position (simplified - in reality you'd need pathfinding)
            walkToPosition(Vec3.atCenterOf(placeFrom), 1.5).join();
            
            // Look at the target position
            lookAtBlock(pos).join();
            
            // Right click to place
            rightClickBlock(pos).join();
            
            return true;
        });
    }
    
    private static BlockPos findAdjacentAirBlock(BlockPos target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        
        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, 
                                Direction.WEST, Direction.UP, Direction.DOWN};
        
        for (Direction dir : directions) {
            BlockPos adjacent = target.relative(dir);
            if (mc.level.getBlockState(adjacent).isAir()) {
                return adjacent;
            }
        }
        
        return null;
    }
}