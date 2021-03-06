package com.hansung.drawingtogether.view.drawing;


import android.graphics.Point;

import com.hansung.drawingtogether.data.remote.model.MyLog;

import java.util.Vector;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DrawingItem {
    private Mode mode;
    private Vector<DrawingComponent> components;
    private TextMode textMode;
    private TextAttribute textAttribute;
    private Point movePoint;
    private DrawingComponent component;
    //private ArrayList<Point> drawingBoardItem;
    //private Bitmap bitmap;

    //만약 dc[]에 있는 dc면, 속성변경
    //속성변경이면 dc[]의 dc빼고, di의 dc로 바꾸고 & db[]도 갱신한 뒤 redraw

    public DrawingItem(Mode mode, DrawingComponent component) {
        this.mode = mode;
        this.components = new Vector<>();
        this.components.add(component);
        //this.bitmap = bitmap.copy(bitmap.getConfig(), true);//Bitmap.createBitmap(bitmap);
    }

    public DrawingItem(Mode mode, Vector<DrawingComponent> components) {
        this.mode = mode;
        this.components = new Vector<>();
        this.components.addAll(components);
        //this.bitmap = bitmap.copy(bitmap.getConfig(), true);//Bitmap.createBitmap(bitmap);
    }

    public DrawingItem(Mode mode, DrawingComponent component, Point movePoint) {
        this.mode = mode;
        this.component = component;
        this.movePoint = movePoint;
    }


//    public DrawingItem(TextMode textMode, TextAttribute textAttribute) {
//        this.textMode = textMode;
//        this.textAttribute = new TextAttribute(textAttribute);
//        MyLog.i("drawing", "preText=" + textAttribute.getPreText() + ", text=" + textAttribute.getText());
//    }


    public Vector<DrawingComponent> getComponents() {
        MyLog.i("isSelected", "set DrawingItem components selected false");
        for(DrawingComponent comp : components) {
            comp.setSelected(false);
        }
        return components;
    }

    public DrawingComponent getComponent() {
        MyLog.i("isSelected", "set DrawingItem components selected false");
        component.setSelected(false);
        return component;
    }

}
