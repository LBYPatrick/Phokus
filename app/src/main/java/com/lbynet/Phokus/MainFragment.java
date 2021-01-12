package com.lbynet.Phokus;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.lbynet.Phokus.listener.BMSListener;
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
    private Button btnZoom,
                   btnFps;
    private View rootView = null;
    private OrientationEventListener oel = null;
    private boolean isRecording = false;
    private int viewPortHeight = 0,
                maskedHeight = 0;

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
        return inflater.inflate(R.layout.fragment_main, container, false);
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

        CameraControl.initialize(rootView.findViewById(R.id.pv_preview));

        SysInfo.addBMSListener(new BMSListener() {
            @Override
            public boolean onUpdate(Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL,0);
                SAL.print("level: " + level);
                ((TextView)(rootView.findViewById(R.id.tv_bms_percentage))).setText(Integer.toString(level) + " %");
                return true;
            }
        });


        ConstraintLayout cl = rootView.findViewById(R.id.cl_preview_container);

        cl.post(() -> {
            cl.measure(View.MeasureSpec.EXACTLY,View.MeasureSpec.EXACTLY);

            viewPortHeight = cl.getHeight();

            maskedHeight = (int)(viewPortHeight * 0.25 / 2.00);
        });


        btnZoom = rootView.findViewById(R.id.btn_zoom);
        btnZoom.setText((int)CameraControl.getMinFocalLength() + "mm");
        btnZoom.setOnClickListener(this::onZoomButtonClicked);

        btnFps = rootView.findViewById(R.id.btn_fps);
        btnFps.setText(CameraControl.getVideoFps() + " FPS");
        btnFps.setOnClickListener(this::onFpsButtonClicked);

        rootView.findViewById(R.id.btn_widescreen).setOnClickListener(this::onWidescreenButtonClicked);
        rootView.findViewById(R.id.btn_facing).setOnClickListener(this::onFacingButtonClicked);
        rootView.findViewById(R.id.btn_record).setOnClickListener(this::onRecordButtonClicked);
        rootView.findViewById(R.id.pv_preview).setOnTouchListener(this::onFocusPointTouched);
    }

    boolean onFpsButtonClicked(View v){

        Button b = (Button) v;

        CameraControl.toggleVideoFps(new EventListener() {
            @Override
            public boolean onEventBegan(String extra) {

                UIHelper.runLater(requireContext(),() -> {
                    b.setEnabled(false);
                    b.setText(CameraControl.getVideoFps() + " FPS");
                });

                return super.onEventBegan(extra);
            }

            @Override
            public boolean onEventFinished(boolean isSuccess, String extra) {

                UIHelper.runLater(requireContext(),() -> {
                    b.setEnabled(true);
                });

                return super.onEventFinished(isSuccess, extra);
            }
        });

        return true;
    }

    boolean onZoomButtonClicked(View v) {
        Button b = (Button) v;

        CameraControl.toggleZoom(new EventListener() {
            @Override
            public boolean onEventBegan(String extra) {

                UIHelper.runLater(requireContext(),() -> {
                    b.setEnabled(false);
                });

                return super.onEventBegan(extra);
            }

            @Override
            public boolean onEventUpdated(String extra) {

                UIHelper.runLater(requireContext(), () -> {
                    b.setText((int)(Float.parseFloat(extra)) + "mm");
                });

                return super.onEventUpdated(extra);
            }

            @Override
            public boolean onEventFinished(boolean isSuccess, String extra) {

                UIHelper.runLater(requireContext(),() -> {
                    b.setEnabled(true);
                });

                return super.onEventFinished(isSuccess, extra);
            }
        });

        return true;
    }

    public boolean onFocusPointTouched(View view, MotionEvent motionEvent) {
        //Do nothing if the user is holding the focus rectangle instead of clicking
        if (motionEvent.getActionMasked() != MotionEvent.ACTION_UP) {
            return true;
        }

        if(CameraControl.isWidescreen()) {
            if(motionEvent.getY() < maskedHeight
                    || motionEvent.getY() > (viewPortHeight - maskedHeight)) {
                return false;
            }
        }

        CameraControl.focusToPoint(motionEvent.getX(), motionEvent.getY(), new EventListener() {});
        return true;
    }


    boolean onFacingButtonClicked(View v) {

        Button b = (Button) v;

        CameraControl.toggleCameraFacing(new EventListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public boolean onEventBegan(String extra) {

                UIHelper.runLater(requireContext(),() -> {


                    btnZoom.setText(
                            (CameraControl.isFrontFacing() ?
                                    (int)CameraControl.getFrontFacingFocalLength()
                                    : (int)CameraControl.getMinFocalLength())
                                    + "mm"

                    );


                    btnZoom.setEnabled(!CameraControl.isFrontFacing());

                    b.setEnabled(false);
                });
                return super.onEventBegan(extra);
            }

            @Override
            public boolean onEventFinished(boolean isSuccess, String extra) {

                UIHelper.runLater(requireContext(),() -> {
                    b.setEnabled(true);
                });
                return super.onEventFinished(isSuccess,extra);
            }
        });

        return true;
    }

    boolean onRecordButtonClicked(View v) {
        Button b = (Button) v;

        isRecording = !isRecording;

        //Disable buttons when needed (Since they are not relevant/should not be modified when recording videos)
        rootView.findViewById(R.id.btn_facing).setEnabled(!isRecording);
        rootView.findViewById(R.id.btn_widescreen).setEnabled(!isRecording);
        btnFps.setEnabled(!isRecording);

        CameraControl.toggleRecording(new EventListener() {
            @Override
            public boolean onEventBegan(String extra) {

                UIHelper.runLater(requireContext(),() -> { b.setEnabled(false); });
                return super.onEventBegan(extra);
            }

            @Override
            public boolean onEventFinished(boolean isSuccess, String extra) {

                UIHelper.runLater(requireContext(),() -> { b.setEnabled(true); });
                return super.onEventFinished(isSuccess,extra);
            }
        });

        return true;
    }

    boolean onWidescreenButtonClicked(View v) {

        Button b = (Button) v;

        CameraControl.toggleWidescreen(new EventListener() {
                @Override
                public boolean onEventBegan(String extra) {

                    UIHelper.runLater(requireContext(),() -> { b.setEnabled(false); });
                    return super.onEventBegan(extra);
                }

                @Override
                public boolean onEventFinished(boolean isSuccess, String extra) {

                    UIHelper.runLater(requireContext(),() -> { b.setEnabled(true); });
                    return super.onEventFinished(isSuccess,extra);
                }
            });

        return true;
    }
}