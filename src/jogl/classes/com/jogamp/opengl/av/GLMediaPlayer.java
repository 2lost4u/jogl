package com.jogamp.opengl.av;

import java.io.IOException;
import java.net.URL;

import javax.media.opengl.GL;

import jogamp.opengl.Debug;

import com.jogamp.opengl.util.texture.Texture;

/**
 * Lifecycle of an GLMediaPlayer:
 * <ul>
 *   <li>{@link #setStream(GL, URL)}</li>
 *   <li>{@link #start()}</li>
 *   <li>{@link #stop()}</li>
 *   <li>{@link #destroy(GL)}</li>
 * </ul>
 */
public interface GLMediaPlayer {
    public static final boolean DEBUG = Debug.debug("GLMediaPlayer");
    
    public enum State {
        Uninitialized(0), Stopped(1), Playing(2), Paused(3); 
        
        public final int id;

        State(int id){
            this.id = id;
        }
    }
    
    public static class TextureFrame {
        public TextureFrame(Texture t) {
            texture = t;
            // stMatrix = new float[4*4];
            // ProjectFloat.makeIdentityf(stMatrix, 0);
        }
        
        public final Texture getTexture() { return texture; }
        // public final float[] getSTMatrix() { return stMatrix; }
        
        public String toString() {
            return "TextureFrame[" + texture + "]";
        }
        protected final Texture texture;
        // protected final float[] stMatrix;
    }
    
    public int getTextureCount();
    
    public int getTextureTarget();
    
    /** Sets the texture min-mag filter, defaults to {@link GL#GL_NEAREST}. */
    public void setTextureMinMagFilter(int[] minMagFilter);
    public int[] getTextureMinMagFilter();
    
    /** Sets the texture min-mag filter, defaults to {@link GL#GL_CLAMP_TO_EDGE}. */
    public void setTextureWrapST(int[] wrapST);
    public int[] getTextureWrapST();
    
    /** 
     * Sets the stream to be used. Initializes all stream related states and GL resources.
     * <ul>
     *   <li>ANY -> Uninitialized - invokes destroy(GL)</li>
     *   <li>Uninitialized -> Stopped</li>
     * </ul>
     */
    public void setStream(GL gl, URL url) throws IOException;

    /**
     * Releases the GL and stream resources.
     * <p>
     * <code>ANY</code> -> Uninitialized
     * </p>
     */
    public void destroy(GL gl);

    public void setPlaySpeed(float rate);

    public float getPlaySpeed();

    /**
     * Stopped/Paused -> Playing
     */
    public State start();

    /**
     * Playing -> Paused
     */
    public State pause();

    /**
     * Playing/Paused -> Stopped
     */
    public State stop();
    
    /**
     * @return the current state, either Uninitialized, Stopped, Playing, Paused
     */
    public State getState();
    
    /**
     * @return time current position in milliseconds 
     **/
    public long getCurrentPosition();

    /**
     * @param msec absolute desired time position in milliseconds 
     * @return time current position in milliseconds, after seeking to the desired position  
     **/
    public long seek(long msec);

    /**
     * @return the last updated texture. Not blocking. 
     */
    public TextureFrame getLastTexture();
    
    /**
     * @return the next texture, which should be rendered. May block, depending on implementation.
     * 
     * @see #addEventListener(GLMediaEventListener)
     * @see GLMediaEventListener#newFrameAvailable(GLMediaPlayer, TextureFrame)
     */
    public TextureFrame getNextTexture();
    
    public boolean isValid();

    public URL getURL();

    /**
     * <i>Warning:</i> Optional information, may not be supported by implementation.
     * @return the code of the video stream, if available 
     */
    public String getVideoCodec();

    /**
     * <i>Warning:</i> Optional information, may not be supported by implementation.
     * @return the code of the audio stream, if available 
     */
    public String getAudioCodec();

    /**
     * <i>Warning:</i> Optional information, may not be supported by implementation.
     * @return the total number of video frames
     */
    public long getTotalFrames();

    /**
     * @return total duration of stream in msec.
     */
    public long getDuration();
    
    /**
     * <i>Warning:</i> Optional information, may not be supported by implementation.
     * @return the overall bitrate of the stream.  
     */
    public long getBitrate();

    /**
     * <i>Warning:</i> Optional information, may not be supported by implementation.
     * @return the framerate of the video
     */
    public int getFramerate();

    public int getWidth();

    public int getHeight();

    public String toString();

    public void addEventListener(GLMediaEventListener l);

    public void removeEventListener(GLMediaEventListener l);

    public GLMediaEventListener[] getEventListeners();
}