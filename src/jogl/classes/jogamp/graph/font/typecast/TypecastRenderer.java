/**
 * Copyright 2011 JogAmp Community. All rights reserved.
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
package jogamp.graph.font.typecast;

import java.util.ArrayList;
import java.util.List;

import jogamp.graph.font.FontInt.GlyphInt;
import jogamp.graph.font.typecast.ot.OTGlyph;
import jogamp.graph.font.typecast.ot.Point;
import jogamp.graph.geom.plane.AffineTransform;
import jogamp.graph.geom.plane.Path2D;
import jogamp.graph.geom.plane.PathIterator;

import com.jogamp.graph.curve.OutlineShape;
import com.jogamp.graph.font.Font;
import com.jogamp.graph.font.Font.Glyph;
import com.jogamp.graph.geom.Vertex;
import com.jogamp.graph.geom.Vertex.Factory;

/**
 * Factory to build a {@link com.jogamp.graph.geom.Path2D Path2D} from
 * {@link jogamp.graph.font.typecast.ot.OTGlyph Glyph}s.
 */
public class TypecastRenderer {

    private static void getPaths(TypecastFont font,
            CharSequence string, float pixelSize, AffineTransform transform, Path2D[] p)
    {
        if (string == null) {
            return;
        }
        final Font.Metrics metrics = font.getMetrics();
        final float lineGap = metrics.getLineGap(pixelSize) ;
        final float ascent = metrics.getAscent(pixelSize) ;
        final float descent = metrics.getDescent(pixelSize) ;
        final float advanceY = lineGap - descent + ascent;
        final float scale = metrics.getScale(pixelSize);
        if (transform == null) {
            transform = new AffineTransform();
        }
        final AffineTransform t = new AffineTransform(transform.getFactory());

        final int len = string.length();

        float advanceTotal = 0;
        float y = 0;

        for (int i=0; i<len; i++)
        {
            p[i] = new Path2D();
            p[i].reset();
            final char character = string.charAt(i);
            if (character == '\n') {
                y += advanceY;
                advanceTotal = 0;
            } else if (character == ' ') {
                advanceTotal += font.getAdvanceWidth(Glyph.ID_SPACE, pixelSize);
            } else {
                final Glyph glyph = font.getGlyph(character);
                final Path2D gp = ((GlyphInt)glyph).getPath();
                t.setTransform(transform); // reset transform
                t.translate(advanceTotal, y);
                t.scale(scale, scale);
                p[i].append(gp.iterator(t), false);
                advanceTotal += glyph.getAdvance(pixelSize, true);
            }
        }
    }

    public static OutlineShape getOutlineShape(TypecastFont font, Glyph glyph, Factory<? extends Vertex> vertexFactory) {
        // FIXME: Remove Path2D
        Path2D path = ((GlyphInt)glyph).getPath();
        AffineTransform transform = new AffineTransform(vertexFactory);
        OutlineShape shape = new OutlineShape(vertexFactory);

        PathIterator iterator = path.iterator(transform);
        if(null != iterator){
            while(!iterator.isDone()){
                float[] coords = new float[6];
                int segmentType = iterator.currentSegment(coords);
                addPathVertexToOutline(shape, vertexFactory, coords, segmentType);
                iterator.next();
            }
        }
        return shape;
    }

