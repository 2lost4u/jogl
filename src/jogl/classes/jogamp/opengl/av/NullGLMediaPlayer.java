/**
 * Copyright 2012 JogAmp Community. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 * 
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 * 
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY JogAmp Community ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JogAmp Community OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of JogAmp Community.
 */
package jogamp.opengl.av;

import java.io.IOException;
import java.net.URLConnection;
import java.nio.ByteBuffer;

import javax.media.opengl.GL;
import javax.media.opengl.GLProfile;

import jogamp.opengl.av.GLMediaPlayerImpl;

import com.jogamp.common.nio.Buffers;
import com.jogamp.common.util.IOUtil;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureCoords;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.TextureIO;

/***
 * A dummy null media player implementation using a static test frame (if available).
 */
public class NullGLMediaPlayer extends GLMediaPlayerImpl {
    private TextureData texData = null;
    private TextureFrame frame = null;
    private long pos_ms = 0;
    private long pos_start = 0;
    
    public NullGLMediaPlayer() {
        super();
        this.setTextureCount(1);
    }

    @Override
    public void setPlaySpeed(float rate) {
        // n/a
    }

    @Override
    public float getPlaySpeed() {
        return 0;
    }
    
    @Override
    protected boolean startImpl() {
        pos_start = System.currentTimeMillis();
        return true;
    }

    @Override
    protected boolean pauseImpl() {
        return true;
    }

    @Override
    protected boolean stopImpl() {
        return true;
    }
    
    @Override
    protected long seekImpl(long msec) {
        pos_ms = msec;
        validatePos();
        return pos_ms;
    }
    
    @Override
    public TextureFrame getLastTexture() {
        return frame;
    }

    @Override
    public TextureFrame getNextTexture(GL gl, boolean blocking) {
        return frame;
    }
    
    @Override
    public TextureCoords getTextureCoords() {
        return frame.getTexture().getImageTexCoords();
    }
    
    @Override
    public long getCurrentPosition() {
        pos_ms = System.currentTimeMillis() - pos_start;
        validatePos();
        return pos_ms;
    }

    @Override
    protected void destroyImpl(GL gl) {
    }
    
    @Override
    protected void initGLStreamImpl(GL gl, int[] texNames) throws IOException {
        try {
            URLConnection urlConn = IOUtil.getResource("jogl/util/data/av/test-ntsc01-160x90.png", NullGLMediaPlayer.class.getClassLoader());
            if(null != urlConn) {
                texData = TextureIO.newTextureData(GLProfile.getGL2ES2(), urlConn.getInputStream(), false, TextureIO.PNG);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if(null != texData) {
            width = texData.getWidth();
            height = texData.getHeight();            
        } else {
            width = 640;
            height = 480;
            ByteBuffer buffer = Buffers.newDirectByteBuffer(width*height*4);
            while(buffer.hasRemaining()) {
                buffer.put((byte) 0xEA); buffer.put((byte) 0xEA); buffer.put((byte) 0xEA); buffer.put((byte) 0xEA);
            }
            buffer.rewind();
            texData = new TextureData(GLProfile.getGL2ES2(),
                   GL.GL_RGBA, width, height, 0,
                   GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, false, 
                   false, false, buffer, null);
        }
        fps = 30;
        bps = 0;
        totalFrames = 0;
        duration = 10*60*1000;
        acodec = "none";
        vcodec = "tga-dummy";
    }
    
    @Override
    protected void destroyTexImage(GL gl, TextureFrame imgTex) {
        super.destroyTexImage(gl, imgTex);
    }
    
    @Override
    protected TextureFrame createTexImage(GL gl, int idx, int[] tex) {
        Texture texture = super.createTexImageImpl(gl, idx, tex, false);
        if(null != texData) {
            texture.updateImage(gl, texData);
        }                      
        frame = new TextureFrame( texture );
        return frame;
    }
    
    private void validatePos() {
        boolean considerPausing = false;
        if( 0 > pos_ms) {
            pos_ms = 0;
            considerPausing = true;
        } else if ( pos_ms > getDuration() ) {
            pos_ms = getDuration();
            considerPausing = true;
        }
        if(considerPausing && state == State.Playing) {
            state = State.Paused;
        }
    }
}
