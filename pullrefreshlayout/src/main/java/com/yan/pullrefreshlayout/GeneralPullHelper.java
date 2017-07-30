package com.yan.pullrefreshlayout;

import android.content.Context;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

/**
 * support general view to pull refresh
 * Created by yan on 2017/6/29.
 */

class GeneralPullHelper {
    private static final String TAG = "GeneralPullHelper";
    private final PullRefreshLayout pullRefreshLayout;

    /**
     * is moving direct down
     */
    boolean isMovingDirectDown;

    /**
     * is last motion point y set
     */
    private boolean isLastMotionYSet;

    /**
     * is touch final direct down
     */
    private boolean isConsumedDragDown;

    /**
     * first touch point x
     */
    private float actionDownPointX;

    /**
     * first touch point y
     */
    private float actionDownPointY;

    /**
     * is touch direct down
     */
    int dragState;

    /**
     * motion event child consumed
     */
    private int[] childConsumed = new int[2];
    private int lastChildConsumedY;

    /**
     * active pointer id
     */
    private int activePointerId;

    /**
     * nested y offset
     */
    private int nestedYOffset;

    /**
     * last motion y
     */
    private int lastMotionY;

    /**
     * last touch y
     */
    private float lastTouchY;

    /**
     * default values
     */
    private final int minimumFlingVelocity;
    private final int maximumVelocity;
    private final float touchSlop;

    /**
     * scroll consumed offset
     */
    private int[] scrollConsumed = new int[2];
    private int[] scrollOffset = new int[2];

    /**
     * touchEvent velocityTracker
     */
    private VelocityTracker velocityTracker;

    /**
     * velocity y
     */
    private float velocityY;

    GeneralPullHelper(PullRefreshLayout pullRefreshLayout, Context context) {
        this.pullRefreshLayout = pullRefreshLayout;
        ViewConfiguration configuration = ViewConfiguration.get(context);
        minimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        maximumVelocity = configuration.getScaledMaximumFlingVelocity();
        touchSlop = configuration.getScaledTouchSlop();
    }

