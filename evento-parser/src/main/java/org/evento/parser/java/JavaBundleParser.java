package org.evento.parser.java;

import com.google.gson.JsonObject;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersionHandler;
import net.sourceforge.pmd.lang.Parser;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.*;
import org.evento.common.modeling.messaging.payload.*;
import org.evento.parser.model.BundleDescription;
import org.evento.parser.model.handler.Handler;
import org.evento.parser.model.payload.PayloadDescription;
import org.evento.parser.BundleParser;
import org.evento.parser.model.component.Component;
import org.evento.parser.model.component.Invoker;

import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavaBundleParser implements BundleParser {

	public static final String EVENTO_BUNDLE_VERSION_PROPERTY = "evento.bundle.version";
	public static final String EVENTO_BUNDLE_NAME_PROPERTY = "evento.bundle.id";
	public static final String EVENTO_BUNDLE_AUTORUN_PROPERTY = "evento.bundle.autorun";
	public static final String EVENTO_BUNDLE_INSTANCES_MIN_PROPERTY = "evento.bundle.instances.min";
	public static final String EVENTO_BUNDLE_INSTANCES_MAX_PROPERTY = "evento.bundle.instances.max";

	public BundleDescription parseDirectory(File file) throws Exception {
		LanguageVersionHandler java = LanguageRegistry.getLanguage("Java").getDefaultVersion().getLanguageVersionHandler();
		Parser parser = java.getParser(java.getDefaultParserOptions());
		if (!file.isDirectory()) throw new RuntimeException("error.not.dir");
		var components = Files.walk(file.toPath())
				.filter(p -> p.toString().endsWith(".java"))
				.filter(p -> !p.toString().toLowerCase().contains("test"))
				.map(p -> {
					try
					{
						var node = parser.parse(p.getFileName().toString(), new FileReader(p.toFile()));
						System.out.println(p.toAbsolutePath());

						return toComponent(node);
					} catch (Exception e)
					{
						e.printStackTrace();
						return null;
					}
				}).filter(Objects::nonNull).toList();

		var payloads = Stream.concat(
				Files.walk(file.toPath())
						.filter(p -> p.toString().endsWith(".java"))
						.filter(p -> !p.toString().toLowerCase().contains("test"))
						.map(p -> {
							try
							{
								var node = parser.parse(p.getFileName().toString(), new FileReader(p.toFile()));
								System.out.println(p.toAbsolutePath());

								return toPayload(node);
							} catch (Exception e)
							{
								e.printStackTrace();
								return null;
							}
						}).filter(Objects::nonNull),
				components.stream()
						.filter(c -> c instanceof Invoker)
						.map(c -> ((Invoker) c))
						.flatMap(i -> i.getInvocationHandlers().stream())
						.map(Handler::getPayload).distinct()
						.map(p -> new PayloadDescription(p.getName(), "Invocation", "{}"))
		).collect(Collectors.toList());

		var bundleVersion = Files.walk(file.toPath())
				.filter(p -> p.toString().endsWith(".properties"))
				.mapToInt(p -> {
					try
					{
						var prop = new Properties();
						prop.load(new FileReader(p.toFile()));
						return Integer.parseInt(prop.getProperty(EVENTO_BUNDLE_VERSION_PROPERTY, "-1"));
					} catch (Exception e)
					{
						return -1;
					}
				}).filter(v -> v >= 0).findFirst().orElseThrow(() -> new Exception("Cannot find %s in a .property file".formatted(EVENTO_BUNDLE_VERSION_PROPERTY)));

		var bundleId = Files.walk(file.toPath())
				.filter(p -> p.toString().endsWith(".properties"))
				.map(p -> {
					try
					{
						var prop = new Properties();
						prop.load(new FileReader(p.toFile()));
						return prop.getProperty(EVENTO_BUNDLE_NAME_PROPERTY, null);
					} catch (Exception e)
					{
						return null;
					}
				}).filter(Objects::nonNull).findFirst().orElseThrow(() -> new Exception("Cannot find %s in a .property file".formatted(EVENTO_BUNDLE_NAME_PROPERTY)));

		var autorun = Files.walk(file.toPath())
				.filter(p -> p.toString().endsWith(".properties"))
				.map(p -> {
					try
					{
						var prop = new Properties();
						prop.load(new FileReader(p.toFile()));
						return Boolean.parseBoolean(prop.getProperty(EVENTO_BUNDLE_AUTORUN_PROPERTY, "false"));
					} catch (Exception e)
					{
						return null;
					}
				}).filter(Objects::nonNull).findFirst().orElseThrow(() -> new Exception("Cannot find %s in a .property file".formatted(EVENTO_BUNDLE_NAME_PROPERTY)));

		var minInstances = Files.walk(file.toPath())
				.filter(p -> p.toString().endsWith(".properties"))
				.map(p -> {
					try
					{
						var prop = new Properties();
						prop.load(new FileReader(p.toFile()));
						var i = Integer.parseInt(prop.getProperty(EVENTO_BUNDLE_INSTANCES_MIN_PROPERTY, "0"));
						if(i == 0 && autorun) return 1;
						else return i;
					} catch (Exception e)
					{
						return null;
					}
				}).filter(Objects::nonNull).findFirst().orElseThrow(() -> new Exception("Cannot find %s in a .property file".formatted(EVENTO_BUNDLE_NAME_PROPERTY)));

		var maxInstances = Files.walk(file.toPath())
				.filter(p -> p.toString().endsWith(".properties"))
				.map(p -> {
					try
					{
						var prop = new Properties();
						prop.load(new FileReader(p.toFile()));
						return Integer.parseInt(prop.getProperty(EVENTO_BUNDLE_INSTANCES_MAX_PROPERTY, "64"));
					} catch (Exception e)
					{
						return null;
					}
				}).filter(Objects::nonNull).findFirst().orElseThrow(() -> new Exception("Cannot find %s in a .property file".formatted(EVENTO_BUNDLE_NAME_PROPERTY)));


		return new BundleDescription(
				bundleId,
				bundleVersion,
				autorun,
				minInstances,
				maxInstances,
				components,
				payloads);
	}

	private Component toComponent(Node node) throws Exception {

		var classDef = node.getFirstDescendantOfType(ASTTypeDeclaration.class);
		if (JavaComponentParser.isSaga(classDef))
		{
			return new JavaSagaParser(node).parse();
		} else if (JavaComponentParser.isAggregate(classDef))
		{
			return new JavaAggregateParser(node).parse();
		} else if (JavaComponentParser.isProjection(classDef))
		{
			return new JavaProjectionParser(node).parse();
		} else if (JavaComponentParser.isProjector(classDef))
		{
			return new JavaProjectorParser(node).parse();
		}else if (JavaComponentParser.isObserver(classDef))
		{
			return new JavaObserverParser(node).parse();
		} else if (JavaComponentParser.isService(classDef))
		{
			return new JavaServiceParser(node).parse();
		} else if (JavaComponentParser.isInvoker(classDef))
		{
			return new JavaInvokerParser(node).parse();
		}
		return null;
	}

	private PayloadDescription toPayload(net.sourceforge.pmd.lang.ast.Node node) throws Exception {


		try
		{
			var classDef = node.getFirstDescendantOfType(ASTClassOrInterfaceDeclaration.class);
			var payloadType = classDef.getParent().getFirstDescendantOfType(ASTExtendsList.class).getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
			if (payloadType.equals("DomainCommand") || payloadType.equals("DomainEvent") ||
					payloadType.equals("ServiceCommand") || payloadType.equals("ServiceEvent") ||
					payloadType.equals("Query") || payloadType.equals("View"))
			{
				JsonObject schema = new JsonObject();
				var fields = node.findDescendantsOfType(ASTFieldDeclaration.class);
				for (var field : fields)
				{
					var name = field.getFirstDescendantOfType(ASTVariableDeclaratorId.class).getName();
					var type = field.getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
					schema.addProperty(name, type);
				}
				if (payloadType.equals("DomainCommand"))
				{
					addSuperFields(schema, DomainCommand.class);
				} else if (payloadType.equals("DomainEvent"))
				{
					addSuperFields(schema, DomainEvent.class);
				} else if (payloadType.equals("ServiceCommand"))
				{
					addSuperFields(schema, ServiceCommand.class);
				} else if (payloadType.equals("ServiceEvent"))
				{
					addSuperFields(schema, ServiceEvent.class);
				} else if (payloadType.equals("Query"))
				{
					addSuperFields(schema, Query.class);
				} else
				{
					addSuperFields(schema, View.class);
				}
				return new PayloadDescription(classDef.getSimpleName(), payloadType, schema.toString());
			}
			return null;

		} catch (Exception e)
		{
			return null;
		}
	}

	private void addSuperFields(JsonObject schema, Class<?> clazz) {
		for (Field declaredField : clazz.getDeclaredFields())
		{
			schema.addProperty(declaredField.getName(), declaredField.getType().getSimpleName());
		}
	}
}
