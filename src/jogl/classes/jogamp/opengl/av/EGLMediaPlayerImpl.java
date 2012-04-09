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

import java.nio.IntBuffer;

import javax.media.opengl.GL;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.util.texture.Texture;

import jogamp.opengl.egl.EGL;
import jogamp.opengl.egl.EGLContext;
import jogamp.opengl.egl.EGLDrawable;
import jogamp.opengl.egl.EGLExt;

public abstract class EGLMediaPlayerImpl extends GLMediaPlayerImpl {
    TextureType texType;
    boolean useKHRSync;
    
    public enum TextureType {
        GL(0), KHRImage(1); 
        
        public final int id;

        TextureType(int id){
            this.id = id;
        }
    }    
    
    public static class EGLTextureFrame extends TextureFrame {
        
        public EGLTextureFrame(Texture t, long khrImage, long khrSync) {
            super(t);
            this.image = khrImage;
            this.sync = khrSync;
        }
        
        public final long getImage() { return image; }        
        public final long getSync() { return sync; }
        
        public String toString() {
            return "EGLTextureFrame[" + texture + ", img "+ image + ", sync "+ sync+"]";
        }
        protected final long image;
        protected final long sync;
    }

    
    protected EGLMediaPlayerImpl() {
        this(TextureType.GL, false);
    }
    
    protected EGLMediaPlayerImpl(TextureType texType, boolean useKHRSync) {
        super();
        this.texType = texType;
        this.useKHRSync = useKHRSync;
    }

    @Override
    protected TextureFrame createTexImage(GL gl, int idx, int[] tex) {
        final Texture texture = super.createTexImageImpl(gl, idx, tex, true);
        final long image;
        final long sync;
        
        final EGLContext eglCtx = (EGLContext) gl.getContext();
        final EGLExt eglExt = eglCtx.getEGLExt();
        final EGLDrawable eglDrawable = (EGLDrawable) eglCtx.getGLDrawable();            
        int[] tmp = new int[1];
        IntBuffer nioTmp = Buffers.newDirectIntBuffer(1);
        
        if(TextureType.KHRImage == texType) {
            // create EGLImage from texture
            nioTmp.put(0, EGL.EGL_NONE);
            image =  eglExt.eglCreateImageKHR( eglDrawable.getDisplay(), eglCtx.getHandle(),
                                               EGLExt.EGL_GL_TEXTURE_2D_KHR,
                                               null /* buffer */, nioTmp);
            if (0==image) {
                throw new RuntimeException("EGLImage creation failed: "+EGL.eglGetError()+", ctx "+eglCtx+", tex "+tex[idx]+", err "+toHexString(EGL.eglGetError()));
            }
        } else {
            image = 0;
        }

        if(useKHRSync) {
            // Create sync object so that we can be sure that gl has finished
            // rendering the EGLImage texture before we tell OpenMAX to fill
            // it with a new frame.
            tmp[0] = EGL.EGL_NONE;
            sync = eglExt.eglCreateSyncKHR(eglDrawable.getDisplay(), EGLExt.EGL_SYNC_FENCE_KHR, tmp, 0);
            if (0==sync) {
                throw new RuntimeException("EGLSync creation failed: "+EGL.eglGetError()+", ctx "+eglCtx+", err "+toHexString(EGL.eglGetError()));
            }
        } else {
            sync = 0;
        }
        return new EGLTextureFrame(texture, image, sync);
    }
    
    @Override
    protected void destroyTexImage(GL gl, TextureFrame imgTex) {
        final EGLContext eglCtx = (EGLContext) gl.getContext();
        final EGLExt eglExt = eglCtx.getEGLExt();
        final EGLDrawable eglDrawable = (EGLDrawable) eglCtx.getGLDrawable();
        final EGLTextureFrame eglTex = (EGLTextureFrame) imgTex;
        
        if(0!=eglTex.getImage()) {
            eglExt.eglDestroyImageKHR(eglDrawable.getDisplay(), eglTex.getImage());
        }
        if(0!=eglTex.getSync()) {
            eglExt.eglDestroySyncKHR(eglDrawable.getDisplay(), eglTex.getSync());
        }
        super.destroyTexImage(gl, imgTex);
    }
}
