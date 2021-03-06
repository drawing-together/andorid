package com.hansung.drawingtogether.view.drawing;

import android.graphics.Point;

import java.util.Vector;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DrawingTool {
    private Command command;
    private Vector<Integer> ids;

    public DrawingTool(Command command) {
        this.command = command;
    }

    public void doCommand(Point selectedPoint) {    //fixme grouping 이면 ArrayList<Point>이므로 수정 필요
        command.execute(selectedPoint);
    }
    public Vector<Integer> getIds() { return command.getIds(); }
}
