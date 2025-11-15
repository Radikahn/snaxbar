package snax;

import java.io.File;
import java.io.IOException;

public class AppRegister {

    private final String mainPath = System.getProperty("user.dir");

    private final String tomlName = "snaxAppRegistrations.toml";

    public String configPath;

    public AppRegister() {

        System.out.println(
                "===============================Running startup service for SnaxAppRegistration===============================");
        this.configPath = buildConfigPath(isUnix(this.mainPath));

    }

    public boolean isUnix(String filePath) {

        for (int i = 0; i < filePath.length(); i++) {

            if (filePath.charAt(i) == '\\') {
                return false;
            }

        }

        // final sanity check for windows machine -> check os detection from java os
        if (System.getProperty("os.name").startsWith("Windows")) {
            return false;
        }

        return true;

    }

    public String buildConfigPath(boolean unix) {

        String separator = unix ? "/" : "\\";

        String systemType = unix ? "Unix/BSD" : "Windows";

        System.out.println("Detected system as " + systemType + " machine");

        String dirPath = checkDir(new File(this.mainPath + separator + "config" + separator));

        File filePath = new File(dirPath + separator + tomlName);

        createFile(filePath);

        return filePath.toString();

    }

    public String checkDir(File directory) {

        if (!directory.exists()) {
            directory.mkdirs();
        }

        return directory.toString();

    }

    public void createFile(File file) {

        System.out.println("Searching for snaxAppRegistration config file");

        try {

            if (!file.exists()) {
                boolean created = file.createNewFile();

                if (created) {
                    System.out.println("Successfully initialized config file");
                }

                else if (!created) {
                    System.out.println("Failed to initialize or find the config file");
                }

            }

            else {
                System.out.println("Loading preexisting config file");
            }

        }

        catch (IOException e) {
            System.out.println("An error occurred when trying to create the config file" + e);
            e.printStackTrace();
        }

    }

}
