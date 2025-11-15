package snax;

import java.util.ArrayList;

public class AppManager {
    // TODO: add support for multi arg command

    private ArrayList<SnaxApp> registeredApps = new ArrayList<SnaxApp>();

    public AppManager() {

    }

    /**
     * Handle Special Command is the logic behind adding applications to the command
     * interface
     * the current implementation is a long if chain that checks keywords, but
     * ideally it will be a app list
     * that is checked for the name, then runs the given chain
     *
     * @param command -> this is the input text that the user has put after "/"
     * @return boolean -> states if the input command was in fact a recognized app
     */
    public static boolean handleSpecialCommand(String command) {
        System.out.println("DEBUG: Checking special command: '" + command + "'");

        if (command.equalsIgnoreCase("clear")) {
            System.out.println("DEBUG: Executing clear command");
            Notes.setNoteText("");
            Notes.setCursorPosition(0);
            NetworkManager.saveNotesToServer();
            return true;
        }

        System.out.println("DEBUG: Not a special command, returning false");
        return false; // Not a special command
    }

}
