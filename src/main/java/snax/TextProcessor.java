package snax;

public class TextProcessor {
    
    // Check if the current line starts with "/"
    public static boolean isAIQueryLine() {
        String noteText = Notes.getNoteText();
        int cursorPosition = Notes.getCursorPosition();
        
        if (noteText.isEmpty()) return false;
        
        // Find the start of the current line
        int lineStart = getLineStart(cursorPosition);
        return lineStart < noteText.length() && noteText.charAt(lineStart) == '/';
    }
    
    // Extract AI query from the current line
    public static String extractAIQuery() {
        String noteText = Notes.getNoteText();
        int cursorPosition = Notes.getCursorPosition();
        
        int lineStart = getLineStart(cursorPosition);
        int lineEnd = getLineEnd(cursorPosition);
        
        if (lineStart < noteText.length() && noteText.charAt(lineStart) == '/') {
            String line = noteText.substring(lineStart + 1, lineEnd); // Skip the '/' character
            return line.trim();
        }
        
        return "";
    }
    
    // Remove the current AI query line
    public static void removeCurrentAIQueryLine() {
        String noteText = Notes.getNoteText();
        int cursorPosition = Notes.getCursorPosition();
        
        int lineStart = getLineStart(cursorPosition);
        int lineEnd = getLineEnd(cursorPosition);
        
        // Include the newline character if it exists
        if (lineEnd < noteText.length() && noteText.charAt(lineEnd) == '\n') {
            lineEnd++;
        }
        
        Notes.setNoteText(noteText.substring(0, lineStart) + noteText.substring(lineEnd));
        Notes.setCursorPosition(lineStart);
    }
    
    // Get the start position of the current line
    public static int getLineStart(int position) {
        String noteText = Notes.getNoteText();
        int lineStart = 0;
        for (int i = position - 1; i >= 0; i--) {
            if (noteText.charAt(i) == '\n') {
                lineStart = i + 1;
                break;
            }
        }
        return lineStart;
    }
    
    // Get the end position of the current line
    public static int getLineEnd(int position) {
        String noteText = Notes.getNoteText();
        int lineEnd = noteText.length();
        for (int i = position; i < noteText.length(); i++) {
            if (noteText.charAt(i) == '\n') {
                lineEnd = i;
                break;
            }
        }
        return lineEnd;
    }
    
    // Handle Enter key with bullet point formatting and AI queries - WITH DEBUG
    public static void handleEnterKey() {
        System.out.println("DEBUG: handleEnterKey called");
        
        // Check if this is an AI query line (starts with "/")
        if (isAIQueryLine()) {
            System.out.println("DEBUG: This is an AI query line");
            String query = extractAIQuery();
            System.out.println("DEBUG: Extracted query: '" + query + "'");
            
            if (!query.isEmpty() && !AIIntegration.isWaitingForResponse()) {
                
                // Check for special commands first
                if (AIIntegration.handleSpecialCommand(query)) {
                    System.out.println("DEBUG: Handled as special command");
                    // Remove the command line
                    removeCurrentAIQueryLine();
                    return;
                }
                
                System.out.println("DEBUG: Sending to AI: '" + query + "'");
                // If not a special command, treat as AI query
                removeCurrentAIQueryLine();
                AIIntegration.handleAIQuery(query);
                return;
            }
        } else {
            System.out.println("DEBUG: Not an AI query line");
        }
        
        String noteText = Notes.getNoteText();
        int cursorPosition = Notes.getCursorPosition();
        
        if (noteText.length() + 3 > Notes.getMaxTextLength()) return; // Not enough space for newline + bullet
        
        // Check if current line starts with a bullet point
        int lineStart = getLineStart(cursorPosition);
        if (lineStart < noteText.length() && noteText.charAt(lineStart) == '•') {
            // Add newline and new bullet point
            Notes.insertText("\n• ");
        } else {
            // Regular newline
            Notes.insertText("\n");
        }
    }
    
    // Check for bullet point formatting (dash followed by space)
    public static void checkBulletPointFormatting() {
        String noteText = Notes.getNoteText();
        int cursorPosition = Notes.getCursorPosition();
        
        if (cursorPosition >= 2 && noteText.charAt(cursorPosition - 2) == '-') {
            // Check if dash is at beginning of line or at start of text
            int dashPosition = cursorPosition - 2;
            if (dashPosition == 0 || noteText.charAt(dashPosition - 1) == '\n') {
                formatBulletPoint();
            }
        }
    }
    
    // Helper method to format bullet points
    private static void formatBulletPoint() {
        String noteText = Notes.getNoteText();
        int cursorPosition = Notes.getCursorPosition();
        
        if (noteText.length() + 1 > Notes.getMaxTextLength()) return; // Not enough space
        
        // Replace "- " with "• " (bullet character)
        String beforeCursor = noteText.substring(0, cursorPosition);
        String afterCursor = noteText.substring(cursorPosition);
        
        // Remove the "- " and add "• "
        if (beforeCursor.endsWith("- ")) {
            beforeCursor = beforeCursor.substring(0, beforeCursor.length() - 2) + "• ";
            Notes.setNoteText(beforeCursor + afterCursor);
            // Cursor position stays the same since we replaced 2 chars with 2 chars
        }
    }
}