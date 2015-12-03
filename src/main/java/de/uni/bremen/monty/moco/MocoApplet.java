package de.uni.bremen.monty.moco;

import de.uni.bremen.monty.moco.ast.Package;
import de.uni.bremen.monty.moco.ast.PackageBuilder;
import de.uni.bremen.monty.moco.visitor.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import java.applet.Applet;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class MocoApplet extends Applet {
	public static String compile(String program) {

		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		System.setOut(new PrintStream(outStream));

		ByteArrayOutputStream errStream = new ByteArrayOutputStream();
		System.setErr(new PrintStream(errStream));

		// build the package
		PackageBuilder packageBuilder = new PackageBuilder();
		InputStream stream = new ByteArrayInputStream(program.getBytes(StandardCharsets.UTF_8));
		Package ast = packageBuilder.buildPackage(stream);

		if (!errStream.toString().isEmpty()) {
			return jsonify(outStream.toString(), "Syntax Error:\n" + errStream.toString(), "");
		}

		// visit visitors
		CodeGenerationVisitor cgv = new CodeGenerationVisitor();
		BaseVisitor[] visitors =
		        new BaseVisitor[] { new SetParentVisitor(), new DeclarationVisitor(), new ResolveVisitor(),
		                new TypeCheckVisitor(), new ControlFlowVisitor(), cgv };
		boolean everyThingIsAwesome = true;

		for (BaseVisitor visitor : visitors) {
			visitor.setStopOnFirstError(true);

			try {
				visitor.visitDoubleDispatched(ast);
			} catch (RuntimeException exception) {
				visitor.logError(exception);
				everyThingIsAwesome = false;
				break;
			}

			if (visitor.foundError()) {
				everyThingIsAwesome = false;
				break;
			}
		}

		// write output
		StringWriter writer = new StringWriter();
		try {
			IOUtils.copy(Main.class.getResourceAsStream("/std_llvm_include.ll"), writer);
		} catch (IOException e) {
			e.printStackTrace();
			return jsonify(outStream.toString(), "IO Error:\n" + errStream.toString(), "");
		}
		cgv.writeLLVMCode(writer.getBuffer());
		if (everyThingIsAwesome) {
			return jsonify(outStream.toString(), errStream.toString(), writer.toString());
		}
		return jsonify(outStream.toString(), errStream.toString(), "");
	}

	protected static String jsonify(String out, String err, String assembly) {
		return "{\n\tout : \"" + StringEscapeUtils.escapeJson(out) + "\",\n\terr : \""
		        + StringEscapeUtils.escapeJson(err) + "\",\n\tassembly : \"" + StringEscapeUtils.escapeJson(assembly)
		        + "\"\n}";
	}
}
