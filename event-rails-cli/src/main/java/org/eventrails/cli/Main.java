package org.eventrails.cli;

import okhttp3.*;
import org.eventrails.parser.java.JavaBundleParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.eventrails.common.serialization.ObjectMapperUtils.getPayloadObjectMapper;


public class Main {
	public static void main(String[] args) throws Exception {
		String bundlePath = args[0];
		String serverUrl = args[1];

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
		var bundleFile = bundlePath + "/build/bundle-dist/" + components.getBundleId() + ".bundle";
		final ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(
				bundleFile
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

		System.out.println("Uploading to server");
		OkHttpClient client = new OkHttpClient();
		Request request = new Request.Builder()
				.url(serverUrl + "/api/bundle/")
				.post(new MultipartBody.Builder().setType(MultipartBody.FORM)
						.addFormDataPart("bundle", components.getBundleId(), RequestBody.create(new File(bundleFile), null))
						.build())
				.build();
		var resp = client.newCall(request).execute();
		if(resp.code() == 200)
		{
			System.out.println("DONE!");
		}else{
			System.err.println("Server Upload Failed");
			System.err.println(resp.body().string());
		}



	}
}
