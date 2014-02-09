package fr.ensicaen.panandroid.meshs;

import javax.microedition.khronos.opengles.GL10;

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
