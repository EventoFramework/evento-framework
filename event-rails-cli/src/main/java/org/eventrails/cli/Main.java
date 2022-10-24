package org.eventrails.cli;

import org.eventrails.modeling.utils.ObjectMapperUtils;
import org.eventrails.parser.java.JavaRanchApplicationParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.eventrails.modeling.utils.ObjectMapperUtils.getPayloadObjectMapper;
import static org.eventrails.modeling.utils.ObjectMapperUtils.getResultObjectMapper;

public class Main {
	public static void main(String[] args) throws IOException {
		String ranchName = args[0];
		String ranchPath = args[1];

		var jar = Arrays.stream(Objects.requireNonNull(new File(ranchPath + "/build/libs").listFiles()))
				.filter(f -> f.getAbsolutePath().endsWith(".jar"))
				.findFirst().orElseThrow();
		System.out.println("JAR detected: "+jar.getPath());

		System.out.println("Parsing ranch in: " + ranchPath);
		JavaRanchApplicationParser applicationParser = new JavaRanchApplicationParser();
		var components = applicationParser.parseDirectory(
				new File(ranchPath));
		var jsonDescription = getPayloadObjectMapper().writeValueAsString(components);
		System.out.println("JSON created");

		new File(ranchPath + "/build/ranch-dist/" ).mkdir();
		final ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(
				ranchPath + "/build/ranch-dist/" + ranchName + ".ranch"
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