    public static List<OutlineShape> getOutlineShapes(List<OutlineShape> shapes, TypecastFont font, CharSequence string, float pixelSize, AffineTransform transform, Factory<? extends Vertex> vertexFactory) {
        // FIXME: Remove Path2D
        // FIXME: Remove altogether
        Path2D[] paths = new Path2D[string.length()];
        getPaths(font, string, pixelSize, transform, paths);

        if(null == shapes) {
            shapes = new ArrayList<OutlineShape>();
        }
        final int numGlyps = paths.length;
        for (int index=0;index<numGlyps;index++) {
            if(paths[index] == null){
                continue;
            }
            OutlineShape shape = new OutlineShape(vertexFactory);
            shapes.add(shape);
            PathIterator iterator = paths[index].iterator(transform);
            if(null != iterator){
                while(!iterator.isDone()){
                    float[] coords = new float[6];
                    int segmentType = iterator.currentSegment(coords);
                    addPathVertexToOutline(shape, vertexFactory, coords, segmentType);
                    iterator.next();
                }
            }
        }
        return shapes;
    }
    private static void addPathVertexToOutline(OutlineShape shape, Factory<? extends Vertex> vertexFactory, float[] coords, int segmentType){
        switch(segmentType) {
        case PathIterator.SEG_MOVETO:
            shape.closeLastOutline();
            shape.addEmptyOutline();
            shape.addVertex(0, vertexFactory.create(coords, 0, 2, true));
            break;
        case PathIterator.SEG_LINETO:
            shape.addVertex(0, vertexFactory.create(coords, 0, 2, true));
            break;
        case PathIterator.SEG_QUADTO:
            shape.addVertex(0, vertexFactory.create(coords, 0, 2, false));
            shape.addVertex(0, vertexFactory.create(coords, 2, 2, true));
            break;
        case PathIterator.SEG_CUBICTO:
            shape.addVertex(0, vertexFactory.create(coords, 0, 2, false));
            shape.addVertex(0, vertexFactory.create(coords, 2, 2, false));
            shape.addVertex(0, vertexFactory.create(coords, 4, 2, true));
            break;
        case PathIterator.SEG_CLOSE:
            shape.closeLastOutline();
            break;
        default:
            throw new IllegalArgumentException("Unhandled Segment Type: "+segmentType);
        }
    }

    private static void addShapeMoveTo(final OutlineShape shape, Factory<? extends Vertex> vertexFactory, Point p1) {
        shape.closeLastOutline();
        shape.addEmptyOutline();
        shape.addVertex(0, vertexFactory.create(p1.x,  p1.y, 0, true)); // p1.onCurve));
        // shape.addVertex(0, vertexFactory.create(coords, 0, 2, true));
    }
    private static void addShapeLineTo(final OutlineShape shape, Factory<? extends Vertex> vertexFactory, Point p1) {
        shape.addVertex(0, vertexFactory.create(p1.x,  p1.y, 0, true)); // p1.onCurve));
        // shape.addVertex(0, vertexFactory.create(coords, 0, 2, true));
    }
    private static void addShapeQuadTo(final OutlineShape shape, Factory<? extends Vertex> vertexFactory, Point p1, Point p2) {
        shape.addVertex(0, vertexFactory.create(p1.x,  p1.y, 0, false)); // p1.onCurve));
        shape.addVertex(0, vertexFactory.create(p2.x,  p2.y, 0, true)); // p2.onCurve));
        // shape.addVertex(0, vertexFactory.create(coords, 0, 2, false));
        // shape.addVertex(0, vertexFactory.create(coords, 2, 2, true));
    }
    private static void addShapeQuadTo(final OutlineShape shape, Factory<? extends Vertex> vertexFactory, Point p1, int p2x, int p2y) {
        shape.addVertex(0, vertexFactory.create(p1.x,  p1.y, 0, false)); // p1.onCurve));
        shape.addVertex(0, vertexFactory.create(p2x,  p2y, 0, true)); // p2.onCurve));
        // shape.addVertex(0, vertexFactory.create(coords, 0, 2, false));
        // shape.addVertex(0, vertexFactory.create(coords, 2, 2, true));
    }
    /**
    private static void addShapeCubicTo(final OutlineShape shape, Factory<? extends Vertex> vertexFactory, Point p1, Point p2, Point p3) {
        shape.addVertex(0, vertexFactory.create(p1.x,  p1.y, 0, false)); // p1.onCurve));
        shape.addVertex(0, vertexFactory.create(p2.x,  p2.y, 0, false)); // p2.onCurve));
        shape.addVertex(0, vertexFactory.create(p3.x,  p3.y, 0, true)); // p3.onCurve));
        // shape.addVertex(0, vertexFactory.create(coords, 0, 2, false));
        // shape.addVertex(0, vertexFactory.create(coords, 2, 2, false));
        // shape.addVertex(0, vertexFactory.create(coords, 4, 2, true));

    }
    private static void addShapeClose(final OutlineShape shape, Factory<? extends Vertex> vertexFactory) {
        shape.closeLastOutline();
    } */

