package org.evento.cli;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import static org.evento.parser.java.JavaBundleParser.EVENTO_BUNDLE_VERSION_PROPERTY;

public class UpdateVersion {
	public static void main(String[] args) throws Exception {
		run(args[0]);
	}

	public static void run(String bundlePath) throws Exception{
		if (Files.walk(new File(bundlePath).toPath())
				.filter(p -> p.toString().endsWith(".properties"))
				.noneMatch(p -> {
					try
					{
						var prop = new Properties() {
							@Override
							public synchronized Set<Map.Entry<Object, Object>> entrySet() {
								return Collections.synchronizedSet(
										super.entrySet()
												.stream()
												.sorted(Comparator.comparing(e -> e.getKey().toString()))
												.collect(Collectors.toCollection(LinkedHashSet::new)));
							}
						};
						prop.load(new FileReader(p.toFile()));
						if (prop.containsKey(EVENTO_BUNDLE_VERSION_PROPERTY))
						{
							var old = Integer.parseInt(prop.getProperty(EVENTO_BUNDLE_VERSION_PROPERTY, "-1"));
							prop.setProperty(EVENTO_BUNDLE_VERSION_PROPERTY, String.valueOf(old + 1));
							prop.store(new FileWriter(p.toFile()), null);
							System.out.println("Version property fond in " + p);
							System.out.printf("Update %d -> %d%n\n", old, old + 1);
							return true;
						}
						return false;
					} catch (Exception e)
					{
						return false;
					}
				}))
		{
			throw new Exception("Version property not found!");
		}


	}
}
