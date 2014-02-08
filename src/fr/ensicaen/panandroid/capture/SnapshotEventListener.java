package fr.ensicaen.panandroid.capture;

public interface SnapshotEventListener
{
	abstract void onSnapshotTaken(byte[] pictureData, Snapshot snapshot);
}
