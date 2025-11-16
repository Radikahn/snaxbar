package snax;

import com.moandjiezana.toml.Toml;
import java.io.File;
import java.util.List;

public class RegistrationParser {

    public List<String> registrations;

    public RegistrationParser(File toml) {
        Toml config = new Toml().read(toml);

        this.registrations = config.getList("registrations");

        if (this.registrations == null) {
            System.out.println("[SNAX APP]: Registration list is empty // toml file does exist though.");
        }

        else {

            System.out.println("Preview of registrations for confirmation " + this.registrations);
        }
    }

}