    public static OutlineShape buildShape(OTGlyph glyph, Factory<? extends Vertex> vertexFactory) {

        if (glyph == null) {
            return null;
        }

        final OutlineShape shape = new OutlineShape(vertexFactory);

        // Iterate through all of the points in the glyph.  Each time we find a
        // contour end point, add the point range to the path.
        int startIndex = 0;
        int count = 0;
        for (int i = 0; i < glyph.getPointCount(); i++) {
            count++;
            if (glyph.getPoint(i).endOfContour) {
                {
                    int offset = 0;
                    while (offset < count) {
                        final Point point = glyph.getPoint(startIndex + offset%count);
                        final Point point_plus1 = glyph.getPoint(startIndex + (offset+1)%count);
                        final Point point_plus2 = glyph.getPoint(startIndex + (offset+2)%count);
                        if(offset == 0)
                        {
                            addShapeMoveTo(shape, vertexFactory, point);
                            // gp.moveTo(point.x, point.y);
                        }

                        if (point.onCurve) {
                            if (point_plus1.onCurve) {
                                // s = new Line2D.Float(point.x, point.y, point_plus1.x, point_plus1.y);
                                addShapeLineTo(shape, vertexFactory, point_plus1);
                                // gp.lineTo( point_plus1.x, point_plus1.y );
                                offset++;
                            } else {
                                if (point_plus2.onCurve) {
                                    // s = new QuadCurve2D.Float( point.x, point.y, point_plus1.x, point_plus1.y, point_plus2.x, point_plus2.y);
                                    addShapeQuadTo(shape, vertexFactory, point_plus1, point_plus2);
                                    // gp.quadTo(point_plus1.x, point_plus1.y, point_plus2.x, point_plus2.y);
                                    offset+=2;
                                } else {
                                    // s = new QuadCurve2D.Float(point.x,point.y,point_plus1.x,point_plus1.y,
                                    //                           midValue(point_plus1.x, point_plus2.x), midValue(point_plus1.y, point_plus2.y));
                                    addShapeQuadTo(shape, vertexFactory, point_plus1, midValue(point_plus1.x, point_plus2.x), midValue(point_plus1.y, point_plus2.y));
                                    // gp.quadTo(point_plus1.x, point_plus1.y, midValue(point_plus1.x, point_plus2.x), midValue(point_plus1.y, point_plus2.y));
                                    offset+=2;
                                }
                            }
                        } else {
                            if (point_plus1.onCurve) {
                                // s = new QuadCurve2D.Float(midValue(point_minus1.x, point.x), midValue(point_minus1.y, point.y),
                                //                           point.x, point.y, point_plus1.x, point_plus1.y);
                                //gp.curve3(point_plus1.x, point_plus1.y, point.x, point.y);
                                addShapeQuadTo(shape, vertexFactory, point, point_plus1);
                                // gp.quadTo(point.x, point.y, point_plus1.x, point_plus1.y);
                                offset++;

                            } else {
                                // s = new QuadCurve2D.Float(midValue(point_minus1.x, point.x), midValue(point_minus1.y, point.y), point.x, point.y,
                                //                           midValue(point.x, point_plus1.x), midValue(point.y, point_plus1.y));
                                //gp.curve3(midValue(point.x, point_plus1.x), midValue(point.y, point_plus1.y), point.x, point.y);
                                addShapeQuadTo(shape, vertexFactory, point, midValue(point.x, point_plus1.x), midValue(point.y, point_plus1.y));
                                // gp.quadTo(point.x, point.y, midValue(point.x, point_plus1.x), midValue(point.y, point_plus1.y));
                                offset++;
                            }
                        }
                    }

                }
                startIndex = i + 1;
                count = 0;
            }
        }
        shape.closeLastOutline();
        return shape;
    }

