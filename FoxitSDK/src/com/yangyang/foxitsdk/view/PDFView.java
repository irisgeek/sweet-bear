package com.yangyang.foxitsdk.view;

import java.util.ArrayList;

import FoxitEMBSDK.EMBJavaSupport;
import FoxitEMBSDK.EMBJavaSupport.PointF;
import FoxitEMBSDK.EMBJavaSupport.RectangleF;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;

import com.yangyang.foxitsdk.exception.memoryException;
import com.yangyang.foxitsdk.service.YYPDFDoc;
import com.yangyang.foxitsdk.service.YYPDFDoc.AnnotationType;
import com.yangyang.foxitsdk.service.YYPDFDoc.Mode;
import com.yangyang.foxitsdk.util.ZoomStatus;

public class PDFView extends SurfaceView implements Callback, Runnable,
		OnGestureListener, OnDoubleTapListener {

	private SurfaceHolder Holder;
	private Rect rect = null;
	private Bitmap pdfbmp = null;
	private Bitmap dirtydib = null;
	private Bitmap CurrentBitmap = null;
	private int nDisplayWidth = 0;
	private int nDisplayHeight = 0;
	private Thread mViewThread = null;
	private ArrayList<CPSIAction> mPSIActionList;
	private YYPDFDoc pDoc;
	GestureDetector detector;
	private ZoomStatus zoomStatus;
	private int nStartX = 0;
	private int nStartY = 0;
	private int nCurDisplayX = 0;
	private int nCurDisplayY = 0;
	private int leftBound, topBound;// 左边界，上边界
	private boolean scrolling = false;

	public class CPSIAction {
		public int nActionType;
		public float x;
		public float y;
		public float nPressures;
		public int flag;
	}

	private Mode mode = Mode.Read;// 操作类型
	private AnnotationType annotationType = AnnotationType.NONE;

	public PDFView(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
		this.init();
	}

	public PDFView(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.init();
	}

	public PDFView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		this.init();
	}

	private void init() {
		Holder = this.getHolder();
		Holder.addCallback(this);
		setFocusable(true);
		setFocusableInTouchMode(true);
		detector = new GestureDetector(this);
		detector.setOnDoubleTapListener(this);
		mPSIActionList = new ArrayList<CPSIAction>();
		mViewThread = new Thread(this);
		mViewThread.start();
	}

	/**
	 * 改变控件模式
	 * 
	 * @param mode
	 */
	public void changeMode(Mode mode) {
		this.mode = mode;
		EMBJavaSupport.FPDFFormFillUpdatForm(pDoc.getPDFFormHandler());
		// this.pDoc.updateMode(mode);
		this.showCurrentPage();
	}

	float baseValue, last_x, last_y;

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (this.mode == Mode.Read) {
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				baseValue = 0;
				last_x = event.getRawX();
				last_y = event.getRawY();
			} else if (event.getAction() == MotionEvent.ACTION_MOVE) {
				if (event.getPointerCount() == 2) {
					float x = event.getX(0) - event.getX(1);
					float y = event.getY(0) - event.getY(1);
					float value = (float) Math.sqrt(x * x + y * y);// 计算两点的距离
					if (baseValue == 0) {
						baseValue = value;
					} else {
						if (value - baseValue >= 10 || value - baseValue <= -10) {
							float scale = value / (baseValue * 20);// 当前两点间的距离除以手指落下时两点间的距离就是需要缩放的比例。
							if (value - baseValue < -10)
								scale = -scale;
							Log.i("pdfview", "zoom image:" + scale);
							zoomStatus.nextZoom(scale);
							baseValue = 0;
							showCurrentPage();
							return true;
						}
					}
					return true;
				}
			}
			if (this.detector != null)
				return this.detector.onTouchEvent(event);
		} else if (this.mode == Mode.Annotation) {
			RectangleF rect = EMBJavaSupport.instance.new RectangleF();
			rect.left = event.getX() - 10;
			rect.right = event.getX() + 10;
			rect.top = event.getY() - 10;
			rect.bottom = event.getY() + 10;
			rect.left = rect.left >= 0 ? rect.left : 0;
			rect.right = rect.right >= 0 ? rect.right : 0;
			rect.top = rect.top >= 0 ? rect.top : 0;
			rect.bottom = rect.bottom >= 0 ? rect.bottom : 0;
			this.addNote(AnnotationType.NOTE, rect);
			return true;

		} else if (this.mode == Mode.Form) {

			int actionType = event.getAction() & MotionEvent.ACTION_MASK;
			int actionId = event.getAction()
					& MotionEvent.ACTION_POINTER_ID_MASK;
			actionId = actionId >> 8;

			PointF point = new EMBJavaSupport().new PointF();
			point.x = event.getX();
			point.y = event.getY();
			EMBJavaSupport.FPDFPageDeviceToPagePointF(
					pDoc.getCurrentPageHandler(), 0, 0,
					zoomStatus.getDisplayWidth(),
					zoomStatus.getDisplayHeight(), 0, point);

			switch (actionType) {
			case MotionEvent.ACTION_MOVE://
				EMBJavaSupport.FPDFFormFillOnMouseMove(
						pDoc.getPDFFormHandler(), pDoc.getCurrentPageHandler(),
						0, point.x, point.y);
				break;
			case MotionEvent.ACTION_DOWN: //
				EMBJavaSupport.FPDFFormFillOnMouseMove(
						pDoc.getPDFFormHandler(), pDoc.getCurrentPageHandler(),
						0, point.x, point.y);
				EMBJavaSupport.FPDFFormFillOnLButtonDown(
						pDoc.getPDFFormHandler(), pDoc.getCurrentPageHandler(),
						0, point.x, point.y);
				break;
			case MotionEvent.ACTION_UP: //
				EMBJavaSupport.FPDFFormFillOnLButtonUp(
						pDoc.getPDFFormHandler(), pDoc.getCurrentPageHandler(),
						0, point.x, point.y);
				break;
			}
			return true;

		} else if (this.mode == Mode.PSI) {

		}
		return false;
	}

	private void addNote(AnnotationType annotationType, RectangleF rect) {
		switch (annotationType) {
		case NOTE:
			try {
				pDoc.addAnnot(annotationType, rect);
			} catch (memoryException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;
		default:
			break;
		}
	}

	public void InitView(YYPDFDoc pDoc, int pageWidth, int pageHeight,
			int displayWidth, int displayHeight) {
		this.pDoc = pDoc;
		this.nDisplayWidth = displayWidth;
		this.nDisplayHeight = displayHeight;
		this.zoomStatus = new ZoomStatus(pageWidth, pageHeight, displayWidth,
				displayHeight);
	}

	public int getCurrentPage() {
		return pDoc.getCurrentPage();
	}

	public int getCurrentPageHandler() {
		return pDoc.getCurrentPageHandler();
	}

	public int getPageCount() {
		return pDoc.getPageCounts();
	}

	public int getPageHandler(int pageNumber) {
		return pDoc.getPageHandler(pageNumber);
	}

	public void gotoPage(int pageNumber) {
		pDoc.gotoPage(pageNumber);
		this.showCurrentPage();
	}

	public void previousPage() {
		this.scrolling = false;
		pDoc.previoutPage();
		this.showCurrentPage();
	}

	public void nextPage() {
		this.scrolling = false;
		pDoc.nextPage();
		this.showCurrentPage();
	}

	public void showCurrentPage() {
		if (this.mode == Mode.Read)
			this.setPDFBitmap(pDoc.getPageBitmap(zoomStatus.getWidth(),
					zoomStatus.getHeight()));
		else if (this.mode == Mode.Form) {
			this.setPDFBitmap(pDoc.getPageBitmap(zoomStatus.getDisplayWidth(),
					zoomStatus.getDisplayHeight()));
		}
		this.OnDraw();
	}

	public void pause() {

	}

	public void resume() {

	}

	public YYPDFDoc getDoc() {
		return this.pDoc;
	}

	public void finalize() {
		if (pDoc != null)
			pDoc.close();
	}

	public synchronized void addAction(int nActionType, float x, float y,
			float nPressures, int flag) {
		CPSIAction action = new CPSIAction();
		action.nActionType = nActionType;
		action.x = x;
		action.y = y;
		action.nPressures = nPressures;
		action.flag = flag;

		mPSIActionList.add(action);
	}

	public synchronized CPSIAction getHeadAction() {
		CPSIAction action = null;
		if (mPSIActionList == null)
			return null;

		int nSize = mPSIActionList.size();
		if (nSize <= 0)
			return null;

		action = mPSIActionList.remove(0);
		return action;
	}

	public void setDirtyRect(int left, int top, int right, int bottom) {
		if (rect == null) {
			rect = new Rect();
		}
		rect.left = left;
		rect.top = top;
		rect.right = right;
		rect.bottom = bottom;
	}

	public void setDirtyRect(Rect rc) {
		rect = rc;
	}

	public void setDirtyBitmap(Bitmap dib) {
		dirtydib = dib;
	}

	public void SetMartix(float CurrentoffsetX, float CurrentoffsetY) {
		if (pdfbmp == null)
			return;
		nStartX = nCurDisplayX - (int) CurrentoffsetX;
		nStartY = nCurDisplayY - (int) CurrentoffsetY;
		if (nStartX > (pdfbmp.getWidth() - nDisplayWidth)) {
			if (nStartX > (pdfbmp.getWidth() - nDisplayWidth) + 50) {
				nStartX = nStartY = nCurDisplayX = nCurDisplayY = 0;
				this.nextPage();
				return;
			}
			nStartX = (int) (pdfbmp.getWidth() - nDisplayWidth);
		}
		if (nStartX < 0) {
			if (nStartX < -50) {
				nStartX = nStartY = nCurDisplayX = nCurDisplayY = 0;
				this.previousPage();
				return;
			}
			nStartX = 0;
		}
		if (nStartY > (pdfbmp.getHeight() - nDisplayHeight))
			nStartY = (int) (pdfbmp.getHeight() - nDisplayHeight);
		if (nStartY < 0)
			nStartY = 0;
		nCurDisplayX = nStartX;
		nCurDisplayY = nStartY;
		Log.i("pdfview", "startx:" + nStartX + ",starty:" + nStartY + "width:"
				+ (pdfbmp.getWidth() - nStartX));
		CurrentBitmap = Bitmap.createBitmap(pdfbmp, nStartX, nStartY,
				pdfbmp.getWidth() - nStartX, pdfbmp.getHeight() - nStartY);
		this.OnDraw();
	}

	public void setPDFBitmap(Bitmap dib) {
		if (pdfbmp != null) {
			pdfbmp.recycle();
			pdfbmp = null;
		}
		if (dirtydib != null) {
			dirtydib.recycle();
			dirtydib = null;
		}
		pdfbmp = dib;
		CurrentBitmap = dib;
	}

	public void OnDraw() {
		Canvas canvas = null;
		try {
			if (rect == null) {
				canvas = Holder.lockCanvas();
			} else {
				canvas = Holder.lockCanvas(rect);
			}
			if (canvas == null)
				return;
			Paint mPaint = new Paint();
			if (pdfbmp != null && rect == null) {
				Matrix mt = new Matrix();
				mt.postRotate(0, nDisplayWidth, nDisplayHeight);
				mt.postTranslate(0, 0);
				canvas.drawColor(0xffffff);
				canvas.drawBitmap(this.CurrentBitmap, mt, mPaint);
				if (this.CurrentBitmap != this.pdfbmp) {
					this.CurrentBitmap.recycle();
					this.CurrentBitmap = null;
				}
			}
			if (dirtydib != null) {
				Matrix m = new Matrix();
				m.postRotate(0, rect.width() / 2, rect.height() / 2);
				m.postTranslate(rect.left, rect.top);
				canvas.drawBitmap(dirtydib, m, mPaint);
			}
		} finally {
			if (canvas != null) {
				Holder.unlockCanvasAndPost(canvas);
			}
		}
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		// TODO Auto-generated method stub

	}

	public void surfaceCreated(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		int count = 0;
		while (count < 2) {
			OnDraw();
			count++;
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub

	}

	public void run() {
		// TODO Auto-generated method stub
		while (!Thread.currentThread().isInterrupted()) {
			CPSIAction action = getHeadAction();
			if (action != null) {
				Log.e("xxxxxxxxxx run run run", "" + pDoc.getCurPSIHandle()
						+ action.x + action.y + action.nPressures + action.flag);
				EMBJavaSupport.FPSIAddPoint(pDoc.getCurPSIHandle(), action.x,
						action.y, action.nPressures, action.flag);
			}
		}
	}

	@Override
	public boolean onDown(MotionEvent e) {
		// TODO Auto-generated method stub
		this.scrolling = true;
		return false;
	}

	// private final static int FLING_SIZE = 120;
	// private final static int VELOCITYLIMIT = 100;

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
			float velocityY) {
		// TODO Auto-generated method stub
		// if (true)
		// return false;
		// if (e1.getX() - e2.getX() > FLING_SIZE && velocityX > VELOCITYLIMIT
		// && velocityY < VELOCITYLIMIT) {
		// this.nextPage();
		// return true;
		// } else if (e1.getX() - e2.getX() < -FLING_SIZE
		// && velocityX > VELOCITYLIMIT && velocityY < VELOCITYLIMIT) {
		// this.previousPage();
		// return true;
		// }
		return false;
	}

	private void openLink(int x, int y) {
		PointF point = new EMBJavaSupport().new PointF();
		point.x = x + nStartX - leftBound;
		point.y = y + nStartY - topBound;
		if (point.x < 0 || point.y < 0)
			return;
		int textPage = EMBJavaSupport.FPDFTextLoadPage(pDoc
				.getCurrentPageHandler());
		EMBJavaSupport.FPDFPageDeviceToPagePointF(pDoc.getCurrentPageHandler(),
				0, 0, zoomStatus.getWidth(), zoomStatus.getHeight(), 0, point);
		String url = EMBJavaSupport.FPDFLinkOpenOuterLink(textPage,
				(int) point.x, (int) point.y);
		Log.i("link", url);
		if (url.length() > 0
				&& (url.startsWith("http") || url.startsWith("HTTP")
						|| url.startsWith("HTTPS") || url.startsWith("https"))) {
			Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
			new Activity().startActivity(it);

		} else {
			int pageNumber = EMBJavaSupport.FPDFLinkOpenInnerLink(
					pDoc.getDocumentHandler(), pDoc.getCurrentPageHandler(),
					(int) point.x, (int) point.y);
			if (pageNumber > 0) {
				pDoc.gotoPage(pageNumber);
				this.showCurrentPage();
			}
		}
		EMBJavaSupport.FPDFTextCloseTextPage(textPage);
	}

	@Override
	public void onLongPress(MotionEvent e) {
		// TODO Auto-generated method stub
		if (this.zoomStatus != null) {
			this.openLink(nStartX + (int) e.getX(), nStartY + (int) e.getY());
			Log.i("pdfview", "screenx:" + (nStartX + (int) e.getX())
					+ ",screeny:" + (nStartY + (int) e.getY()));
		}
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
			float distanceY) {
		// TODO Auto-generated method stub
		// this.SetMartix(e2.getX() - e1.getX(), e2.getY() - e1.getY());
		if (this.scrolling)
			this.SetMartix(-distanceX, -distanceY);
		return true;
	}

	@Override
	public void onShowPress(MotionEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		// TODO Auto-generated method stub
		this.scrolling = false;
		return false;
	}

	@Override
	public boolean onSingleTapConfirmed(MotionEvent e) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onDoubleTap(MotionEvent e) {
		// TODO Auto-generated method stub
		if (this.zoomStatus != null) {
			this.zoomStatus.nextZoom(0);
			this.showCurrentPage();
			return true;
		}
		return false;
	}

	@Override
	public boolean onDoubleTapEvent(MotionEvent e) {
		// TODO Auto-generated method stub
		return false;
	}

	public void invalidate(float left, float top, float right, float bottom) {
		// TODO Auto-generated method stub
		if (this.mode == Mode.Form) {
			int l, t, r, b;
			RectangleF rect = new EMBJavaSupport().new RectangleF();
			rect.left = left;
			rect.top = top;
			rect.right = right;
			rect.bottom = bottom;
			EMBJavaSupport.FPDFPagePageToDeviceRectF(
					pDoc.getCurrentPageHandler(), 0, 0, nDisplayWidth,
					nDisplayHeight, 0, rect);
			l = (int) rect.left;
			t = (int) rect.top;
			r = (int) rect.right;
			b = (int) rect.bottom;
			Rect rc = new Rect(l, t, r, b);
			this.setDirtyRect(l, t, r, b);
			this.setDirtyBitmap(pDoc.getDirtyBitmap(rc, nDisplayWidth,
					nDisplayHeight));
			this.OnDraw();
		}
	}
}