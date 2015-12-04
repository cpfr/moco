/*
 * moco, the Monty Compiler
 * Copyright (c) 2013-2014, Monty's Coconut, All rights reserved.
 *
 * This file is part of moco, the Monty Compiler.
 *
 * moco is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * moco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * Linking this program and/or its accompanying libraries statically or
 * dynamically with other modules is making a combined work based on this
 * program. Thus, the terms and conditions of the GNU General Public License
 * cover the whole combination.
 *
 * As a special exception, the copyright holders of moco give
 * you permission to link this programm and/or its accompanying libraries
 * with independent modules to produce an executable, regardless of the
 * license terms of these independent modules, and to copy and distribute the
 * resulting executable under terms of your choice, provided that you also meet,
 * for each linked independent module, the terms and conditions of the
 * license of that module.
 *
 * An independent module is a module which is not
 * derived from or based on this program and/or its accompanying libraries.
 * If you modify this library, you may extend this exception to your version of
 * the program or library, but you are not obliged to do so. If you do not wish
 * to do so, delete this exception statement from your version.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library.
 */
package de.uni.bremen.monty.moco;

import de.uni.bremen.monty.moco.ast.Package;
import de.uni.bremen.monty.moco.ast.PackageBuilder;
import de.uni.bremen.monty.moco.util.Logger;
import de.uni.bremen.monty.moco.visitor.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import java.applet.Applet;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class MocoApplet extends Applet {
	public static String compile(String program) {
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		Logger.setOutStream(new PrintStream(outStream));

		ByteArrayOutputStream errStream = new ByteArrayOutputStream();
		Logger.setErrStream(new PrintStream(errStream));

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
			Logger.logStackTrace(e);
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
