package org.eventrails.cli;

import org.eventrails.parser.java.JavaBundleParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.eventrails.modeling.utils.ObjectMapperUtils.getPayloadObjectMapper;

public class Main {
	public static void main(String[] args) throws IOException {
		String bundleName = args[0];
		String bundlePath = args[1];

		var jar = Arrays.stream(Objects.requireNonNull(new File(bundlePath + "/build/libs").listFiles()))
				.filter(f -> f.getAbsolutePath().endsWith(".jar"))
				.findFirst().orElseThrow();
		System.out.println("JAR detected: "+jar.getPath());

		System.out.println("Parsing bundle in: " + bundlePath);
		JavaBundleParser applicationParser = new JavaBundleParser();
		var components = applicationParser.parseDirectory(
				new File(bundlePath));
		var jsonDescription = getPayloadObjectMapper().writeValueAsString(components);
		System.out.println("JSON created");

		new File(bundlePath + "/build/bundle-dist/" ).mkdir();
		final ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(
				bundlePath + "/build/bundle-dist/" + bundleName + ".bundle"
		));
		// PUT THE JAR
		outputStream.putNextEntry(new ZipEntry(jar.getName()));
		byte[] bytes = Files.readAllBytes(jar.toPath());
		outputStream.write(bytes, 0, bytes.length);
		outputStream.closeEntry();

		outputStream.putNextEntry(new ZipEntry("description.json"));
		bytes = jsonDescription.getBytes();
		outputStream.write(bytes, 0, bytes.length);
		outputStream.closeEntry();

		outputStream.close();






	}
}
