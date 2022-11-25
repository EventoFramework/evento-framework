package org.eventrails.cli;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

import static org.eventrails.parser.java.JavaBundleParser.EVENTRAILS_BUNDLE_VERSION_PROPERTY;

public class UpdateVersion {
    public static void main(String[] args) throws Exception {
        String bundlePath = args[0];
        if(Files.walk(new File(bundlePath).toPath())
                .filter(p -> p.toString().endsWith(".properties"))
                .noneMatch(p -> {
                    try {
                        var prop = new Properties();
                        prop.load(new FileReader(p.toFile()));
                        if (prop.containsKey(EVENTRAILS_BUNDLE_VERSION_PROPERTY)) {
                            var old = Integer.parseInt(prop.getProperty(EVENTRAILS_BUNDLE_VERSION_PROPERTY, "-1"));
                            prop.setProperty(EVENTRAILS_BUNDLE_VERSION_PROPERTY, String.valueOf(old + 1));
                            prop.store(new FileWriter(p.toFile()), null);
                            System.out.println("Version property fond in " + p);
                            System.out.println("Update %d -> %d".formatted(old, old+1));
                            return true;
                        }
                        return false;
                    } catch (Exception e) {
                        return false;
                    }
                })){
            throw new Exception("Version property not found!");
        }


    }
}
