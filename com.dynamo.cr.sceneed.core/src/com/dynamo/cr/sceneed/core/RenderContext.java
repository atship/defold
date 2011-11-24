package com.dynamo.cr.sceneed.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.media.opengl.GL;
import javax.media.opengl.glu.GLU;
import javax.vecmath.Vector3d;

import org.eclipse.jface.viewers.IStructuredSelection;

public class RenderContext {
    private GL gl;
    private GLU glu;
    private Pass pass;
    private ArrayList<RenderData<? extends Node>> renderDataList;
    private Set<Node> selectedNodes = new HashSet<Node>();

    public enum Pass {
        BACKGROUND,
        OPAQUE,
        TRANSPARENT,
        OUTLINE,
        SELECTION,
    }

    public enum Status {
        SELECTED,
        NONE,
    }

    public enum Color {
        CONVEX_SHAPE,
    }

    @SuppressWarnings("unchecked")
    public RenderContext(GL gl, GLU glu, IStructuredSelection selection) {
        this.gl = gl;
        this.glu = glu;
        this.renderDataList = new ArrayList<RenderData<? extends Node>>(1024);
        this.selectedNodes.addAll(selection.toList());
    }

    public GL getGL() {
        return gl;
    }

    public GLU getGLU() {
        return glu;
    }

    public Pass getPass() {
        return pass;
    }

    public void setPass(Pass pass) {
        this.pass = pass;
    }

    public boolean shouldDraw(EnumSet<Pass> passes) {
        return passes.contains(pass);
    }

    public <T extends Node> void add(INodeRenderer<T> nodeRenderer, T node, Vector3d position, Object key) {
        renderDataList.add(new RenderData<T>(pass, nodeRenderer, node, position, key));
    }

    public List<RenderData<? extends Node>> getRenderData() {
        return renderDataList;
    }

    public void sort() {
        for (RenderData<? extends Node> renderData : renderDataList) {
            renderData.calculateKey();
        }

        Collections.sort(renderDataList);
    }

    // NOTE: We hard-code colors for now
    private static float OBJECT_COLOR[] = new float[] { 43.0f/255, 25.0f/255, 116.0f/255 };
    private static float SELECTED_COLOR[] = new float[] { 69.0f/255, 255.0f/255, 162.0f/255 };

    /**
     * Select color based on pass. If not default color is selected objectColor is returned
     * @param node node to render
     * @param objectColor object color to fallback to
     * @return color as float[4]
     */
    public float[] selectColor(Node node, float[] objectColor) {
        switch (pass) {
        case OPAQUE:
            return objectColor;
        case OUTLINE:
            if (selectedNodes.contains(node)) {
                return SELECTED_COLOR;
            } else {
                return OBJECT_COLOR;
            }
        case TRANSPARENT:
            return objectColor;
        }

        return objectColor;
    }

}
