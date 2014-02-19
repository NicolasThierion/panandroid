package fr.ensicaen.panandroid.capture;

/**
 * When the camera build a snapshot object from a picture it has just taken, it throw the event onSnapshotTaken.
 * @author Nicolas
 *
 */
public interface SnapshotEventListener
{
	abstract void onSnapshotTaken(byte[] pictureData, Snapshot snapshot);
}