    void dellDirection(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            lastTouchY = event.getY();
            return;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float tempY = event.getY();
            if (tempY - lastTouchY > 0) {
                dragState = 1;
                isMovingDirectDown = true;
            } else if (tempY - lastTouchY < 0) {
                dragState = -1;
                isMovingDirectDown = false;
            }
            lastTouchY = tempY;
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            dragState = 0;
        }
    }

    boolean dispatchTouchEvent(MotionEvent ev, MotionEvent[] finalMotionEvent) {
        finalMotionEvent[0] = ev;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                onTouchEvent(ev);
                initVelocityTracker(ev);
                actionDownPointX = ev.getX();
                actionDownPointY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                velocityTrackerCompute(ev);
                float movingX = ev.getX() - actionDownPointX;
                float movingY = ev.getY() - actionDownPointY;
                if ((Math.sqrt(movingY * movingY + movingX * movingX) > touchSlop
                        && Math.abs(movingY) > Math.abs(movingX))
                        || pullRefreshLayout.moveDistance != 0) {
                    if (!isLastMotionYSet) {
                        isLastMotionYSet = true;
                        lastMotionY = (int) ev.getY();
                    }
                    onTouchEvent(ev);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                onTouchEvent(ev);
                cancelVelocityTracker();
                velocityY = 0;
                isLastMotionYSet = false;
                break;
        }
        return false;
    }

    boolean onInterceptTouchEvent(MotionEvent ev) {
        return pullRefreshLayout.moveDistance != 0;
    }

    boolean onTouchEvent(MotionEvent ev) {
        MotionEvent vtev = MotionEvent.obtain(ev);
        final int actionMasked = MotionEventCompat.getActionMasked(ev);
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            nestedYOffset = 0;
        }
        vtev.offsetLocation(0, nestedYOffset);
        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN: {
                lastMotionY = (int) ev.getY();
                activePointerId = ev.getPointerId(0);
                pullRefreshLayout.onNestedScrollAccepted(pullRefreshLayout.targetView, pullRefreshLayout.targetView, 2);
                pullRefreshLayout.onStartNestedScroll(pullRefreshLayout.targetView, pullRefreshLayout.targetView, 2);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (activePointerId != vtev.getPointerId(0)) {
                    lastMotionY = (int) vtev.getY();
                    activePointerId = vtev.getPointerId(0);
                }

                final int y = (int) ev.getY();
                int deltaY = lastMotionY - y;
                if (pullRefreshLayout.dispatchNestedPreScroll(0, deltaY, scrollConsumed, scrollOffset)) {
                    deltaY -= scrollConsumed[1];
                    vtev.offsetLocation(0, scrollOffset[1]);
                    nestedYOffset += scrollOffset[1];
                }
                lastMotionY = y - scrollOffset[1];
                final int oldY = pullRefreshLayout.targetView.getScrollY();

                if (deltaY < 0) {
                    isConsumedDragDown = true;
                } else if (deltaY > 0) {
                    isConsumedDragDown = false;
                }
                if ((pullRefreshLayout.moveDistance < 0 && isConsumedDragDown)
                        || (pullRefreshLayout.moveDistance > 0 && !isConsumedDragDown)) {
                    pullRefreshLayout.onNestedPreScroll(null, 0, deltaY, childConsumed);
                    vtev.offsetLocation(0, childConsumed[1] - lastChildConsumedY);
                    lastChildConsumedY = childConsumed[1];
                    return true;
                }

                final int scrolledDeltaY = pullRefreshLayout.targetView.getScrollY() - oldY;
                final int unconsumedY = deltaY - scrolledDeltaY;

                if (pullRefreshLayout.dispatchNestedScroll(0, 0
                        , (ScrollingUtil.canChildScrollUp(pullRefreshLayout.targetView)
                                && ScrollingUtil.canChildScrollDown(pullRefreshLayout.targetView)
                                && pullRefreshLayout.moveDistance == 0 ? deltaY : 0)
                        , ((isConsumedDragDown && !ScrollingUtil.canChildScrollUp(pullRefreshLayout.targetView))
                                || (!isConsumedDragDown && !ScrollingUtil.canChildScrollDown(pullRefreshLayout.targetView)))
                                ? unconsumedY : 0
                        , scrollOffset)) {
                    lastMotionY -= scrollOffset[1];
                    vtev.offsetLocation(0, scrollOffset[1]);
                    nestedYOffset += scrollOffset[1];

                }
                if ((isConsumedDragDown && !ScrollingUtil.canChildScrollUp(pullRefreshLayout.targetView))
                        || (!isConsumedDragDown && !ScrollingUtil.canChildScrollDown(pullRefreshLayout.targetView))) {
                    pullRefreshLayout.onNestedScroll(null, 0, 0, 0, scrollOffset[1] == 0 ? deltaY : 0);
                }

                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isLastMotionYSet) {
                    flingWithNestedDispatch(-(int) velocityY);
                }
                pullRefreshLayout.onStopNestedScroll(pullRefreshLayout.targetView);
                activePointerId = -1;
                childConsumed[0] = 0;
                childConsumed[1] = 0;
                lastChildConsumedY = 0;
                scrollOffset[1] = 0;
                break;
        }

        vtev.recycle();
        return true;
    }

    /**
     * velocityTracker dell
     *
     * @param ev MotionEvent
     */
    private void initVelocityTracker(MotionEvent ev) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(ev);
    }

    private void velocityTrackerCompute(MotionEvent ev) {
        try {
            velocityTracker.addMovement(ev);
            velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
            velocityY = velocityTracker.getYVelocity();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cancelVelocityTracker() {
        try {
            velocityTracker.clear();
            velocityTracker.recycle();
            velocityTracker = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void flingWithNestedDispatch(int velocityY) {
        if (!pullRefreshLayout.dispatchNestedPreFling(0, velocityY)) {
            pullRefreshLayout.dispatchNestedFling(0, velocityY, true);
            if ((Math.abs(velocityY) > minimumFlingVelocity)) {
                pullRefreshLayout.onNestedPreFling(pullRefreshLayout.targetView, 0, velocityY);
            }
        }
    }
}
