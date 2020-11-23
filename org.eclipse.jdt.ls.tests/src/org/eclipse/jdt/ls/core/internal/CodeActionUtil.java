/*******************************************************************************
 * Copyright (c) 2019-2020 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class CodeActionUtil {
	public static final String SQUARE_BRACKET_OPEN = "/*[*/";
	public static final int SQUARE_BRACKET_OPEN_LENGTH = SQUARE_BRACKET_OPEN.length();
	public static final String SQUARE_BRACKET_CLOSE = "/*]*/";
	public static final int SQUARE_BRACKET_CLOSE_LENGTH = SQUARE_BRACKET_CLOSE.length();

	protected static final int VALID_SELECTION = 1;
	protected static final int INVALID_SELECTION = 2;
	protected static final int COMPARE_WITH_OUTPUT = 3;

	/**
	 * Return the selection enclosed by the marker "&#47;*[*&#47;" and "&#47;*]*&#47;".
	 */
	public static int[] getSelection(String source) {
		int start = -1;
		int end = -1;
		int includingStart = source.indexOf(SQUARE_BRACKET_OPEN);
		int excludingStart = source.indexOf(SQUARE_BRACKET_CLOSE);
		int includingEnd = source.lastIndexOf(SQUARE_BRACKET_CLOSE);
		int excludingEnd = source.lastIndexOf(SQUARE_BRACKET_OPEN);

		if (includingStart > excludingStart && excludingStart != -1) {
			includingStart = -1;
		} else if (excludingStart > includingStart && includingStart != -1) {
			excludingStart = -1;
		}

		if (includingEnd < excludingEnd) {
			includingEnd = -1;
		} else if (excludingEnd < includingEnd) {
			excludingEnd = -1;
		}

		if (includingStart != -1) {
			start = includingStart;
		} else {
			start = excludingStart + SQUARE_BRACKET_CLOSE_LENGTH;
		}

		if (excludingEnd != -1) {
			end = excludingEnd;
		} else {
			end = includingEnd + SQUARE_BRACKET_CLOSE_LENGTH;
		}

		return new int[] { start, end - start };
	}

	/**
	 * Return the selected range enclosed by the marker "&#47;*[*&#47;" and "&#47;*]*&#47;".
	 */
	public static Range getRange(ICompilationUnit cu) throws JavaModelException {
		int[] ranges = getSelection(cu.getSource());
		return JDTUtils.toRange(cu, ranges[0], ranges[1]);
	}

	public static Range getRange(ICompilationUnit unit, String search) throws JavaModelException {
		return getRange(unit, search, search.length());
	}

	public static Range getRange(ICompilationUnit unit, String search, int length) throws JavaModelException {
		String str = unit.getSource();
		int start = str.lastIndexOf(search);
		return JDTUtils.toRange(unit, start, length);
	}

	public static CodeActionParams constructCodeActionParams(ICompilationUnit unit, String search) throws JavaModelException {
		final Range range = getRange(unit, search);
		return constructCodeActionParams(unit, range);

	}

	public static CodeActionParams constructCodeActionParams(ICompilationUnit unit, Range range) {
		CodeActionParams params = new CodeActionParams();
		params.setTextDocument(new TextDocumentIdentifier(JDTUtils.toURI(unit)));
		params.setRange(range);
		params.setContext(new CodeActionContext(Collections.emptyList()));
		return params;
	}

	public static String evaluateWorkspaceEdit(WorkspaceEdit edit) throws JavaModelException, BadLocationException {
		if (edit == null) {
			return null;
		}

		if (edit.getDocumentChanges() != null) {
			return evaluateChanges(edit.getDocumentChanges());
		}

		return evaluateChanges(edit.getChanges());
	}

	private static String evaluateChanges(List<Either<TextDocumentEdit, ResourceOperation>> documentChanges) throws BadLocationException, JavaModelException {
		List<TextDocumentEdit> changes = documentChanges.stream().filter(e -> e.isLeft()).map(e -> e.getLeft()).collect(Collectors.toList());
		assertFalse("No edits generated", changes.isEmpty());
		Set<String> uris = changes.stream().map(tde -> tde.getTextDocument().getUri()).distinct().collect(Collectors.toSet());
		assertEquals("Only one resource should be modified", 1, uris.size());
		String uri = uris.iterator().next();
		List<TextEdit> edits = changes.stream().flatMap(e -> e.getEdits().stream()).collect(Collectors.toList());
		return evaluateChanges(uri, edits);
	}

	private static String evaluateChanges(Map<String, List<TextEdit>> changes) throws BadLocationException, JavaModelException {
		Iterator<Entry<String, List<TextEdit>>> editEntries = changes.entrySet().iterator();
		Entry<String, List<TextEdit>> entry = editEntries.next();
		assertNotNull("No edits generated", entry);
		assertEquals("More than one resource modified", false, editEntries.hasNext());
		return evaluateChanges(entry.getKey(), entry.getValue());
	}

	private static String evaluateChanges(String uri, List<TextEdit> edits) throws BadLocationException, JavaModelException {
		assertFalse("No edits generated: " + edits, edits == null || edits.isEmpty());
		ICompilationUnit cu = JDTUtils.resolveCompilationUnit(uri);
		assertNotNull("CU not found: " + uri, cu);
		Document doc = new Document();
		if (cu.exists()) {
			doc.set(cu.getSource());
		}
		return TextEditUtil.apply(doc, edits);
	}
}
