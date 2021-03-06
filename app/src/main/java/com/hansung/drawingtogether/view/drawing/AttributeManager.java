package com.hansung.drawingtogether.view.drawing;

import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import com.hansung.drawingtogether.R;
import com.hansung.drawingtogether.data.remote.model.Logger;
import com.hansung.drawingtogether.data.remote.model.MyLog;
import com.hansung.drawingtogether.databinding.FragmentDrawingBinding;

public enum AttributeManager {
    INSTANCE;

    private FragmentDrawingBinding binding;
    private DrawingEditor de = DrawingEditor.getInstance();
    private Logger logger = Logger.getInstance();

    private FrameLayout textEditingLayout; // 텍스트 편집 레이아웃

    private View.OnClickListener colorButtonClickListener;
    private SeekBar.OnSeekBarChangeListener sizeBarChangeListener;

    // DECLARE LISTENER
    public void setListener() {

        colorButtonClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MyLog.d("button", "color button click");

                String hexColor = ((Button)view).getText().toString();

                switch (de.getCurrentMode()) {
                    case DRAW:
                    case ERASE:
                    case SELECT:
                    case WARP:
                    case AUTODRAW:
                        de.setStrokeColor(hexColor);
                        de.setFillColor(hexColor);
                        break;
                    case TEXT:
                        if(de.getCurrentText() != null) { // 현재 선택 된 텍스트 색상 편집
                            Text text = de.getCurrentText();
                            text.getTextAttribute().setTextColor(hexColor);
                            text.setTextViewAttribute();
                            text.setEditTextAttribute();
                        }
                        break;
                }
                showCurrentColor(Color.parseColor(hexColor));
            }
        };

        sizeBarChangeListener = new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                if(progress % 10 != 0) return; // text size only 10, 20, 30

                switch(de.getCurrentMode()) {
                    case TEXT:
                        Text text = de.getCurrentText();

                        text.getTextAttribute().setTextSize(progress);
                        text.setEditTextAttribute(); // edit text 에 텍스트 크기 적용
                        break;
                    /*case DRAW:
                        de.setStrokeWidth(stepOfProgress);
                        break;*/ // todo nayeon - 펜 굵기
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                de.setTextSizeBeingChanged(true);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                de.setTextSizeBeingChanged(false);
            }
        };

        setPaletteButtonListener();
        ((SeekBar)textEditingLayout.findViewById(R.id.sizeBar)).setOnSeekBarChangeListener(sizeBarChangeListener);
    }


    public void showCurrentColor(int color) {
        binding.currentColorBtn.setBackgroundColor(color);
    }

    public void setPaletteButtonListener() {
        LinearLayout colorPaletteLayout =  binding.colorLayout;
        int paletteBtnCount = colorPaletteLayout.getChildCount();

        for(int i=0; i < paletteBtnCount; i++) {
            colorPaletteLayout.getChildAt(i).setOnClickListener(colorButtonClickListener);
        }
    }


    public static AttributeManager getInstance() { return INSTANCE; }

    public void setBinding(FragmentDrawingBinding binding) { this.binding = binding; }

    public void setTextEditingLayout(FrameLayout textEditingLayout) { this.textEditingLayout = textEditingLayout; }
}
