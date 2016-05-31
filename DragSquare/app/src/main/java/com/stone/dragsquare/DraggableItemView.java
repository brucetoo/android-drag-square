package com.stone.dragsquare;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.facebook.rebound.SimpleSpringListener;
import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringConfig;
import com.facebook.rebound.SpringSystem;
import com.nostra13.universalimageloader.core.ImageLoader;

/**
 * Created by xmuSistone on 2016/5/23.
 */
public class DraggableItemView extends FrameLayout {

    public static final int STATUS_LEFT_TOP = 0;
    public static final int STATUS_RIGHT_TOP = 1;
    public static final int STATUS_RIGHT_MIDDLE = 2;
    public static final int STATUS_RIGHT_BOTTOM = 3;
    public static final int STATUS_MIDDLE_BOTTOM = 4;
    public static final int STATUS_LEFT_BOTTOM = 5;

    public static final int SCALE_LEVEL_1 = 1; // 最大状态，缩放比例是100%
    public static final int SCALE_LEVEL_2 = 2; // 中间状态，缩放比例scaleRate
    public static final int SCALE_LEVEL_3 = 3; // 最小状态，缩放比例是smallerRate

    private ImageView imageView;
    private View maskView;
    private int status;
    private float scaleRate = 0.5f;
    private float smallerRate = scaleRate * 0.9f;
    private Spring springX, springY;
    private ObjectAnimator scaleAnimator;
    private boolean hasSetCurrentSpringValue = false;
    private DraggableSquareView parentView;
    private SpringConfig springConfigCommon = SpringConfig.fromOrigamiTensionAndFriction(140, 7);
    private SpringConfig springConfigDragging = SpringConfig.fromOrigamiTensionAndFriction(300, 6);
    private int anchorX = Integer.MIN_VALUE, anchorY = Integer.MIN_VALUE;
    private View.OnClickListener dialogListener;

    private String imagePath;
    private View addView;

    public DraggableItemView(Context context) {
        this(context, null);
    }

    public DraggableItemView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DraggableItemView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        //view 可以通过inflate来设置view的内容
        inflate(context, R.layout.drag_item, this);
        imageView = (ImageView) findViewById(R.id.drag_item_imageview);
        maskView = findViewById(R.id.drag_item_mask_view);
        addView = findViewById(R.id.add_view);

        dialogListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.getId() == R.id.pick_image) {
                    // 从相册选择图片
                    pickImage();
                } else {
                    // 删除
                    imagePath = null;
                    imageView.setImageBitmap(null);
                    addView.setVisibility(View.VISIBLE);
                    parentView.onDedeleteImage(DraggableItemView.this);
                }
            }
        };

        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                //onGlobalLayout 返回会回调两次 所以要用 @param hasSetCurrentSpringValue 来保证只执行一次
                if (!hasSetCurrentSpringValue) {
                    adjustImageView();
                    hasSetCurrentSpringValue = true;
                }
            }
        });

        maskView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isDraggable()) {
                    pickImage();
                } else {
                    CustDialog dialog = new CustDialog(getContext());
                    dialog.setClickListener(dialogListener);
                    dialog.show();
                }
            }
        });

        initSpring();
    }

    private void pickImage() {
        MainActivity mainActivity = (MainActivity) getContext();
        mainActivity.pickImage(status, isDraggable());
    }

    /**
     * 初始化Spring相关
     */
    private void initSpring() {
        SpringSystem mSpringSystem = SpringSystem.create();
        springX = mSpringSystem.createSpring();
        springY = mSpringSystem.createSpring();

        springX.addListener(new SimpleSpringListener() {
            @Override
            public void onSpringUpdate(Spring spring) {
                int xPos = (int) spring.getCurrentValue();
                setScreenX(xPos);
            }
        });

        springY.addListener(new SimpleSpringListener() {
            @Override
            public void onSpringUpdate(Spring spring) {
                int yPos = (int) spring.getCurrentValue();
                setScreenY(yPos);
            }
        });

        springX.setSpringConfig(springConfigCommon);
        springY.setSpringConfig(springConfigCommon);
    }

    /**
     * 调整ImageView的宽度和高度各为FrameLayout的一半
     */
    private void adjustImageView() {
        if (status != STATUS_LEFT_TOP) {
            imageView.setScaleX(scaleRate);
            imageView.setScaleY(scaleRate);

            maskView.setScaleX(scaleRate);
            maskView.setScaleY(scaleRate);
        }

        //其实我始终没搞懂这个是用来干嘛的 去掉后的效果是一样的
//        setCurrentSpringPos(getLeft(), getTop());
    }

    /**
     * 设置每个view的缩放比例
     * @param scaleRate
     */
    public void setScaleRate(float scaleRate) {
        this.scaleRate = scaleRate;
        this.smallerRate = scaleRate * 0.9f;
    }

    /**
     * 从一个状态切换到另一个状态
     */
    public void switchPosition(int toStatus) {
        if (this.status == toStatus) {
            throw new RuntimeException("程序错乱");
        }

        if (toStatus == STATUS_LEFT_TOP) {
            scaleSize(SCALE_LEVEL_1);
        } else if (this.status == STATUS_LEFT_TOP) {
            scaleSize(SCALE_LEVEL_2);
        }

        this.status = toStatus;
        //将toStatus 移动到 fromStatus的 point
        Point point = parentView.getOriginViewPos(status);
        animTo(point.x, point.y);
    }

    public void animTo(int xPos, int yPos) {
        //Set the spring in motion; moving from 0 to xPos
        springX.setEndValue(xPos);
        springY.setEndValue(yPos);
    }

    /**
     * 设置缩放的大小 通过动画来渐变
     */
    public void scaleSize(int scaleLevel) {
        float rate = scaleRate;
        if (scaleLevel == SCALE_LEVEL_1) {
            rate = 1.0f;
        } else if (scaleLevel == SCALE_LEVEL_3) {
            rate = smallerRate;
        }

        if (scaleAnimator != null && scaleAnimator.isRunning()) {
            scaleAnimator.cancel();
        }

        scaleAnimator = ObjectAnimator
                .ofFloat(this, "custScale", imageView.getScaleX(), rate)
                .setDuration(200);
        scaleAnimator.setInterpolator(new DecelerateInterpolator());
        scaleAnimator.start();
    }

    public void saveAnchorInfo(int downX, int downY) {
        int halfSide = getMeasuredWidth() / 2;
        //这个地方容易误解  anchor 其实是在左上角 而不是center
        anchorX = downX - halfSide;
        anchorY = downY - halfSide;
    }

    /**
     * 真正开始动画
     */
    public void startAnchorAnimation() {
        if (anchorX == Integer.MIN_VALUE || anchorY == Integer.MIN_VALUE) {
            return;
        }

        springX.setOvershootClampingEnabled(true);
        springY.setOvershootClampingEnabled(true);
        springX.setSpringConfig(springConfigDragging);
        springY.setSpringConfig(springConfigDragging);
        animTo(anchorX, anchorY);
        scaleSize(DraggableItemView.SCALE_LEVEL_3);
    }

    public void setScreenX(int screenX) {
        //screenX 表示 0 - 目的点view的 x坐标,需要减去 当前正在移动view的left坐标 才是正在的offset位置
        //拖动View的时候 getLeft 会一直变,所以这里的作用相当于纠正位置
        Log.e("setScreenX", "offsetX = " + (screenX - getLeft()));
        this.offsetLeftAndRight(screenX - getLeft());
    }

    public void setScreenY(int screenY) {
        Log.e("setScreenY", "offsetY = " + (screenY - getTop()));
        this.offsetTopAndBottom(screenY - getTop());
    }

    /**
     * 设置当前spring位置
     */
    private void setCurrentSpringPos(int xPos, int yPos) {
        springX.setCurrentValue(xPos);
        springY.setCurrentValue(yPos);
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public void setParentView(DraggableSquareView parentView) {
        this.parentView = parentView;
    }


    /**
     * 处理放开draggingView的时候需要落在的位置点
     */
    public void onDragRelease() {
        if (status == DraggableItemView.STATUS_LEFT_TOP) {
            scaleSize(DraggableItemView.SCALE_LEVEL_1);
        } else {
            scaleSize(DraggableItemView.SCALE_LEVEL_2);
        }

        springX.setOvershootClampingEnabled(true);
        springY.setOvershootClampingEnabled(true);
        springX.setSpringConfig(springConfigCommon);
        springY.setSpringConfig(springConfigCommon);

        Point point = parentView.getOriginViewPos(status);
//        setCurrentSpringPos(getLeft(), getTop());//其实这个根本不需要
        animTo(point.x, point.y);
    }

    public void fillImageView(String imagePath) {
        this.imagePath = imagePath;
        addView.setVisibility(View.GONE);
        ImageLoader.getInstance().displayImage(imagePath, imageView);
    }

    // 以下两个get、set方法是为自定义的属性动画CustScale服务，不能删
    public void setCustScale(float scale) {
        imageView.setScaleX(scale);
        imageView.setScaleY(scale);

        maskView.setScaleX(scale);
        maskView.setScaleY(scale);
    }

    public float getCustScale() {
        return imageView.getScaleX();
    }

    public void updateEndSpringX(int dx) {
        springX.setEndValue(springX.getEndValue() + dx);
    }

    public void updateEndSpringY(int dy) {
        springY.setEndValue(springY.getEndValue() + dy);
    }

    public boolean isDraggable() {
        return imagePath != null;
    }
}

