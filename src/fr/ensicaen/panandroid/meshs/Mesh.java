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
 * Based on Jim Cornmell's Sphere.
 * @author Nicolas THIERION.
 *
 */
public abstract class Mesh
{

//	/** Master rotation axis around which one the mesh rotates first **/
//	private Axis mAxis = Axis.VERTICAL; 
//	
//	/** model matrix of the mesh. Define rotation/transformation of the mesh applied in the view matrix **/
//	protected float[] mModelMatrix = {1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
//	
	/**
	 * Load the texture for the square.
	 *
	 * @param gl Handle.
	 */
	public abstract void loadGLTexture(final GL10 gl);

	public abstract void unloadGLTexture(final GL10 gl);

	
  

	/**
	 * The draw method for the square with the GL context.
	 *
	 * @param gl Graphics handle.
	 * @param modelViewMatrix 
	 */
	public abstract void draw(final GL10 gl, float[] modelViewMatrix);
	  
	  

//	public void rotate(float rx, float ry, float rz)
//	{
//		switch(mAxis)
//		{
//		//rotate yaw first
//		case VERTICAL :
//			Matrix.rotateM(mModelMatrix, 0, ry, 0, 1, 0);
//			Matrix.rotateM(mModelMatrix, 0, rx, 1, 0, 0);
//			break;
//		default:
//			Matrix.rotateM(mModelMatrix, 0, rx, 1, 0, 0);
//			Matrix.rotateM(mModelMatrix, 0, ry, 0, 1, 0);
//		}
//		
//		Matrix.rotateM(mModelMatrix, 0, rz, 0, 0, 1);
//	}
//	
//	/**
//	 * Translate the mesh by the given vector
//	 * @param tx
//	 * @param ty
//	 * @param tz
//	 */
//	public void translate(float tx,float ty,float tz)
//	{
//	    Matrix.translateM(mModelMatrix, 0, -tx, -ty, -tz);
//		
//	}
//	
//	public void setAxis(Axis axis)
//	{
//		mAxis = axis;
//	}
}
