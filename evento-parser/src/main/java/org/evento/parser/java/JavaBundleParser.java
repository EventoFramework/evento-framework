package org.evento.parser.java;

import com.google.gson.JsonObject;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersionHandler;
import net.sourceforge.pmd.lang.Parser;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.*;
import org.evento.common.modeling.messaging.payload.*;
import org.evento.parser.BundleParser;
import org.evento.parser.model.BundleDescription;
import org.evento.parser.model.component.Component;
import org.evento.parser.model.component.Invoker;
import org.evento.parser.model.handler.Handler;
import org.evento.parser.model.handler.InvocationHandler;
import org.evento.parser.model.payload.PayloadDescription;

import java.io.Console;
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

public class JavaBundleParser implements BundleParser {

    public static final String EVENTO_BUNDLE_VERSION_PROPERTY = "evento.bundle.version";
    public static final String EVENTO_BUNDLE_NAME_PROPERTY = "evento.bundle.id";
    public static final String EVENTO_BUNDLE_AUTORUN_PROPERTY = "evento.bundle.autorun";
    public static final String EVENTO_BUNDLE_INSTANCES_MIN_PROPERTY = "evento.bundle.instances.min";
    public static final String EVENTO_BUNDLE_INSTANCES_MAX_PROPERTY = "evento.bundle.instances.max";

    public BundleDescription parseDirectory(File directory, String repositoryRoot) throws Exception {
        LanguageVersionHandler java = LanguageRegistry.getLanguage("Java").getDefaultVersion().getLanguageVersionHandler();
        Parser parser = java.getParser(java.getDefaultParserOptions());
        if (!directory.isDirectory()) throw new RuntimeException("error.not.dir");
        System.out.println("Looking for components...");
        var components = Files.walk(directory.toPath())
                .filter(p -> p.toString().endsWith(".java"))
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
                            System.out.println("Component found in: " + p.toAbsolutePath());
                        }
                        return c;

                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }).filter(Objects::nonNull).toList();

        System.out.println("Total components detected: " + components.size() );
        System.out.println("Looking for payloads...");
        var payloads = Stream.concat(
                Files.walk(directory.toPath())
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
                                    System.out.println("Payload found in: " + p.toAbsolutePath());
                                }
                                return pl;
                            } catch (Exception e) {
                                System.out.println("Error parsing: " + p.toAbsolutePath());
                                e.printStackTrace();
                                return null;
                            }
                        }).filter(Objects::nonNull),
                components.stream()
                        .filter(c -> c instanceof Invoker)
                        .map(c -> ((Invoker) c))
						.flatMap(in -> in.getInvocationHandlers().stream().distinct().map(
								p -> {
									var pl = new PayloadDescription(p.getPayload().getName(), p.getPayload().getDomain(), "Invocation", "{}", p.getLine());
									pl.setPath(in.getPath());
									return pl;
								}
						))
        ).collect(Collectors.toList());
        System.out.println("Total payloads detected: " + payloads.size() );

        var bundleVersion = Files.walk(directory.toPath())
                .filter(p -> p.toString().endsWith(".properties"))
                .mapToInt(p -> {
                    try {
                        var prop = new Properties();
                        prop.load(new FileReader(p.toFile()));
                        return Integer.parseInt(prop.getProperty(EVENTO_BUNDLE_VERSION_PROPERTY, "-1"));
                    } catch (Exception e) {
                        return -1;
                    }
                }).filter(v -> v >= 0).findFirst().orElseThrow(() -> new Exception("Cannot find %s in a .property file".formatted(EVENTO_BUNDLE_VERSION_PROPERTY)));

        var bundleId = Files.walk(directory.toPath())
                .filter(p -> p.toString().endsWith(".properties"))
                .map(p -> {
                    try {
                        var prop = new Properties();
                        prop.load(new FileReader(p.toFile()));
                        return prop.getProperty(EVENTO_BUNDLE_NAME_PROPERTY, null);
                    } catch (Exception e) {
                        return null;
                    }
                }).filter(Objects::nonNull).findFirst().orElseThrow(() -> new Exception("Cannot find %s in a .property file".formatted(EVENTO_BUNDLE_NAME_PROPERTY)));

        var autorun = Files.walk(directory.toPath())
                .filter(p -> p.toString().endsWith(".properties"))
                .map(p -> {
                    try {
                        var prop = new Properties();
                        prop.load(new FileReader(p.toFile()));
                        return Boolean.parseBoolean(prop.getProperty(EVENTO_BUNDLE_AUTORUN_PROPERTY, "false"));
                    } catch (Exception e) {
                        return null;
                    }
                }).filter(Objects::nonNull).findFirst().orElseThrow(() -> new Exception("Cannot find %s in a .property file".formatted(EVENTO_BUNDLE_NAME_PROPERTY)));

        var minInstances = Files.walk(directory.toPath())
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
                }).filter(Objects::nonNull).findFirst().orElseThrow(() -> new Exception("Cannot find %s in a .property file".formatted(EVENTO_BUNDLE_NAME_PROPERTY)));

        var maxInstances = Files.walk(directory.toPath())
                .filter(p -> p.toString().endsWith(".properties"))
                .map(p -> {
                    try {
                        var prop = new Properties();
                        prop.load(new FileReader(p.toFile()));
                        return Integer.parseInt(prop.getProperty(EVENTO_BUNDLE_INSTANCES_MAX_PROPERTY, "64"));
                    } catch (Exception e) {
                        return null;
                    }
                }).filter(Objects::nonNull).findFirst().orElseThrow(() -> new Exception("Cannot find %s in a .property file".formatted(EVENTO_BUNDLE_NAME_PROPERTY)));

        var bundleDetail = new AtomicReference<String>();
        var bundleDescription = new AtomicReference<String>();
        Files.walk(directory.toPath())
                .filter(p -> p.toString().endsWith(".md"))
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
                            return;
                        } catch (IOException ignored) {
                        }
                    }
                    System.out.println();
                });

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
                if (payloadType.equals("DomainCommand")) {
                    addSuperFields(schema, DomainCommand.class);
                } else if (payloadType.equals("DomainEvent")) {
                    addSuperFields(schema, DomainEvent.class);
                } else if (payloadType.equals("ServiceCommand")) {
                    addSuperFields(schema, ServiceCommand.class);
                } else if (payloadType.equals("ServiceEvent")) {
                    addSuperFields(schema, ServiceEvent.class);
                } else if (payloadType.equals("Query")) {
                    addSuperFields(schema, Query.class);
                } else {
                    addSuperFields(schema, View.class);
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
