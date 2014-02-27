/*
 * Copyright (C) 2013 Nicolas THIERION, Saloua BENSEDDIK, Jean Marguerite.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package fr.ensicaen.panandroid.meshs;

import javax.microedition.khronos.opengles.GL10;

/**
 * Non-drawable dummy mesh.
 * @author Nicolas THIERION.
 *
 */
public class NullMesh extends Mesh
{

	@Override
	public void draw(GL10 gl, float[] modelViewMatrix)
	{
		System.err.println("Trying to draw a null mesh. Forgotten to call \"setMesh()\" method?");
	}

	@Override
	public void loadGLTexture(GL10 gl) {
		
	}

	@Override
	public void unloadGLTexture(GL10 gl) {
		
	}

}
