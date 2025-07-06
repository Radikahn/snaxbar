package snax;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import java.util.function.Supplier;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NetworkManager {
    
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
    
    // Network state
    private static boolean networkInitialized = false;
    
    // Server-side storage for notes
    private static final Map<UUID, String> serverNotes = new HashMap<>();
    
    // Initialize network packets
    public static void initNetwork() {
        if (!networkInitialized) {
            int packetId = 0;
            INSTANCE.registerMessage(packetId++, NotesUpdatePacket.class, NotesUpdatePacket::encode, NotesUpdatePacket::decode, NotesUpdatePacket::handle);
            INSTANCE.registerMessage(packetId++, NotesRequestPacket.class, NotesRequestPacket::encode, NotesRequestPacket::decode, NotesRequestPacket::handle);
            networkInitialized = true;
        }
    }
    
    public static boolean isNetworkInitialized() {
        return networkInitialized;
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
            
            // Load configuration on client side
            if (event.getEntity().level().isClientSide) {
                AIIntegration.loadConfiguration();
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
                Notes.setNoteText(packet.notesText);
                Notes.setCursorPosition(Math.min(Notes.getCursorPosition(), packet.notesText.length()));
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
    public static void saveNotesToServer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null && networkInitialized) {
            try {
                INSTANCE.sendToServer(new NotesRequestPacket(Notes.getNoteText()));
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
    public static void saveNotesLocally() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            CompoundTag playerData = mc.player.getPersistentData();
            playerData.putString(NOTES_NBT_KEY, Notes.getNoteText());
        }
    }
    
    // Method to load notes locally
    public static void loadNotesLocally() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            CompoundTag playerData = mc.player.getPersistentData();
            String savedNotes = playerData.getString(NOTES_NBT_KEY);
            if (savedNotes != null && !savedNotes.isEmpty()) {
                Notes.setNoteText(savedNotes);
                Notes.setCursorPosition(Math.min(Notes.getCursorPosition(), savedNotes.length()));
            }
        }
    }
}