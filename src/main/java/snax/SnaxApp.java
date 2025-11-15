package snax;

import java.util.UUID;

public class SnaxApp {

    private String name;
    private String description;
    private String id;

    public SnaxApp(String name) {

        this.name = name;

        this.id = UUID.randomUUID().toString();

    }

    /**
     * process holds the logic for what your registered application can do
     * - Please refer to the docs for the Notes package that allows for CLI
     * manipulation
     *
     *
     */
    public void process() {

        System.out.println("DEBUG: Executing help command");

        String processText = "This is the default app process!";

        Notes.insertText(processText);

    }

}
