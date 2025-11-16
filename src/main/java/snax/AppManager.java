package snax;

import java.util.ArrayList;
import java.util.List;

public class AppManager {
    // TODO: add support for multi arg command

    private static AppRegister appRepo = new AppRegister();

    public static boolean checkReop(String command) {

        try {
            // implement error handling because there are cases in which toml might be
            // corrupted.
            List<String> repo = appRepo.appMaster.registrations;

            if (repo == null) {
                System.out.println("[SNAXAPP]: The app repo is null/empty");
                return false;
            }

            for (String installedApp : repo) {

                if (command.equalsIgnoreCase(installedApp)) {
                    System.out.println("DEBUG: Executing clear command");
                    Notes.setNoteText("");
                    Notes.setCursorPosition(0);
                    NetworkManager.saveNotesToServer();
                    return true;
                }

            }

            System.out.println("DEBUG: Not a special command, returning false");
            return false;
        }

        catch (Exception e) {
            System.out.println("Failed view repo due to registration issues");
            e.printStackTrace();
        }

        return false;
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

        return checkReop(command);
    }
}
