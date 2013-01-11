/*
 * Copyright (c) 2003 Sun Microsystems, Inc. All Rights Reserved.
 * Copyright (c) 2010 JogAmp Community. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * - Redistribution of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * 
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 * 
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES,
 * INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN
 * MICROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR
 * ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR
 * DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE
 * DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
 * ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF
 * SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 * 
 * You acknowledge that this software is not designed or intended for use
 * in the design, construction, operation or maintenance of any nuclear
 * facility.
 * 
 * Sun gratefully acknowledges that this software was originally authored
 * and developed by Kenneth Bradley Russell and Christopher John Kline.
 */

package com.jogamp.opengl.util;

import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLException;

/** <P> An Animator can be attached to one or more {@link
    GLAutoDrawable}s to drive their display() methods in a loop. </P>

    <P> The Animator class creates a background thread in which the
    calls to <code>display()</code> are performed. After each drawable
    has been redrawn, a brief pause is performed to avoid swamping the
    CPU, unless {@link #setRunAsFastAsPossible} has been called.  </P>

 * <p>
 * The Animator execution thread does not run as a daemon thread,
 * so it is able to keep an application from terminating.<br>
 * Call {@link #stop() } to terminate the animation and it's execution thread.
 * </p>
 */
public class Animator extends AnimatorBase {    
    protected ThreadGroup threadGroup;
    private Runnable runnable;
    private boolean runAsFastAsPossible;
    protected boolean isAnimating;
    protected boolean pauseIssued;
    protected volatile boolean stopIssued;

    /**
     * Creates a new, empty Animator.
     */
    public Animator() {
        super();
        if(DEBUG) {
            System.err.println("Animator created");
        }
    }

    /** 
     * Creates a new Animator w/ an associated ThreadGroup.
     */
    public Animator(ThreadGroup tg) {
        super();
        setThreadGroup(tg);
        if(DEBUG) {
            System.err.println("Animator created, ThreadGroup: "+threadGroup);
        }
    }

    /** 
     * Creates a new Animator for a particular drawable.
     */
    public Animator(GLAutoDrawable drawable) {
        super();
        add(drawable);
        if(DEBUG) {
            System.err.println("Animator created, w/ "+drawable);
        }
    }

    /** 
     * Creates a new Animator w/ an associated ThreadGroup for a particular drawable.
     */
    public Animator(ThreadGroup tg, GLAutoDrawable drawable) {
        super();
        setThreadGroup(tg);
        add(drawable);
        if(DEBUG) {
            System.err.println("Animator created, ThreadGroup: "+threadGroup+" and "+drawable);
        }
    }

    protected String getBaseName(String prefix) {
        return prefix + "Animator" ;
    }

    /**
     * Sets a flag in this Animator indicating that it is to run as
     * fast as possible. By default there is a brief pause in the
     * animation loop which prevents the CPU from getting swamped.
     * This method may not have an effect on subclasses.
     */
    public final void setRunAsFastAsPossible(boolean runFast) {
        stateSync.lock();
        try {
            runAsFastAsPossible = runFast;
        } finally {
            stateSync.unlock();
        }
    }
    
    private final void setIsAnimatingSynced(boolean v) {
        stateSync.lock();
        try {
            isAnimating = v;
        } finally {
            stateSync.unlock();
        }
    }

    class MainLoop implements Runnable {
        public String toString() {
            return "[started "+isStartedImpl()+", animating "+isAnimatingImpl()+", paused "+isPausedImpl()+", drawable "+drawables.size()+", drawablesEmpty "+drawablesEmpty+"]";
        }

