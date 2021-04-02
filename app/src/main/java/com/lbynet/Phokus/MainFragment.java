package com.lbynet.Phokus;

import android.content.Intent;
import android.os.BatteryManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.lbynet.Phokus.backend.CameraControl;
import com.lbynet.Phokus.listener.BMSListener;
import com.lbynet.Phokus.listener.ColorListener;
import com.lbynet.Phokus.listener.EventListener;
import com.lbynet.Phokus.ui.UIHelper;
import com.lbynet.Phokus.utils.SAL;
import com.lbynet.Phokus.utils.SysInfo;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MainFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MainFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;
    private CardView cardZoom,
                     cardRecord,
                     cardCapture;
    private PreviewView preview;
    private View rootView = null,
                 focusCircle = null,
                 iconRecordStart = null,
                 iconRecordStop  = null,
                 iconCaptureIdle = null;
    private OrientationEventListener oel = null;
    private double previewFullWidth = 0,
                previewCompressedWidth = 0,
                previewFullHeight = 0;
    public MainFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment MainFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static MainFragment newInstance(String param1, String param2) {
        MainFragment fragment = new MainFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        oel = new OrientationEventListener(requireContext()) {
            @Override
            public void onOrientationChanged(int orientation) {
                CameraControl.updateRotation(UIHelper.getSurfaceOrientation(orientation), new EventListener() {});
            }
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.layout_viewfinder, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        oel.enable();
    }

    @Override
    public void onPause() {
        super.onPause();
        oel.disable();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rootView = view;

        SysInfo.addListener(new BMSListener() {
            @Override
            public boolean onUpdate(Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL,0);
                SAL.print("level: " + level);
                //((TextView)(rootView.findViewById(R.id.tv_bms_percentage))).setText(Integer.toString(level) + " %");
                return true;
            }
        });


        findViews();
        setupViewVisibility();
        setupListeners();
        setupMisc();
        CameraControl.initialize(rootView.findViewById(R.id.preview));
    }

    void findViews() {
        cardZoom = rootView.findViewById(R.id.card_zoom);

        focusCircle = rootView.findViewById(R.id.focus_circle);
        iconCaptureIdle = rootView.findViewById(R.id.v_caputre_idle);
        iconRecordStop = rootView.findViewById(R.id.v_record_stop);
        iconRecordStart = rootView.findViewById(R.id.v_record_start);
        cardRecord = rootView.findViewById(R.id.card_record);
        cardCapture = rootView.findViewById(R.id.card_capture);
        preview = rootView.findViewById(R.id.preview);
    }

    void setupViewVisibility() {
        UIHelper.setViewAlpha(focusCircle,0,0);
        UIHelper.setViewAlpha(iconRecordStop,0,0);
    }

    void setupListeners() {
        cardZoom.setOnClickListener(this::onZoomButtonClicked);
        preview.setOnTouchListener(this::onPreviewTouched);
        cardRecord.setOnClickListener(this::onRecordClicked);
    }

    void setupMisc() {

        ((TextView)cardZoom.findViewById(R.id.text_zoom)).setText((int)CameraControl.getEquivalentFocalLength(0) + "mm");

        preview.post(() -> {
            preview.measure(View.MeasureSpec.EXACTLY,View.MeasureSpec.EXACTLY);

            previewFullWidth = preview.getWidth();
            previewFullHeight = preview.getHeight();
            previewCompressedWidth = previewFullWidth * 0.75;

            ViewGroup.LayoutParams params = preview.getLayoutParams();
            params.width = (int) previewCompressedWidth;
            preview.setLayoutParams(params);

        });
    }

    boolean onZoomButtonClicked(View v) {
        CardView b = (CardView) v;

        CameraControl.toggleZoom(new EventListener() {
            @Override
            public boolean onEventBegan(String extra) {

                UIHelper.runLater(requireContext(),() -> {
                    b.setClickable(false);
                });

                return super.onEventBegan(extra);
            }

            @Override
            public boolean onEventUpdated(String extra) {

                UIHelper.runLater(requireContext(), () -> {
                    ((TextView)cardZoom.findViewById(R.id.text_zoom)).setText((int)(Float.parseFloat(extra)) + "mm");
                });

                return super.onEventUpdated(extra);
            }

            @Override
            public boolean onEventFinished(boolean isSuccess, String extra) {

                UIHelper.runLater(requireContext(),() -> {
                    b.setClickable(true);
                });

                return super.onEventFinished(isSuccess, extra);
            }
        });

        return true;
    }

    public boolean onPreviewTouched(View view, MotionEvent motionEvent) {
        //Do nothing if the user is holding the focus rectangle instead of clicking
        if (motionEvent.getActionMasked() != MotionEvent.ACTION_UP) {
            return true;
        }

        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) focusCircle.getLayoutParams();
        params.setMargins((int)motionEvent.getX() - 70, (int)motionEvent.getY() -70,0,0);

        focusCircle.setLayoutParams(params);

        CameraControl.focusToPoint(motionEvent.getX(), motionEvent.getY(), new EventListener() {
            @Override
            public boolean onEventBegan(String extra) {

                UIHelper.setViewAlpha(focusCircle,100,1);

                UIHelper.runLater(requireContext(),() -> {
                    focusCircle.getForeground().setTint(UIHelper.getColors(requireContext(),R.color.focus_busy)[0]);
                });
                return super.onEventBegan(extra);
            }

            @Override
            public boolean onEventUpdated(String extra) {

                int [] colors = UIHelper.getColors(requireContext(),R.color.focus_busy,R.color.focus_success);

                UIHelper.getColorAnimator(new ColorListener() {
                    @Override
                    public void onColorUpdated(int newColor) {
                        UIHelper.runLater(requireContext(),() -> {
                            focusCircle.getForeground().setTint(newColor);
                        });
                    }
                },100,true,colors[0],colors[1]).start();

                return super.onEventUpdated(extra);
            }
        });
        return true;
    }

    boolean onRecordClicked(View v) {

        int [] colors = UIHelper.getColors(requireContext(),R.color.record_inactive,R.color.record_active);

        CameraControl.toggleRecording(new EventListener() {
            @Override
            public boolean onEventBegan(String extra) {

                UIHelper.runLater(requireContext(), () -> {cardRecord.setClickable(false);});

                return super.onEventBegan(extra);
            }

            @Override
            public boolean onEventUpdated(String extra) {

                if(extra.equals("START") || extra.equals("END")) {
                    boolean isFilming = extra.equals("START");

                    UIHelper.setViewAlpha(iconRecordStop, 200, isFilming ? 1 : 0);
                    UIHelper.setViewAlpha(iconRecordStart, 200, isFilming ? 0 : 1);

                    UIHelper.runLater(requireContext(), () -> {

                        UIHelper.getColorAnimator(new ColorListener() {
                            @Override
                            public void onColorUpdated(int newColor) {
                                UIHelper.runLater(requireContext(), () -> {
                                    cardRecord.setCardBackgroundColor(newColor);
                                });
                            }
                        }, 200, true, isFilming ? colors : new int[]{colors[1], colors[0]}).start();
                        cardRecord.setClickable(true);

                    });
                }
                else if(extra.equals("Updating widescreen")) {
                    UIHelper.runLater(requireContext(),() -> {

                        ViewGroup.LayoutParams params = preview.getLayoutParams();

                        params.width = (int)((CameraControl.isFilming() || CameraControl.isWidescreen()) ? previewFullWidth : previewCompressedWidth);

                        preview.setLayoutParams(params);
                    });
                }

                return super.onEventUpdated(extra);
            }
        });

        return true;
    }
}