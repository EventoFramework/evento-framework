package com.evento.cli;

import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import com.evento.parser.java.JavaBundleParser;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.evento.common.serialization.ObjectMapperUtils.getPayloadObjectMapper;


/**
 * The PublishBundle class provides a static method to publish a bundle to a server.
 */
public class PublishBundle {

	/**
	 * Executes the bundle publishing process.
	 *
	 * @param bundlePath           The file path to the bundle directory.
	 * @param serverUrl            The URL of the server to upload the bundle to.
	 * @param repositoryUrl        The URL of the repository.
	 * @param repositoryLinePrefix The repository line prefix string.
	 * @param deployable           Indicates if the bundle is deployable.
	 * @param token                The authorization token for the server.
	 *
	 * @throws Exception if an error occurs during the publishing process.
	 */
	public static void run(String bundlePath, String serverUrl, String repositoryUrl, String repositoryLinePrefix,
						   boolean deployable,
						   String token) throws Exception {
		var jar = Arrays.stream(Objects.requireNonNull(new File(bundlePath + "/build/libs").listFiles()))
				.filter(f -> f.getAbsolutePath().endsWith(".jar"))
				.findFirst().orElseThrow();
		System.out.println("JAR detected: " + jar.getPath());

		System.out.println("Parsing bundle in: " + bundlePath);
		JavaBundleParser applicationParser = new JavaBundleParser();
		var bundleDescription = applicationParser.parseDirectory(
				new File(bundlePath), repositoryUrl, repositoryLinePrefix);
		bundleDescription.setDeployable(deployable);
		var jsonDescription = getPayloadObjectMapper().writeValueAsString(bundleDescription);
		System.out.println("JSON created");

		if(!new File(bundlePath + "/build/bundle-dist/").exists() && !new File(bundlePath + "/build/bundle-dist/").mkdir()){
			throw new IllegalStateException("Output directory creation failed");
		}

		var bundleFile = bundlePath + "/build/bundle-dist/" + bundleDescription.getBundleId() + ".bundle";
		final ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(
				bundleFile
		));
		// PUT THE JAR

		outputStream.putNextEntry(new ZipEntry("description.json"));
		byte[] bytes = jsonDescription.getBytes();
		outputStream.write(bytes, 0, bytes.length);
		outputStream.closeEntry();

		if(deployable) {
			outputStream.putNextEntry(new ZipEntry(jar.getName()));
			bytes = Files.readAllBytes(jar.toPath());
			outputStream.write(bytes, 0, bytes.length);
			outputStream.closeEntry();
		}

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
	/**
	 * The main method of the PublishBundle class.
	 *
	 * @param args an array of command-line arguments. The arguments are as follows:
	 *             - args[0]: the path to the bundle directory
	 *             - args[1]: the URL of the server to upload the bundle to
	 *             - args[2]: the URL of the repository
	 *             - args[3]: the repository line prefix
	 *             - args[4]: the bundle is deployable by evento server (true)
	 *             - args[4]: the authorization token
	 * @throws Exception if an error occurs during the publishing process
	 */
	public static void main(String[] args) throws Exception {
		run(args[0],  args[1], args[2], args[3], args[4].equals("true"), args[5]);
	}
}
