package org.eventrails.parser.java;

import com.google.gson.JsonObject;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersionHandler;
import net.sourceforge.pmd.lang.Parser;
import net.sourceforge.pmd.lang.java.ast.*;
import org.eventrails.modeling.messaging.payload.*;
import org.eventrails.parser.RanchApplicationParser;
import org.eventrails.parser.model.RanchApplicationDescription;
import org.eventrails.parser.model.node.Node;
import org.eventrails.parser.model.payload.PayloadDescription;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Objects;

public class JavaRanchApplicationParser implements RanchApplicationParser {

	public RanchApplicationDescription parseDirectory(File file) throws IOException {
		LanguageVersionHandler java = LanguageRegistry.getLanguage("Java").getDefaultVersion().getLanguageVersionHandler();
		Parser parser = java.getParser(java.getDefaultParserOptions());
		if (!file.isDirectory()) throw new RuntimeException("error.not.dir");
		var components =  Files.walk(file.toPath())
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

		var payloads =  Files.walk(file.toPath())
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
				}).filter(Objects::nonNull).toList();

		return new RanchApplicationDescription(components, payloads);
	}

	private Node toComponent(net.sourceforge.pmd.lang.ast.Node node) throws Exception {

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
		} else if (JavaComponentParser.isService(classDef))
		{
			return new JavaServiceParser(node).parse();
		}
		return null;
	}

	private PayloadDescription toPayload(net.sourceforge.pmd.lang.ast.Node node) throws Exception {


		try
		{
			var classDef = node.getFirstDescendantOfType(ASTClassOrInterfaceDeclaration.class);
			var payloadType = classDef.getParent().getFirstDescendantOfType(ASTExtendsList.class).getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
			if(payloadType.equals("DomainCommand") || payloadType.equals("DomainEvent") ||
					payloadType.equals("ServiceCommand") || payloadType.equals("ServiceEvent") ||
			payloadType.equals("Query") || payloadType.equals("View")){
				JsonObject schema = new JsonObject();
				var fields = node.findDescendantsOfType(ASTFieldDeclaration.class);
				for(var field: fields){
					var name = field.getFirstDescendantOfType(ASTVariableDeclaratorId.class).getName();
					var type = field.getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
					schema.addProperty(name, type);
				}
				if(payloadType.equals("DomainCommand")){
					addSuperFields(schema, DomainCommand.class);
				}else if( payloadType.equals("DomainEvent")){
					addSuperFields(schema, DomainEvent.class);
				}else if( payloadType.equals("ServiceCommand")){
					addSuperFields(schema, ServiceCommand.class);
				}else if( payloadType.equals("ServiceEvent")){
					addSuperFields(schema, ServiceEvent.class);
				}else if( payloadType.equals("Query")){
					addSuperFields(schema, Query.class);
				}else
				{
					addSuperFields(schema, View.class);
				}
				return new PayloadDescription(classDef.getSimpleName(), payloadType,  schema.toString());
			}
			return null;

		}catch (Exception e){
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
