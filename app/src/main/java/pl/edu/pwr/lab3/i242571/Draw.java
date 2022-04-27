package pl.edu.pwr.lab3.i242571;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

public class Draw extends View {

    Paint boundaryPaint;
    Paint textPaint;
    Rect rectangle;
    String text;
    boolean hasLabel;

    public Draw(Context context, Rect rectangle, String text) {
        super(context);
        boundaryPaint = new Paint();
        boundaryPaint.setColor(Color.BLACK);
        boundaryPaint.setStrokeWidth(10f);
        boundaryPaint.setStyle(Paint.Style.STROKE);

        textPaint = new Paint();
        textPaint.setColor(Color.BLUE);
        textPaint.setTextSize(50f);
        textPaint.setStyle(Paint.Style.FILL);

        this.rectangle = rectangle;
        if (text != null && !text.isEmpty()){
            hasLabel = true;
            this.text = text;
        }
        else{
            hasLabel = false;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(hasLabel){
            canvas.drawText(text, rectangle.centerX(), rectangle.centerY(), textPaint);
        }
        canvas.drawRect(rectangle.left, rectangle.top, rectangle.right, rectangle.bottom, boundaryPaint);
    }
}