        public void run() {
            try {
                if(DEBUG) {
                    System.err.println("Animator start:" + Thread.currentThread() + ": " + toString());
                }
                fpsCounter.resetFPSCounter();
                animThread = Thread.currentThread();
                setIsAnimatingSynced(false); // barrier
                // 'waitForStartedCondition' wake-up is handled below!

                while (!stopIssued) {
                    synchronized (Animator.this) {
                        // Pause; Also don't consume CPU unless there is work to be done and not paused
                        boolean ectCleared = false;
                        while (!stopIssued && (pauseIssued || drawablesEmpty)) {
                            boolean wasPaused = pauseIssued;
                            if (DEBUG) {
                                System.err.println("Animator pause:" + Thread.currentThread() + ": " + toString());
                            }
                            if ( exclusiveContext && !drawablesEmpty && !ectCleared ) {
                                ectCleared = true;
                                setDrawablesExclCtxState(false);
                                display(); // propagate exclusive change!
                            }
                            setIsAnimatingSynced(false); // barrier
                            Animator.this.notifyAll();
                            try {
                                Animator.this.wait();
                            } catch (InterruptedException e) {
                            }
                            if (wasPaused) {
                                // resume from pause -> reset counter
                                fpsCounter.resetFPSCounter();
                                if (DEBUG) {
                                    System.err.println("Animator resume:" + Thread.currentThread() + ": " + toString());
                                }
                            }
                        }
                        if (!stopIssued && !isAnimating) {
                            // Wakes up 'waitForStartedCondition' sync
                            // - and - 
                            // Resume from pause or drawablesEmpty,
                            // implies !pauseIssued and !drawablesEmpty
                            setIsAnimatingSynced(true); // barrier
                            setDrawablesExclCtxState(exclusiveContext);
                            Animator.this.notifyAll();
                        }
                    } // sync Animator.this
                    if (!stopIssued) {
                        display();
                    }
                    if (!stopIssued && !runAsFastAsPossible) {
                        // Avoid swamping the CPU
                        Thread.yield();
                    }
                }
            } finally {
                if( exclusiveContext && !drawablesEmpty ) {
                    setDrawablesExclCtxState(false);
                    display(); // propagate exclusive change!
                }
                synchronized (Animator.this) {
                    if(DEBUG) {
                        System.err.println("Animator stop " + Thread.currentThread() + ": " + toString());
                    }
                    stopIssued = false;
                    pauseIssued = false;
                    animThread = null;
                    setIsAnimatingSynced(false); // barrier
                    Animator.this.notifyAll();
                }
            }
        }
    }

    private final boolean isAnimatingImpl() {
        return animThread != null && isAnimating ;
    }
    public final boolean isAnimating() {
        stateSync.lock();
        try {
            return animThread != null && isAnimating ;
        } finally {
            stateSync.unlock();
        }
    }

    private final boolean isPausedImpl() {
        return animThread != null && pauseIssued ;
    }
    public final boolean isPaused() {
        stateSync.lock();
        try {
            return animThread != null && pauseIssued ;
        } finally {
            stateSync.unlock();
        }
    }

    /**
     * Set a {@link ThreadGroup} for the {@link #getThread() animation thread}.
     * 
     * @param tg the {@link ThreadGroup}
     * @throws GLException if the animator has already been started
     */
    public synchronized void setThreadGroup(ThreadGroup tg) throws GLException {
        if ( isStartedImpl() ) {
            throw new GLException("Animator already started.");
        }
        threadGroup = tg;
    }
    
    public synchronized boolean start() {
        if ( isStartedImpl() ) {
            return false;
        }
        if (runnable == null) {
            runnable = new MainLoop();
        }
        fpsCounter.resetFPSCounter();
        String threadName = Thread.currentThread().getName()+"-"+baseName;
        Thread thread;
        if(null==threadGroup) {
            thread = new Thread(runnable, threadName);
        } else {
            thread = new Thread(threadGroup, runnable, threadName);
        }
        thread.setDaemon(false); // force to be non daemon, regardless of parent thread 
        if(DEBUG) {
            final Thread ct = Thread.currentThread();
            System.err.println("Animator "+ct.getName()+"[daemon "+ct.isDaemon()+"]: starting "+thread.getName()+"[daemon "+thread.isDaemon()+"]");
        }
        thread.start();
        return finishLifecycleAction(waitForStartedCondition, 0);
    }
    private final Condition waitForStartedCondition = new Condition() {
        public boolean eval() {
            return !isStartedImpl() || (!drawablesEmpty && !isAnimating) ;
        } };    

    public synchronized boolean stop() {
        if ( !isStartedImpl() ) {
            return false;
        }
        stopIssued = true;
        return finishLifecycleAction(waitForStoppedCondition, 0);
    }
    private final Condition waitForStoppedCondition = new Condition() {
        public boolean eval() {
            return isStartedImpl();
        } };

    public synchronized boolean pause() {
        if ( !isStartedImpl() || pauseIssued ) {
            return false;
        }
        pauseIssued = true;
        return finishLifecycleAction(waitForPausedCondition, 0);
    }
    private final Condition waitForPausedCondition = new Condition() {
        public boolean eval() {
            // end waiting if stopped as well
            return isAnimating && isStartedImpl();
        } };

    public synchronized boolean resume() {
        if ( !isStartedImpl() || !pauseIssued ) {
            return false;
        }
        pauseIssued = false;
        return finishLifecycleAction(waitForResumeCondition, 0);
    }
    private final Condition waitForResumeCondition = new Condition() {
        public boolean eval() {
            // end waiting if stopped as well
            return !drawablesEmpty && !isAnimating && isStartedImpl();
        } };
}
