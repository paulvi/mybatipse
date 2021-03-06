/*-****************************************************************************** 
 * Copyright (c) 2014 Iwao AVE!.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Iwao AVE! - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.bean;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * @author Iwao AVE!
 */
public class BeanPropertyVisitor extends ASTVisitor
{
	private IJavaProject project;

	private final Map<String, String> readableFields;

	private final Map<String, String> writableFields;

	public BeanPropertyVisitor(
		IJavaProject project,
		Map<String, String> readableFields,
		Map<String, String> writableFields)
	{
		super();
		this.project = project;
		this.readableFields = readableFields;
		this.writableFields = writableFields;
	}

	@Override
	public boolean visit(FieldDeclaration node)
	{
		int modifiers = node.getModifiers();
		if (Modifier.isPublic(modifiers))
		{
			@SuppressWarnings("unchecked")
			List<VariableDeclarationFragment> fragments = node.fragments();
			for (VariableDeclarationFragment fragment : fragments)
			{
				String fieldName = fragment.getName().toString();
				String qualifiedName = getQualifiedNameFromType(node.getType());
				if (qualifiedName == null)
					; // ignore
				else
				{
					readableFields.put(fieldName, qualifiedName);
					if (!Modifier.isFinal(modifiers))
						writableFields.put(fieldName, qualifiedName);
				}
			}
		}
		return false;
	}

	@Override
	public boolean visit(MethodDeclaration node)
	{
		// Resolve binding first to support Lombok generated methods.
		// node.getModifiers() returns incorrect access modifiers for them.
		// https://github.com/harawata/stlipse/issues/2
		IMethodBinding method = node.resolveBinding();
		if (Modifier.isPublic(method.getModifiers()))
		{
			final String methodName = node.getName().toString();
			final int parameterCount = node.parameters().size();
			final Type returnType = node.getReturnType2();
			if (returnType == null)
			{
				// Ignore constructor
			}
			else if (isReturnVoid(returnType))
			{
				if (isSetter(methodName, parameterCount))
				{
					SingleVariableDeclaration param = (SingleVariableDeclaration)node.parameters().get(0);
					String qualifiedName = getQualifiedNameFromType(param.getType());
					String fieldName = getFieldNameFromAccessor(methodName);
					writableFields.put(fieldName, qualifiedName);
				}
			}
			else
			{
				if (isGetter(methodName, parameterCount))
				{
					String fieldName = getFieldNameFromAccessor(methodName);
					String qualifiedName = getQualifiedNameFromType(returnType);
					readableFields.put(fieldName, qualifiedName);
				}
			}
		}
		return false;
	}

	private String getQualifiedNameFromType(Type type)
	{
		String qualifiedName = null;
		ITypeBinding binding = type.resolveBinding();
		if (binding != null)
		{
			if (binding.isParameterizedType())
			{
				ITypeBinding[] arguments = binding.getTypeArguments();
				// length = 1 -> List, length > 1 -> Map
				qualifiedName = arguments[arguments.length > 1 ? 1 : 0].getQualifiedName();
			}
			else
			{
				qualifiedName = binding.getQualifiedName();
			}
		}
		return qualifiedName;
	}

	public static boolean isGetter(String methodName, int parameterCount)
	{
		return (methodName.startsWith("get") && methodName.length() > 3)
			|| (methodName.startsWith("is") && methodName.length() > 2) && parameterCount == 0;
	}

	public static boolean isSetter(String methodName, int parameterCount)
	{
		return methodName.startsWith("set") && methodName.length() > 3 && parameterCount == 1;
	}

	private boolean isReturnVoid(Type type)
	{
		return type.isPrimitiveType()
			&& PrimitiveType.VOID.equals(((PrimitiveType)type).getPrimitiveTypeCode());
	}

	@Override
	public void endVisit(TypeDeclaration node)
	{
		Type superclassType = node.getSuperclassType();
		if (superclassType != null)
		{
			ITypeBinding binding = superclassType.resolveBinding();
			BeanPropertyCache.parseBean(project, binding.getQualifiedName(), readableFields,
				writableFields);
		}
	}

	public static String getFieldNameFromAccessor(String methodName)
	{
		if (methodName == null)
			return "";
		StringBuilder sb = new StringBuilder();
		if (methodName.startsWith("set") || methodName.startsWith("get"))
		{
			sb.append(Character.toLowerCase(methodName.charAt(3)));
			if (methodName.length() > 4)
				sb.append(methodName.substring(4));
		}
		else if (methodName.startsWith("is"))
		{
			sb.append(Character.toLowerCase(methodName.charAt(2)));
			if (methodName.length() > 3)
				sb.append(methodName.substring(3));
		}
		else
		{
			// No such accessor.
		}
		return sb.toString();
	}
}
