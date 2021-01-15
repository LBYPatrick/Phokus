package com.lbynet.Phokus;

import android.content.Intent;
import android.os.BatteryManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Preview;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
    private CardView cardZoom;
    private View rootView = null,
                 focusCircle = null;
    private OrientationEventListener oel = null;
    private double previewFullWidth = 0,
                compressedWidth = 0,
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

        PreviewView preview = rootView.findViewById(R.id.preview);

        preview.post(() -> {
            preview.measure(View.MeasureSpec.EXACTLY,View.MeasureSpec.EXACTLY);

            previewFullWidth = preview.getWidth();
            previewFullHeight = preview.getHeight();
            compressedWidth = previewFullWidth * 0.75;

            ViewGroup.LayoutParams params = preview.getLayoutParams();
            params.width = (int)compressedWidth;
            preview.setLayoutParams(params);

        });

        CameraControl.initialize(rootView.findViewById(R.id.preview));

        SysInfo.addBMSListener(new BMSListener() {
            @Override
            public boolean onUpdate(Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL,0);
                SAL.print("level: " + level);
                //((TextView)(rootView.findViewById(R.id.tv_bms_percentage))).setText(Integer.toString(level) + " %");
                return true;
            }
        });


        cardZoom = rootView.findViewById(R.id.card_zoom);
        ((TextView)cardZoom.findViewById(R.id.text_zoom)).setText((int)CameraControl.getMinFocalLength() + "mm");
        cardZoom.setOnClickListener(this::onZoomButtonClicked);

        rootView.findViewById(R.id.preview).setOnTouchListener(this::onPreviewTouched);

        focusCircle = rootView.findViewById(R.id.focus_circle);
        focusCircle.setVisibility(View.INVISIBLE);
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

        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) rootView.findViewById(R.id.focus_circle).getLayoutParams();

        params.setMargins((int)motionEvent.getX() - 70, (int)motionEvent.getY() -70,0,0);

        rootView.findViewById(R.id.focus_circle).setLayoutParams(params);

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
}