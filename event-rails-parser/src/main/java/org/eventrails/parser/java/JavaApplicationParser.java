package org.eventrails.parser.java;

import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersionHandler;
import net.sourceforge.pmd.lang.Parser;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceType;
import net.sourceforge.pmd.lang.java.ast.ASTExtendsList;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import org.eventrails.parser.ApplicationParser;
import org.eventrails.parser.model.component.Component;
import org.eventrails.parser.model.payload.PayloadDescription;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

public class JavaApplicationParser implements ApplicationParser {

	public List<Component> parseDirectory(File file) throws IOException {
		LanguageVersionHandler java = LanguageRegistry.getLanguage("Java").getDefaultVersion().getLanguageVersionHandler();
		Parser parser = java.getParser(java.getDefaultParserOptions());
		if (!file.isDirectory()) throw new RuntimeException("error.not.dir");
		return Files.walk(file.toPath())
				.filter(p -> p.toString().endsWith(".java"))
				.filter(p -> !p.toString().toLowerCase().contains("test"))
				.map(p -> {
					try
					{
						var node = parser.parse(p.getFileName().toString(), new FileReader(p.toFile()));
						System.out.println(p.toAbsolutePath());
						var payload = toPayload(node);
						return toComponent(node);
					} catch (Exception e)
					{
						e.printStackTrace();
						return null;
					}
				}).filter(Objects::nonNull).toList();
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
		} else if (JavaComponentParser.isService(classDef))
		{
			return new JavaServiceParser(node).parse();
		}
		return null;
	}

	private Object toPayload(Node node) throws Exception {

/*

		try
		{
			var classDef = node.getFirstDescendantOfType(ASTClassOrInterfaceDeclaration.class);
			var payloadType = classDef.getParent().getFirstDescendantOfType(ASTExtendsList.class).getFirstDescendantOfType(ASTClassOrInterfaceType.class).getImage();
			if(payloadType.equals("DomainCommand") || payloadType.equals("DomainEvent") ||
					payloadType.equals("ServiceCommand") || payloadType.equals("ServiceEvent") ||
			payloadType.equals("Query") || payloadType.equals("View")){
				return new PayloadDescription(classDef.getSimpleName(), payloadType, )
			}
			return null;

		}catch (Exception e){
			return null;
		}*/
		return null;
	}
}
