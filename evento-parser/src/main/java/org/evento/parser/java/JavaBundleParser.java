package org.evento.parser.java;

import com.google.gson.JsonObject;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersionHandler;
import net.sourceforge.pmd.lang.Parser;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.evento.common.modeling.messaging.payload.*;
import org.evento.common.utils.FileUtils;
import org.evento.parser.BundleParser;
import org.evento.parser.model.BundleDescription;
import org.evento.parser.model.component.Component;
import org.evento.parser.model.component.Invoker;
import org.evento.parser.model.payload.PayloadDescription;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The JavaBundleParser class is responsible for parsing a directory containing Java source files and extracting information about the bundle.
 */
public class JavaBundleParser implements BundleParser {

    private final static Logger logger = LogManager.getLogger(JavaBundleParser.class);

    /**
     * Represents the property name for the version of the bundle.
     * <p>
     * This constant is used to retrieve the version of a bundle from the bundle properties.
     * It is typically used as a key in a key-value map.
     * <p>
     * Example usage:
     * <pre>{@code
     * String version = bundleProperties.get(EVENTO_BUNDLE_VERSION_PROPERTY);
     * }</pre>
     */
    public static final String EVENTO_BUNDLE_VERSION_PROPERTY = "evento.bundle.version";
    /**
     * A constant variable representing the property name for the evento bundle.
     * The value of this property can be retrieved using the bundle ID.
     * <p>
     * Example usage:
     * String bundleName = System.getProperty(EVENTO_BUNDLE_NAME_PROPERTY);
     */
    public static final String EVENTO_BUNDLE_NAME_PROPERTY = "evento.bundle.id";
    /**
     * The EVENTO_BUNDLE_AUTORUN_PROPERTY variable represents the key used to retrieve the autorun status of a bundle from a configuration.
     * It is a constant string value.
     */
    public static final String EVENTO_BUNDLE_AUTORUN_PROPERTY = "evento.bundle.autorun";
    /**
     * This constant represents the property name for the minimum number of instances allowed for a bundle.
     * The value of this property is "evento.bundle.instances.min".
     * It is used in the {@link JavaBundleParser} class to define the minimum instances value for a bundle when parsing a directory.
     */
    public static final String EVENTO_BUNDLE_INSTANCES_MIN_PROPERTY = "evento.bundle.instances.min";
    /**
     * Represents the maximum number of instances allowed for a bundle.
     * This property defines the upper limit on the number of instances that can be created for a bundle.
     * The value of this property must be an integer.
     */
    public static final String EVENTO_BUNDLE_INSTANCES_MAX_PROPERTY = "evento.bundle.instances.max";

    public BundleDescription parseDirectory(File directory, String repositoryRoot) throws Exception {
        LanguageVersionHandler java = LanguageRegistry.getLanguage("Java").getDefaultVersion().getLanguageVersionHandler();
        Parser parser = java.getParser(java.getDefaultParserOptions());
        if (!directory.isDirectory()) throw new RuntimeException("error.not.dir");
        logger.info("Looking for components...");
        var components = FileUtils.autoCloseWalk(directory.toPath(), s ->
                        s.filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !p.toString().toLowerCase().contains("test"))
                        .filter(p -> !p.toString().toLowerCase().contains("package-info"))
                        .map(p -> {
                            try {
                                var node = parser.parse(p.getFileName().toString(), new FileReader(p.toFile()));

                                var c = toComponent(node);
                                if (c != null)
                                    c.setPath(p.toAbsolutePath().toString().replace(directory.getAbsolutePath(), repositoryRoot)
                                            .replace("\\","/"));
                                if(c!=null){
                                    logger.info("Component found in: " + p.toAbsolutePath() + " ("+c.getLine()+")");
                                }
                                return c;

                            } catch (Exception e) {
                                logger.error("Parsing error", e);
                                return null;
                            }
                        }).filter(Objects::nonNull).toList()
                        );

