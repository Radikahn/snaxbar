package snax;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class SettingsScreen extends Screen {
    private EditBox ollamaUrlField;
    private EditBox modelField;
    private EditBox playerNameField;
    private EditBox serverInfoField;
    private Button saveButton;
    private Button cancelButton;
    private Button testConnectionButton;
    private Button useAndyPresetButton;
    private final Screen parentScreen;
    
    public SettingsScreen(Screen parentScreen) {
        super(Component.literal("Snax AI Settings"));
        this.parentScreen = parentScreen;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        // Ollama URL field
        this.ollamaUrlField = new EditBox(this.font, centerX - 150, centerY - 80, 300, 20, 
            Component.literal("Ollama Base URL"));
        this.ollamaUrlField.setValue(AIIntegration.getOllamaBaseUrl());
        this.ollamaUrlField.setMaxLength(256);
        this.ollamaUrlField.setBordered(true);
        this.ollamaUrlField.setVisible(true);
        this.ollamaUrlField.setEditable(true);
        this.addRenderableWidget(this.ollamaUrlField);
        
        // Model field
        this.modelField = new EditBox(this.font, centerX - 150, centerY - 50, 300, 20, 
            Component.literal("AI Model"));
        this.modelField.setValue(AIIntegration.getDefaultModel());
        this.modelField.setMaxLength(64);
        this.modelField.setBordered(true);
        this.modelField.setVisible(true);
        this.modelField.setEditable(true);
        this.addRenderableWidget(this.modelField);
        
        // Player name field
        this.playerNameField = new EditBox(this.font, centerX - 150, centerY - 20, 300, 20,
            Component.literal("Player Name for AI"));
        this.playerNameField.setValue(AIIntegration.getPlayerName());
        this.playerNameField.setMaxLength(32);
        this.playerNameField.setBordered(true);
        this.playerNameField.setVisible(true);
        this.playerNameField.setEditable(true);
        this.addRenderableWidget(this.playerNameField);
        
        // Server info field
        this.serverInfoField = new EditBox(this.font, centerX - 150, centerY + 10, 300, 20,
            Component.literal("Server/World Context"));
        this.serverInfoField.setValue(AIIntegration.getServerInfo());
        this.serverInfoField.setMaxLength(128);
        this.serverInfoField.setBordered(true);
        this.serverInfoField.setVisible(true);
        this.serverInfoField.setEditable(true);
        this.addRenderableWidget(this.serverInfoField);
        
        // Use Andy-4 Preset Button
        this.useAndyPresetButton = Button.builder(Component.literal("Use Andy-4 Preset"), (button) -> {
            this.modelField.setValue("Sweaterdog/Andy-4:latest");
            this.playerNameField.setValue(Minecraft.getInstance().getUser().getName());
            this.serverInfoField.setValue("Minecraft Survival Server");
        }).bounds(centerX - 150, centerY + 40, 145, 20).build();
        
        // Test Connection Button
        this.testConnectionButton = Button.builder(Component.literal("Test Connection"), (button) -> {
            testOllamaConnection();
        }).bounds(centerX + 5, centerY + 40, 145, 20).build();
        
        // Save button
        this.saveButton = Button.builder(Component.literal("Save"), (button) -> {
            // Save all settings
            AIIntegration.setOllamaBaseUrl(this.ollamaUrlField.getValue());
            AIIntegration.setDefaultModel(this.modelField.getValue());
            AIIntegration.setPlayerName(this.playerNameField.getValue());
            AIIntegration.setServerInfo(this.serverInfoField.getValue());
            this.minecraft.setScreen(this.parentScreen);
        }).bounds(centerX - 75, centerY + 70, 70, 20).build();
        
        // Cancel button
        this.cancelButton = Button.builder(Component.literal("Cancel"), (button) -> {
            this.minecraft.setScreen(this.parentScreen);
        }).bounds(centerX + 5, centerY + 70, 70, 20).build();
        
        this.addRenderableWidget(this.useAndyPresetButton);
        this.addRenderableWidget(this.testConnectionButton);
        this.addRenderableWidget(this.saveButton);
        this.addRenderableWidget(this.cancelButton);
        
        // Set initial focus
        this.setInitialFocus(this.ollamaUrlField);
    }
    
    private void testOllamaConnection() {
        // Test the connection to Ollama
        AIIntegration.testOllamaConnection(this.ollamaUrlField.getValue(), this.modelField.getValue());
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        
        // Draw title
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        
        // Draw field labels
        guiGraphics.drawString(this.font, "Ollama Base URL:", this.width / 2 - 150, this.height / 2 - 95, 0xFFFFFF);
        guiGraphics.drawString(this.font, "AI Model:", this.width / 2 - 150, this.height / 2 - 65, 0xFFFFFF);
        guiGraphics.drawString(this.font, "Your Name (for AI context):", this.width / 2 - 150, this.height / 2 - 35, 0xFFFFFF);
        guiGraphics.drawString(this.font, "Server/World Context:", this.width / 2 - 150, this.height / 2 - 5, 0xFFFFFF);
        
        // Draw help text
        guiGraphics.drawCenteredString(this.font, "Configure Andy-4 AI to roleplay as you in Minecraft", 
            this.width / 2, this.height / 2 + 105, 0xAAAAAA);
        guiGraphics.drawCenteredString(this.font, "Andy will respond as if they are the player in your world", 
            this.width / 2, this.height / 2 + 115, 0x888888);
        guiGraphics.drawCenteredString(this.font, "Example: 'Can you mine that iron ore?' or 'Help me build a house'", 
            this.width / 2, this.height / 2 + 125, 0x888888);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.setScreen(this.parentScreen);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parentScreen);
    }
}