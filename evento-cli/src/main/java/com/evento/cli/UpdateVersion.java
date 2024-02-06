package com.evento.cli;

import com.evento.common.utils.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;

import static com.evento.parser.java.JavaBundleParser.EVENTO_BUNDLE_VERSION_PROPERTY;

/**
 * The UpdateVersion class is responsible for updating the version property in a bundle.
 */
public class UpdateVersion {
	/**
	 * The main method is the entry point of the program. It runs the `run` method with the provided command line argument.
	 *
	 * @param args The command line arguments.
	 * @throws Exception if an error occurs during the execution of the `run` method.
	 */
	public static void main(String[] args) throws Exception {
		run(args[0]);
	}

	/**
	 * The run method updates the version property in a bundle file.
	 *
	 * @param bundlePath The path to the bundle file.
	 * @throws Exception if the version property is not found or an error occurs during the update process.
	 */
	public static void run(String bundlePath) throws Exception{
		if (FileUtils.autoCloseWalk(new File(bundlePath).toPath(), a -> a
				.filter(p -> p.toString().endsWith(".properties"))
				.noneMatch(p -> {
					try
					{
						var prop = new Properties() {
							@NotNull
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
				})))
		{
			throw new Exception("Version property not found!");
		}


	}
}