        logger.info("Total components detected: " + components.size() );
        logger.info("Looking for payloads...");
        var payloads = Stream.concat(
                FileUtils.autoCloseWalk(directory.toPath(), s-> s
                        .filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !p.toString().toLowerCase().contains("package-info"))
                        .filter(p -> !p.toString().toLowerCase().contains("test"))
                        .map(p -> {
                            try {
                                var node = parser.parse(p.getFileName().toString(), new FileReader(p.toFile()));
								var pl = toPayload(node);
								if (pl != null) {
									pl.setPath(p.toAbsolutePath().toString().replace(directory.getAbsolutePath(), repositoryRoot)
											.replace("\\", "/"));
								}
                                if(pl!=null){
                                    logger.info("Payload found in: " + p.toAbsolutePath() + " ("+pl.getLine()+")");
                                }
                                return pl;
                            } catch (Exception e) {
                                logger.error("Error parsing: " + p.toAbsolutePath(), e);
                                return null;
                            }
                        }).filter(Objects::nonNull)),
                components.stream()
                        .filter(c -> c instanceof Invoker)
                        .map(c -> ((Invoker) c))
						.flatMap(in -> in.getInvocationHandlers().stream().distinct().map(
								p -> {
									var pl = new PayloadDescription(p.getPayload().getName(), p.getPayload().getDomain(), "Invocation", "{}", p.getLine());
									pl.setPath(in.getPath());
                                    logger.info("Invocation found in: " + in.getPath() + " ("+p.getLine()+")");
									return pl;
								}
						))
        ).collect(Collectors.toList());
        logger.info("Total payloads detected: " + payloads.size() );

        var bundleVersion = FileUtils.autoCloseWalk(directory.toPath(), s -> s
                .filter(p -> p.toString().endsWith(".properties"))
                .mapToInt(p -> {
                    try {
                        var prop = new Properties();
                        prop.load(new FileReader(p.toFile()));
                        return Integer.parseInt(prop.getProperty(EVENTO_BUNDLE_VERSION_PROPERTY, "-1"));
                    } catch (Exception e) {
                        return -1;
                    }
                }).filter(v -> v >= 0).findFirst().orElseThrow(() -> new Exception("Cannot find %s in a .property file".formatted(EVENTO_BUNDLE_VERSION_PROPERTY))));

        var bundleId = FileUtils.autoCloseWalk(directory.toPath(), s -> s
                .filter(p -> p.toString().endsWith(".properties"))
                .map(p -> {
                    try {
                        var prop = new Properties();
                        prop.load(new FileReader(p.toFile()));
                        return prop.getProperty(EVENTO_BUNDLE_NAME_PROPERTY, null);
                    } catch (Exception e) {
                        return null;
                    }
                }).filter(Objects::nonNull).findFirst().orElseThrow(() -> new Exception("Cannot find %s in a .property file".formatted(EVENTO_BUNDLE_NAME_PROPERTY))));

        var autorun = FileUtils.autoCloseWalk(directory.toPath(), s -> s
                .filter(p -> p.toString().endsWith(".properties"))
                .map(p -> {
                    try {
                        var prop = new Properties();
                        prop.load(new FileReader(p.toFile()));
                        return Boolean.parseBoolean(prop.getProperty(EVENTO_BUNDLE_AUTORUN_PROPERTY, "false"));
                    } catch (Exception e) {
                        return null;
                    }
                }).filter(Objects::nonNull).findFirst().orElseThrow(() -> new Exception("Cannot find %s in a .property file".formatted(EVENTO_BUNDLE_NAME_PROPERTY))));

        var minInstances = FileUtils.autoCloseWalk(directory.toPath(), s -> s
                .filter(p -> p.toString().endsWith(".properties"))
                .map(p -> {
                    try {
                        var prop = new Properties();
                        prop.load(new FileReader(p.toFile()));
                        var i = Integer.parseInt(prop.getProperty(EVENTO_BUNDLE_INSTANCES_MIN_PROPERTY, "0"));
                        if (i == 0 && autorun) return 1;
                        else return i;
                    } catch (Exception e) {
                        return null;
                    }
                }).filter(Objects::nonNull).findFirst().orElseThrow(() -> new Exception("Cannot find %s in a .property file".formatted(EVENTO_BUNDLE_NAME_PROPERTY))));

        var maxInstances = FileUtils.autoCloseWalk(directory.toPath(), s->s
                .filter(p -> p.toString().endsWith(".properties"))
                .map(p -> {
                    try {
                        var prop = new Properties();
                        prop.load(new FileReader(p.toFile()));
                        return Integer.parseInt(prop.getProperty(EVENTO_BUNDLE_INSTANCES_MAX_PROPERTY, "64"));
                    } catch (Exception e) {
                        return null;
                    }
                }).filter(Objects::nonNull).findFirst().orElseThrow(() -> new Exception("Cannot find %s in a .property file".formatted(EVENTO_BUNDLE_NAME_PROPERTY))));

        var bundleDetail = new AtomicReference<String>();
        var bundleDescription = new AtomicReference<String>();
        try(var s = Files.walk(directory.toPath())){
                s.filter(p -> p.toString().endsWith(".md"))
                .forEach(p -> {
                    var name = p.getFileName().toString().replace(".md", "");
                    for (Component component : components) {
                        if (component.getComponentName().equals(name)) {
                            try {
                                var content = Files.readString(p).split("--detail");
                                component.setDescription(content[0].trim());
                                if (content.length > 1) {
                                    component.setDetail(content[1].trim());
                                }
                                return;
                            } catch (IOException ignored) {
                            }
                        }
                    }
                    for (PayloadDescription payloadDescription : payloads) {
                        if (payloadDescription.getName().equals(name)) {
                            try {
                                var content = Files.readString(p).split("--detail");
                                payloadDescription.setDescription(content[0].trim());
                                if (content.length > 1) {
                                    payloadDescription.setDetail(content[1].trim());
                                }
                                return;
                            } catch (IOException ignored) {
                            }
                        }
                    }
                    if (name.equals("bundle")) {
                        try {
                            var content = Files.readString(p).split("--detail");
                            bundleDescription.set(content[0].trim());
                            if (content.length > 1) {
                                bundleDetail.set(content[1].trim());
                            }
                        } catch (IOException ignored) {
                        }
                    }
                });
                }

        return new BundleDescription(
                bundleId,
                bundleVersion,
                autorun,
                minInstances,
                maxInstances,
                components,
                payloads,
                bundleDescription.get(),
                bundleDetail.get());
    }

    private Component toComponent(Node node) throws Exception {

        var classDef = node.getFirstDescendantOfType(ASTTypeDeclaration.class);
        Component resp = null;
        if (JavaComponentParser.isSaga(classDef)) {
            resp = new JavaSagaParser(node).parse();
        } else if (JavaComponentParser.isAggregate(classDef)) {
            resp = new JavaAggregateParser(node).parse();
        } else if (JavaComponentParser.isProjection(classDef)) {
            resp = new JavaProjectionParser(node).parse();
        } else if (JavaComponentParser.isProjector(classDef)) {
            resp = new JavaProjectorParser(node).parse();
        } else if (JavaComponentParser.isObserver(classDef)) {
            resp = new JavaObserverParser(node).parse();
        } else if (JavaComponentParser.isService(classDef)) {
            resp = new JavaServiceParser(node).parse();
        } else if (JavaComponentParser.isInvoker(classDef)) {
            resp = new JavaInvokerParser(node).parse();
        }
        if(resp != null){
            resp.setLine(classDef.getBeginLine());
        }
        return resp;
    }

    private PayloadDescription toPayload(net.sourceforge.pmd.lang.ast.Node node) {
            var classDef = node.getFirstDescendantOfType(ASTClassOrInterfaceDeclaration.class);
            if(classDef == null) return null;
            var extendedClass = classDef.getParent().getFirstDescendantOfType(ASTExtendsList.class);
            if(extendedClass == null) return null;
            var payloadType = extendedClass.getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
            if (payloadType.equals("DomainCommand") || payloadType.equals("DomainEvent") ||
                    payloadType.equals("ServiceCommand") || payloadType.equals("ServiceEvent") ||
                    payloadType.equals("Query") || payloadType.equals("View")) {
                JsonObject schema = new JsonObject();
                var fields = node.findDescendantsOfType(ASTFieldDeclaration.class);
                for (var field : fields) {
                    var name = field.getFirstDescendantOfType(ASTVariableDeclaratorId.class).getName();
                    var t = field.getFirstDescendantOfType(ASTClassOrInterfaceType.class);
                    var type = t != null ? t.getImage() : field.getFirstDescendantOfType(ASTPrimitiveType.class).getImage();
                    schema.addProperty(name, type);
                }
                switch (payloadType) {
                    case "DomainCommand" -> addSuperFields(schema, DomainCommand.class);
                    case "DomainEvent" -> addSuperFields(schema, DomainEvent.class);
                    case "ServiceCommand" -> addSuperFields(schema, ServiceCommand.class);
                    case "ServiceEvent" -> addSuperFields(schema, ServiceEvent.class);
                    case "Query" -> addSuperFields(schema, Query.class);
                    default -> addSuperFields(schema, View.class);
                }
                String domain = null;
                try {
                    domain = classDef.getParent().findDescendantsOfType(ASTMemberValuePair.class)
                            .stream().filter(p -> p.getFirstParentOfType(ASTAnnotation.class)
                                    .getFirstDescendantOfType(ASTName.class)
                                    .getImage().equals("Domain"))
                            .findFirst().orElseThrow().getFirstDescendantOfType(ASTLiteral.class)
                            .getImage().replace("\"", "");
                } catch (Exception ignored) {
                }
                return new PayloadDescription(classDef.getSimpleName(), domain, payloadType, schema.toString(), classDef.getBeginLine());
            }
            return null;
    }

    private void addSuperFields(JsonObject schema, Class<?> clazz) {
        for (Field declaredField : clazz.getDeclaredFields()) {
            schema.addProperty(declaredField.getName(), declaredField.getType().getSimpleName());
        }
    }
}
