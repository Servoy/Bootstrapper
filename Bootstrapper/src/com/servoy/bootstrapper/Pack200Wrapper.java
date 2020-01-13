/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2020 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
*/

package com.servoy.bootstrapper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.jar.Pack200.Packer;
import java.util.jar.Pack200.Unpacker;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Class to wrap calls to the Pack200 class because that is removed from Java14.
 *
 * @author jcompagner
 * @since 2020.03
 *
 */
public class Pack200Wrapper
{
	private final static Class< ? > pack200Class;

	static
	{
		Class< ? > cls = null;
		try
		{
			cls = Class.forName("java.util.jar.Pack200");
		}
		catch (Throwable t)
		{
			// ignore
		}
		pack200Class = cls;
	}

	/**
	 * @return
	 */
	public static boolean isPack200Loaded()
	{
		return pack200Class != null;
	}

	/**
	 * @param sourceFile
	 * @param targetFile
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public static void pack(File sourceFile, File targetFile) throws IOException, FileNotFoundException
	{
		try (GZIPOutputStream bos = new GZIPOutputStream(new FileOutputStream(targetFile)))
		{
			Packer packer = Pack200.newPacker();
			packer.pack(new JarFile(sourceFile), bos);
		}
	}

	/**
	 * @param is
	 * @param os
	 * @throws IOException
	 */
	public static void unpack(InputStream is, OutputStream os) throws IOException
	{
		try (JarOutputStream jos = new JarOutputStream(os))
		{
			Unpacker unpacker = Pack200.newUnpacker();
			unpacker.unpack(is, jos);
			jos.flush();
		}
	}
}