    /**
     * Build a {@link com.jogamp.graph.geom.Path2D Path2D} from a
     * {@link jogamp.graph.font.typecast.ot.OTGlyph Glyph}.  This glyph path can then
     * be transformed and rendered.
     */
    public static Path2D buildPath(OTGlyph glyph) {

        if (glyph == null) {
            return null;
        }

        Path2D glyphPath = new Path2D();

        // Iterate through all of the points in the glyph.  Each time we find a
        // contour end point, add the point range to the path.
        int firstIndex = 0;
        int count = 0;
        for (int i = 0; i < glyph.getPointCount(); i++) {
            count++;
            if (glyph.getPoint(i).endOfContour) {
                addContourToPath(glyphPath, glyph, firstIndex, count);
                firstIndex = i + 1;
                count = 0;
            }
        }
        return glyphPath;
    }

    private static void addContourToPath(Path2D gp, OTGlyph glyph, int startIndex, int count) {
        int offset = 0;
        while (offset < count) {
            Point point = glyph.getPoint(startIndex + offset%count);
            Point point_plus1 = glyph.getPoint(startIndex + (offset+1)%count);
            Point point_plus2 = glyph.getPoint(startIndex + (offset+2)%count);
            if(offset == 0)
            {
                gp.moveTo(point.x, point.y);
            }

            if (point.onCurve) {
                if (point_plus1.onCurve) {
                    // s = new Line2D.Float(point.x, point.y, point_plus1.x, point_plus1.y);
                    gp.lineTo( point_plus1.x, point_plus1.y );
                    offset++;
                } else {
                    if (point_plus2.onCurve) {
                        // s = new QuadCurve2D.Float( point.x, point.y, point_plus1.x, point_plus1.y, point_plus2.x, point_plus2.y);
                        gp.quadTo(point_plus1.x, point_plus1.y, point_plus2.x, point_plus2.y);
                        offset+=2;
                    } else {
                        // s = new QuadCurve2D.Float(point.x,point.y,point_plus1.x,point_plus1.y,
                        //                           midValue(point_plus1.x, point_plus2.x), midValue(point_plus1.y, point_plus2.y));
                        gp.quadTo(point_plus1.x, point_plus1.y, midValue(point_plus1.x, point_plus2.x), midValue(point_plus1.y, point_plus2.y));
                        offset+=2;
                    }
                }
            } else {
                if (point_plus1.onCurve) {
                    // s = new QuadCurve2D.Float(midValue(point_minus1.x, point.x), midValue(point_minus1.y, point.y),
                    //                           point.x, point.y, point_plus1.x, point_plus1.y);
                    //gp.curve3(point_plus1.x, point_plus1.y, point.x, point.y);
                    gp.quadTo(point.x, point.y, point_plus1.x, point_plus1.y);
                    offset++;

                } else {
                    // s = new QuadCurve2D.Float(midValue(point_minus1.x, point.x), midValue(point_minus1.y, point.y), point.x, point.y,
                    //                           midValue(point.x, point_plus1.x), midValue(point.y, point_plus1.y));
                    //gp.curve3(midValue(point.x, point_plus1.x), midValue(point.y, point_plus1.y), point.x, point.y);
                    gp.quadTo(point.x, point.y, midValue(point.x, point_plus1.x), midValue(point.y, point_plus1.y));
                    offset++;
                }
            }
        }
    }

    private static int midValue(int a, int b) {
        return a + (b - a)/2;
    }
}
