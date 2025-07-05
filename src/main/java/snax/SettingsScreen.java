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
    private Button saveButton;
    private Button cancelButton;
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
        
        // Create text fields
        this.ollamaUrlField = new EditBox(this.font, centerX - 100, centerY - 40, 200, 20, 
            Component.literal("Ollama Base URL"));
        this.ollamaUrlField.setValue(Notes.getOllamaBaseUrl());
        this.ollamaUrlField.setMaxLength(256);
        this.ollamaUrlField.setBordered(true);
        this.ollamaUrlField.setVisible(true);
        this.ollamaUrlField.setEditable(true);
        this.addRenderableWidget(this.ollamaUrlField);
        
        this.modelField = new EditBox(this.font, centerX - 100, centerY, 200, 20, 
            Component.literal("Default Model"));
        this.modelField.setValue(Notes.getDefaultModel());
        this.modelField.setMaxLength(64);
        this.modelField.setBordered(true);
        this.modelField.setVisible(true);
        this.modelField.setEditable(true);
        this.addRenderableWidget(this.modelField);
        
        // Create buttons
        this.saveButton = Button.builder(Component.literal("Save"), (button) -> {
            // Save the settings
            Notes.setOllamaBaseUrl(this.ollamaUrlField.getValue());
            Notes.setDefaultModel(this.modelField.getValue());
            this.minecraft.setScreen(this.parentScreen);
        }).bounds(centerX - 100, centerY + 40, 95, 20).build();
        
        this.cancelButton = Button.builder(Component.literal("Cancel"), (button) -> {
            this.minecraft.setScreen(this.parentScreen);
        }).bounds(centerX + 5, centerY + 40, 95, 20).build();
        
        this.addRenderableWidget(this.saveButton);
        this.addRenderableWidget(this.cancelButton);
        
        // Set initial focus
        this.setInitialFocus(this.ollamaUrlField);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        
        // Draw title
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        
        // Draw field labels
        guiGraphics.drawString(this.font, "Ollama Base URL:", this.width / 2 - 100, this.height / 2 - 55, 0xFFFFFF);
        guiGraphics.drawString(this.font, "Default Model:", this.width / 2 - 100, this.height / 2 - 15, 0xFFFFFF);
        
        // Draw help text
        guiGraphics.drawCenteredString(this.font, "Configure your Ollama server settings", 
            this.width / 2, this.height / 2 + 70, 0xAAAAAA);
        guiGraphics.drawCenteredString(this.font, "Example URL: http://localhost:11434", 
            this.width / 2, this.height / 2 + 85, 0x888888);
        guiGraphics.drawCenteredString(this.font, "Example Model: llama3:latest", 
            this.width / 2, this.height / 2 + 100, 0x888888);
        
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