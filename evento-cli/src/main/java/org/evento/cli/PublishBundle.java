package org.evento.cli;

import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.evento.parser.java.JavaBundleParser;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.evento.common.serialization.ObjectMapperUtils.getPayloadObjectMapper;


public class PublishBundle {

	public static void run(String bundlePath, String serverUrl, String repositoryUrl,
						   String token) throws Exception {
		var jar = Arrays.stream(Objects.requireNonNull(new File(bundlePath + "/build/libs").listFiles()))
				.filter(f -> f.getAbsolutePath().endsWith(".jar"))
				.findFirst().orElseThrow();
		System.out.println("JAR detected: " + jar.getPath());

		System.out.println("Parsing bundle in: " + bundlePath);
		JavaBundleParser applicationParser = new JavaBundleParser();
		var bundleDescription = applicationParser.parseDirectory(
				new File(bundlePath), repositoryUrl);
		var jsonDescription = getPayloadObjectMapper().writeValueAsString(bundleDescription);
		System.out.println("JSON created");

		if(!new File(bundlePath + "/build/bundle-dist/").mkdir()){
			throw new IllegalStateException("Output directory creation failed");
		}

		var bundleFile = bundlePath + "/build/bundle-dist/" + bundleDescription.getBundleId() + ".bundle";
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
		OkHttpClient client = new OkHttpClient.Builder()
				.connectTimeout(60, TimeUnit.SECONDS)
				.readTimeout(60, TimeUnit.SECONDS)
				.writeTimeout(60 * 5, TimeUnit.SECONDS)
				.build();
		Request request = new Request.Builder()
				.url(serverUrl + "/api/bundle/")
				.post(new MultipartBody.Builder().setType(MultipartBody.FORM)
						.addFormDataPart("bundle", bundleDescription.getBundleId(), RequestBody.create(new File(bundleFile), null))
						.build())
				.header("Authorization", "Bearer " + token)
				.build();
		var resp = client.newCall(request).execute();
		if (resp.code() == 200)
		{
			System.out.println("DONE!");
		} else
		{
			System.err.println("Server Upload Failed");
			System.err.println(Objects.requireNonNull(resp.body()).string());
		}
	}
	public static void main(String[] args) throws Exception {
		run(args[0],  args[1], args[2], args[3]);
	}
}
