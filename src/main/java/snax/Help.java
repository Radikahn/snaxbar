package snax;

public class Help extends SnaxApp {

    private String helpText;

    public Help() {

        super("Help");

        this.helpText = "\n=== Available Commands ===\n" +
                "/clear - Clear all notes\n" +
                "/help - Show this help\n";
    }

    @Override
    public void process() {

        System.out.println("DEBUG: Executing help command");

        Notes.insertText(this.helpText);

    }

}
